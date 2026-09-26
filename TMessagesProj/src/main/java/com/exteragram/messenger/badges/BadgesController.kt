package com.exteragram.messenger.badges

import android.widget.FrameLayout
import com.exteragram.messenger.api.db.ExteraDatabase
import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.badges.source.ApiBadgeSource
import com.exteragram.messenger.components.SupporterBottomSheet
import com.exteragram.messenger.utils.chats.ChatUtils
import com.exteragram.messenger.utils.text.LocaleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.BulletinFactory
import java.util.function.Consumer

object BadgesController {
    private val scope = CoroutineScope(Dispatchers.IO)

    // TODO(openextera): exteraSquad infrastructure (remote-config key and default channel id)
    private val trustedPluginsCache = CachedRemoteSet("trusted_plugins", setOf(2562664432L))

    private val DEV_BADGE = BadgeDTO(5359407509327085568L, null)
    private val SUPPORTER_BADGE = BadgeDTO(5391059537102927631L, null)
    private val TRUSTED_BADGE = BadgeDTO(5452008215409629764L, null)

    private val apiBadgeSource = ApiBadgeSource(ExteraDatabase.getInstance().profileDao())

    init {
        scope.launch {
            apiBadgeSource.loadToCache()
        }
    }

    private val currentUser: TLRPC.User?
        get() = UserConfig.getInstance(UserConfig.selectedAccount).currentUser

    @JvmOverloads
    fun getBadge(obj: TLObject? = currentUser): BadgeDTO? {
        try {
            val (id, isUser) = when (obj) {
                is TLRPC.User -> obj.id to true
                is TLRPC.Chat -> obj.id to false
                else -> return null
            }
            if (!isUser && isTrusted(id)) {
                return TRUSTED_BADGE
            }
            return apiBadgeSource.getBadge(id, isUser)
        } catch (e: Exception) {
            FileLog.e(e)
        }
        return null
    }

    @JvmOverloads
    fun hasBadge(obj: TLObject? = currentUser): Boolean = getBadge(obj) != null

    fun isTrusted(id: Long): Boolean = trustedPluginsCache.contains(id)

    fun isExtera(id: Long): Boolean = apiBadgeSource.isDeveloper(id)

    fun isExtera(chat: TLRPC.Chat?): Boolean = chat != null && isExtera(chat.id)

    fun getDefaultBadge(): BadgeDTO {
        return if (apiBadgeSource.isDeveloper(UserConfig.getInstance(UserConfig.selectedAccount).clientUserId)) {
            DEV_BADGE
        } else {
            SUPPORTER_BADGE
        }
    }

    fun getDefaultBadge(user: TLRPC.User?): BadgeDTO? {
        if (user == null) {
            return null
        }
        return if (isDeveloper(user)) DEV_BADGE else SUPPORTER_BADGE
    }

    fun shouldUseSecondaryBadgeSlot(user: TLRPC.User?, badge: BadgeDTO?): Boolean {
        return user != null && badge != null && canChangeBadge(user) && (badge != getDefaultBadge(user) || isDeveloper(user))
    }

    fun getSecondaryBadge(user: TLRPC.User?): BadgeDTO? {
        val badge = getBadge(user)
        return if (badge != null && shouldUseSecondaryBadgeSlot(user, badge)) badge else null
    }

    @JvmOverloads
    fun canChangeBadge(user: TLRPC.User = currentUser!!): Boolean = apiBadgeSource.canChangeBadge(user.id)

    @JvmOverloads
    fun isDeveloper(user: TLRPC.User = currentUser!!): Boolean = apiBadgeSource.isDeveloper(user.id)

