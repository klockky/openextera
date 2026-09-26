package com.exteragram.messenger.utils.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.exteragram.messenger.ExteraConfig
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import org.telegram.ui.LaunchActivity
import org.telegram.ui.LoginActivity
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.IntPredicate

object AccountsUiHelper {

    private const val MAX_ACCOUNTS = UserConfig.MAX_ACCOUNT_COUNT
    private const val DEFAULT_ACCOUNTS = UserConfig.MAX_ACCOUNT_DEFAULT_COUNT

    @JvmStatic
    @JvmOverloads
    fun activated(filter: IntPredicate? = null): List<Int> =
        (0 until MAX_ACCOUNTS)
            .filter { UserConfig.getInstance(it).isClientActivated && (filter == null || filter.test(it)) }
            .sortedBy { UserConfig.getInstance(it).loginTime }

    @JvmStatic
    fun switchTo(account: Int) {
        LaunchActivity.instance?.switchToAccount(account, true)
    }

    @JvmStatic
    fun hasFreeSlot(): Boolean = UserConfig.getActivatedAccountsCount() < MAX_ACCOUNTS

    @JvmStatic
    fun freeSlotWithinLimit(): Int? {
        var freeCount = 0
        var slot: Int? = null
        for (account in MAX_ACCOUNTS - 1 downTo 0) {
            if (!UserConfig.getInstance(account).isClientActivated) {
                freeCount++
                if (slot == null) {
                    slot = account
                }
            }
        }
        if (!UserConfig.hasPremiumOnAccounts()) {
            freeCount -= MAX_ACCOUNTS - DEFAULT_ACCOUNTS
        }
        return if (freeCount > 0) slot else null
    }

    @JvmStatic
    @JvmOverloads
    fun add(fragment: BaseFragment? = null) {
        val target = fragment ?: LaunchActivity.getSafeLastFragment()
        val slot = freeSlotWithinLimit()
        if (slot == null) {
            if (UserConfig.hasPremiumOnAccounts() || target == null) {
                return
            }
            val context = target.context ?: return
            target.showDialog(LimitReachedBottomSheet(target, context, LimitReachedBottomSheet.TYPE_ACCOUNTS, target.currentAccount, null))
            return
        }
        if (target != null) {
            target.presentFragment(LoginActivity(slot))
        } else {
            LaunchActivity.instance?.presentFragment(LoginActivity(slot))
        }
    }

    @JvmStatic
    fun row(
        context: Context,
        resourcesProvider: Theme.ResourcesProvider?,
        account: Int,
        selected: Boolean,
        roundTop: Boolean,
        roundBottom: Boolean
    ): LinearLayout {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.background = Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 0)
        UIUtil.applyScaleStateListAnimator(layout, 12f, roundTop, roundBottom, 3, 0.04f, 1.5f)

        val user = UserConfig.getInstance(account).currentUser
        val avatarDrawable = AvatarDrawable().apply { setInfo(user) }

        val avatarContainer = object : FrameLayout(context) {
            private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            override fun dispatchDraw(canvas: Canvas) {
                if (selected) {
                    selectedPaint.style = Paint.Style.STROKE
                    selectedPaint.strokeWidth = AndroidUtilities.dp(1.33f).toFloat()
                    selectedPaint.color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)
                    val radius = ExteraConfig.getAvatarCorners(34f).toFloat()
                    val inset = AndroidUtilities.dp(1f).toFloat()
                    canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, selectedPaint)
                }
                super.dispatchDraw(canvas)
            }
        }
        layout.addView(avatarContainer, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 12, 0, 0, 0))

        val imageView = BackupImageView(context)
        if (selected) {
            imageView.scaleX = 0.833f
            imageView.scaleY = 0.833f
        }
        imageView.setRoundRadius(ExteraConfig.getAvatarCorners(32f))
        imageView.imageReceiver.setCurrentAccount(account)
        imageView.setForUserOrChat(user, avatarDrawable)
        avatarContainer.addView(imageView, LayoutHelper.createLinear(32, 32, Gravity.CENTER, 1, 1, 1, 1))

        val textView = TextView(context)
        NotificationCenter.listenEmojiLoading(textView)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider))
        textView.text = Emoji.replaceEmoji(UserObject.getUserName(user), textView.paint.fontMetricsInt, false)
        textView.maxLines = 2
        textView.ellipsize = TextUtils.TruncateAt.END
        layout.addView(textView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 13, 0, 14, 0))
        return layout
    }

    @JvmStatic
    fun menu(fragment: BaseFragment, anchor: View): Builder = Builder(fragment, anchor)

    class Builder(private val fragment: BaseFragment, private val anchor: View) {
        private var fromBottom = false
        private var withAddAccount = true
        private var touchRelayView: View? = null
        private var onSelected: IntConsumer = IntConsumer { switchTo(it) }
        private var extraItems: Consumer<ItemOptions>? = null

        fun fromBottom(value: Boolean) = apply { fromBottom = value }

        fun withAddAccount(value: Boolean) = apply { withAddAccount = value }

        fun touchRelay(view: View?) = apply { touchRelayView = view }

        fun onSelected(listener: IntConsumer) = apply { onSelected = listener }

        fun extraItems(consumer: Consumer<ItemOptions>?) = apply { extraItems = consumer }

        @SuppressLint("ClickableViewAccessibility")
        fun show(): Boolean {
            val context = fragment.context ?: return false
            val resourcesProvider = fragment.resourceProvider
            val currentAccount = fragment.currentAccount
            val accounts = activated().let { if (fromBottom) it.asReversed() else it }

            val options = ItemOptions.makeOptions(fragment, anchor)
            val showAddAccount = withAddAccount && hasFreeSlot()
            if (showAddAccount && !fromBottom) {
                addAccountItem(options)
            }
            extraItems?.accept(options)

            if (accounts.isNotEmpty()) {
                touchRelayView?.setOnTouchListener { view, event ->
                    if (!options.isShown) {
                        return@setOnTouchListener false
                    }
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    options.dispatchCapturedTouchEvent(event)
                    false
                }
                if (options.itemsCount > 0) {
                    options.addGap()
                }
                accounts.forEachIndexed { index, account ->
                    val row = row(
                        context,
                        resourcesProvider,
                        account,
                        currentAccount == account,
                        fromBottom && index == 0,
                        !fromBottom && index == accounts.size - 1
                    )
                    row.setOnClickListener {
                        if (currentAccount != account) {
                            options.dismiss()
                            onSelected.accept(account)
                        }
                    }
                    options.addView(row, LayoutHelper.createLinear(230, 48))
                }
            }

            if (showAddAccount && fromBottom) {
                if (options.itemsCount > 0) {
                    options.addGap()
                }
                addAccountItem(options)
            }

            options.setBlur(true)
                .translate(0f, -AndroidUtilities.dp(4f).toFloat())
                .setScrimViewBackground(MainTabsUiHelper.createMainTabsScrimBackground(resourcesProvider, fromBottom))
                .setDismissOnMoveOutside(true)
                .show()
            return true
        }

        private fun addAccountItem(options: ItemOptions) {
            options.add(R.drawable.msg_addbot, LocaleController.getString(R.string.AddAccount)) { add(fragment) }
        }
    }
}
