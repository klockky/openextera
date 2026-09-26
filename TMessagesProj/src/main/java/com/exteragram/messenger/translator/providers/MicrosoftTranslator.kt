package com.exteragram.messenger.translator.providers

import android.util.Base64
import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.ProviderLimits
import com.exteragram.messenger.translator.core.ProviderResponse
import com.exteragram.messenger.translator.core.TranslationError
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.FileLog
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MicrosoftTranslator private constructor() : HttpTranslator() {

    override val displayName = "Microsoft"
    override val supportedLanguages = emptySet<String>()
    override val limits = ProviderLimits.MICROSOFT

    override fun buildRequest(texts: List<String>, fromLang: String, toLang: String): Request {
        check(SIGNATURE_KEY.isNotEmpty()) { "Microsoft Translator signature key is not configured" }
        val from = if (fromLang.equals("auto", ignoreCase = true)) "" else fromLang
        val path = "api.cognitive.microsofttranslator.com/translate?api-version=3.0&from=$from&to=$toLang"
        val body = JSONArray()
        texts.forEach { body.put(JSONObject().put("Text", it)) }
        return Request.Builder()
            .url("https://$path")
            .header("X-Mt-Signature", signature(path))
            .header("User-Agent", "okhttp/4.5.0")
            .post(body.toString().toByteArray(Charsets.UTF_8).toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
            .build()
    }

    private fun signature(path: String): String {
        val guid = UUID.randomUUID().toString().replace("-", "")
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US).apply { timeZone = GMT }
        val date = dateFormat.format(Date(Calendar.getInstance(GMT).timeInMillis)).lowercase(Locale.US) + "GMT"
        val payload = ("MSTranslatorAndroidApp" + URLEncoder.encode(path, "UTF-8") + date + guid).lowercase(Locale.US)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Base64.decode(SIGNATURE_KEY, Base64.DEFAULT), "HmacSHA256"))
        val hash = Base64.encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "MSTranslatorAndroidApp::$hash::$date::$guid"
    }

    override fun parseResponse(response: Response, expectedCount: Int): ProviderResponse {
        val body = response.body?.string().orEmpty()
        if (body.isEmpty()) {
            return ProviderResponse.Failure(if (response.isSuccessful) TranslationError.Transient else httpError(response))
        }
        if (!body.startsWith("[")) {
            return ProviderResponse.Failure(failure(response, body))
        }
        val array = JSONArray(body)
        if (array.length() != expectedCount) {
            return ProviderResponse.Failure(TranslationError.Transient)
        }
        return ProviderResponse.Success(List(array.length()) {
            array.getJSONObject(it).getJSONArray("translations").getJSONObject(0).optString("text")
        })
    }

    private fun failure(response: Response, body: String): TranslationError {
        val code = JSONObject(body).optJSONObject("error")?.optInt("code") ?: 0
        return when {
            response.code == 429 -> TranslationError.RateLimited(retryAfterMs(response))
            code / 1000 == 401 -> {
                FileLog.e("translator: Microsoft rejected the signature, invalid credentials")
                TranslationError.Fatal
            }
            response.code in 500..599 -> TranslationError.Transient
            else -> TranslationError.Fatal
        }
    }

    companion object {
        // TODO(openextera): key. exteraGram used an HMAC key extracted from the Microsoft Translator app;
        //  it must not be redistributed. Until a legitimate key is configured, every request fails as Fatal.
        private const val SIGNATURE_KEY = ""

        private val GMT = TimeZone.getTimeZone("GMT")
        private val shared by lazy { MicrosoftTranslator() }

        @JvmStatic
        fun getInstance(): MicrosoftTranslator = shared
    }
}
