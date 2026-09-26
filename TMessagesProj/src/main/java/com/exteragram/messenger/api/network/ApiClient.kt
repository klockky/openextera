package com.exteragram.messenger.api.network

import com.exteragram.messenger.debug.DebugConfig
import com.exteragram.messenger.utils.network.ExteraHttpClient
import com.google.gson.GsonBuilder
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // TODO(openextera): exteraSquad infrastructure, to be reworked
    private const val BASE_URL = "https://api.exteragram.app/api/v1/"

    internal val requestsEnabled: Boolean
        get() = !DebugConfig.disableApiRequests

    val apiService: ApiService by lazy {
        val client = ExteraHttpClient.client.newBuilder()
            .addInterceptor { chain ->
                if (requestsEnabled) {
                    chain.proceed(chain.request())
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("extera API requests disabled")
                        .body(ByteArray(0).toResponseBody())
                        .build()
                }
            }
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(ApiService::class.java)
    }
}
