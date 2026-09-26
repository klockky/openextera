package com.exteragram.messenger.translator.core

import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.utils.network.ExteraHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

abstract class HttpTranslator : BaseTranslator() {

    abstract val limits: ProviderLimits

    open val client: OkHttpClient
        get() = ExteraHttpClient.client

    abstract fun buildRequest(texts: List<String>, fromLang: String, toLang: String): Request

    abstract fun parseResponse(response: Response, expectedCount: Int): ProviderResponse

    final override fun translate(text: String, fromLang: String, toLang: String, callback: TranslatorUtils.TranslateCallback) {
        if (text.isBlank()) {
            callback.onFailed(TranslationError.Fatal)
            return
        }
        TranslationDispatcher.enqueue(this, 0L, listOf(text), fromLang, toLang, true) { result, error ->
            val translated = result?.firstOrNull()
            if (!translated.isNullOrEmpty()) {
                callback.onSuccess(translated)
            } else {
                callback.onFailed(error ?: TranslationError.Fatal)
            }
        }
    }

    fun httpError(response: Response): TranslationError = when (response.code) {
        429 -> TranslationError.RateLimited(retryAfterMs(response))
        in 500..599 -> TranslationError.Transient
        else -> TranslationError.Fatal
    }

    fun retryAfterMs(response: Response): Long {
        val header = response.header("Retry-After")?.trim() ?: return 0L
        return (header.toLongOrNull() ?: 0L) * 1000
    }
}
