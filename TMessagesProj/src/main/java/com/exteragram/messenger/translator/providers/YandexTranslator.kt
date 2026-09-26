package com.exteragram.messenger.translator.providers

import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.ProviderLimits
import com.exteragram.messenger.translator.core.ProviderResponse
import com.exteragram.messenger.translator.core.TranslationError
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.UUID

class YandexTranslator private constructor() : HttpTranslator() {

    override val displayName = "Yandex"
    override val supportedLanguages = setOf(
        "az", "sq", "am", "en", "ar", "hy", "af", "eu", "ba", "be", "bn", "my", "bg", "bs", "cv", "cy", "hu", "vi",
        "ht", "gl", "nl", "mrj", "el", "ka", "gu", "da", "he", "yi", "id", "ga", "it", "is", "es", "kk", "kn", "ca",
        "ky", "zh", "ko", "xh", "km", "lo", "la", "lv", "lt", "lb", "mg", "ms", "ml", "mt", "mk", "mi", "mr", "mhr",
        "mn", "de", "ne", "no", "pa", "pap", "fa", "pl", "pt", "ro", "ru", "ceb", "sr", "si", "sk", "sl", "sw", "su",
        "tg", "th", "tl", "ta", "tt", "te", "tr", "udm", "uz", "uk", "ur", "fi", "fr", "hi", "hr", "cs", "sv", "gd",
        "et", "eo", "jv", "ja",
    )
    override val limits = ProviderLimits.YANDEX

    override fun buildRequest(texts: List<String>, fromLang: String, toLang: String): Request {
        val form = FormBody.Builder().add("lang", toLang)
        texts.forEach { form.add("text", it) }
        return Request.Builder()
            .url("https://translate.yandex.net/api/v1/tr.json/translate?&srv=android&id=$uuid-0-0")
            .header("User-Agent", "ru.yandex.translate/21.15.4.21402814 (Xiaomi Redmi K20 Pro; Android 11)")
            .post(form.build())
            .build()
    }

    override fun parseResponse(response: Response, expectedCount: Int): ProviderResponse {
        val body = response.body?.string().orEmpty()
        if (body.isEmpty()) {
            return ProviderResponse.Failure(if (response.isSuccessful) TranslationError.Transient else httpError(response))
        }
        val json = JSONObject(body)
        val code = json.optInt("code", if (response.isSuccessful) 200 else response.code)
        if (code != 200 || !json.has("text")) {
            val error = when {
                code == 429 || code == 1052 || response.code == 429 -> TranslationError.RateLimited(retryAfterMs(response))
                code == 501 -> TranslationError.LanguageUnsupported
                response.code in 500..599 -> TranslationError.Transient
                else -> TranslationError.Fatal
            }
            return ProviderResponse.Failure(error)
        }
        val array = json.getJSONArray("text")
        if (array.length() != expectedCount) {
            return ProviderResponse.Failure(TranslationError.Transient)
        }
        return ProviderResponse.Success(List(array.length()) { array.optString(it) })
    }

    companion object {
        private val uuid = UUID.randomUUID().toString().replace("-", "")
        private val shared by lazy { YandexTranslator() }

        @JvmStatic
        fun getInstance(): YandexTranslator = shared
    }
}
