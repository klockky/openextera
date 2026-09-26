package com.exteragram.messenger.translator.providers

import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.ProviderLimits
import com.exteragram.messenger.translator.core.ProviderResponse
import com.exteragram.messenger.translator.core.TranslationError
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray

class GoogleTranslator private constructor() : HttpTranslator() {

    override val displayName = "Google"
    override val supportedLanguages = emptySet<String>()
    override val limits = ProviderLimits.GOOGLE

    override fun buildRequest(texts: List<String>, fromLang: String, toLang: String): Request {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("translate.googleapis.com")
            .addPathSegments("translate_a/t")
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", fromLang)
            .addQueryParameter("tl", toLang)
            .addQueryParameter("dt", "t")
            .addQueryParameter("format", "text")
            .addQueryParameter("ie", "UTF-8")
            .addQueryParameter("oe", "UTF-8")
        texts.forEach { url.addQueryParameter("q", it) }
        return Request.Builder()
            .url(url.build())
            .header("User-Agent", TranslatorUtils.formatUserAgent())
            .build()
    }

    override fun parseResponse(response: Response, expectedCount: Int): ProviderResponse {
        if (!response.isSuccessful) {
            return ProviderResponse.Failure(httpError(response))
        }
        val body = response.body?.string().orEmpty()
        if (body.isEmpty()) {
            return ProviderResponse.Failure(TranslationError.Transient)
        }
        val array = JSONArray(body)
        val result = ArrayList<String>(expectedCount)
        for (i in 0 until array.length()) {
            val item = array.get(i)
            result.add(if (item is JSONArray) item.optString(0) else item.toString())
        }
        if (result.size != expectedCount) {
            return ProviderResponse.Failure(TranslationError.Transient)
        }
        return ProviderResponse.Success(result)
    }

    companion object {
        private val shared by lazy { GoogleTranslator() }

        @JvmStatic
        fun getInstance(): GoogleTranslator = shared
    }
}
