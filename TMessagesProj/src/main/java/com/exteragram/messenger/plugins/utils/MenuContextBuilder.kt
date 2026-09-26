package com.exteragram.messenger.plugins.utils

import android.content.Context
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_bots
import org.telegram.ui.ActionBar.BaseFragment

/**
 * Lite build: plugins are not available, the builder does not collect anything.
 */
class MenuContextBuilder {

    fun withAccount(account: Int) = this

    fun withContext(context: Context?) = this

    fun withDialogId(dialogId: Long) = this

    fun withUser(user: TLRPC.User?) = this

    fun withUserFull(userFull: TLRPC.UserFull?) = this

    fun withChat(chat: TLRPC.Chat?) = this

    fun withChatFull(chatFull: TLRPC.ChatFull?) = this

    fun withEncryptedChat(encryptedChat: TLRPC.EncryptedChat?) = this

    fun withBotInfo(botInfo: TL_bots.BotInfo?) = this

    fun withMessage(messageObject: MessageObject?) = this

    fun withGroupedMessage(groupedMessages: MessageObject.GroupedMessages?) = this

    fun build(): Map<String, Any> = emptyMap()

    companion object {
        @JvmStatic
        fun create() = MenuContextBuilder()

        @JvmStatic
        fun from(fragment: BaseFragment?) = MenuContextBuilder()
    }
}
