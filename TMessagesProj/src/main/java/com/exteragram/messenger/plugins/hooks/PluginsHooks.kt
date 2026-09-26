package com.exteragram.messenger.plugins.hooks

import org.telegram.messenger.SendMessagesHelper
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC

interface PluginsHooks {

    fun executePreRequestHook(requestName: String?, account: Int, request: TLObject?): TLObject?

    fun executePostRequestHook(requestName: String?, account: Int, response: TLObject?, error: TLRPC.TL_error?): PostRequestResult

    fun executeUpdateHook(updateName: String?, account: Int, update: TLRPC.Update?): TLRPC.Update?

    fun executeUpdatesHook(updatesName: String?, account: Int, updates: TLRPC.Updates?): TLRPC.Updates?

    fun executeSendMessageHook(account: Int, params: SendMessagesHelper.SendMessageParams?): SendMessagesHelper.SendMessageParams?

    class PostRequestResult(
        val response: TLObject?,
        val error: TLRPC.TL_error?
    )
}
