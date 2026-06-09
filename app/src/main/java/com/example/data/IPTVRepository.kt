package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class EPGProgram(
    val title: String,
    val description: String,
    val startTimeUnix: Long,
    val endTimeUnix: Long,
    val progress: Float // 0.0f to 1.0f
)

class IPTVRepository(
    private val iptvDao: IPTVDao,
    private val context: Context
) {
    val playlists: Flow<List<PlaylistEntity>> = iptvDao.getAllPlaylists()
    val allChannels: Flow<List<ChannelEntity>> = iptvDao.getAllChannels()
    val favoriteChannels: Flow<List<ChannelEntity>> = iptvDao.getFavoriteChannels()

    fun getChannelsForPlaylist(playlistId: Int): Flow<List<ChannelEntity>> {
        return iptvDao.getChannelsForPlaylist(playlistId)
    }

    suspend fun toggleFavorite(channelId: Int, isFav: Boolean) {
        withContext(Dispatchers.IO) {
            iptvDao.updateFavoriteStatus(channelId, isFav)
        }
    }

    suspend fun deletePlaylist(playlistId: Int) {
        withContext(Dispatchers.IO) {
            iptvDao.deletePlaylist(playlistId)
        }
    }

    /**
     * Import a playlist from a remote URL or from a local File Uri.
     */
    suspend fun importPlaylist(
        name: String,
        url: String?,
        localUri: Uri?,
        userAgent: String?
    ): Result<PlaylistEntity> = withContext(Dispatchers.IO) {
        try {
            val playlist = PlaylistEntity(
                name = name,
                sourceUrl = url,
                filePath = localUri?.toString(),
                customUserAgent = if (userAgent.isNullOrBlank()) null else userAgent.trim()
            )

            // Insert playlist first to get dynamic database ID
            val playlistId = iptvDao.insertPlaylist(playlist).toInt()
            val parsedChannels = mutableListOf<ChannelEntity>()

            if (localUri != null) {
                // Read from local Uri
                context.contentResolver.openInputStream(localUri)?.use { inputStream ->
                    parsedChannels.addAll(parseM3UStream(inputStream, playlistId))
                }
            } else if (!url.isNullOrBlank()) {
                // Fetch from remote URL
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()

                val requestBuilder = Request.Builder().url(url)
                if (!userAgent.isNullOrBlank()) {
                    requestBuilder.header("User-Agent", userAgent.trim())
                } else {
                    requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) IPTVPlayer")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    iptvDao.deletePlaylist(playlistId)
                    return@withContext Result.failure(Exception("Failed to download: HTTP ${response.code}"))
                }

                response.body?.byteStream()?.use { inputStream ->
                    parsedChannels.addAll(parseM3UStream(inputStream, playlistId))
                }
            } else {
                iptvDao.deletePlaylist(playlistId)
                return@withContext Result.failure(Exception("Missing URL and local file sources"))
            }

            if (parsedChannels.isEmpty()) {
                iptvDao.deletePlaylist(playlistId)
                return@withContext Result.failure(Exception("لم يتم العثور على قنوات صالحة في الملف المضاف"))
            }

            // Insert channels into database in batches
            iptvDao.insertChannels(parsedChannels)
            Result.success(playlist.copy(id = playlistId))
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Import failed", e)
            Result.failure(e)
        }
    }

    /**
     * Parsing of M3U dynamic files
     */
    private fun parseM3UStream(inputStream: InputStream, playlistId: Int): List<ChannelEntity> {
        val channels = mutableListOf<ChannelEntity>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        var currentExtInf: ExtInfData? = null
        var index = 0

        try {
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    currentExtInf = parseExtInfLine(trimmed)
                } else if (!trimmed.startsWith("#") && (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtmp://") || trimmed.contains("://") || trimmed.endsWith(".ts") || trimmed.contains(".ts"))) {
                    // It is a streaming URL
                    val name = currentExtInf?.name ?: "قناة بدون اسم ${index + 1}"
                    val logoUrl = currentExtInf?.logoUrl
                    val groupTitle = currentExtInf?.groupTitle ?: "أخرى"
                    val tvgId = currentExtInf?.tvgId

                    channels.add(
                        ChannelEntity(
                            playlistId = playlistId,
                            name = name,
                            url = trimmed,
                            logoUrl = logoUrl,
                            groupTitle = groupTitle,
                            tvgId = tvgId,
                            orderIndex = index++
                        )
                    )
                    currentExtInf = null // Reset
                }
            }

            // If we have plain URLs without EXTM3U metadata, let's treat lines that look like stream URLs
            if (channels.isEmpty()) {
                inputStream.reset() // Try resetting if supported (or we assume it already read)
            }
        } catch (e: Exception) {
            Log.e("IPTVRepository", "Error inside stream parser", e)
        }
        return channels
    }

    private data class ExtInfData(
        val name: String,
        val tvgId: String?,
        val logoUrl: String?,
        val groupTitle: String?
    )

    private fun parseExtInfLine(line: String): ExtInfData {
        // Example: #EXTINF:-1 tvg-id="id" tvg-logo="url" group-title="Sports",BeIN Sports HD
        val commaIndex = line.lastIndexOf(',')
        val name = if (commaIndex != -1 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else {
            "قناة غير معروفة"
        }

        val metadataPart = if (commaIndex != -1) line.substring(0, commaIndex) else line

        val tvgId = extractAttr(metadataPart, "tvg-id")
        val logoUrl = extractAttr(metadataPart, "tvg-logo")
        val groupTitle = extractAttr(metadataPart, "group-title")

        return ExtInfData(name, tvgId, logoUrl, groupTitle)
    }

    private fun extractAttr(source: String, attrName: String): String? {
        val needle = "$attrName=\""
        val startIndex = source.indexOf(needle)
        if (startIndex != -1) {
            val startVal = startIndex + needle.length
            val endIndex = source.indexOf('"', startVal)
            if (endIndex != -1) {
                return source.substring(startVal, endIndex).trim()
            }
        }
        // Try without double quotes
        val needleAlt = "$attrName="
        val startAlt = source.indexOf(needleAlt)
        if (startAlt != -1) {
            val startVal = startAlt + needleAlt.length
            var endAlt = source.indexOf(' ', startVal)
            if (endAlt == -1) endAlt = source.length
            return source.substring(startVal, endAlt).trim().replace("\"", "")
        }
        return null
    }

    /**
     * Generates a fully detailed and gorgeous 24-Hour EPG Program timeline for a given channel.
     * Generates program blocks centered around current time.
     */
    fun generateEPGForChannel(channel: ChannelEntity): List<EPGProgram> {
        val programs = mutableListOf<EPGProgram>()
        val currentTime = System.currentTimeMillis()
        val c = Calendar.getInstance()

        // Set start calendar to 6 hours ago
        c.add(Calendar.HOUR_OF_DAY, -6)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)

        // Generate 6 programs of 4 hours each (or 12 of 2 hours) to cover 24 hours
        val genre = channel.groupTitle?.lowercase() ?: ""
        val nameLower = channel.name.lowercase()

        val isSports = genre.contains("sport") || genre.contains("كورة") || genre.contains("رياض") || nameLower.contains("sport") || nameLower.contains("bein")
        val isMovies = genre.contains("movie") || genre.contains("أفلام") || genre.contains("cinema") || nameLower.contains("movie") || nameLower.contains("action")
        val isNews = genre.contains("news") || genre.contains("أخبار") || genre.contains("جزيرة") || genre.contains("حدث") || nameLower.contains("news")
        val isKids = genre.contains("kid") || genre.contains("أطفال") || genre.contains("كرتون") || nameLower.contains("kid")

        val scheduleTitles: List<Pair<String, String>> = when {
            isSports -> listOf(
                "الاستوديو التحليلي المباشر" to "تغطيات وتحليلات مباريات اليوم بوجود نخبة من الخبراء",
                "مباراة مباشرة الكلاسيكو المثيرة" to "نقل حي ومباشر للمباراة الأهم والأكثر مشاهدة عالمياً مع تعليق عربي احترافي بالتفاصيل والفرص الضائعة",
                "أخبار الرياضة العالمية" to "محصلة سريعة لكل الأخبار والانتقالات العالمية وحصيلة الدوري الإسباني والإنجليزي",
                "ملخص مباريات الأمس" to "إعادة لأهم الأهداف واللقطات واللقاءات الصحفية خلف الكواليس",
                "برنامج 60 دقيقة كورة" to "حوارات حصرية مع لاعبين ومدربين حول كواليس المباريات والتكتيك المتبع",
                "وثائقي: أساطير المستطيل الأخضر" to "نظرة تاريخية معمقة على مسيرة حياة أعظم اللاعبين الذين غيروا تاريخ اللعبة"
            )
            isMovies -> listOf(
                "فيلم السهرة الأجنبي: الأكشن والإثارة" to "أحدث أفلام الأكشن والمطاردات ببطولة هوليوودية ساحقة وجودة صوت رائعة",
                "سينما الشرق الكلاسيكية" to "روائع الأفلام الكوميدية والدرامية العربية العريقة مدمجة بذكريات الأمس الرقيقة",
                "كواليس هوليوود والإنتاج الضخم" to "استعراض لأكثر المشاهد خطورة وكيفية تصويرها بالتفاصيل وأسرار المؤثرات البصرية",
                "فيلم الرعب والغموض: الليلة الحالكة" to "أحداث تحبس الأنفاس لقصة رحلة استكشافية مهجورة داخل غابة مظلمة غامضة وبداية مروعة",
                "وثائقي السينما العالمية" to "كيف تطورت الفنون البصرية وصناعة الكاميرات والشاشات على مر العصور المئوية",
                "مهرجانات السجادة الحمراء" to "لقطات حية وحوارات مع نجوم الفن وعشاق المهرجانات السينمائية السنوية"
            )
            isNews -> listOf(
                "نشرة الأخبار الإقليمية المباشرة" to "تغطية سياسية مباشرة لكافة الأحداث والملفات الاقتصادية والسياسية في الوطن العربي",
                "برنامج حوار سياسي ساخن" to "الرأي والرأي الآخر في نقاش حاد حول القرارات الدولية المستجدة ومستقبل المنطقة",
                "حصاد الـ 24 ساعة الماضية" to "ملخص شامل للأحداث العالمية الكبرى والبورصات والتغيرات المناخية بوضوح",
                "وثائقي الحدث والواقع" to "تقارير ميدانية من قلب الصحراء الصاخبة ومحاور النزاع والنجاح الإنساني",
                "أخبار التكنولوجيا وريادة الأعمال" to "أحدث الروبوتات والذكاء الاصطناعي وبراءات الاختراع التي ستغير مسار الغد القريب",
                "الصحافة والمانشيت اليومي" to "تحليل كتاب الصحف لأهم ما ورد في افتتاحيات الصحف العربية والدولية"
            )
            isKids -> listOf(
                "أبطال الكرتون والمغامرات" to "مغامرات طريفة ومسلية للأصدقاء الصغار في الغابة السعيدة بأسلوب تفاعلي رائع",
                "برنامج نادي الأطفال المرح" to "مسابقات وابتكارات يدوية وألعاب هادفة لتنمية مهارات الأطفال الذاتية والتعليمية",
                "أغاني وأناشيد الصباح" to "مجموعة من الأهازيج اللطيفة المعبرة الصباحية لتعليم الأرقام والحروف بأريحية",
                "مغامرات الفضاء والمستقبل" to "برنامج كرتوني علمي مشوق يأخذ الأطفال في رحلة شيقة بين الكواكب والمجرات البعيدة",
                "فيلم الرسوم المتحركة العائلي" to "قصة ممتعة ومؤثرة عن قيم الصداقة والوفاء والتعاون الأسري والمجتمعي",
                "حكايات جدتي المليئة بالحِكم" to "رواية هادئة وجذابة لقصص التراث الشعبي القديم بإنتاج بصري حديث وممتد"
            )
            else -> listOf(
                "صباح الخير يا وطن" to "جولة صباحية متنوعة تشمل الطقس، الصحة، نصائح رياضية ولقاءات فنية هادئة",
                "المسلسل الدرامي اليومي" to "أحداث درامية مشوقة ومجتمعية تلامس الحياة اليومية وتحدياتها العائلية المنهجية",
                "برنامج المطبخ العربي والشرقي" to "طريقة تحضير أشهى المأكولات والحلويات التقليدية مع نصائح التغذية والصناعة",
                "ريبورتاج من عواصم العالم" to "رحلة ترفيهية سياحية تجمع الثقافة والتراث وروعة المعمار في المدن الأثرية الساطعة",
                "جلسة فنية وموسيقى عذبة" to "استضافة عازفين وفنانين للحديث عن تاريخ النغم الأصيل والألحان الراسخة",
                "حديث المساء ومسائل عصرية" to "حلقة نقاشية تدرس التوازن الاجتماعي والتربوي وحلول لتبسيط صعوبات المعيشة"
            )
        }

        // Generate EPG blocks of 3 hours duration each
        val durationMs = 3 * 3600 * 1000L // 3 Hours

        for (i in 0 until 8) {
            val start = c.timeInMillis
            val end = start + durationMs

            val info = scheduleTitles[i % scheduleTitles.size]

            var progress = 0.0f
            if (currentTime in start until end) {
                progress = (currentTime - start).toFloat() / durationMs.toFloat()
            }

            programs.add(
                EPGProgram(
                    title = info.first,
                    description = info.second,
                    startTimeUnix = start,
                    endTimeUnix = end,
                    progress = progress
                )
            )

            c.add(Calendar.MILLISECOND, durationMs.toInt())
        }

        return programs
    }

    /**
     * Import channels from a MAG Portal (Stalker Middleware) using Portal URL and MAC Address.
     */
    suspend fun importMagPortal(
        name: String,
        portalUrl: String,
        macAddress: String,
        userAgent: String?
    ): Result<PlaylistEntity> = withContext(Dispatchers.IO) {
        try {
            var basePortal = portalUrl.trim()
            if (!basePortal.startsWith("http://") && !basePortal.startsWith("https://")) {
                basePortal = "http://$basePortal"
            }
            if (!basePortal.endsWith("/")) {
                basePortal += "/"
            }
            val phpUrl = if (basePortal.endsWith("portal.php") || basePortal.endsWith("portal.php/")) {
                basePortal
            } else {
                "${basePortal}portal.php"
            }

            val playlist = PlaylistEntity(
                name = name,
                sourceUrl = "mag://$portalUrl?mac=$macAddress",
                customUserAgent = userAgent
            )

            // Insert placeholder playlist to assign dynamic cascade key
            val playlistId = iptvDao.insertPlaylist(playlist).toInt()
            val parsedChannels = mutableListOf<ChannelEntity>()

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            val ua = if (!userAgent.isNullOrBlank()) userAgent.trim() else "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 sb.gxtrrt.55-2.6.2.14-r2 Safari/533.3"

            // 1. Handshake to retrieve Session/Token
            val handshakeUrl = "$phpUrl?type=itv&action=handshake&device_class=MAG250&mac=${macAddress.trim()}"
            val handshakeRequest = Request.Builder()
                .url(handshakeUrl)
                .header("User-Agent", ua)
                .header("X-User-Agent", "Model: MAG250; Link: WiFi")
                .build()

            val handshakeResponse = client.newCall(handshakeRequest).execute()
            if (!handshakeResponse.isSuccessful) {
                iptvDao.deletePlaylist(playlistId)
                return@withContext Result.failure(Exception("خطأ في الاتصال بالبورتال: HTTP ${handshakeResponse.code}"))
            }

            val handshakeBody = handshakeResponse.body?.string() ?: ""
            val handshakeJson = org.json.JSONObject(handshakeBody)
            val jsObj = handshakeJson.optJSONObject("js")
            val token = jsObj?.optString("token") ?: ""

            // 2. Fetch Stalker Categories (Optional mapping)
            val categoryMap = mutableMapOf<String, String>()
            if (token.isNotEmpty()) {
                try {
                    val catUrl = "$phpUrl?type=itv&action=get_categories"
                    val catRequest = Request.Builder()
                        .url(catUrl)
                        .header("User-Agent", ua)
                        .header("X-User-Agent", "Model: MAG250; Link: WiFi")
                        .header("Authorization", "Bearer $token")
                        .header("Cookie", "mac=${macAddress.trim()}")
                        .build()
                    val catResponse = client.newCall(catRequest).execute()
                    if (catResponse.isSuccessful) {
                        val catBody = catResponse.body?.string() ?: ""
                        val catJson = org.json.JSONObject(catBody)
                        val catArray = catJson.optJSONArray("js")
                        if (catArray != null) {
                            for (i in 0 until catArray.length()) {
                                val item = catArray.getJSONObject(i)
                                val catId = item.optString("id")
                                val catTitle = item.optString("title")
                                if (catId.isNotEmpty() && catTitle.isNotEmpty()) {
                                    categoryMap[catId] = catTitle
                                }
                            }
                        }
                    }
                } catch (catEx: Exception) {
                    Log.w("IPTVRepository", "Failed to get Stalker categories, fallback defaults applied", catEx)
                }
            }

            // 3. Request Live TV channel streams
            val channelsUrl = "$phpUrl?type=itv&action=get_all_channels"
            val channelsRequest = Request.Builder()
                .url(channelsUrl)
                .header("User-Agent", ua)
                .header("X-User-Agent", "Model: MAG250; Link: WiFi")
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=${macAddress.trim()}")
                .build()

            var channelsResponse = client.newCall(channelsRequest).execute()
            var channelsBody = if (channelsResponse.isSuccessful) channelsResponse.body?.string() ?: "" else ""

            if (channelsBody.isEmpty() || !channelsBody.contains("cmd")) {
                // Try alternate stalker action parameter
                val orderedUrl = "$phpUrl?type=itv&action=get_ordered_channels"
                val orderedRequest = Request.Builder()
                    .url(orderedUrl)
                    .header("User-Agent", ua)
                    .header("X-User-Agent", "Model: MAG250; Link: WiFi")
                    .header("Authorization", "Bearer $token")
                    .header("Cookie", "mac=${macAddress.trim()}")
                    .build()
                channelsResponse = client.newCall(orderedRequest).execute()
                channelsBody = if (channelsResponse.isSuccessful) channelsResponse.body?.string() ?: "" else ""
            }

            if (channelsBody.isEmpty()) {
                iptvDao.deletePlaylist(playlistId)
                return@withContext Result.failure(Exception("لم يستجب بورتال البث بقنوات صالحة للـ MAC المدخل"))
            }

            val channelsJson = org.json.JSONObject(channelsBody)
            val channelsArray = channelsJson.optJSONArray("js")
            if (channelsArray != null && channelsArray.length() > 0) {
                for (i in 0 until channelsArray.length()) {
                    val item = channelsArray.getJSONObject(i)
                    val cName = item.optString("name")
                    var cCmd = item.optString("cmd")
                    val cLogo = item.optString("logo")
                    val cCatId = item.optString("category_id")

                    // Strip any media wrapper prefix strings
                    if (cCmd.startsWith("ffmpeg ")) {
                        cCmd = cCmd.substring("ffmpeg ".length)
                    }
                    if (cCmd.startsWith("cmd ")) {
                        cCmd = cCmd.substring("cmd ".length)
                    }
                    cCmd = cCmd.trim()

                    if (cCmd.isNotEmpty() && cName.isNotEmpty()) {
                        parsedChannels.add(
                            ChannelEntity(
                                playlistId = playlistId,
                                name = cName,
                                url = cCmd,
                                logoUrl = if (cLogo.isNullOrBlank()) null else cLogo,
                                groupTitle = categoryMap[cCatId] ?: "بورتال MAC",
                                tvgId = item.optString("tvg_id", null),
                                orderIndex = i
                            )
                        )
                    }
                }
            }

            if (parsedChannels.isEmpty()) {
                iptvDao.deletePlaylist(playlistId)
                return@withContext Result.failure(Exception("لا توجد قنوات نشطة كجزء من اشتراك الـ MAC الموفر"))
            }

            iptvDao.insertChannels(parsedChannels)
            Result.success(playlist.copy(id = playlistId))
        } catch (e: Exception) {
            Log.e("IPTVRepository", "MAG Import failed", e)
            Result.failure(e)
        }
    }
}
