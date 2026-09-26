package com.exteragram.messenger.api.network

import com.exteragram.messenger.api.dto.BoostySubscriberDTO
import com.exteragram.messenger.api.dto.NowPlayingDTO
import com.exteragram.messenger.api.dto.ProfileDTO
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("profiles")
    suspend fun getAllProfiles(
        @Header("If-None-Match") etag: String? = null
    ): Response<List<ProfileDTO>>

    @GET("profiles/updates")
    suspend fun getUpdates(
        @Query("since") since: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<List<ProfileDTO>>

    @GET("profiles/{userId}")
    suspend fun getProfile(@Path("userId") userId: Long): Response<ProfileDTO>

    @GET("profiles/{userId}/now-playing")
    suspend fun getCurrentPlayingTrack(@Path("userId") userId: Long): Response<NowPlayingDTO>

    @GET("boosty-subscribers")
    suspend fun getBoostySubscribers(): Response<List<BoostySubscriberDTO>>
}
