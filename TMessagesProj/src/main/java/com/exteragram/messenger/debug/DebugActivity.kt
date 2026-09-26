package com.exteragram.messenger.debug

import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.exteragram.messenger.api.ApiController
import com.exteragram.messenger.api.db.ExteraDatabase
import com.exteragram.messenger.preferences.BasePreferencesActivity
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.tgnet.ConnectionsManager
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

class DebugActivity : BasePreferencesActivity() {

    enum class DebugItem {
        DEBUG_CAMERA_METRICS,
        FORCE_COMPACT_SAVED_MUSIC,
        DISABLE_API_REQUESTS,
        DISABLE_CHAT_FADE_WALLPAPER_BLEND,
        CHAT_FADE_USE_WHITE_BACKGROUND,
        FORCE_REFRESH_REMOTE_CONFIG,
        CLEAR_DB,
        CLEAR_TRANSLATIONS,
        SET_IPCONFIG_OVERRIDE,
        CLEAR_IPCONFIG_OVERRIDE;

        val id: Int
            get() = ordinal + 1
    }

    override fun getTitle(): String = "Debug"

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asCheck(DebugItem.DEBUG_CAMERA_METRICS.id, "Metrics in InstantCameraView").setChecked(DebugConfig.debugCameraMetrics))
        items.add(UItem.asCheck(DebugItem.FORCE_COMPACT_SAVED_MUSIC.id, "Compact Saved Music view").setChecked(DebugConfig.forceCompactSavedMusic))
        items.add(UItem.asCheck(DebugItem.DISABLE_API_REQUESTS.id, "Disable exteraGram API requests").setChecked(DebugConfig.disableApiRequests))
        items.add(UItem.asCheck(DebugItem.DISABLE_CHAT_FADE_WALLPAPER_BLEND.id, "Disable chat fade wallpaper blend").setChecked(DebugConfig.disableChatFadeWallpaperBlend))
        items.add(UItem.asCheck(DebugItem.CHAT_FADE_USE_WHITE_BACKGROUND.id, "Use windowBackgroundWhite for chat fade").setChecked(DebugConfig.chatFadeUseWhiteBackground))
        items.add(UItem.asShadow())
        items.add(UItem.asButton(DebugItem.FORCE_REFRESH_REMOTE_CONFIG.id, "Refresh Remote Config"))
        items.add(UItem.asButton(DebugItem.CLEAR_DB.id, "Clear exteraDatabase"))
        items.add(UItem.asButton(DebugItem.CLEAR_TRANSLATIONS.id, "Clear translations cache"))
        items.add(UItem.asShadow())
        items.add(UItem.asButton(DebugItem.SET_IPCONFIG_OVERRIDE.id, "Set ipconfigv3 override", ipConfigOverrideValue))
        items.add(UItem.asButton(DebugItem.CLEAR_IPCONFIG_OVERRIDE.id, "Clear ipconfigv3 override"))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val debugItem = DebugItem.entries.getOrNull(item.id - 1) ?: return
        when (debugItem) {
            DebugItem.DEBUG_CAMERA_METRICS -> toggleBooleanSettingAndRefresh(item) { DebugConfig.debugCameraMetrics = it }
            DebugItem.FORCE_COMPACT_SAVED_MUSIC -> toggleBooleanSettingAndRefresh(item) { DebugConfig.forceCompactSavedMusic = it }
            DebugItem.DISABLE_API_REQUESTS -> toggleBooleanSettingAndRefresh(item) { DebugConfig.disableApiRequests = it }
            DebugItem.DISABLE_CHAT_FADE_WALLPAPER_BLEND -> toggleBooleanSettingAndRefresh(item) { DebugConfig.disableChatFadeWallpaperBlend = it }
            DebugItem.CHAT_FADE_USE_WHITE_BACKGROUND -> toggleBooleanSettingAndRefresh(item) { DebugConfig.chatFadeUseWhiteBackground = it }
            DebugItem.FORCE_REFRESH_REMOTE_CONFIG -> {
                // TODO(openextera): disabled, exteraSquad infrastructure (remote-config channel fetching)
                // RemoteUtils.forceRefresh()
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, "Remote config is disabled in this build.").show()
            }
            DebugItem.CLEAR_DB -> Utilities.globalQueue.postRunnable {
                ExteraDatabase.getInstance().clearAllTables()
                ApiController.resetSyncState()
                AndroidUtilities.runOnUIThread {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Successfully cleared all tables.").show()
                }
            }
            DebugItem.CLEAR_TRANSLATIONS -> clearTranslations()
            DebugItem.SET_IPCONFIG_OVERRIDE -> showIpConfigOverrideDialog()
            DebugItem.CLEAR_IPCONFIG_OVERRIDE -> {
                ConnectionsManager.setDebugDnsConfigOverride(null)
                listView.adapter.update(true)
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "ipconfigv3 override cleared.").show()
            }
        }
    }

    private fun clearTranslations() {
        val translateController = messagesController.translateController
        translateController.clearTranslationCache()
        messagesController.dialogMessage?.let { dialogMessages ->
            for (i in 0 until dialogMessages.size()) {
                dialogMessages.valueAt(i)?.forEach { translateController.clearMessageTranslationState(it) }
            }
        }
        messagesController.dialogMessagesByIds?.let { messages ->
            for (i in 0 until messages.size()) {
                translateController.clearMessageTranslationState(messages.valueAt(i))
            }
        }
        messagesController.dialogMessagesByRandomIds?.let { messages ->
            for (i in 0 until messages.size()) {
                translateController.clearMessageTranslationState(messages.valueAt(i))
            }
        }
        AndroidUtilities.runOnUIThread {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Translation cache cleared.").show()
        }
    }

    private val ipConfigOverrideValue: String
        get() {
            val value = ConnectionsManager.getDebugDnsConfigOverride()
            return if (value.isNullOrBlank()) "Not set" else "${value.length} chars"
        }

    private fun showIpConfigOverrideDialog() {
        val activity = parentActivity ?: return

        val editText = EditTextBoldCursor(activity).apply {
            lineYFix = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            setText(ConnectionsManager.getDebugDnsConfigOverride())
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider))
            setHintText("Base64 from Firebase Remote Config")
            gravity = Gravity.TOP or Gravity.START
            minLines = 6
            maxLines = 10
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setCursorColor(Theme.getColor(Theme.key_dialogInputFieldActivated, resourceProvider))
            setLineColors(
                Theme.getColor(Theme.key_dialogInputField, resourceProvider),
                Theme.getColor(Theme.key_dialogInputFieldActivated, resourceProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourceProvider)
            )
            background = null
            setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f))
            setSelection(text?.length ?: 0)
        }

        val container = FrameLayout(activity)
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.START, 24f, 12f, 24f, 0f))

        showDialog(
            AlertDialog.Builder(activity)
                .setTitle("ipconfigv3 override")
                .setView(container)
                .setPositiveButton("Apply") { _, _ ->
                    val value = editText.text?.toString()?.trim().orEmpty()
                    if (value.isEmpty()) {
                        ConnectionsManager.setDebugDnsConfigOverride(null)
                        listView.adapter.update(true)
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "ipconfigv3 override cleared.").show()
                    } else {
                        val applied = ConnectionsManager.setAndApplyDebugDnsConfigOverride(currentAccount, value)
                        listView.adapter.update(true)
                        BulletinFactory.of(this).createSimpleBulletin(
                            if (applied) R.raw.contact_check else R.raw.error,
                            if (applied) "ipconfigv3 override saved and submitted." else "Failed to decode ipconfigv3 override."
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()
        )
    }
}
