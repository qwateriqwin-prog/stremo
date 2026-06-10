package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.ChannelEntity
import com.example.data.EPGProgram
import com.example.data.IPTVDatabase
import com.example.data.IPTVRepository
import com.example.data.PlaylistEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IPTVViewModel(application: Application) : AndroidViewModel(application) {

    // Initialize Room Database with migration fallback
    private val database = Room.databaseBuilder(
        application,
        IPTVDatabase::class.java,
        "iptv_database"
    ).fallbackToDestructiveMigration().build()

    private val repository = IPTVRepository(database.iptvDao(), application)

    // Raw database queries
    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allChannels: StateFlow<List<ChannelEntity>> = repository.allChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<ChannelEntity>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state filters
    private val _activePlaylistId = MutableStateFlow<Int?>(null)
    val activePlaylistId: StateFlow<Int?> = _activePlaylistId.asStateFlow()

    private val _activeCategory = MutableStateFlow<String?>(null)
    val activeCategory: StateFlow<String?> = _activeCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _userPreferredOrder = MutableStateFlow("DEFAULT") // "DEFAULT", "ALPHABETICAL", "FAVORITE_FIRST"
    val userPreferredOrder: StateFlow<String> = _userPreferredOrder.asStateFlow()

    // Active Channel Playing
    private val _activeChannel = MutableStateFlow<ChannelEntity?>(null)
    val activeChannel: StateFlow<ChannelEntity?> = _activeChannel.asStateFlow()

    // Import states
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    // Theme state
    private val _isDarkMode = MutableStateFlow(true) // Dark by default for premium feel
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // UI Reactive Filtered list of channels (Combining 5 flows is fully type-safe in Kotlin)
    val filteredChannels: StateFlow<List<ChannelEntity>> = combine(
        allChannels,
        _activePlaylistId,
        _activeCategory,
        _searchQuery,
        _userPreferredOrder
    ) { channels, playlistId, category, query, order ->
        var list = channels

        // Filter by playlist
        if (playlistId != null) {
            list = list.filter { it.playlistId == playlistId }
        }

        // Filter by category
        if (!category.isNullOrBlank()) {
            list = list.filter { it.groupTitle == category }
        }

        // Filter by query
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.groupTitle?.contains(query, ignoreCase = true) == true
            }
        }

        // Apply sorting
        when (order) {
            "ALPHABETICAL" -> list.sortedBy { it.name }
            "FAVORITE_FIRST" -> {
                // Sort favorite channels to the top
                list.sortedWith(compareBy({ !it.isFavorite }, { it.orderIndex }))
            }
            else -> list.sortedBy { it.orderIndex }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of exclusive categories inside the active channel scope
    val availableCategories: StateFlow<List<String>> = combine(
        allChannels,
        _activePlaylistId
    ) { channels, playlistId ->
        val filtered = if (playlistId != null) {
            channels.filter { it.playlistId == playlistId }
        } else {
            channels
        }
        filtered.mapNotNull { it.groupTitle }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of categories with their corresponding channel counts
    val categoriesWithCounts: StateFlow<Map<String, Int>> = combine(
        allChannels,
        _activePlaylistId
    ) { channels, playlistId ->
        val filtered = if (playlistId != null) {
            channels.filter { it.playlistId == playlistId }
        } else {
            channels
        }
        filtered.groupBy { it.groupTitle ?: "" }
            .filterKeys { it.isNotBlank() }
            .mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // EPG schedules for active playing channel
    private val _channelEPG = MutableStateFlow<List<EPGProgram>>(emptyList())
    val channelEPG: StateFlow<List<EPGProgram>> = _channelEPG.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("iptv_settings", android.content.Context.MODE_PRIVATE)

    private val _recentlyWatchedIds = MutableStateFlow<List<Int>>(
        sharedPrefs.getString("recently_watched_ids", "")
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
    )

    val recentlyWatchedChannels: StateFlow<List<ChannelEntity>> = combine(
        allChannels,
        _recentlyWatchedIds
    ) { channels, ids ->
        ids.mapNotNull { id -> channels.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _globalUserAgent = MutableStateFlow(sharedPrefs.getString("global_user_agent", "Smart-IPTV-Agent-Pro") ?: "Smart-IPTV-Agent-Pro")
    val globalUserAgent: StateFlow<String> = _globalUserAgent.asStateFlow()

    fun updateGlobalUserAgent(userAgent: String) {
        sharedPrefs.edit().putString("global_user_agent", userAgent).apply()
        _globalUserAgent.value = userAgent
    }

    fun selectPlaylist(playlistId: Int?) {
        _activePlaylistId.value = playlistId
        _activeCategory.value = null // Reset category filter on playlist change
    }

    fun selectCategory(category: String?) {
        _activeCategory.value = category
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOrder(order: String) {
        _userPreferredOrder.value = order
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun playChannel(channel: ChannelEntity?) {
        _activeChannel.value = channel
        if (channel != null) {
            // Immediately generate mock/real EPG schedules for this channel
            _channelEPG.value = repository.generateEPGForChannel(channel)

            // Update recently watched list
            val currentIds = _recentlyWatchedIds.value.toMutableList()
            currentIds.remove(channel.id) // Remove duplicate if exists
            currentIds.add(0, channel.id) // Prepend to top
            val updatedIds = currentIds.take(5) // Limit to last 5
            _recentlyWatchedIds.value = updatedIds
            sharedPrefs.edit().putString("recently_watched_ids", updatedIds.joinToString(",")).apply()
        } else {
            _channelEPG.value = emptyList()
        }
    }

    /**
     * Toggles a channel's favorite status reactively in the database
     */
    fun toggleFavorite(channelId: Int, isFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(channelId, isFav)
        }
    }

    /**
     * Delete an imported playlist and cascade-delete all its channels
     */
    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            // If the playing channel belonged to this playlist, stop playing
            val current = _activeChannel.value
            if (current != null && current.playlistId == playlistId) {
                playChannel(null)
            }
            // Clear filters if active
            if (_activePlaylistId.value == playlistId) {
                _activePlaylistId.value = null
                _activeCategory.value = null
            }
        }
    }

    /**
     * Get User Agent associated with a channel's playlist
     */
    fun getUserAgentForChannel(channel: ChannelEntity): String? {
        val list = playlists.value
        val playlistUa = list.find { it.id == channel.playlistId }?.customUserAgent
        return if (!playlistUa.isNullOrBlank()) playlistUa else _globalUserAgent.value
    }

    /**
     * Sequential Navigation: Play Next Channel
     */
    fun playNextChannel() {
        val currentList = filteredChannels.value
        val currentChannel = _activeChannel.value ?: return
        val currentIndex = currentList.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex != -1 && currentIndex < currentList.size - 1) {
            playChannel(currentList[currentIndex + 1])
        } else if (currentList.isNotEmpty()) {
            playChannel(currentList.first()) // Loop around
        }
    }

    /**
     * Sequential Navigation: Play Previous Channel
     */
    fun playPreviousChannel() {
        val currentList = filteredChannels.value
        val currentChannel = _activeChannel.value ?: return
        val currentIndex = currentList.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex > 0) {
            playChannel(currentList[currentIndex - 1])
        } else if (currentList.isNotEmpty()) {
            playChannel(currentList.last()) // Loop around
        }
    }

    /**
     * Importing flow
     */
    fun importM3U(name: String, url: String?, localUri: Uri?, userAgent: String?) {
        viewModelScope.launch {
            _isImporting.value = true
            _importMessage.value = "جاري استيراد القنوات وتحديث دليل البرامج..."
            val result = repository.importPlaylist(
                name = name.ifBlank { "سيرفر المستندات" },
                url = url,
                localUri = localUri,
                userAgent = userAgent
            )
            _isImporting.value = false
            if (result.isSuccess) {
                val pl = result.getOrThrow()
                _importMessage.value = "تم استيراد قائمة (${pl.name}) بنجاح!"
                _activePlaylistId.value = pl.id // Switch to imported list automatically
                _activeCategory.value = null
            } else {
                _importMessage.value = "خطأ أثناء الاستيراد: ${result.exceptionOrNull()?.localizedMessage ?: "تنسيق غير مدعوم"}"
            }
        }
    }

    /**
     * Import MAG Portal
     */
    fun importMagPortal(name: String, portalUrl: String, macAddress: String, userAgent: String?) {
        viewModelScope.launch {
            _isImporting.value = true
            _importMessage.value = "جاري الاتصال بـ البورتال واستيراد قنوات الـ MAC..."
            val result = repository.importMagPortal(
                name = name.ifBlank { "بورتال MAC" },
                portalUrl = portalUrl,
                macAddress = macAddress,
                userAgent = userAgent
            )
            _isImporting.value = false
            if (result.isSuccess) {
                val pl = result.getOrThrow()
                _importMessage.value = "تم استيراد بورتال الـ MAC (${pl.name}) بنجاح!"
                _activePlaylistId.value = pl.id
                _activeCategory.value = null
            } else {
                _importMessage.value = "خطأ أثناء استيراد البورتال: ${result.exceptionOrNull()?.localizedMessage ?: "خطأ غير معروف"}"
            }
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }

    /**
     * Triggers dynamic mock population on initial empty boot so users can instantly test playback
     */
    fun seedDefaultPlaylistIfEmpty() {
        viewModelScope.launch {
            if (playlists.value.isEmpty() && allChannels.value.isEmpty()) {
                _isImporting.value = true
                _importMessage.value = "جاري إنشاء سيرفر اختبار لقنوات TS و M3U..."
                try {
                    val sampleName = "سيرفر البث الافتراضي (تجريبي)"
                    val playlist = PlaylistEntity(
                        name = sampleName,
                        customUserAgent = "Smart-IPTV-Agent-Pro"
                    )
                    val playlistId = database.iptvDao().insertPlaylist(playlist).toInt()
                    
                    val samples = listOf(
                        ChannelEntity(
                            playlistId = playlistId,
                            name = "BeIN Sports Match 1 HD (Live Test)",
                            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                            logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=128",
                            groupTitle = "رياضة وطب عربية",
                            tvgId = "bein_sports_hd"
                        ),
                        ChannelEntity(
                            playlistId = playlistId,
                            name = "Sky Movies Action HD (TS Live)",
                            url = "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4v3/gear1/prog_index.m3u8",
                            logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=128",
                            groupTitle = "أفلام ومسلسلات",
                            tvgId = "movies_action"
                        ),
                        ChannelEntity(
                            playlistId = playlistId,
                            name = "Al Jazeera Live HD (مباشر الجزيرة)",
                            url = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                            logoUrl = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=128",
                            groupTitle = "أخبار وتقارير",
                            tvgId = "news_jazeera"
                        ),
                        ChannelEntity(
                            playlistId = playlistId,
                            name = "Cartoon Network Arabia Test",
                            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                            logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=128",
                            groupTitle = "أطفال ومسليات",
                            tvgId = "kids_cn"
                        ),
                        ChannelEntity(
                            playlistId = playlistId,
                            name = "National Geographic Live",
                            url = "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4v3/gear2/prog_index.m3u8",
                            logoUrl = "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=128",
                            groupTitle = "وثائقي وطبيعة",
                            tvgId = "documentary_natgeo"
                        ),
                        ChannelEntity(
                            playlistId = playlistId,
                            name = "TS Broadcast Live Server Test (6)",
                            url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                            logoUrl = "https://images.unsplash.com/photo-1478737270239-2f02b77fc618?w=128",
                            groupTitle = "سيرفرات TS المباشرة",
                            tvgId = "ts_live_stream"
                        )
                    )
                    database.iptvDao().insertChannels(samples)
                    _importMessage.value = "تمت تهيئة سيرفر البث الافتراضي بنجاح!"
                } catch (e: Exception) {
                    _importMessage.value = "فشلت التهيأة الافتراضية"
                } finally {
                    _isImporting.value = false
                }
            }
        }
    }
}
