package com.exteragram.messenger.nowplaying

import androidx.collection.LruCache
import com.exteragram.messenger.api.db.DatabaseHelper
import com.exteragram.messenger.api.dto.NowPlayingDTO
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.exteragram.messenger.api.model.NowPlayingServiceType
import com.exteragram.messenger.api.network.ApiClient
import com.exteragram.messenger.debug.DebugConfig
import com.exteragram.messenger.nowplaying.ui.components.NowPlayingCardData
import com.exteragram.messenger.utils.chats.ChatUtils
import com.exteragram.messenger.utils.network.ExteraHttpClient
import com.exteragram.messenger.utils.network.RemoteUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.ProfileMusicView
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NowPlayingController {

    private val ARTISTS_SPLITTER = Regex("(?i)\\s*(?:,|&|\\bfeat\\b\\.?|\\bft\\b\\.?)\\s*")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val itunesCache = LruCache<String, List<ItunesTrack>>(50)

    private val httpClient: OkHttpClient = ExteraHttpClient.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json { ignoreUnknownKeys = true }

    @JvmStatic
    fun shouldShowCard(data: NowPlayingCardData?): Boolean {
        if (data == null) {
            return false
        }
        return !(data.nowPlayingDTO.platform == "TELEGRAM" && isSeparateStylesSupported())
    }

    @JvmStatic
    fun isSeparateStylesSupported(): Boolean =
        DebugConfig.forceCompactSavedMusic || RemoteUtils.getBooleanConfigValue("separate_music_styles", false)

    @JvmStatic
    fun getCurrentPlayingTrack(
        userId: Long,
        savedMusic: TLRPC.Document?,
        checkApi: Boolean,
        callback: BiConsumer<NowPlayingDTO?, Long>
    ): Job = scope.launch {
        val startTime = System.currentTimeMillis()
        val liveTrackDeferred = async(Dispatchers.IO) {
            try {
                if (checkApi && ApiClient.requestsEnabled) {
                    val response = ApiClient.apiService.getCurrentPlayingTrack(userId)
                    if (response.isSuccessful) response.body() else null
                } else {
                    null
                }
            } catch (e: Throwable) {
                FileLog.e(e)
                null
            }
        }
        val savedTrackDeferred = async(Dispatchers.IO) { processSavedMusic(savedMusic) }

        val liveTrack = liveTrackDeferred.await()
        val finalTrack = if (liveTrack != null && liveTrack.isPlaying) {
            savedTrackDeferred.cancel()
            liveTrack
        } else {
            savedTrackDeferred.await()
        }
        withContext(Dispatchers.Main) {
            callback.accept(finalTrack, System.currentTimeMillis() - startTime)
        }
    }

    private fun splitArtists(artists: String): List<String> =
        ARTISTS_SPLITTER.split(artists).map { it.trim() }.filter { it.isNotEmpty() }

    private suspend fun processSavedMusic(document: TLRPC.Document?): NowPlayingDTO? {
        if (document == null) {
            return null
        }
        val title = ProfileMusicView.getTitle(document) as? String
            ?: LocaleController.getString(R.string.AudioUnknownTitle)
        val author = ProfileMusicView.getAuthor(document) as? String
            ?: LocaleController.getString(R.string.AudioUnknownArtist)
        if (title == null || author == null) {
            return null
        }

        val track = NowPlayingDTO(title, splitArtists(author), null, null, null, null, true, null, "TELEGRAM", null)
        if (isSeparateStylesSupported()) {
            return track
        }

        val results = fetchItunesTrack(author, title)
        if (results.isNullOrEmpty()) {
            return track
        }
        val match = results.firstOrNull {
            it.trackName.equals(track.trackName, ignoreCase = true) && hasCommonArtist(track.artists, it.artistName).first
        } ?: results.first()

        val artists = hasCommonArtist(track.artists, match.artistName).second
        val coverUrl = if (!match.artworkUrl100.isNullOrEmpty()) {
            match.artworkUrl100.replace("100x100", "300x300")
        } else {
            track.coverUrl
        }
        return track.copy(
            artists = artists,
            albumName = match.collectionName ?: track.albumName,
            coverUrl = coverUrl,
            platform = track.platform
        )
    }

    private fun hasCommonArtist(artists: List<String>?, itunesArtist: String?): Pair<Boolean, List<String>?> {
        if (itunesArtist.isNullOrBlank()) {
            return false to artists
        }
        val itunesArtists = splitArtists(itunesArtist)
        if (artists.isNullOrEmpty()) {
            return false to itunesArtists
        }
        val hasCommon = artists.any { artist ->
            val lower = artist.lowercase(Locale.ROOT)
            val translit = AndroidUtilities.translitSafe(lower)
            itunesArtists.any { other ->
                val otherLower = other.lowercase(Locale.ROOT)
                val otherTranslit = AndroidUtilities.translitSafe(otherLower)
                otherLower == lower || otherLower == translit || otherTranslit == lower
            }
        }
        return hasCommon to (if (hasCommon) itunesArtists else artists)
    }

    @JvmStatic
    fun getNowPlayingInfo(callback: Consumer<NowPlayingInfoDTO?>) {
        scope.launch {
            callback.accept(getNowPlayingInfoInternal())
        }
    }

    @JvmStatic
    @JvmOverloads
    fun updateNowPlayingInfo(newNowPlaying: NowPlayingInfoDTO?, cache: Boolean = true, callback: Consumer<Boolean>) {
        scope.launch {
            callback.accept(updateNowPlayingInfoInternal(newNowPlaying, cache))
        }
    }

    private suspend fun getNowPlayingInfoInternal(): NowPlayingInfoDTO? {
        val userId = ChatUtils.getInstance().userConfig.clientUserId
        return dbGetNowPlaying(userId) ?: fetchProfileNowPlayingInfo(userId)
    }

    private suspend fun fetchProfileNowPlayingInfo(userId: Long): NowPlayingInfoDTO? = withContext(Dispatchers.IO) {
        try {
            if (!ApiClient.requestsEnabled) {
                return@withContext null
            }
            val response = ApiClient.apiService.getProfile(userId)
            val profile = if (response.isSuccessful) response.body() else null
            if (profile == null) {
                return@withContext null
            }
            DatabaseHelper.insertProfiles(listOf(profile))
            profile.nowPlaying
        } catch (e: Throwable) {
            FileLog.e(e)
            null
        }
    }

    private suspend fun updateNowPlayingInfoInternal(newNowPlaying: NowPlayingInfoDTO?, cache: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val command = if (newNowPlaying == null || newNowPlaying.serviceType == NowPlayingServiceType.NONE) {
                    "clear_now_playing"
                } else {
                    "set_now_playing ${newNowPlaying.serviceType.name} ${newNowPlaying.username}"
                }
                val message = suspendCancellableCoroutine { continuation ->
                    ChatUtils.getInstance().sendBotRequest(command, cache) { result -> continuation.resume(result) }
                }
                if (message == "ok") {
                    dbUpdateNowPlaying(ChatUtils.getInstance().userConfig.clientUserId, newNowPlaying) > 0
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

    private suspend fun fetchItunesTrack(artist: String, title: String): List<ItunesTrack>? {
        try {
            if (artist.isBlank() && title.isBlank()) {
                return null
            }
            val cacheKey = "$artist - $title"
            itunesCache.get(cacheKey)?.let { return it }

            val url = "https://itunes.apple.com/search".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("term", "$title - $artist")
                ?.addQueryParameter("entity", "song")
                ?.addQueryParameter("limit", "5")
                ?.build()
                ?: return null

            val response = httpClient.newCall(Request.Builder().url(url).build()).await()
            if (!response.isSuccessful) {
                return null
            }
            val results = response.use { jsonParser.decodeFromString(ItunesSearchResponse.serializer(), it.body!!.string()).results }
            itunesCache.put(cacheKey, results)
            return results
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private suspend fun dbGetNowPlaying(userId: Long): NowPlayingInfoDTO? = suspendCancellableCoroutine { continuation ->
        DatabaseHelper.getNowPlaying(userId) { continuation.resume(it) }
    }

    private suspend fun dbUpdateNowPlaying(userId: Long, nowPlaying: NowPlayingInfoDTO?): Int =
        suspendCancellableCoroutine { continuation ->
            DatabaseHelper.updateNowPlaying(userId, nowPlaying) { continuation.resume(it) }
        }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) {
                    return
                }
                continuation.resumeWithException(e)
            }
        })
    }

    @Serializable
    data class ItunesSearchResponse(
        val resultCount: Int,
        val results: List<ItunesTrack>
    )

    @Serializable
    data class ItunesTrack(
        val trackName: String? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val artworkUrl100: String? = null,
        val previewUrl: String? = null,
        val trackTimeMillis: Long? = null
    )
}
