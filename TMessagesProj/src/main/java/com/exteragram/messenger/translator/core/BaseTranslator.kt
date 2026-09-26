package com.exteragram.messenger.translator.core

import com.exteragram.messenger.translator.TranslatorUtils
import java.util.Locale

abstract class BaseTranslator {

    abstract val displayName: String

    abstract val supportedLanguages: Set<String>

    abstract fun translate(text: String, fromLang: String, toLang: String, callback: TranslatorUtils.TranslateCallback)

    fun isLanguageSupported(language: String?): Boolean {
        if (supportedLanguages.isEmpty()) {
            return true
        }
        val primary = TranslatorUtils.primaryLanguageOf(language) ?: return false
        return supportedLanguages.any { it.lowercase(Locale.US).substringBefore('-') == primary }
    }
}
