package com.example.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ChannelEntity
import com.example.data.EPGProgram
import com.example.data.PlaylistEntity
import java.util.Date

// Premium Sophisticated Dark Theme Colors
val ObsidianBg = Color(0xFF0F0F12)
val CardMidnight = Color(0xFF1C1B1F)
val AccentRed = Color(0xFFB3261E)
val AccentCyan = Color(0xFFD0BCFF)
val PremiumGold = Color(0xFFFFD700)
val SoftGray = Color(0xFF938F99)
val BorderColor = Color(0xFF36343B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: IPTVViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Collect Reactive States
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val channels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val activePlaylistId by viewModel.activePlaylistId.collectAsStateWithLifecycle()
    val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.userPreferredOrder.collectAsStateWithLifecycle()
    val activeChannel by viewModel.activeChannel.collectAsStateWithLifecycle()
    val channelEPG by viewModel.channelEPG.collectAsStateWithLifecycle()
    val recentlyWatchedChannels by viewModel.recentlyWatchedChannels.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val globalUserAgent by viewModel.globalUserAgent.collectAsStateWithLifecycle()

    var showImportDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEPGDrawerChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    // Seed default server if database starts clean
    LaunchedEffect(playlists) {
        if (playlists.isEmpty()) {
            viewModel.seedDefaultPlaylistIfEmpty()
        }
    }

    // Dismiss message banners automatically
    LaunchedEffect(importMessage) {
        if (importMessage != null) {
            kotlinx.coroutines.delay(5000)
            viewModel.clearImportMessage()
        }
    }

    // Material Theme Wrapper matching isDarkMode state
    val themeBgColor = if (isDarkMode) ObsidianBg else Color(0xFFF6F8FA)
    val themeSurfaceColor = if (isDarkMode) CardMidnight else Color.White
    val themeTextColor = if (isDarkMode) Color(0xFFE6E1E5) else Color(0xFF1C1D24)
    val borderBrush = Brush.linearGradient(
        colors = listOf(AccentRed.copy(alpha = 0.8f), AccentCyan.copy(alpha = 0.8f))
    )

    if (activeChannel != null) {
        BackHandler {
            viewModel.playChannel(null)
        }
        val userAgent = viewModel.getUserAgentForChannel(activeChannel!!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ChannelVideoPlayer(
                channel = activeChannel!!,
                customUserAgent = userAgent,
                onNextChannel = { viewModel.playNextChannel() },
                onPreviousChannel = { viewModel.playPreviousChannel() },
                onClosePlayer = { viewModel.playChannel(null) },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Scaffold(
        modifier = modifier.background(themeBgColor),
        containerColor = themeBgColor,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(AccentRed, AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Logo icon",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "البدر IPTV برو",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = themeTextColor
                            )
                            Text(
                                text = "مستقبل تشغيل وسيرفرات القنوات",
                                fontSize = 10.sp,
                                color = SoftGray
                            )
                        }
                    }
                },
                actions = {
                    // Dark theme toggle
                    IconButton(onClick = { viewModel.toggleDarkMode() }) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme Toggle",
                            tint = if (isDarkMode) AccentCyan else Color.DarkGray
                        )
                    }

                    // Settings Button
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = if (isDarkMode) AccentCyan else Color.DarkGray
                        )
                    }

                    // Import Button
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إضافة سيرفر",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeSurfaceColor,
                    titleContentColor = themeTextColor
                ),
                modifier = Modifier.drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val y = size.height - strokeWidth / 2
                    drawLine(
                        color = BorderColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = strokeWidth
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Player Pane (Sticky on top once playing)
            AnimatedVisibility(
                visible = activeChannel != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                activeChannel?.let { channel ->
                    val userAgent = viewModel.getUserAgentForChannel(channel)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            ChannelVideoPlayer(
                                channel = channel,
                                customUserAgent = userAgent,
                                onNextChannel = { viewModel.playNextChannel() },
                                onPreviousChannel = { viewModel.playPreviousChannel() },
                                onClosePlayer = { viewModel.playChannel(null) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        // Mini EPG details overlay under active screen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardMidnight)
                                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val currentProg = channelEPG.firstOrNull { it.progress > 0f }
                                Text(
                                    text = "يعرض الآن على: ${channel.name}",
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentProg?.title ?: "البث المباشر المستمر",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                currentProg?.let {
                                    LinearProgressIndicator(
                                        progress = { it.progress },
                                        color = AccentRed,
                                        trackColor = Color.DarkGray,
                                        modifier = Modifier
                                            .fillMaxWidth(0.95f)
                                            .padding(top = 6.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }

                            Button(
                                onClick = { showEPGDrawerChannel = channel },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "EPG",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("دليل البرامج", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Status import snackbar feedback
            AnimatedVisibility(visible = importMessage != null) {
                importMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (msg.contains("خطأ")) AccentRed else AccentCyan.copy(alpha = 0.9f))
                            .padding(8.dp)
                    )
                }
            }

            if (isImporting) {
                LinearProgressIndicator(
                    color = AccentRed,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // FILTER & SEARCH CONTROL DASHBOARD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeSurfaceColor)
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        val y = size.height - strokeWidth / 2
                        drawLine(
                            color = BorderColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                    }
                    .padding(12.dp)
            ) {
                // Search Field
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("ابحث عن القنوات بالاسم أو التصنيف...", fontSize = 13.sp, color = SoftGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = SoftGray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = SoftGray)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = themeBgColor,
                        unfocusedContainerColor = themeBgColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = themeTextColor,
                        unfocusedTextColor = themeTextColor
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Playlists lists / server row
                Text(
                    text = "سيرفرات وقوائم التشغيل المضافة 🌐",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeTextColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        // All channels filter pill
                        FilterChip(
                            selected = activePlaylistId == null,
                            onClick = { viewModel.selectPlaylist(null) },
                            label = { Text("الكل / جميع السيرفرات") },
                            leadingIcon = { Icon(Icons.Default.Tv, contentDescription = "All", modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed.copy(alpha = 0.2f),
                                selectedLabelColor = AccentRed,
                                selectedLeadingIconColor = AccentRed,
                                labelColor = SoftGray
                            )
                        )
                    }

                    items(playlists) { playlist ->
                        Box {
                            FilterChip(
                                selected = activePlaylistId == playlist.id,
                                onClick = { viewModel.selectPlaylist(playlist.id) },
                                label = { Text(playlist.name) },
                                trailingIcon = {
                                    // Delete server action
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Server",
                                        tint = Color.Red.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                viewModel.deletePlaylist(playlist.id)
                                            }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = AccentCyan,
                                    labelColor = SoftGray
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Sorting row and EPG trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الترتيب:", fontSize = 11.sp, color = SoftGray)
                        Spacer(modifier = Modifier.width(6.dp))

                        val orders = listOf(
                            "DEFAULT" to "الافتراضي 📉",
                            "ALPHABETICAL" to "أبجدي أ-ي 🔠",
                            "FAVORITE_FIRST" to "المفضلة أولاً ⭐"
                        )
                        orders.forEach { (key, label) ->
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (sortOrder == key) FontWeight.Bold else FontWeight.Normal,
                                color = if (sortOrder == key) AccentRed else SoftGray,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.updateSortOrder(key) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
            }

            // HORIZONTAL CATEGORIES ROW
            if (availableCategories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeBgColor)
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Button(
                            onClick = { viewModel.selectCategory(null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeCategory == null) AccentRed else themeSurfaceColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                "جميع التصنيفات",
                                color = if (activeCategory == null) Color.White else themeTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(availableCategories) { category ->
                        val isSelected = activeCategory == category
                        Button(
                            onClick = { viewModel.selectCategory(category) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) AccentRed else themeSurfaceColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) Color.White else themeTextColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // RECENTLY WATCHED SECTION
            if (recentlyWatchedChannels.isNotEmpty() && channels.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "بث سريع ⚡",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "شوهد مؤخراً 🕒",
                            color = themeTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = true // Supports Arabic RTL scrolling
                    ) {
                        items(recentlyWatchedChannels, key = { "recent_${it.id}" }) { channel ->
                            val isPlayingThis = activeChannel?.id == channel.id
                            
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isPlayingThis) AccentRed.copy(alpha = 0.15f) else themeSurfaceColor)
                                    .border(
                                        1.dp,
                                        if (isPlayingThis) AccentRed else BorderColor,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.playChannel(channel) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = channel.name,
                                        color = themeTextColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        textAlign = TextAlign.Right
                                    )
                                    if (!channel.groupTitle.isNullOrBlank()) {
                                        Text(
                                            text = channel.groupTitle!!,
                                            color = SoftGray,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            textAlign = TextAlign.Right
                                        )
                                    }
                                }

                                // Logo Circle or Backstop
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!channel.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = channel.logoUrl,
                                            contentDescription = channel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        val initial = channel.name.firstOrNull()?.toString() ?: "TV"
                                        Text(
                                            text = initial,
                                            color = if (isPlayingThis) AccentRed else AccentCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = BorderColor.copy(alpha = 0.6f))
                }
            }

            // MAIN CHANNEL GRID
            if (channels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TvOff,
                            contentDescription = "Empty Channels",
                            tint = SoftGray,
                            modifier = Modifier.size(68.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "لا توجد قنوات متاحة حالياً",
                            color = themeTextColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "اضغط على \"إضافة سيرفر\" بالأعلى لاستيراد قنوات من سيرفر M3U/TS أو اختر ملفاً محلياً، أو جرب تهيأة خادم البث التجريبي التلقائي.",
                            color = SoftGray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.seedDefaultPlaylistIfEmpty() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                        ) {
                            Text("تهيئة السيرفر التجريبي المباشر 🔄", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 165.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(channels, key = { it.id }) { channel ->
                        val isPlayingThis = activeChannel?.id == channel.id
                        ChannelGridCard(
                            channel = channel,
                            isPlaying = isPlayingThis,
                            onClick = { viewModel.playChannel(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel.id, !channel.isFavorite) },
                            onViewSchedule = { showEPGDrawerChannel = channel },
                            surfaceColor = themeSurfaceColor,
                            textColor = themeTextColor,
                            borderBrush = if (isPlayingThis) borderBrush else null
                        )
                    }
                }
            }
        }
    }

    // 1. IMPORT PLAYLIST DIALOG
    if (showImportDialog) {
        ImportPlaylistDialog(
            isDarkMode = isDarkMode,
            surfaceColor = themeSurfaceColor,
            bgColor = themeBgColor,
            textColor = themeTextColor,
            onDismiss = { showImportDialog = false },
            onImport = { name, url, uri, agent ->
                viewModel.importM3U(name, url, uri, agent)
                showImportDialog = false
            },
            onMagImport = { name, portalUrl, macAddress, agent ->
                viewModel.importMagPortal(name, portalUrl, macAddress, agent)
                showImportDialog = false
            }
        )
    }

    // 2. ELECTRONIC PROGRAM GUIDE (EPG) DRAWER / BOTTOM SHEET OVERLAY
    if (showEPGDrawerChannel != null) {
        val channel = showEPGDrawerChannel!!
        EPGScheduleDialog(
            channel = channel,
            programs = viewModel.channelEPG.value.ifEmpty {
                // If not active, generate on the fly
                remember(channel.id) {
                    val app = context.applicationContext as android.app.Application
                    com.example.data.IPTVRepository(
                        com.example.data.IPTVDatabase.getInstance(app).iptvDao(),
                        app
                    ).generateEPGForChannel(channel)
                }
            },
            surfaceColor = themeSurfaceColor,
            bgColor = themeBgColor,
            textColor = themeTextColor,
            onDismiss = { showEPGDrawerChannel = null }
        )
    }

    // 3. PERSISTENT GLOBAL SETTINGS DIALOG
    if (showSettingsDialog) {
        GlobalSettingsDialog(
            isDarkMode = isDarkMode,
            surfaceColor = themeSurfaceColor,
            bgColor = themeBgColor,
            textColor = themeTextColor,
            currentGlobalUserAgent = globalUserAgent,
            onSave = { updatedUserAgent ->
                viewModel.updateGlobalUserAgent(updatedUserAgent)
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
} }

/**
 * Custom styled grid item representing a broadcast channel
 */
@Composable
fun ChannelGridCard(
    channel: ChannelEntity,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onViewSchedule: () -> Unit,
    surfaceColor: Color,
    textColor: Color,
    borderBrush: Brush?,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = if (borderBrush != null) BorderStroke(2.dp, borderBrush) else BorderStroke(1.dp, BorderColor),
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (isPlaying) 6.dp else 1.dp, RoundedCornerShape(12.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Channel Logo / AsyncImage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(72.dp)
                        )
                    } else {
                        // Dynamic vector letter inside circle
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(AccentCyan, Color.Transparent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Channel logo backup",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Active Playing indicator
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "يعرض الآن 📺",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Channel Name
                Text(
                    text = channel.name,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Category tag
                channel.groupTitle?.let { group ->
                    Text(
                        text = group,
                        color = SoftGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // EPG triggering link
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onViewSchedule() }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "EPG",
                            tint = AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("البرامج EPG", fontSize = 10.sp, color = AccentCyan, fontWeight = FontWeight.Medium)
                    }

                    // Love Heart Favorite Toggle
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (channel.isFavorite) AccentRed else SoftGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * High-fidelity Dialog layout for adding/importing servers
 */
/**
 * High-fidelity Dialog layout for adding/importing servers
 * Supports M3U Lists, Xtream Codes, and MAG/MAC Portals
 */
@Composable
fun ImportPlaylistDialog(
    isDarkMode: Boolean,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    onDismiss: () -> Unit,
    onImport: (name: String, url: String?, localUri: Uri?, userAgent: String?) -> Unit,
    onMagImport: (name: String, portalUrl: String, macAddress: String, userAgent: String?) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: M3U, 1: Xtream, 2: MAG/MAC

    // Common
    var serverName by remember { mutableStateOf("") }
    var customUserAgent by remember { mutableStateOf("") }

    // Tab 0: M3U Link / Local File
    var playlistUrl by remember { mutableStateOf("") }
    var localFileUri by remember { mutableStateOf<Uri?>(null) }
    var localFileName by remember { mutableStateOf<String?>(null) }

    // Tab 1: Xtream Codes
    var xtreamHost by remember { mutableStateOf("") }
    var xtreamPort by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }

    // Tab 2: MAG / MAC Portal
    var portalUrl by remember { mutableStateOf("") }
    var macAddress by remember { mutableStateOf("00:1A:79:") }

    // Launcher to select `.m3u` / `.ts` local files from device
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            localFileUri = uri
            localFileName = uri.lastPathSegment ?: "ملف IPTV مضاف"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End // RTL supportive alignment
            ) {
                Text(
                    text = "إضافة اشتراك جديد ⚡",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "إختر نوع السيرفر من الخيارات بالأسفل لتعبئة البيانات",
                    fontSize = 11.sp,
                    color = SoftGray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Tab Selector Styled for IPTV Client
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        "بورتال MAG / MAC" to 2,
                        "سيرفر Xtream" to 1,
                        "رابط أو ملف M3U" to 0
                    ).forEach { (title, tabIndex) ->
                        val isSelected = selectedTab == tabIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentCyan else Color.Transparent)
                                .clickable { selectedTab = tabIndex }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else SoftGray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Server / Playlist Name (Common for all tabs)
                Text(
                    text = "اسم الخادم/السيرفر (اختياري):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    placeholder = { Text("مثال: خادم القنوات المفضل", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = AccentCyan,
                        unfocusedLabelColor = SoftGray
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // RENDER FIELDS ACCORDING TO SELECTED TAB
                when (selectedTab) {
                    0 -> {
                        // OPTION A: M3U Link
                        Text(
                            text = "رابط خادم البث (M3U URL):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = playlistUrl,
                            onValueChange = {
                                playlistUrl = it
                                if (it.isNotBlank()) {
                                    localFileUri = null
                                    localFileName = null
                                }
                            },
                            placeholder = { Text("http://example.com:8080/get.php...", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderColor,
                                focusedLabelColor = AccentCyan,
                                unfocusedLabelColor = SoftGray
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // OPTION B: local M3U File
                        Text(
                            text = "أو استيراد ملف M3U محلي من الهاتف:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = "Upload", tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اختر ملف", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            localFileName?.let {
                                Text(
                                    text = it,
                                    color = AccentRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                    textAlign = TextAlign.Start
                                )
                            } ?: Text(
                                text = "لم يتم اختيار ملف",
                                color = SoftGray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }

                    1 -> {
                        // Xtream Codes Forms: Host, Port, Username, Password
                        Text("رابط الهوست (Host Server URL):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = xtreamHost,
                            onValueChange = { xtreamHost = it },
                            placeholder = { Text("مثال: http://example.com", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderColor
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("البورت (Port):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = xtreamPort,
                            onValueChange = { xtreamPort = it },
                            placeholder = { Text("مثال: 8080 (أو اتركه فارغاً إذا كان بالهوست)", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderColor
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("اسم المستخدم (Username):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = xtreamUser,
                            onValueChange = { xtreamUser = it },
                            placeholder = { Text("مثال: user123", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderColor
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("كلمة المرور / الباسورد (Password):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = xtreamPass,
                            onValueChange = { xtreamPass = it },
                            placeholder = { Text("مثال: pass990", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                    }

                    2 -> {
                        // MAC Address and Portal URL
                        Text("رابط بورتال الماك (Portal URL):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = portalUrl,
                            onValueChange = { portalUrl = it },
                            placeholder = { Text("http://magic-iptv.top:8000/c/", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderColor
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("عنوان الـ MAC Address الخاص بالاشتراك:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = macAddress,
                            onValueChange = { macAddress = it },
                            placeholder = { Text("00:1A:79:xx:xx:xx", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderColor
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = BorderColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Custom User-Agent input (Common)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Agent", tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "رمز وكيل المستخدم (User-Agent) لهذا الاشتراك - اختياري:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = customUserAgent,
                    onValueChange = { customUserAgent = it },
                    placeholder = { Text("مثال: VLC/3.0.18, IPTVSmarters, etc.", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderColor
                    )
                )

                Spacer(modifier = Modifier.height(22.dp))

                // ACTIONS BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // CANCEL
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء", color = SoftGray)
                    }

                    // SAVE / IMPORT
                    Button(
                        onClick = {
                            when (selectedTab) {
                                0 -> {
                                    val targetName = serverName.ifBlank {
                                        localFileName ?: "خادم بث شبكي"
                                    }
                                    onImport(
                                        targetName,
                                        playlistUrl.ifBlank { null },
                                        localFileUri,
                                        customUserAgent.ifBlank { null }
                                    )
                                }

                                1 -> {
                                    // Construct M3U URL from Xtream Codes parameters
                                    var host = xtreamHost.trim()
                                    if (!host.startsWith("http://") && !host.startsWith("https://")) {
                                        host = "http://$host"
                                    }
                                    if (host.endsWith("/")) {
                                        host = host.dropLast(1)
                                    }
                                    val port = xtreamPort.trim()
                                    val hostWithPort = if (port.isNotEmpty() && !host.contains(":$port")) {
                                        "$host:$port"
                                    } else {
                                        host
                                    }
                                    val xtreamUrl = "$hostWithPort/get.php?username=${xtreamUser.trim()}&password=${xtreamPass.trim()}&output=ts"
                                    
                                    val targetName = serverName.ifBlank { "سيرفر Xtream - $xtreamUser" }
                                    onImport(
                                        targetName,
                                        xtreamUrl,
                                        null,
                                        customUserAgent.ifBlank { null }
                                    )
                                }

                                2 -> {
                                    val targetName = serverName.ifBlank { "بورتال MAC - $macAddress" }
                                    onMagImport(
                                        targetName,
                                        portalUrl.trim(),
                                        macAddress.trim(),
                                        customUserAgent.ifBlank { null }
                                    )
                                }
                            }
                        },
                        enabled = when (selectedTab) {
                            0 -> playlistUrl.isNotBlank() || localFileUri != null
                            1 -> xtreamHost.isNotBlank() && xtreamUser.isNotBlank() && xtreamPass.isNotBlank()
                            2 -> portalUrl.isNotBlank() && macAddress.isNotBlank()
                            else -> false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("استيراد وتشغيل 🚀", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Fully interactive EPG (Electronic Program Guide) list display popup
 */
@Composable
fun EPGScheduleDialog(
    channel: ChannelEntity,
    programs: List<EPGProgram>,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.End // RTL EPG supportive alignment
            ) {
                // Header Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "دليل البرامج الإلكتروني (EPG) 📅",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                        Text(
                            text = channel.name,
                            fontSize = 12.sp,
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Divider(color = Color.White.copy(alpha = 0.15f))

                Spacer(modifier = Modifier.height(10.dp))

                // Schedule list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(programs) { prog ->
                        val isAiringNow = prog.progress > 0.0f && prog.progress < 1.0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAiringNow) AccentRed.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f))
                                .border(
                                    1.dp,
                                    if (isAiringNow) AccentRed.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Airing Now Badge
                                    if (isAiringNow) {
                                        Text(
                                            text = "يعرض الآن 🔴",
                                            color = AccentRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(AccentRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "قريباً 🕒",
                                            color = SoftGray,
                                            fontSize = 10.sp
                                        )
                                    }

                                    // Time span
                                    val startText = remember(prog.startTimeUnix) { formatUnixTime(prog.startTimeUnix) }
                                    val endText = remember(prog.endTimeUnix) { formatUnixTime(prog.endTimeUnix) }
                                    Text(
                                        text = "$startText - $endText",
                                        color = if (isAiringNow) AccentCyan else SoftGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Program Title
                                Text(
                                    text = prog.title,
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Program Description
                                Text(
                                    text = prog.description,
                                    color = SoftGray,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Live airing progress bar
                                if (isAiringNow) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { prog.progress },
                                        color = AccentRed,
                                        trackColor = Color.DarkGray,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format timestamp Unix Ms to friendly readable Arabic standard time
 */
fun formatUnixTime(unixMs: Long): String {
    val date = Date(unixMs)
    return DateFormat.format("hh:mm a", date).toString()
        .replace("AM", "ص")
        .replace("PM", "م")
}

/**
 * Global persistent settings dialog allowing customization of User-Agent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsDialog(
    isDarkMode: Boolean,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    currentGlobalUserAgent: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var userAgent by remember { mutableStateOf(currentGlobalUserAgent) }

    val presets = listOf(
        "Smart-IPTV-Agent-Pro",
        "VLC/3.0.18",
        "IPTVSmarters",
        "ExoPlayer/2.18",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, BorderColor), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.End // RTL supportive alignment
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.05f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "الإعدادات العامة ⚙️",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                        Text(
                            text = "تخصيص مشغل البث والشبكة",
                            fontSize = 11.sp,
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = BorderColor)
                Spacer(modifier = Modifier.height(16.dp))

                // Field Label & Instruction Text
                Text(
                    text = "رمز تعريف وكيل المستخدم (User-Agent):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "يساعد تعيين وكيل مخصص في تجاوز قيود الحظر التي يفرضها بعض مزودي خدمة البث المباشر (M3U8). سيتم تطبيقه تلقائياً لجميع القنوات.",
                    fontSize = 11.sp,
                    color = SoftGray,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input Box
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    placeholder = { Text("مثال: VLC/3.0.18, IPTVSmarters, etc.", fontSize = 12.sp, color = SoftGray) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Left),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = AccentCyan,
                        unfocusedLabelColor = SoftGray
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Presets Quick Selection Label
                Text(
                    text = "وكلاء الاستخدام الشائعة (اضغط للتعبئة التلقائية) ⚡:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontal list of presets
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    reverseLayout = true // RTL alignment style
                ) {
                    items(presets) { preset ->
                        val isSelected = userAgent == preset
                        SuggestionChip(
                            onClick = { userAgent = preset },
                            label = { Text(preset, fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) AccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                                labelColor = if (isSelected) AccentCyan else SoftGray
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (isSelected) AccentCyan else BorderColor
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = BorderColor)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "الدعم والـتواصل وتحديثات التطبيق 💫:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                val context = LocalContext.current
                var showAboutDialog by remember { mutableStateOf(false) }

                if (showAboutDialog) {
                    AlertDialog(
                        onDismissRequest = { showAboutDialog = false },
                        title = {
                            Text(
                                "معلومات حول التطبيق ℹ️",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "البدر IPTV برو",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "الإصدار المحدث المعتمد: v2.5.0 Pro",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRed
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "مشغل البث المباشر المطور للباقات والقنوات العربية والعالمية. يدعم صيغ البث المختلفة وسيرفرات IPTV والـ Xtream وقوائم MAC Portals بسهولة وسرعة فائقة في معالجة فك التشفير وعرض المحتوى بدون تقطيع.",
                                    fontSize = 12.sp,
                                    color = SoftGray,
                                    textAlign = TextAlign.Right,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "حقوق الطبع والنشر © 2026 محفوظة.",
                                    fontSize = 10.sp,
                                    color = SoftGray
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showAboutDialog = false }) {
                                Text("حسناً", color = AccentCyan, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = surfaceColor
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/zozmmnosh"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text("تواصل مع المطور 💬", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BadrIPTV_Support"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text("الدعم الفني 🛠️", fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BadrIPTVUpdates"))
                                try { context.startActivity(intent) } catch (e: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text("تحديثات التطبيق 📢", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showAboutDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text("معلومات التطبيق ℹ️", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            try {
                                val pm = context.packageManager
                                val appInfo = pm.getApplicationInfo(context.packageName, 0)
                                val apkFile = java.io.File(appInfo.sourceDir)
                                
                                val shareUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "com.example.provider",
                                    apkFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.android.package-archive"
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "تطبيق البدر IPTV برو المطور")
                                    putExtra(Intent.EXTRA_TEXT, "قم بتنزيل تطبيق البدر IPTV برو المحدث v2.5.0 لتشغيل اشتراكات وقنوات البث المباشر!\nرابط تشغيل النسخة المباشرة:\nhttps://ais-pre-u3vxat6yva32qg3vfvp7zs-535001705030.europe-west2.run.app")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملف الـ APK المباشر للتطبيق"))
                            } catch (ex: Exception) {
                                val shareTxtIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "تطبيق البدر IPTV برو المحدث")
                                    putExtra(Intent.EXTRA_TEXT, "حمل الآن تطبيق البدر IPTV برو المحدث لتشغيل جميع القنوات وسيرفرات البث المباشر وبورتال IPTV:\nhttps://ais-pre-u3vxat6yva32qg3vfvp7zs-535001705030.europe-west2.run.app")
                                }
                                context.startActivity(Intent.createChooser(shareTxtIntent, "مشاركة رابط التطبيق"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("مشاركة التطبيق مباشرة كـ APK 📤", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // CANCEL
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء", color = SoftGray, fontWeight = FontWeight.Bold)
                    }

                    // SAVE
                    Button(
                        onClick = { onSave(userAgent) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ التغييرات 💾", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