    fun updateBadge(badge: BadgeDTO?, callback: Consumer<String?>) {
        if (badge == null) {
            callback.accept(null)
            return
        }
        val text = badge.text
        val request = if (text.isNullOrEmpty()) {
            "badge ${badge.documentId}"
        } else {
            "badge ${badge.documentId} $text"
        }
        ChatUtils.getInstance(UserConfig.selectedAccount).sendBotRequest(request, false) { result: String? ->
            if (result == "ok") {
                scope.launch {
                    apiBadgeSource.updateLocalBadge(UserConfig.getInstance(UserConfig.selectedAccount).clientUserId, badge)
                }
            }
            callback.accept(result)
        }
    }

    @JvmOverloads
    fun showBadgeBulletin(
        fragment: BaseFragment,
        user: TLRPC.User?,
        resourcesProvider: Theme.ResourcesProvider? = fragment.resourceProvider,
        account: Int = fragment.getCurrentAccount(),
        containerLayout: FrameLayout? = null,
        showButton: Boolean? = null
    ) {
        if (user == null) {
            return
        }
        val badge = getBadge(user) ?: return
        showBadgeBulletin(fragment, badge, user, resourcesProvider, account, containerLayout, showButton)
    }

    @JvmOverloads
    fun showBadgeBulletin(
        fragment: BaseFragment,
        badge: BadgeDTO,
        user: TLRPC.User?,
        resourcesProvider: Theme.ResourcesProvider? = fragment.resourceProvider,
        account: Int = fragment.getCurrentAccount(),
        containerLayout: FrameLayout? = null,
        showButton: Boolean? = null
    ) {
        if (user == null) {
            return
        }
        val developer = isDeveloper(user)
        val text = formatBadgeText(
            badge,
            LocaleController.formatString(if (developer) R.string.Developer else R.string.Supporter, user.first_name)
        )
        val withButton = showButton ?: !(developer || canChangeBadge(user))
        showBadgeBulletin(fragment, badge, text, withButton, resourcesProvider, account, containerLayout)
    }

    @JvmOverloads
    fun showBadgeBulletin(
        fragment: BaseFragment,
        badge: BadgeDTO,
        chat: TLRPC.Chat?,
        resourcesProvider: Theme.ResourcesProvider? = fragment.resourceProvider,
        account: Int = fragment.getCurrentAccount(),
        containerLayout: FrameLayout? = null,
        showButton: Boolean? = null
    ) {
        if (chat == null) {
            return
        }
        val extera = isExtera(chat)
        val trusted = isTrusted(chat.id)
        val description = when {
            extera -> LocaleController.formatString(R.string.OfficialChannel, chat.title)
            trusted -> LocaleController.getString(R.string.PluginSourceTrustedInfo)
            else -> LocaleController.formatString(R.string.Supporter, chat.title)
        }
        val text = formatBadgeText(badge, description)
        val withButton = showButton ?: !(extera || trusted)
        showBadgeBulletin(fragment, badge, text, withButton, resourcesProvider, account, containerLayout)
    }

    private fun showBadgeBulletin(
        fragment: BaseFragment,
        badge: BadgeDTO,
        text: CharSequence,
        showButton: Boolean,
        resourcesProvider: Theme.ResourcesProvider?,
        account: Int,
        containerLayout: FrameLayout?
    ) {
        val factory = if (containerLayout != null) {
            BulletinFactory.of(containerLayout, resourcesProvider)
        } else {
            BulletinFactory.of(fragment)
        }
        val bulletin = factory.createEmojiBulletin(
            AnimatedEmojiDrawable.findDocument(account, badge.documentId),
            text,
            if (showButton) LocaleController.getString(R.string.FragmentUsernameOpen) else null,
            if (showButton) Runnable { SupporterBottomSheet.showAlert(fragment, resourcesProvider) } else null
        )
        if (!showButton) {
            bulletin.wrapContent()
        }
        bulletin.show()
    }

    private fun formatBadgeText(badge: BadgeDTO, fallback: CharSequence): CharSequence {
        return badge.text?.let { LocaleUtils.formatWithUsernames(it) } ?: fallback
    }
}
