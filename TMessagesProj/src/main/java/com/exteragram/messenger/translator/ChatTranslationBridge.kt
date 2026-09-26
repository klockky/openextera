package com.exteragram.messenger.translator

import android.os.SystemClock
import com.exteragram.messenger.translator.core.TranslationError
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.TranslateAlert2

object ChatTranslationBridge {

    private const val FAILURE_NOTICE_INTERVAL = 60_000L

    private val lastFailureNotice = HashMap<Long, Long>()

    @JvmStatic
    fun translateMessages(
        dialogId: Long,
        forceUpdate: Boolean,
        messageIds: List<Int>,
        texts: List<TLRPC.TL_textWithEntities>,
        callbacks: List<Utilities.Callback4<Boolean, Int, TLRPC.TL_textWithEntities?, String>>,
        toLang: String,
        onDone: Runnable,
    ) {
        translate(dialogId, texts, toLang) { translated, cancelled ->
            if (!cancelled) {
                for (i in callbacks.indices) {
                    callbacks[i].run(forceUpdate, messageIds[i], translated?.getOrNull(i), toLang)
                }
            }
            onDone.run()
        }
    }

    @JvmStatic
    fun translateTexts(
        dialogId: Long,
        texts: List<TLRPC.TL_textWithEntities>,
        toLang: String,
        callback: Utilities.Callback<List<TLRPC.TL_textWithEntities>?>,
    ) {
        translate(dialogId, texts, toLang) { translated, _ ->
            callback.run(translated?.filterNotNull()?.takeIf { it.size == texts.size })
        }
    }

    private fun translate(
        dialogId: Long,
        texts: List<TLRPC.TL_textWithEntities>,
        toLang: String,
        onResult: (List<TLRPC.TL_textWithEntities?>?, Boolean) -> Unit,
    ) {
        ChatTranslator.translate(dialogId, texts.map { it.text ?: "" }, toLang) { results, error ->
            val cancelled = error is TranslationError.Cancelled
            val translated = if (cancelled) {
                null
            } else {
                texts.indices.map { i ->
                    results.getOrNull(i)?.let { text ->
                        TranslateAlert2.preprocess(texts[i], TLRPC.TL_textWithEntities().apply { this.text = text })
                    }
                }
            }
            onResult(translated, cancelled)
            if (error != null && !cancelled) {
                notifyFailure(dialogId, error)
            }
        }
    }

    private fun notifyFailure(dialogId: Long, error: TranslationError) {
        val now = SystemClock.elapsedRealtime()
        val last = lastFailureNotice[dialogId]
        if (last != null && now - last < FAILURE_NOTICE_INTERVAL) {
            return
        }
        lastFailureNotice[dialogId] = now
        val text = LocaleController.getString(
            if (error is TranslationError.RateLimited) R.string.TranslationFailedAlert1 else R.string.TranslationFailedAlert2
        )
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, 1, text)
    }
}
