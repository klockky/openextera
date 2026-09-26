package com.exteragram.messenger.translator.providers

import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.translator.core.BaseTranslator

class TelegramTranslator private constructor() : BaseTranslator() {

    override val displayName = "Telegram"
    override val supportedLanguages = emptySet<String>()

    override fun translate(text: String, fromLang: String, toLang: String, callback: TranslatorUtils.TranslateCallback) {
        TranslatorUtils.translateWithDefault(text, null, 0, toLang, null, callback)
    }

    companion object {
        private val shared by lazy { TelegramTranslator() }

        @JvmStatic
        fun getInstance(): TelegramTranslator = shared
    }
}
