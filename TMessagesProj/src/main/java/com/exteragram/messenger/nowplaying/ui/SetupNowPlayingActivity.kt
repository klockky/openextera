package com.exteragram.messenger.nowplaying.ui

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.exteragram.messenger.api.model.NowPlayingServiceType
import com.exteragram.messenger.nowplaying.NowPlayingController
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BotWebViewVibrationEffect
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DialogRadioCell
import org.telegram.ui.Cells.EditTextCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.CircularProgressDrawable
import org.telegram.ui.Components.CrossfadeDrawable
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView

class SetupNowPlayingActivity : BaseFragment() {

    data class NowPlayingState(
        val serviceType: NowPlayingServiceType,
        val username: String?
    )

    private lateinit var listView: UniversalRecyclerView
    private lateinit var nowPlayingEdit: EditTextCell
    private var doneButton: ActionBarMenuItem? = null
    private lateinit var doneButtonDrawable: CrossfadeDrawable

    private var initialState: NowPlayingState? = null
    private var currentState = NowPlayingState(NowPlayingServiceType.NONE, null)
    private var shiftDp = -4

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(LocaleController.getString(R.string.NowPlaying))
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment()
                    }
                } else if (id == DONE_BUTTON) {
                    processDone(true)
                }
            }
        })

        val frameLayout = FrameLayout(context)
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))

        listView = UniversalRecyclerView(this, ::fillItems, ::onClick, null)
        listView.setSections()
        actionBar.setAdaptiveBackground(listView)
        adapter.setApplyBackground(false)
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

        nowPlayingEdit = object : EditTextCell(context, LocaleController.getString(R.string.Username), false, false, -1, resourceProvider) {
            override fun onTextChanged(newText: CharSequence) {
                super.onTextChanged(newText)
                currentState = currentState.copy(username = newText.toString())
                checkDone(true)
            }
        }
        nowPlayingEdit.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite))
        nowPlayingEdit.hideKeyboardOnEnter()

        NowPlayingController.getNowPlayingInfo { info ->
            AndroidUtilities.runOnUIThread {
                val state = if (info != null) {
                    NowPlayingState(info.serviceType, info.username ?: "")
                } else {
                    NowPlayingState(NowPlayingServiceType.NONE, "")
                }
                currentState = state
                initialState = state
                nowPlayingEdit.setText(state.username)
                adapter.update(true)
                checkDone(false)
            }
        }

        val doneDrawable = ContextCompat.getDrawable(context, R.drawable.ic_ab_done)?.mutate()
        doneDrawable?.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY)
        doneButtonDrawable = CrossfadeDrawable(doneDrawable, CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)))
        doneButton = actionBar.createMenu().addItemWithWidth(DONE_BUTTON, doneButtonDrawable, AndroidUtilities.dp(56f), LocaleController.getString(R.string.Done))
        checkDone(false)

        fragmentView = frameLayout
        return frameLayout
    }

    // UniversalRecyclerView exposes its adapter as a public field that clashes with RecyclerView.getAdapter()
    private val adapter: UniversalAdapter
        get() = listView.adapter as UniversalAdapter

    private fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.SelectService)))
        for (type in NowPlayingServiceType.entries) {
            items.add(UItem.asRadio(type.ordinal, type.displayName).setChecked(type == currentState.serviceType && initialState != null))
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.SelectServiceInfo)))
        if (currentState.serviceType != NowPlayingServiceType.NONE) {
            items.add(UItem.asHeader(LocaleController.getString(R.string.EnterUsername)))
            items.add(UItem.asCustom(nowPlayingEdit))
            items.add(UItem.asShadow(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.EnterUsernameInfo)) {
                val url = if (currentState.serviceType == NowPlayingServiceType.LAST_FM) "https://www.last.fm/" else "https://stats.fm/"
                Browser.openUrl(parentActivity, url)
            }))
        }
    }

    private fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (view is DialogRadioCell) {
            currentState = currentState.copy(serviceType = NowPlayingServiceType.entries[item.id])
            adapter.update(true)
            checkDone(true)
        }
    }

    private fun processDone(validate: Boolean) {
        if (doneButtonDrawable.progress > 0f) {
            return
        }
        if (validate && currentState.serviceType != NowPlayingServiceType.NONE && nowPlayingEdit.text.isNullOrEmpty()) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate()
            shiftDp = -shiftDp
            AndroidUtilities.shakeViewSpring(nowPlayingEdit, shiftDp.toFloat())
            return
        }
        doneButtonDrawable.animateToProgress(1f)
        val info = NowPlayingInfoDTO(
            currentState.serviceType,
            if (currentState.serviceType == NowPlayingServiceType.NONE) null else currentState.username
        )
        NowPlayingController.updateNowPlayingInfo(info) { success ->
            AndroidUtilities.runOnUIThread {
                if (success) {
                    notificationCenter.postNotificationName(NotificationCenter.nowPlayingUpdated, info.serviceType)
                    finishFragment()
                } else {
                    doneButtonDrawable.animateToProgress(0f)
                    BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show()
                }
            }
        }
    }

    private fun checkDone(animated: Boolean) {
        val button = doneButton ?: return
        val changed = initialState != currentState
        button.isEnabled = changed
        val value = if (changed) 1f else 0f
        if (animated) {
            button.animate().alpha(value).scaleX(value).scaleY(value).setDuration(180).start()
        } else {
            button.alpha = value
            button.scaleX = value
            button.scaleY = value
        }
    }

    companion object {
        private const val DONE_BUTTON = 0
    }
}
