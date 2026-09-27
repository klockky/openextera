package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.WindowInsetsCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.MainMenuItem;
import com.exteragram.messenger.config.BottomNavigationBar;
import com.exteragram.messenger.feed.FeedController;
import com.exteragram.messenger.feed.ui.FeedActivity;
import com.exteragram.messenger.feed.ui.FeedChannelsActivity;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.exteragram.messenger.utils.chats.MainMenuHelper;
import com.exteragram.messenger.utils.ui.AccountsUiHelper;
import com.exteragram.messenger.utils.ui.MainTabsUiHelper;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EdgeToEdgeSupportMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.HintsController;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProxyDrawable;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.RenderNodeWithHash;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class MainTabsActivity extends ViewPagerActivity implements NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {

    private static final int INDEX_CHATS = 0;
    private static final int INDEX_CONTACTS = 1;
    private static final int INDEX_SETTINGS = 2;
    private static final int INDEX_CALLS = 3;
    private static final int INDEX_PROFILE = 4;
    private static final int INDEX_FEED = 5;

    private int getPositionChats() {
        return 0;
    }

    private int getPositionContacts() {
        return 1;
    }

    private int getPositionCallsOrSettings() {
        return hasContactsOrFeedTab() ? 2 : 1;
    }

    private int getPositionProfile() {
        return hasContactsOrFeedTab() ? 3 : 2;
    }

    public int getTabsCount() {
        return hasContactsOrFeedTab() ? 4 : 3;
    }

    private boolean isFeedTabEnabled() {
        return ExteraConfig.getShowFeedTab();
    }

    private boolean hasContactsOrFeedTab() {
        return getUserConfig().showContactsTab || isFeedTabEnabled();
    }

    private int indexToPosition(int index) {
        switch (index) {
            case INDEX_CHATS:
                return getPositionChats();
            case INDEX_CONTACTS:
            case INDEX_FEED:
                return getPositionContacts();
            case INDEX_SETTINGS:
            case INDEX_CALLS:
                return getPositionCallsOrSettings();
            case INDEX_PROFILE:
                return getPositionProfile();
        }
        return 0;
    }

    private static final int ANIMATOR_ID_TABS_VISIBLE = 0;
    private final BoolAnimator animatorTabsVisible = new BoolAnimator(ANIMATOR_ID_TABS_VISIBLE,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, true);


    private IUpdateLayout updateLayout;
    private boolean dropCallsFragmentAfterPageScroll;

    private UpdateLayoutWrapper updateLayoutWrapper;
    private FrameLayout tabsViewWrapper;
    private MainTabsLayout tabsView;
    private BlurredBackgroundDrawable tabsViewBackground;
    private View fadeView;

    public MainTabsActivity() {
        this(null);
    }

    public MainTabsActivity(Bundle args) {
        arguments = args;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            iBlur3SourceTabGlass = new BlurredBackgroundSourceRenderNode(null);
            iBlur3SourceTabGlass.setupRenderer(new RenderNodeWithHash.Renderer() {
                @Override
                public void renderNodeCalculateHash(IBlur3Hash hash) {
                    hash.add(getThemedColor(Theme.key_windowBackgroundWhite));
                    hash.add(SharedConfig.chatBlurEnabled());

                    for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
                        final FragmentState state = fragmentsArr.valueAt(a);
                        final BaseFragment fragment = state.fragment;
                        if (fragment.fragmentView == null) {
                            continue;
                        }
                        if (!ViewPositionWatcher.computeRectInParent(fragment.fragmentView, contentView, fragmentPosition)) {
                            continue;
                        }
                        if (fragmentPosition.right <= 0 || fragmentPosition.left >= fragmentView.getMeasuredWidth()) {
                            continue;
                        }

                        if (fragment instanceof TabFragmentDelegate) {
                            TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
                            BlurredBackgroundSourceRenderNode source = delegate.getGlassSource();
                            if (source != null) {
                                hash.addF(fragmentPosition.left);
                                hash.addF(fragmentPosition.top);
                                hash.add(fragment.getClassGuid());
                            }
                        }
                    }
                }

                @Override
                public void renderNodeUpdateDisplayList(Canvas canvas) {
                    final int width = fragmentView.getMeasuredWidth();
                    final int height = fragmentView.getMeasuredHeight();

                    canvas.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));

                    for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
                        final FragmentState state = fragmentsArr.valueAt(a);
                        final BaseFragment fragment = state.fragment;
                        if (fragment.fragmentView == null) {
                            continue;
                        }
                        if (!ViewPositionWatcher.computeRectInParent(fragment.fragmentView, contentView, fragmentPosition)) {
                            continue;
                        }
                        if (fragmentPosition.right <= 0 || fragmentPosition.left >= fragmentView.getMeasuredWidth()) {
                            continue;
                        }

                        if (fragment instanceof TabFragmentDelegate) {
                            TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
                            BlurredBackgroundSourceRenderNode source = delegate.getGlassSource();
                            if (source != null) {
                                canvas.save();
                                canvas.translate(fragmentPosition.left, fragmentPosition.top);
                                source.draw(canvas, 0, 0, width, height);
                                canvas.restore();
                            }
                        }
                    }
                }
            });
        } else {
            iBlur3SourceTabGlass = null;
        }

        iBlur3SourceColor = new BlurredBackgroundSourceColor();

        Bulletin.Delegate delegate = new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return navigationBarHeight + (isBottomTabsEnabled() ? Math.round(dp(MainTabsUiHelper.getTabsFabOffsetDp()) * animatorTabsVisible.getFloatValue()) : 0);
            }

            @Override
            public boolean bottomOffsetAnimated() {
                return !BottomNavigationBar.floating();
            }
        };

        Bulletin.addDelegate(this, delegate);
        Bulletin.addDelegate(contentView, delegate);
    }

    @Override
    protected FrameLayout createContentView(Context context) {
        return new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                checkUi_tabsPosition();
                checkUi_fadeView();
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                final int color = getEstBackgroundColor();
                if (insetLeft != 0) {
                    canvas.drawRect(0, 0, insetLeft, getHeight(), Theme.fillingPaint(color));
                }
                if (insetRight != 0) {
                    canvas.drawRect(getWidth() - insetRight, 0, getWidth(), getHeight(), Theme.fillingPaint(color));
                }

                super.dispatchDraw(canvas);
                blur3_invalidateBlur();
                blur3_updateFadeColors();
            }
        };
    }

    private int getEstBackgroundColor() {
        return ColorUtils.blendARGB(
                getThemedColor(Theme.key_windowBackgroundGray),
                getThemedColor(Theme.key_windowBackgroundWhite),
                viewPager != null ? viewPager.getPositionVisibility(0) : 1);
    }

    private boolean tabletLayout;
    public void updateLayout() {
//        if (tabletLayout == AndroidUtilities.isTablet()) return;
//        tabletLayout = AndroidUtilities.isTablet();
//
//        final boolean isUpdateLayoutVisible = updateLayoutWrapper.isUpdateLayoutVisible();
//        final int updateLayoutHeight = isUpdateLayoutVisible ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
//        int bottomMargin = isUpdateLayoutVisible ? (navigationBarHeight + updateLayoutHeight) : 0;
//        if (tabletLayout) {
//            bottomMargin = Math.max(bottomMargin, navigationBarHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
//        }
//        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL);
//        if (tabletLayout) {
//            lp.leftMargin = dp(6);
//            lp.rightMargin = dp(6);
//            lp.topMargin = dp(6);
//        }
//        lp.bottomMargin = bottomMargin;
//
//        viewPager.setLayoutParams(lp);
//        viewPager.setTabletLayout(tabletLayout);
//        checkUi_fadeView();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateLayout();
    }

    @Override
    public void onResume() {
        super.onResume();
        blur3_updateColors();
        checkUi_tabsPosition();
        checkUi_fadeView();
        checkContactsTabBadge();
        checkUnreadCount(true);

        showAccountChangeHint();
        updateProxyButton(false, false);
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment instanceof TabFragmentDelegate) {
            ((TabFragmentDelegate) fragment).onParentBecomeFullyVisible();
        }
    }

    private void checkContactsTabBadge() {
        if (tabsView != null && tabs[INDEX_CONTACTS] != null) {
            final boolean hasPermission = Build.VERSION.SDK_INT >= 23 && ContactsController.hasContactsPermission();
            if (hasPermission) {
                MessagesController.getGlobalNotificationsSettings().edit().putBoolean("askAboutContacts2", true).apply();
            }
            if (Build.VERSION.SDK_INT >= 23 && UserConfig.getInstance(currentAccount).syncContacts && !hasPermission && MessagesController.getGlobalNotificationsSettings().getBoolean("askAboutContacts2", true)) {
                tabs[INDEX_CONTACTS].setCounter("!", true, true);
            } else {
                tabs[INDEX_CONTACTS].setCounter(null, true, true);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (accountSwitchHint != null) {
            accountSwitchHint.hide();
        }
    }

    @Override
    public View createView(Context context) {
        if (contentView != null) {
            Bulletin.removeDelegate(contentView);
        }
        super.createView(context);
        tabletLayout = false;

        tabsView = new MainTabsLayout(context, resourceProvider);
        tabsView.setClipChildren(false);
        MainTabsUiHelper.applyTabsLayoutStyle(tabsView);

        tabs = new GlassTabView[6];
        tabs[INDEX_CHATS] = GlassTabView.createMainNavigationTab(context, resourceProvider, GlassTabView.TabAnimation.CHATS, R.string.MainTabsChats);
        tabs[INDEX_CHATS].setOnLongClickListener(v -> {
            final int position = indexToPosition(INDEX_CHATS);
            if (viewPager.getCurrentPosition() != position) {
                selectTab(position, true);
                viewPager.scrollToPosition(position);
            }
            if (dialogsActivity != null && !dialogsActivity.hasRightFragment()) {
                final ArrayList<MessagesController.DialogFilter> filters = getMessagesController().getDialogFilters();
                final boolean hasArchivedChats = ChatUtils.getInstance(currentAccount).hasArchivedChats();
                if ((filters != null && filters.size() > 1) || hasArchivedChats) {
                    return showFiltersMenu(v, filters, hasArchivedChats);
                }
            }
            return false;
        });
        tabs[INDEX_CONTACTS] = GlassTabView.createMainNavigationTab(context, resourceProvider, GlassTabView.TabAnimation.CONTACTS, R.string.MainTabsContacts);
        tabs[INDEX_CONTACTS].setOnLongClickListener(v -> {
            ItemOptions.makeOptions(this, v)
                .add(R.drawable.msg_contact_add, getString(R.string.NewContact), () -> {
                    new NewContactBottomSheet(this, getContext()).show();
                })
                .add(R.drawable.msg_calls, getString(R.string.VoipChatRecentCalls), () -> {
                    Bundle args = new Bundle();
                    args.putBoolean("needFinishFragment", false);
                    presentFragment(new CallLogActivity(args));
                })
                .add(R.drawable.msg_archive_hide, getString(R.string.HideContactsTab), () -> {
                    getUserConfig().setShowContactsTab(null, false);
                })
                .setGravity(Gravity.CENTER_HORIZONTAL)
                .translate(0, -dp(4))
                .setScrimViewBackground(MainTabsUiHelper.createMainTabsScrimBackground(resourceProvider, false))
                .setDismissOnMoveOutside(true)
                .show();
            return true;
        });
        tabs[INDEX_SETTINGS] = GlassTabView.createMainNavigationTab(context, resourceProvider, GlassTabView.TabAnimation.SETTINGS, R.string.Settings);
        tabs[INDEX_SETTINGS].setOnLongClickListener(v -> {
            openSettingsTabOptions(v);
            return true;
        });
        tabs[INDEX_CALLS] = GlassTabView.createMainNavigationTab(context, resourceProvider, GlassTabView.TabAnimation.CALLS, R.string.MainTabsCalls);
        tabs[INDEX_PROFILE] = GlassTabView.createMainNavigationAvatar(context, resourceProvider, currentAccount, R.string.MainTabsProfile);
        tabs[INDEX_CALLS].setOnLongClickListener(this::openCallsSelector);
        tabs[INDEX_PROFILE].setOnLongClickListener(this::openAccountSelector);
        tabs[INDEX_FEED] = GlassTabView.createMainNavigationTab(context, resourceProvider, GlassTabView.TabAnimation.FEED, R.string.Feed);
        tabs[INDEX_FEED].setOnLongClickListener(v -> {
            ItemOptions.makeOptions(this, v)
                .add(R.drawable.msg_archive_hide, getString(R.string.HideFeedTab), () -> {
                    ExteraConfig.setShowFeedTab(false);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedTabVisibleToggled);
                })
                .add(R.drawable.msg_markread, getString(R.string.FeedMarkAllRead), () -> {
                    final FragmentState state = fragmentsArr.get(getPositionContacts());
                    if (state != null && state.fragment instanceof FeedActivity) {
                        ((FeedActivity) state.fragment).markAllRead();
                    } else {
                        FeedController.getInstance(currentAccount).markAllRead();
                    }
                    checkUnreadCount(true);
                })
                .add(R.drawable.msg_settings, getString(R.string.FeedSettings), () -> {
                    presentFragment(new FeedChannelsActivity());
                })
                .setGravity(Gravity.CENTER_HORIZONTAL)
                .translate(0, -dp(4))
                .setScrimViewBackground(MainTabsUiHelper.createMainTabsScrimBackground(resourceProvider, false))
                .setDismissOnMoveOutside(true)
                .show();
            return true;
        });

        tabsView.addTabToIgnoreClick(tabs[INDEX_CHATS]);
        tabsView.addTabToIgnoreClick(tabs[INDEX_CONTACTS]);
        tabsView.addTabToIgnoreClick(tabs[INDEX_PROFILE]);
        tabsView.addTabToIgnoreClick(tabs[INDEX_CALLS]);
        tabsView.addTabToIgnoreClick(tabs[INDEX_FEED]);

        for (int index = 0; index < tabs.length; index++) {
            final int tabIndex = index;
            tabs[index].setOnClickListener(v -> {
                if (viewPager.isManualScrolling() || viewPager.isTouch()) {
                    return;
                }

                if (tabIndex == INDEX_FEED && isFeedTabEnabled() && AndroidUtilities.isTablet()) {
                    FeedActivity.presentFeed(this);
                    return;
                }

                final int position = indexToPosition(tabIndex);
                if (viewPager.getCurrentPosition() == position) {
                    final BaseFragment fragment = getCurrentVisibleFragment();
                    if (fragment instanceof MainTabsActivity.TabFragmentDelegate) {
                        ((MainTabsActivity.TabFragmentDelegate) fragment).onParentScrollToTop();
                    }
                    return;
                }

                selectTab(position, true);
                viewPager.scrollToPosition(position);
            });
        }

        final int[] tabsOrder = {INDEX_CHATS, INDEX_FEED, INDEX_CONTACTS, INDEX_SETTINGS, INDEX_CALLS, INDEX_PROFILE};
        for (int index : tabsOrder) {
            tabsView.addView(tabs[index]);
            tabsView.setViewVisible(tabs[index], true, false);
        }
        checkUi_contactsOrFeedTabVisible(false);
        checkUi_callTabVisible(getUserConfig().showCallsTab, false);

        selectTab(viewPager.getCurrentPosition(), false);

        iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));

        if (viewPositionWatcher != null) {
            viewPositionWatcher.shutdown();
        }
        viewPositionWatcher = new ViewPositionWatcher(contentView);

        final BlurredBackgroundSource glassSource = iBlur3SourceTabGlass != null ? iBlur3SourceTabGlass : iBlur3SourceColor;
        BlurredBackgroundDrawableViewFactory iBlur3FactoryGlass = new BlurredBackgroundDrawableViewFactory(glassSource);
        iBlur3FactoryGlass.setSourceRootView(viewPositionWatcher, contentView);
        iBlur3FactoryGlass.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));

        tabsViewBackground = iBlur3FactoryGlass.create(tabsView, BlurredBackgroundProviderImpl.mainTabs(resourceProvider));
        tabsViewBackground.setRadius(MainTabsUiHelper.getBackgroundRadius());
        tabsViewBackground.setPadding(MainTabsUiHelper.getBackgroundInset());
        tabsView.setBackground(tabsViewBackground);

        BlurredBackgroundDrawableViewFactory iBlur3FactoryFade = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
        iBlur3FactoryFade.setSourceRootView(viewPositionWatcher, contentView);

        fadeView = new View(context);
        BlurredBackgroundWithFadeDrawable fadeDrawable = new BlurredBackgroundWithFadeDrawable(iBlur3FactoryFade.create(fadeView, null));
        fadeDrawable.setFadeHeight(dp(60), true);
        fadeView.setBackground(fadeDrawable);

        contentView.addView(fadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));

        tabsViewWrapper = new FrameLayout(context);
        tabsViewWrapper.setOnClickListener(v -> {});
        tabsViewWrapper.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, MainTabsUiHelper.getTabsViewHeightDp(), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        tabsViewWrapper.setClipToPadding(false);
        contentView.addView(tabsViewWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        updateLayoutWrapper = new UpdateLayoutWrapper(context);
        contentView.addView(updateLayoutWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        updateLayout = ApplicationLoader.applicationLoaderInstance.takeUpdateLayout(getParentActivity(), updateLayoutWrapper);
        if (updateLayout != null) {
            updateLayout.updateAppUpdateViews(currentAccount, false);
        }

        updateLayout();
        checkUnreadCount(false);
        return contentView;
    }

    private void checkUnreadCount(boolean animated) {
        if (tabsView == null) {
            return;
        }

        final int unreadCount = MessagesStorage.getInstance(currentAccount).getMainUnreadCount();
        if (unreadCount > 0) {
            final String unreadCountFmt = LocaleController.formatNumber(unreadCount, ',');
            tabs[INDEX_CHATS].setCounter(unreadCountFmt, false, animated);
        } else {
            tabs[INDEX_CHATS].setCounter(null, false, animated);
        }

        if (tabs[INDEX_FEED] != null && isFeedTabEnabled()) {
            final int feedUnreadCount = FeedController.getInstance(currentAccount).getUnreadCount();
            tabs[INDEX_FEED].setCounter(feedUnreadCount > 0 ? LocaleController.formatNumber(feedUnreadCount, ',') : null, false, animated);
        }
    }

    public boolean openCallsSelector(View anchor) {
        if (getContext() == null || getParentActivity() == null) return false;
        final ItemOptions o = ItemOptions.makeOptions(this, anchor);
        o.add(R.drawable.menu_call_create, getString(R.string.GroupCallCreate2), () -> CallLogActivity.openCreateCall(this));
        if (getUserConfig().showCallsTab) {
            o.add(R.drawable.msg_archive_hide, getString(R.string.HideCallTab), () -> {
                getUserConfig().setShowCallsTab(false);
                checkUi_callTabVisible(false, true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.callTabsVisibleToggled);
            });
        } else {
            o.add(R.drawable.menu_add_tab_24, getString(R.string.GroupCallShowInMainTabs), () -> {
                getUserConfig().setShowCallsTab(true);
                checkUi_callTabVisible(true, true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.callTabsVisibleToggled);
            });
        }
        o.translate(0, -dp(4));
        o.setScrimViewBackground(MainTabsUiHelper.createMainTabsScrimBackground(resourceProvider, false));
        o.setDismissOnMoveOutside(true);
        o.show();
        return true;
    }

    public boolean openAccountSelector(View button) {
        return openAccountSelectorInternal(button, null, false);
    }

    private boolean openAccountSelector(View button, View touchRelayView) {
        return openAccountSelectorInternal(button, touchRelayView, true);
    }

    private boolean openAccountSelectorInternal(View button, View touchRelayView, boolean fromBottom) {
        final boolean shown = AccountsUiHelper.menu(this, button)
            .fromBottom(fromBottom)
            .touchRelay(touchRelayView)
            .extraItems(!BuildVars.DEBUG_PRIVATE_VERSION ? null : o -> {
                o.add(R.drawable.menu_download_round, "Dump Canvas", () -> AndroidUtilities.runOnUIThread(this::dumpCanvas, 1000));
            })
            .show();
        if (shown) {
            HintsController.Hint.AccountSwitchHint.doNotShowAgain();
        }
        return shown;
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean showFiltersMenu(View anchor, ArrayList<MessagesController.DialogFilter> filters, boolean hasArchivedChats) {
        final ItemOptions o = ItemOptions.makeOptions(this, anchor);
        if (filters != null && !filters.isEmpty()) {
            for (int i = 0; i < filters.size(); i++) {
                final MessagesController.DialogFilter filter = filters.get(i);
                if (filter.isDefault() && ExteraConfig.getHideAllChats()) {
                    continue;
                }
                CharSequence title = Emoji.replaceEmoji(filter.isDefault() ? getString(R.string.FilterAllChats) : filter.name, Theme.chat_msgTextPaint.getFontMetricsInt(), false);
                if (filter.entities != null && !filter.entities.isEmpty()) {
                    title = MessageObject.replaceAnimatedEmoji(title, filter.entities, Theme.chat_msgTextPaint.getFontMetricsInt());
                }
                final ActionBarMenuSubItem item = o.add();
                item.setTextAndIcon(title, getIcon(filter));
                NotificationCenter.listenEmojiLoading(item.textView);
                item.imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                item.imageView.setLayoutParams(LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
                final int filterIndex = i;
                item.setOnClickListener(v -> {
                    o.dismiss();
                    dialogsActivity.switchToFilter(filterIndex);
                });
            }
            o.addGap();
        }
        o.add(R.drawable.msg_saved, getString(R.string.SavedMessages), () -> {
            o.dismiss();
            Bundle args = new Bundle();
            args.putLong("user_id", getUserConfig().getClientUserId());
            if (getMessagesController().checkCanOpenChat(args, this)) {
                presentFragment(new ChatActivity(args));
            }
        });
        o.addIf(hasArchivedChats, R.drawable.msg_archive, getString(R.string.ArchivedChats), () -> {
            o.dismiss();
            Bundle args = new Bundle();
            args.putInt("folderId", 1);
            presentFragment(new DialogsActivity(args));
        });
        o.setGravity(Gravity.LEFT)
            .translate(0, -dp(4))
            .setScrimViewBackground(MainTabsUiHelper.createMainTabsScrimBackground(resourceProvider, false))
            .setDismissOnMoveOutside(true)
            .show();
        return true;
    }

    private static int getIcon(MessagesController.DialogFilter filter) {
        final int flags = filter.flags;
        if ((flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == (MessagesController.DIALOG_FILTER_FLAG_CONTACTS | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS)) {
            return R.drawable.msg_openprofile;
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0 && (flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) {
            return R.drawable.msg_markunread;
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_CHANNELS) {
            return R.drawable.msg_channel;
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_GROUPS) {
            return R.drawable.msg_groups;
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_CONTACTS) {
            return R.drawable.msg_contacts;
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_BOTS) {
            return R.drawable.msg_bots;
        }
        return R.drawable.msg_folders;
    }

    private ProxyDrawable proxyDrawable;
    private ActionBarMenuSubItem proxyMenuSubItem;
    private int currentConnectionState;

    @SuppressLint("ClickableViewAccessibility")
    private void openSettingsTabOptions(View anchor) {
        final ItemOptions o = ItemOptions.makeOptions(this, anchor, true);
        final MainMenuHelper.MenuContext menuContext = MainMenuHelper.createMenuContext(currentAccount, this, () -> {
            Bundle args = new Bundle();
            args.putInt("folderId", 1);
            presentFragment(new DialogsActivity(args));
        }, null);

        final boolean isDark = resourceProvider != null ? resourceProvider.isDark() : Theme.isCurrentThemeDark();
        o.add(isDark ? R.drawable.menu_day_mode_24 : R.drawable.menu_night_mode_24, getString(isDark ? R.string.SwitchThemeToDay : R.string.SwitchThemeToNight), () -> {
            if (DialogsActivity.switchingTheme) {
                return;
            }
            DialogsActivity.switchingTheme = true;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
            String dayThemeName = preferences.getString("lastDayTheme", "Blue");
            if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                dayThemeName = "Blue";
            }
            String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
            if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                nightThemeName = "Dark Blue";
            }
            Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
            if (dayThemeName.equals(nightThemeName)) {
                if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                    dayThemeName = "Blue";
                } else {
                    nightThemeName = "Dark Blue";
                }
            }

            final boolean toDark = dayThemeName.equals(themeInfo.getKey());
            if (toDark) {
                themeInfo = Theme.getTheme(nightThemeName);
            } else {
                themeInfo = Theme.getTheme(dayThemeName);
            }
            switchTheme(anchor, themeInfo, toDark);
            Theme.turnOffAutoNight(BulletinFactory.of(this), () -> {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT));
            });
        });
        o.addGap();
        MainMenuHelper.addConfiguredItemOptions(o, menuContext, id ->
            id == MainMenuItem.SETTINGS.getId() ||
            id == MainMenuItem.PROFILE.getId() ||
            id == MainMenuItem.ARCHIVE.getId() ||
            id == MainMenuItem.SAVED.getId() ||
            ExteraConfig.getShowFeedTab() && id == MainMenuItem.FEED.getId()
        );
        if (ApplicationLoader.applicationLoaderInstance != null) {
            ApplicationLoader.applicationLoaderInstance.addItemOptions(o);
        }
        if (!SharedConfig.proxyList.isEmpty()) {
            o.addGap();
            if (proxyDrawable == null) {
                proxyDrawable = new ProxyDrawable(getContext());
            }
            proxyMenuSubItem = new ActionBarMenuSubItem(getContext(), false, false, resourceProvider);
            proxyMenuSubItem.setTextAndIcon(getString(R.string.MenuProxyTitle), 0, proxyDrawable);
            proxyMenuSubItem.setOnClickListener(v -> {
                o.dismiss();
                presentFragment(new ProxyListActivity());
            });
            updateProxyButton(false, false);
            o.addView(proxyMenuSubItem);
        }
        o.setGravity(Gravity.CENTER_HORIZONTAL)
            .translate(0, -dp(4))
            .setScrimViewBackground(MainTabsUiHelper.createMainTabsScrimBackground(resourceProvider, false))
            .setDismissOnMoveOutside(true)
            .setSwipebackGravity(false, true)
            .setSwipebackCenterHorizontal(true)
            .show();
    }

    private void switchTheme(View view, Theme.ThemeInfo themeInfo, boolean toDark) {
        if (view == null) return;
        int[] pos = new int[2];
        view.getLocationInWindow(pos);
        pos[0] += view.getMeasuredWidth() / 2;
        pos[1] += view.getMeasuredHeight() / 2;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, null, null, null, true);
    }

    private void updateProxyButton(boolean animated, boolean force) {
        if (proxyDrawable == null || proxyMenuSubItem == null) {
            return;
        }
        final boolean proxyEnabled = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).getBoolean("proxy_enabled", false);
        final boolean connected = currentConnectionState == ConnectionsManager.ConnectionStateConnected || currentConnectionState == ConnectionsManager.ConnectionStateUpdating;
        proxyDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.SRC_IN));
        proxyMenuSubItem.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem));
        if (proxyEnabled) {
            proxyMenuSubItem.setItemHeight(56);
            proxyMenuSubItem.setSubtext(getString(connected ? R.string.MenuProxyConnected : R.string.MenuProxyConnecting));
        } else {
            proxyMenuSubItem.setItemHeight(48);
            proxyMenuSubItem.setSubtext(null);
        }
        proxyDrawable.setConnected(proxyEnabled, connected, animated);
    }

    @Override
    protected void onViewPagerScrollEnd() {
        if (tabsView != null) {
            selectTab(viewPager.getCurrentPosition(), true);
            setGestureSelectedOverride(0, false);
        }
        blur3_invalidateBlur();

        if (viewPager != null) {
            final int currentPosition = viewPager.getCurrentPosition();
            if (currentPosition != getPositionCallsOrSettings() && dropCallsFragmentAfterPageScroll) {
                dropFragmentAtPosition(getPositionCallsOrSettings());
                dropCallsFragmentAfterPageScroll = false;
            }
            if (currentPosition != getPositionProfile()) {
                dropFragmentAtPosition(getPositionProfile());
            }
        }

        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment instanceof TabFragmentDelegate) {
            ((TabFragmentDelegate) fragment).updateMainTabsVisibility();
        }
    }

    @Override
    protected void onViewPagerTabAnimationUpdate(boolean manual) {
        final boolean isDragByGesture = !manual;

        if (tabsView != null) {
            final float position = viewPager.getPositionAnimated();
            setGestureSelectedOverride(position, isDragByGesture);
            if (isDragByGesture) {
                selectTab(Math.round(position), true);
            }
        }

        checkUi_fadeView();
        blur3_invalidateBlur();
        contentView.invalidate();
    }


    @Override
    protected int getFragmentsCount() {
        return getTabsCount();
    }

    @Override
    protected int getStartPosition() {
        return getPositionChats();
    }

    // TODO(openextera): becomes an override once ViewPagerActivity declares canScrollToPage(int)
    public boolean canScrollToPage(int position) {
        return !(AndroidUtilities.isTablet() && isFeedTabEnabled() && position == getPositionContacts());
    }

    private DialogsActivity dialogsActivity;

    @Override
    public boolean onBackPressed(boolean invoked) {
        final boolean result = super.onBackPressed(invoked);
        if (result) {
            final int startPosition = getStartPosition();
            if (viewPager.getCurrentPosition() != startPosition) {
                if (invoked) {
                    viewPager.scrollToPosition(startPosition);
                }
                return false;
            }
        }
        return result;
    }

    private boolean isDrawerAccountPreview() {
        return arguments != null && arguments.getBoolean("drawer_account_preview", false);
    }

    private Bundle createDialogsArguments(Bundle bundle) {
        final Bundle args = bundle != null ? new Bundle(bundle) : new Bundle();
        args.putBoolean("hasMainTabs", true);
        return args;
    }

    private <T extends BaseFragment> T prepareTabFragment(T fragment) {
        fragment.setCurrentAccount(currentAccount);
        fragment.setInPreviewMode(isInPreviewMode());
        if (fragment instanceof TabFragmentDelegate) {
            ((TabFragmentDelegate) fragment).setParentTabsGlassInvalidationCallback(this::invalidateTabsGlass);
        }
        return fragment;
    }

    private DialogsActivity createDialogsActivity(Bundle bundle) {
        final DialogsActivity fragment = prepareTabFragment(new DialogsActivity(createDialogsArguments(bundle)));
        fragment.setMainTabsActivityController(new MainTabsActivityControllerImpl(getPositionChats()));
        return fragment;
    }

    public DialogsActivity prepareDialogsActivity(Bundle bundle) {
        dialogsActivity = createDialogsActivity(bundle);
        putFragmentAtPosition(getPositionChats(), dialogsActivity);
        return dialogsActivity;
    }

    @Override
    protected BaseFragment createBaseFragmentAt(int position) {
        if (position == getPositionContacts() && isFeedTabEnabled()) {
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", isBottomTabsEnabled());
            final FeedActivity fragment = prepareTabFragment(new FeedActivity(args));
            fragment.setMainTabsActivityController(new MainTabsActivityControllerImpl(position));
            return fragment;
        } else if (position == getPositionContacts() && getUserConfig().showContactsTab) {
            Bundle args = new Bundle();
            args.putBoolean("needPhonebook", true);
            args.putBoolean("needFinishFragment", false);
            args.putBoolean("hasMainTabs", isBottomTabsEnabled());
            final ContactsActivity fragment = prepareTabFragment(new ContactsActivity(args));
            fragment.setMainTabsActivityController(new MainTabsActivityControllerImpl(position));
            return fragment;
        } else if (position == getPositionCallsOrSettings()) {
            if (getUserConfig().showCallsTab) {
                Bundle args = new Bundle();
                args.putBoolean("needFinishFragment", false);
                args.putBoolean("hasMainTabs", isBottomTabsEnabled());
                final CallLogActivity fragment = prepareTabFragment(new CallLogActivity(args));
                fragment.setMainTabsActivityController(new MainTabsActivityControllerImpl(position));
                return fragment;
            }
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", isBottomTabsEnabled());
            final SettingsActivity fragment = prepareTabFragment(new SettingsActivity(args));
            fragment.setMainTabsActivityController(new MainTabsActivityControllerImpl(position));
            return fragment;
        } else if (position == getPositionChats()) {
            dialogsActivity = createDialogsActivity(arguments);
            return dialogsActivity;
        } else if (position == getPositionProfile()) {
            Bundle args = new Bundle();
            args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
            args.putBoolean("my_profile", true);
            // args.putBoolean("expandPhoto", true);
            args.putBoolean("hasMainTabs", isBottomTabsEnabled());
            final ProfileActivity fragment = prepareTabFragment(new ProfileActivity(args));
            fragment.setMainTabsActivityController(new MainTabsActivityControllerImpl(position));
            return fragment;
        }
        return null;
    }

    public DialogsActivity getDialogsActivity() {
        return dialogsActivity;
    }

    /* */

    public GlassTabView[] tabs;

    public void selectTab(int position, boolean animated) {
        for (int a = 0; a < tabs.length; a++) {
            GlassTabView tab = tabs[a];
            tab.setSelected(indexToPosition(a) == position, animated);
        }
    }

    public void setGestureSelectedOverride(float animatedPosition, boolean allow) {
        for (int index = 0; index < tabs.length; index++) {
            final int position = indexToPosition(index);
            final float visibility = Math.max(0, 1f - Math.abs(position - animatedPosition));
            tabs[index].setGestureSelectedOverride(visibility, allow);
        }
        tabsView.invalidate();
    }

    @Override
    public void setInPreviewMode(boolean value) {
        super.setInPreviewMode(value);
        for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
            final FragmentState state = fragmentsArr.valueAt(a);
            if (state != null) {
                state.fragment.setInPreviewMode(value);
            }
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onTransitionAnimationStart(isOpen, backward);
        }
    }

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onTransitionAnimationProgress(isOpen, progress);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onTransitionAnimationEnd(isOpen, backward);
        }
    }

    @Override
    public void onPreviewOpenAnimationEnd() {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onPreviewOpenAnimationEnd();
        }
    }


    /* * */

    public interface TabFragmentDelegate {
        default boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
            return false;
        }

        default void onParentScrollToTop() {

        }

        default BlurredBackgroundSourceRenderNode getGlassSource() {
            return null;
        }

        default void onParentBecomeFullyVisible() {

        }

        default void setParentTabsGlassInvalidationCallback(Runnable callback) {

        }

        default void updateMainTabsVisibility() {

        }
    }

    @Override
    protected boolean canScrollForward(MotionEvent ev) {
        return canScrollInternal(ev, true);
    }

    @Override
    protected boolean canScrollBackward(MotionEvent ev) {
        return canScrollInternal(ev, false);
    }

    private boolean canScrollInternal(MotionEvent ev, boolean forward) {
        if (!isBottomTabsEnabled()) {
            return false;
        }
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment instanceof TabFragmentDelegate) {
            final TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
            return delegate.canParentTabsSlide(ev, forward);

        }

        return false;
    }

    private boolean isBottomTabsEnabled() {
        return BottomNavigationBar.visible();
    }


    /* * */

    private int navigationBarHeight;
    private int insetLeft;
    private int insetRight;

    @NonNull
    @Override
    protected WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        if (updateLayoutWrapper == null || fadeView == null || viewPager == null || tabsViewWrapper == null) {
            return super.onApplyWindowInsets(v, insets);
        }
        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);

        insetLeft = systemInsets.left;
        insetRight = systemInsets.right;

        navigationBarHeight = systemInsets.bottom;
        final boolean isUpdateLayoutVisible = updateLayoutWrapper.isUpdateLayoutVisible();
        final int updateLayoutHeight = isUpdateLayoutVisible ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
        updateLayoutWrapper.setPadding(0, 0, 0, navigationBarHeight);

        ViewGroup.MarginLayoutParams lp;
        {
            final int height = navigationBarHeight + updateLayoutHeight + (isBottomTabsEnabled() ? dp(MainTabsUiHelper.getTabsViewHeightDp()) : 0);
            lp = (ViewGroup.MarginLayoutParams) fadeView.getLayoutParams();
            if (lp.height != height) {
                lp.height = height;
                fadeView.setLayoutParams(lp);
            }
        }
        {
            int bottomMargin = isUpdateLayoutVisible ? (navigationBarHeight + updateLayoutHeight) : 0;
            if (tabletLayout) {
                bottomMargin = Math.max(bottomMargin, navigationBarHeight + dp(MainTabsUiHelper.getTabsViewHeightDp()));
            }
            lp = (ViewGroup.MarginLayoutParams) viewPager.getLayoutParams();
            if (lp.bottomMargin != bottomMargin || lp.leftMargin != systemInsets.left || lp.rightMargin != systemInsets.right) {
                lp.leftMargin = systemInsets.left;
                lp.rightMargin = systemInsets.right;
                lp.bottomMargin = bottomMargin;
                viewPager.setLayoutParams(lp);
            }
        }

        MainTabsUiHelper.applyTabsBottomInset(tabsView, tabsViewWrapper, systemInsets.left, systemInsets.right, navigationBarHeight);

        final WindowInsetsCompat consumed = isUpdateLayoutVisible ?
            insets.inset(0, 0, 0, navigationBarHeight) : insets;

        checkUi_tabsPosition();
        checkUi_fadeView();

        return super.onApplyWindowInsets(v, consumed);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.notificationsCountUpdated || id == NotificationCenter.updateInterfaces || id == NotificationCenter.dialogsNeedReload) {
            checkUnreadCount(fragmentView != null && fragmentView.isAttachedToWindow());
        } else if (id == NotificationCenter.appUpdateLoading) {
            if (updateLayout != null) {
                updateLayout.updateFileProgress(null);
                updateLayout.updateAppUpdateViews(currentAccount, true);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            String path = (String) args[0];
            if (SharedConfig.isAppUpdateAvailable()) {
                String name = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
                if (name.equals(path) && updateLayout != null) {
                    updateLayout.updateAppUpdateViews(currentAccount, true);
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String path = (String) args[0];
            if (SharedConfig.isAppUpdateAvailable()) {
                String name = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
                if (name.equals(path) && updateLayout != null) {
                    updateLayout.updateAppUpdateViews(currentAccount, true);
                }
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            if (updateLayout != null) {
                updateLayout.updateFileProgress(args);
            }
        } else if (id == NotificationCenter.appUpdateAvailable) {
            if (updateLayout != null && LaunchActivity.instance != null) {
                updateLayout.updateAppUpdateViews(currentAccount, LaunchActivity.instance.getMainFragmentsStackSize() == 1);
            }
        } else if (id == NotificationCenter.needSetDayNightTheme) {
            clearAllHiddenFragments();
        } else if (id == NotificationCenter.callTabsVisibleToggled) {
            final boolean callTabsVisible = getUserConfig().showCallsTab;
            checkUi_callTabVisible(callTabsVisible, true);
            if (viewPager != null && viewPager.getCurrentPosition() == getPositionCallsOrSettings()) {
                viewPager.scrollToPosition(getPositionChats());
                selectTab(getPositionChats(), true);
                dropCallsFragmentAfterPageScroll = true;
            } else {
                dropFragmentAtPosition(getPositionCallsOrSettings());
            }
        } else if (id == NotificationCenter.contactsTabVisibleToggled) {
            final boolean contactsTabVisible = getUserConfig().showContactsTab;
            checkUi_contactsOrFeedTabVisible(true);
            if (viewPager != null) {
                int position = viewPager.getCurrentPosition();
                if (!isFeedTabEnabled()) {
                    position = shiftPositionOnContactsOrFeedToggle(position, contactsTabVisible);
                }
                rebuildPages(position);
            }
        } else if (id == NotificationCenter.feedTabVisibleToggled) {
            checkUi_contactsOrFeedTabVisible(true);
            checkUnreadCount(false);
            if (viewPager != null) {
                int position = viewPager.getCurrentPosition();
                if (!getUserConfig().showContactsTab) {
                    position = shiftPositionOnContactsOrFeedToggle(position, isFeedTabEnabled());
                }
                rebuildPages(position);
            }
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            if (tabs != null && tabs[INDEX_PROFILE] != null) {
                tabs[INDEX_PROFILE].updateUserAvatar(currentAccount);
            }
        } else if (id == NotificationCenter.contactsPermissionBadgeCheck) {
            checkContactsTabBadge();
        } else if (id == NotificationCenter.proxySettingsChanged) {
            updateProxyButton(false, false);
        } else if (id == NotificationCenter.didUpdateConnectionState) {
            final int state = AccountInstance.getInstance(account).getConnectionsManager().getConnectionState();
            if (currentConnectionState != state) {
                currentConnectionState = state;
                updateProxyButton(true, false);
            }
        }
    }

    private static int shiftPositionOnContactsOrFeedToggle(int position, boolean tabShown) {
        if (tabShown) {
            if (position >= 1) {
                position++;
            }
        } else if (position == 1) {
            position = 0;
        } else if (position > 1) {
            position--;
        }
        return position;
    }

    private void rebuildPages(int position) {
        clearAllHiddenFragments();
        for (int a = 0; a < getTabsCount() + 1; a++) {
            dropFragmentAtPosition(a);
        }
        viewPager.currentPosition = position;
        viewPager.rebuild(false);
        selectTab(position, false);
    }

    private NotificationCenter.ObserversGroup observersGroup;

    @Override
    public boolean onFragmentCreate() {
        observersGroup = NotificationCenter.getInstance(currentAccount)
            .createObserversGroup(this)
            .add(NotificationCenter.fileLoaded)
            .add(NotificationCenter.fileLoadProgressChanged)
            .add(NotificationCenter.fileLoadFailed)
            .add(NotificationCenter.notificationsCountUpdated)
            .add(NotificationCenter.updateInterfaces)
            .add(NotificationCenter.dialogsNeedReload)
            .add(NotificationCenter.callTabsVisibleToggled)
            .add(NotificationCenter.contactsTabVisibleToggled)
            .add(NotificationCenter.feedTabVisibleToggled)
            .add(NotificationCenter.mainUserInfoChanged)
            .add(NotificationCenter.didUpdateConnectionState)
            .add(NotificationCenter.contactsPermissionBadgeCheck)
            .addGlobal(NotificationCenter.appUpdateAvailable)
            .addGlobal(NotificationCenter.appUpdateLoading)
            .addGlobal(NotificationCenter.proxySettingsChanged)
            .addGlobal(NotificationCenter.needSetDayNightTheme);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        Bulletin.removeDelegate(this);
        Bulletin.removeDelegate(contentView);

        if (viewPositionWatcher != null) {
            viewPositionWatcher.shutdown();
            viewPositionWatcher = null;
        }

        if (observersGroup != null) {
            observersGroup.removeAllObservers();
            observersGroup = null;
        }

        super.onFragmentDestroy();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_TABS_VISIBLE) {
            checkUi_tabsPosition();
            checkUi_fadeView();
            final Bulletin bulletin = Bulletin.getVisibleBulletin();
            if (bulletin != null) {
                bulletin.updatePosition();
            }
        }
    }

    private void checkUi_fadeView() {
        if (viewPager == null || fadeView == null) {
            return;
        }
        if (!isBottomTabsEnabled()) {
            fadeView.setAlpha(0f);
            fadeView.setVisibility(View.GONE);
            return;
        }

        final float animatedPosition = viewPager.getPositionAnimated();
        final float isProfile = 1f - MathUtils.clamp(Math.abs(getPositionProfile() - animatedPosition), 0, 1);
        final float hide = 1f - AndroidUtilities.getNavigationBarThirdButtonsFactor(0, 1f, navigationBarHeight);
        float alpha = (1f - isProfile * hide) * animatorTabsVisible.getFloatValue();
        if (tabletLayout) {
            alpha = 0.0f;
        }

        fadeView.setAlpha(alpha);
        fadeView.setTranslationY(isProfile * dp(48));
        fadeView.setVisibility(alpha > 0 ? View.VISIBLE : View.GONE);
    }

    private void checkUi_tabsPosition() {
        if (tabsView == null || tabsViewWrapper == null || updateLayoutWrapper == null) {
            return;
        }
        if (!isBottomTabsEnabled()) {
            tabsView.setClickable(false);
            tabsView.setEnabled(false);
            tabsView.setAlpha(0f);
            tabsView.setVisibility(View.GONE);
            return;
        }
        final boolean isUpdateLayoutVisible = updateLayoutWrapper.isUpdateLayoutVisible();
        final int updateLayoutHeight = isUpdateLayoutVisible ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
        final int normalY = -(updateLayoutHeight);
        final int hiddenY = normalY + dp(40);

        final float factor = animatorTabsVisible.getFloatValue();
        final float scale = lerp(0.85f, 1f, factor);

        tabsViewWrapper.setTranslationY(lerp(hiddenY, normalY, factor));
        tabsView.setClickable(factor > 0.5f);
        tabsView.setEnabled(factor > 0.5f);
        tabsView.setAlpha(factor);
        tabsView.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
    }

    private void checkUi_contactsOrFeedTabVisible(boolean animated) {
        if (tabsView != null) {
            final boolean feedTabEnabled = isFeedTabEnabled();
            tabsView.setViewVisible(tabs[INDEX_FEED], feedTabEnabled, animated);
            tabsView.setViewVisible(tabs[INDEX_CONTACTS], !feedTabEnabled && getUserConfig().showContactsTab, animated);
        }
    }

    private void checkUi_callTabVisible(boolean callTabsVisible, boolean animated) {
        if (tabsView != null) {
            tabsView.setViewVisible(tabs[INDEX_SETTINGS], !callTabsVisible, animated);
            tabsView.setViewVisible(tabs[INDEX_CALLS], callTabsVisible, animated);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = super.getThemeDescriptions();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::blur3_updateColors;
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_dialogBackground));

        return themeDescriptions;
    }

    /* * */

    private class MainTabsActivityControllerImpl implements MainTabsActivityController {
        private final int ownerPosition;

        private MainTabsActivityControllerImpl(int ownerPosition) {
            this.ownerPosition = ownerPosition;
        }

        @Override
        public void setTabsVisible(boolean visible) {
            if (viewPager != null && viewPager.getTargetPosition() != ownerPosition) {
                return;
            }
            if (tabsView == null) {
                animatorTabsVisible.changeValueSilently(visible);
                animatorTabsVisible.changeValueSilently(visible ? 1f : 0f);
            } else {
                animatorTabsVisible.setValue(visible, true);
            }
        }

        @Override
        public boolean openAccountSelector(View button, View touchRelayView) {
            if (button == null) {
                return false;
            }
            return MainTabsActivity.this.openAccountSelector(button, touchRelayView);
        }
    }


    /* Slide */

    @Override
    public boolean canBeginSlide() {
        final BaseFragment fragment = getCurrentVisibleFragment();
        return fragment != null && fragment.canBeginSlide();
    }

    @Override
    public void onBeginSlide() {
        super.onBeginSlide();
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onBeginSlide();
        }
    }

    @Override
    public void onSlideProgress(boolean isOpen, float progress) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onSlideProgress(isOpen, progress);
        }
    }

    @Override
    public Animator getCustomSlideTransition(boolean topFragment, boolean backAnimation, float distanceToMove) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        return fragment != null ? fragment.getCustomSlideTransition(topFragment, backAnimation, distanceToMove) : null;
    }

    @Override
    public void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.prepareFragmentToSlide(topFragment, beginSlide);
        }
    }


    private HintView2 accountSwitchHint;
    private boolean accountSwitchHintShown;

    private void showAccountChangeHint() {
        if (accountSwitchHintShown || isDrawerAccountPreview() || !isBottomTabsEnabled()) return;

        if (accountSwitchHint == null && HintsController.Hint.AccountSwitchHint.show()) {
            AndroidUtilities.runOnUIThread(() -> {
                if (getContext() == null || tabs == null) return;

                final View v = tabs[INDEX_PROFILE];
                final float translate = (contentView.getWidth() - ((tabsView.getX() + v.getX()) + v.getWidth()) + v.getWidth() / 2f) / AndroidUtilities.density;

                accountSwitchHint = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM);
                accountSwitchHint.setTranslationY(-navigationBarHeight + dp(4));
                accountSwitchHint.setPadding(dp(7.33f), 0, dp(7.33f), 0);
                accountSwitchHint.setMultilineText(false);
                accountSwitchHint.setCloseButton(true);
                accountSwitchHint.setText(getString(R.string.SwitchAccountHint));
                accountSwitchHint.setJoint(1, -translate + 7.33f);
                contentView.addView(accountSwitchHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, MainTabsUiHelper.getTabsViewHeightDp()));
                accountSwitchHint.setOnHiddenListener(() -> AndroidUtilities.removeFromParent(accountSwitchHint));
                accountSwitchHint.setDuration(8000);
                accountSwitchHint.show();

                HintsController.Hint.AccountSwitchHint.increment();
            }, 1500);
        }

        accountSwitchHintShown = true;
    }


    /* * */

    private final @NonNull BlurredBackgroundSourceColor iBlur3SourceColor;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceTabGlass;

    private final RectF fragmentPosition = new RectF();
    private ViewPositionWatcher viewPositionWatcher;

    private void invalidateTabsGlass() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || iBlur3SourceTabGlass == null) {
            return;
        }
        iBlur3SourceTabGlass.invalidateDisplayList();
        blur3_invalidateBlur();
        if (tabsView != null) {
            tabsView.invalidate();
        }
        if (tabsViewWrapper != null) {
            tabsViewWrapper.invalidate();
        }
    }

    private void blur3_invalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || iBlur3SourceTabGlass == null || fragmentView == null) {
            return;
        }

        final int width = fragmentView.getMeasuredWidth();
        final int height = fragmentView.getMeasuredHeight();

        iBlur3SourceTabGlass.setSize(width, height);
        iBlur3SourceTabGlass.updateDisplayListIfNeeded();
    }

    private void blur3_updateFadeColors() {
        iBlur3SourceColor.setColor(getEstBackgroundColor());
        if (fadeView != null) {
            fadeView.invalidate();
        }
    }

    private void blur3_updateColors() {
        blur3_updateFadeColors();
        if (tabsViewBackground != null) {
            tabsViewBackground.updateColors();
        }
        blur3_invalidateBlur();
        if (fadeView != null) {
            fadeView.invalidate();
        }
        if (tabsView != null) {
            tabsView.invalidate();
        }
        if (tabs != null) {
            for (GlassTabView tabView : tabs) {
                tabView.updateColorsLottie();
            }
        }
    }

    @Override
    public EdgeToEdgeSupportMode getEdgeToEdgeSupportMode() {
        return EdgeToEdgeSupportMode.FULL;
    }
}
