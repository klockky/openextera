package com.exteragram.messenger.utils

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.exteragram.messenger.ExteraConfig
import com.exteragram.messenger.backup.BackupBottomSheet
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.components.SupporterBottomSheet
import com.exteragram.messenger.feed.ui.FeedActivity
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.preferences.MainPreferencesActivity
import com.exteragram.messenger.preferences.OtherPreferencesActivity
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.chats.ChatUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.EmojiPacksAlert
import org.telegram.ui.LaunchActivity
import org.telegram.ui.ProfileActivity
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

object IntentsController {

    private val telegramHosts = setOf("t.me", "telegram.me", "telegram.dog", "telegram.org")

    private val deeplinkCallbacks: Map<String, (Uri) -> Unit> = mapOf(
        "extera" to { _ ->
            AndroidUtilities.runOnUIThread { LaunchActivity.instance.presentFragment(MainPreferencesActivity()) }
        },
        "export" to { _ ->
            LaunchActivity.getSafeLastFragment()?.let { PreferencesUtils.getInstance().exportSettings(it) }
        },
        "feed" to { _ ->
            AndroidUtilities.runOnUIThread { FeedActivity.presentFeed(LaunchActivity.getSafeLastFragment()) }
        },
        "support" to { _ ->
            LaunchActivity.getSafeLastFragment()?.let { SupporterBottomSheet.showAlert(it) }
        },
        "donate" to { _ ->
            AndroidUtilities.runOnUIThread { LaunchActivity.instance.presentFragment(OtherPreferencesActivity()) }
        },
        "emoji" to { uri -> openEmoji(uri) },
        "user" to { uri -> openUser(uri) },
        "chat" to { uri -> openChat(uri) },
    )

    private val callbacks: Map<String, (Uri) -> Unit> = mapOf(
        "exteraSettings" to { uri ->
            SettingsRegistry.getInstance().handleLink(uri.getQueryParameter("s"), uri.getQueryParameter("p"))
        },
    )

    private val actionCallbacks: Map<String, (Intent) -> Boolean> = mapOf(
        "com.exteragram.plugins.safemode" to { _ ->
            ExteraConfig.pluginsSafeMode = true
            if (ExteraConfig.pluginsEngine) {
                val controller = PluginsController.getInstance()
                if (controller.isInitialized) {
                    controller.restart(true)
                }
            }
            true
        },
        Intent.ACTION_VIEW to { intent -> handleFileIntent(intent) },
    )

    private fun openEmoji(uri: Uri) {
        val documentId = uri.getQueryParameter("id")?.toLongOrNull() ?: return
        val fragment = LaunchActivity.getSafeLastFragment() ?: return
        val document = AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, documentId) ?: return
        val stickerSet = MessageObject.getInputStickerSet(document) ?: return
        val alert = EmojiPacksAlert(fragment, fragment.parentActivity, fragment.resourceProvider, arrayListOf(stickerSet))
        alert.setPreviewEmoji(document)
        fragment.showDialog(alert)
    }

    private fun openUser(uri: Uri) {
        val userId = uri.getQueryParameter("id")?.toLongOrNull() ?: return
        val fragment = LaunchActivity.getSafeLastFragment() ?: return
        val profileActivity = ProfileActivity(Bundle().apply { putLong("user_id", userId) })
        val progressDialog = AlertDialog(fragment.parentActivity, AlertDialog.ALERT_TYPE_SPINNER)
        progressDialog.setCanCancel(false)
        progressDialog.show()
        ChatUtils.getInstance().searchUserById(userId) { user: TLRPC.User? ->
            progressDialog.dismiss()
            if (user != null) {
                presentFound(profileActivity)
            } else {
                LaunchActivity.instance.showBulletin { it.createErrorBulletin(LocaleController.getString(R.string.UserNotFound)) }
            }
        }
    }

    private fun openChat(uri: Uri) {
        val chatId = uri.getQueryParameter("id")?.toLongOrNull() ?: return
        val fragment = LaunchActivity.getSafeLastFragment() ?: return
        val chatActivity = ChatActivity(Bundle().apply { putLong("chat_id", chatId) })
        val progressDialog = AlertDialog(fragment.parentActivity, AlertDialog.ALERT_TYPE_SPINNER)
        progressDialog.setCanCancel(false)
        progressDialog.show()
        ChatUtils.getInstance().searchChatById(chatId) { chat: TLRPC.Chat? ->
            progressDialog.dismiss()
            if (chat != null) {
                presentFound(chatActivity)
            } else {
                LaunchActivity.instance.showBulletin { it.createErrorBulletin(LocaleController.getString(R.string.ChatNotFound)) }
            }
        }
    }

    private fun presentFound(fragment: BaseFragment) {
        AndroidUtilities.runOnUIThread { LaunchActivity.instance.presentFragment(fragment, false, false) }
        if (AndroidUtilities.isTablet()) {
            LaunchActivity.instance.actionBarLayout.showLastFragment()
            LaunchActivity.instance.rightActionBarLayout.showLastFragment()
        }
    }

    private fun handleFileIntent(intent: Intent): Boolean {
        val data = intent.data ?: return false
        val extension = data.path?.substringAfterLast('.', "") ?: return false
        val fragment = LaunchActivity.getSafeLastFragment() ?: return false
        return when (extension) {
            "icons" -> {
                IconManager.handleIconPack(fragment, getTempFileFromIntent(data).absolutePath)
                true
            }
            "plugin" -> {
                PluginsController.getInstance().showInstallDialog(fragment, getTempFileFromIntent(data).absolutePath, false)
                true
            }
            "extera" -> {
                BackupBottomSheet(fragment, getTempFileFromIntent(data)).showIfPossible()
                true
            }
            else -> false
        }
    }

    @Throws(FileNotFoundException::class)
    fun getTempFileFromIntent(uri: Uri): File {
        val dir = File(ApplicationLoader.getFilesDirFixed(), "temp")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "temp_file_${System.currentTimeMillis()}.plugin")
        ApplicationLoader.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        file.deleteOnExit()
        return file
    }

    fun handleIntent(intent: Intent): Boolean {
        val action = intent.action
        if (action != null && actionCallbacks[action]?.invoke(intent) == true) {
            return true
        }
        val data = intent.data
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0 || data == null || intent.action != Intent.ACTION_VIEW) {
            return false
        }
        when (data.scheme) {
            "http", "https" -> {
                val host = data.host
                if (host != null && host in telegramHosts && data.pathSegments.isNotEmpty()) {
                    val callback = callbacks[data.pathSegments[0]]
                    if (callback != null) {
                        callback(data)
                        return true
                    }
                }
            }
            "tg" -> {
                val callback = data.host?.let { deeplinkCallbacks[it] }
                if (callback != null) {
                    callback(data)
                    return true
                }
            }
        }
        return false
    }
}
