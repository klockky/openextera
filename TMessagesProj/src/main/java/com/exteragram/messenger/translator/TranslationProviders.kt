package com.exteragram.messenger.translator

import com.exteragram.messenger.ExteraConfig
import com.exteragram.messenger.translator.core.BaseTranslator
import com.exteragram.messenger.translator.providers.GoogleTranslator
import com.exteragram.messenger.translator.providers.MicrosoftTranslator
import com.exteragram.messenger.translator.providers.TelegramTranslator
import com.exteragram.messenger.translator.providers.YandexTranslator
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig

object TranslationProviders {

    @JvmField
    val ALL: List<BaseTranslator> = listOf(
        TelegramTranslator.getInstance(),
        GoogleTranslator.getInstance(),
        YandexTranslator.getInstance(),
        MicrosoftTranslator.getInstance(),
    )

    @JvmStatic
    val lastIndex: Int
        get() = ALL.size - 1

    @JvmStatic
    fun names(): Array<CharSequence> = Array(ALL.size) { ALL[it].displayName }

    @JvmStatic
    fun current(): BaseTranslator = ALL.getOrNull(ExteraConfig.translationProvider) ?: ALL[1]

    @JvmStatic
    fun isTelegram(): Boolean = current() is TelegramTranslator

    @JvmStatic
    fun isAlternative(): Boolean = !isTelegram()

    @JvmStatic
    fun isChatTranslationUnlocked(account: Int, dialogId: Long): Boolean {
        if (isChatTranslationUnlocked(account)) {
            return true
        }
        val chat = MessagesController.getInstance(account).getChat(-dialogId)
        return chat != null && chat.autotranslation
    }

    @JvmStatic
    fun isChatTranslationUnlocked(account: Int): Boolean = isAlternative() || UserConfig.getInstance(account).isPremium
}
