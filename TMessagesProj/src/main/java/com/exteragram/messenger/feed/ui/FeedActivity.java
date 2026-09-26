package com.exteragram.messenger.feed.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.config.BottomNavigationBar;
import com.exteragram.messenger.feed.FeedConfig;
import com.exteragram.messenger.feed.FeedController;
import com.exteragram.messenger.utils.ui.MainTabsUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatActivityContainer;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.MainTabsActivityController;

/**
 * Hosts an embedded ChatActivity in feed mode (hashtag-search chat mode, searchType 4).
 */
public class FeedActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, MainTabsActivity.TabFragmentDelegate {

    private static final int MENU_SETTINGS = 75;
    private static final int MENU_MARK_ALL_READ = 76;

    private ChatActivityContainer chatContainer;
    private boolean embeddedChatCreated;
    private boolean hasMainTabs;
    private int lastConfigGeneration;
    private WindowInsetsCompat lastWindowInsets;
    private MainTabsActivityController mainTabsActivityController;
    private Runnable parentTabsGlassInvalidationCallback;
    private boolean resumedOnce;
    private boolean uiActiveHeld;
    private boolean uiResumedHeld;
    private boolean viewportFullyVisible;

    private final Runnable loadNewPosts = () -> {
        ChatActivity chatActivity = getChatActivity();
        if (chatActivity != null && uiResumedHeld) {
            chatActivity.loadNewerFeed(true);
        }
    };

    public FeedActivity() {
        this(null);
    }

    public FeedActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    private ChatActivity getChatActivity() {
        return chatContainer != null ? chatContainer.chatActivity : null;
    }

    public void setMainTabsActivityController(MainTabsActivityController controller) {
        mainTabsActivityController = controller;
    }

    @Override
    public void updateMainTabsVisibility() {
        if (mainTabsActivityController != null) {
            mainTabsActivityController.setTabsVisible(BottomNavigationBar.visible());
        }
    }

    public static void presentFeed(BaseFragment fragment) {
        if (!AndroidUtilities.isTablet() || LaunchActivity.instance == null || LaunchActivity.instance.getRightActionBarLayout() == null) {
            if (fragment != null) {
                fragment.presentFragment(new FeedActivity());
            }
            return;
        }
        INavigationLayout rightLayout = LaunchActivity.instance.getRightActionBarLayout();
        if (rightLayout.getLastFragment() instanceof FeedActivity) {
            return;
        }
        if (!rightLayout.getFragmentStack().isEmpty()) {
            while (rightLayout.getFragmentStack().size() - 1 > 0) {
                rightLayout.removeFragmentFromStack(rightLayout.getFragmentStack().get(0));
            }
            rightLayout.closeLastFragment(false);
        }
        rightLayout.presentFragment(new INavigationLayout.NavigationParams(new FeedActivity()).setNoAnimation(true).forceRightLayout());
    }

    @Override
    public boolean onFragmentCreate() {
        hasMainTabs = arguments != null && arguments.getBoolean("hasMainTabs", false);
        viewportFullyVisible = !hasMainTabs;
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.feedNeedReload);
        lastConfigGeneration = FeedConfig.getInstance(currentAccount).getGeneration();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(loadNewPosts);
        destroyEmbeddedChat();
        if (uiResumedHeld) {
            uiResumedHeld = false;
            FeedController.getInstance(currentAccount).setUiResumed(false);
        }
        if (uiActiveHeld) {
            uiActiveHeld = false;
            FeedController.getInstance(currentAccount).setUiActive(false);
        }
        Bulletin.removeDelegate(this);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didReceiveNewMessages);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.feedNeedReload);
        super.onFragmentDestroy();
    }

    private void destroyEmbeddedChat() {
        ChatActivity chatActivity = getChatActivity();
        if (chatActivity != null) {
            if (!hasMainTabs && embeddedChatCreated) {
                chatActivity.saveFeedScrollPosition();
            }
            chatActivity.setFeedChannelsChangedCallback(null);
            chatActivity.setGlassSourceInvalidationCallback(null);
            if (embeddedChatCreated) {
                chatActivity.onFragmentDestroy();
            }
        }
        embeddedChatCreated = false;
        chatContainer = null;
    }

    private boolean isActionModeShowed() {
        ChatActivity chatActivity = getChatActivity();
        return chatActivity != null && chatActivity.getActionBar() != null && chatActivity.getActionBar().isActionModeShowed();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (!isActionModeShowed()) {
            return super.onBackPressed(invoked);
        }
        if (invoked) {
            chatContainer.chatActivity.clearSelectionMode();
        }
        return false;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled || chatContainer == null || !FeedController.getInstance(currentAccount).isIncludedChannelPost((Long) args[0])) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(loadNewPosts);
            AndroidUtilities.runOnUIThread(loadNewPosts, 1000);
        } else if (id == NotificationCenter.feedNeedReload) {
            ChatActivity chatActivity = getChatActivity();
            if (chatActivity != null) {
                chatActivity.onFeedChannelsChanged(args.length > 0 && Boolean.TRUE.equals(args[0]));
            }
            updateFeedSubtitle();
        }
    }

    @Override
    public View createView(Context context) {
        destroyEmbeddedChat();
        lastWindowInsets = null;
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);

        FrameLayout rootView = new FrameLayout(context);
        fragmentView = rootView;
        rootView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (hasMainTabs) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                lastWindowInsets = insets;
                int tabsHeight = BottomNavigationBar.visible() ? AndroidUtilities.dp(MainTabsUiHelper.getTabsViewHeightDp()) : 0;
                return tabsHeight == 0 ? insets : addTabsBottomInset(insets, tabsHeight);
            });
            rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    if (lastWindowInsets != null) {
                        ViewCompat.dispatchApplyWindowInsets(v, lastWindowInsets);
                    } else {
                        v.requestApplyInsets();
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {

                }
            });
        }

        FrameLayout chatLayout = new FrameLayout(context);
        rootView.addView(chatLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        Bundle args = new Bundle();
        args.putInt("chatMode", ChatActivity.MODE_SEARCH);
        args.putInt("searchType", 4);
        args.putBoolean("hasMainTabs", hasMainTabs);
        chatContainer = new ChatActivityContainer(context, getParentLayout(), args) {
            boolean activityCreated = false;

            @Override
            public void initChatActivity() {
                if (activityCreated) {
                    return;
                }
                activityCreated = true;
                embeddedChatCreated = true;
                super.initChatActivity();
                applyFloatingWindowLayout();
                setupChatActionBar();
                setupChatTitle();
                View feedView = FeedActivity.this.fragmentView;
                if (lastWindowInsets != null && feedView != null) {
                    ViewCompat.dispatchApplyWindowInsets(feedView, lastWindowInsets);
                }
                invalidateParentTabsGlass();
            }
        };
        ChatActivity chatActivity = chatContainer.chatActivity;
        chatActivity.isInsideContainer = false;
        chatActivity.setFeedChannelsChangedCallback(this::updateFeedSubtitle);
        chatActivity.setGlassSourceInvalidationCallback(this::invalidateParentTabsGlass);
        updateFeedViewportActive(viewportFullyVisible);
        if (!uiResumedHeld) {
            chatContainer.onPause();
        }
        chatLayout.addView(chatContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        if (!uiActiveHeld) {
            uiActiveHeld = true;
            FeedController.getInstance(currentAccount).setUiActive(true);
        }
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getTopOffset(int tag) {
                ChatActivity chatActivity = getChatActivity();
                if (chatActivity != null) {
                    return chatActivity.getBulletinTopOffset();
                }
                return AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();
            }

            @Override
            public int getBottomOffset(int tag) {
                ChatActivity chatActivity = getChatActivity();
                return chatActivity != null ? chatActivity.getBulletinBottomOffset() : 0;
            }
        });
        return fragmentView;
    }

    private static WindowInsetsCompat addTabsBottomInset(WindowInsetsCompat insets, int bottom) {
        int systemBars = WindowInsetsCompat.Type.systemBars();
        int navigationBars = WindowInsetsCompat.Type.navigationBars();
        return new WindowInsetsCompat.Builder(insets)
            .setStableInsets(addBottomInset(insets.getInsetsIgnoringVisibility(systemBars), bottom))
            .setInsets(systemBars, addBottomInset(insets.getInsets(systemBars), bottom))
            .setInsets(navigationBars, addBottomInset(insets.getInsets(navigationBars), bottom))
            .setInsetsIgnoringVisibility(systemBars, addBottomInset(insets.getInsetsIgnoringVisibility(systemBars), bottom))
            .setInsetsIgnoringVisibility(navigationBars, addBottomInset(insets.getInsetsIgnoringVisibility(navigationBars), bottom))
            .build();
    }

    private static Insets addBottomInset(Insets insets, int bottom) {
        return Insets.of(insets.left, insets.top, insets.right, insets.bottom + bottom);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateMainTabsVisibility();
        if (chatContainer != null) {
            chatContainer.onResume();
            updateFeedViewportActive(viewportFullyVisible);
        }
        if (!uiResumedHeld) {
            uiResumedHeld = true;
            FeedController.getInstance(currentAccount).setUiResumed(true);
        }
        if (hasMainTabs && fragmentView != null && lastWindowInsets != null) {
            ViewCompat.dispatchApplyWindowInsets(fragmentView, lastWindowInsets);
        }
        reattachCurrentFeedVideoTexture();
        int generation = FeedConfig.getInstance(currentAccount).getGeneration();
        ChatActivity chatActivity = getChatActivity();
        if (generation != lastConfigGeneration) {
            lastConfigGeneration = generation;
            if (chatActivity != null) {
                chatActivity.applyFeedConfigChange();
            }
        } else if (resumedOnce && chatActivity != null) {
            chatActivity.reconcileFeedList();
            chatActivity.refreshFeedUnreadDivider();
            if (!FeedController.getInstance(currentAccount).getMessages().isEmpty()) {
                chatActivity.loadNewerFeed(true);
            }
        }
        resumedOnce = true;
        updateFeedSubtitle();
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        viewportFullyVisible = true;
        updateMainTabsVisibility();
        updateFeedViewportActive(true);
        reattachCurrentFeedVideoTexture();
    }

    @Override
    public void onBecomeFullyHidden() {
        viewportFullyVisible = false;
        updateFeedViewportActive(false);
        super.onBecomeFullyHidden();
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        if (hasMainTabs) {
            viewportFullyVisible = false;
            updateFeedViewportActive(false);
        }
        super.onTransitionAnimationStart(isOpen, backward);
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (hasMainTabs) {
            viewportFullyVisible = isOpen;
            updateFeedViewportActive(isOpen);
        }
    }

    @Override
    public void onParentBecomeFullyVisible() {
        reattachCurrentFeedVideoTexture();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (chatContainer != null) {
            updateFeedViewportActive(false);
            chatContainer.onPause();
        }
        if (uiResumedHeld) {
            uiResumedHeld = false;
            FeedController.getInstance(currentAccount).setUiResumed(false);
        }
    }

    private void updateFeedViewportActive(boolean active) {
        ChatActivity chatActivity = getChatActivity();
        if (chatActivity != null) {
            chatActivity.setFeedViewportActive(active);
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return !isActionModeShowed();
    }

    @Override
    public boolean isLightStatusBar() {
        ChatActivity chatActivity = getChatActivity();
        if (chatActivity != null) {
            return chatActivity.isLightStatusBar();
        }
        return !Theme.isCurrentThemeDark();
    }

    private void reattachCurrentFeedVideoTexture() {
        ChatActivity chatActivity = getChatActivity();
        if (chatActivity != null) {
            chatActivity.reattachCurrentFeedVideoTexture();
        }
    }

    private void setupChatActionBar() {
        ChatActivity chatActivity = getChatActivity();
        ActionBar chatActionBar = chatActivity != null ? chatActivity.getActionBar() : null;
        if (chatActionBar == null) {
            return;
        }
        ActionBarMenu menu = chatActionBar.createMenu();
        if (menu.getItem(MENU_MARK_ALL_READ) == null) {
            menu.addItem(MENU_MARK_ALL_READ, R.drawable.msg_markread, chatActivity.themeDelegate).setContentDescription(LocaleController.getString(R.string.FeedMarkAllRead));
        }
        if (menu.getItem(MENU_SETTINGS) == null) {
            menu.addItem(MENU_SETTINGS, R.drawable.msg_settings, chatActivity.themeDelegate).setContentDescription(LocaleController.getString(R.string.FeedSettings));
        }
        if (hasMainTabs) {
            applyMainTabsHeaderLayout();
        }
        ActionBar.ActionBarMenuOnItemClick chatMenuOnItemClick = chatActionBar.getActionBarMenuOnItemClick();
        chatActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && hasMainTabs && !chatActionBar.isActionModeShowed()) {
                    return;
                }
                if (id == MENU_MARK_ALL_READ) {
                    showMarkAllReadDialog();
                } else if (id == MENU_SETTINGS) {
                    presentFragment(new FeedChannelsActivity());
                } else if (chatMenuOnItemClick != null) {
                    chatMenuOnItemClick.onItemClick(id);
                }
            }

            @Override
            public boolean canOpenMenu() {
                return chatMenuOnItemClick == null || chatMenuOnItemClick.canOpenMenu();
            }
        });
    }

    private void applyFloatingWindowLayout() {
        ChatActivity chatActivity = getChatActivity();
        if (getParentLayout() == null || !getParentLayout().isLayersLayout() || chatActivity == null) {
            return;
        }
        if (chatActivity.getActionBar() != null) {
            chatActivity.getActionBar().setOccupyStatusBar(false);
        }
        if (chatActivity.avatarContainer != null) {
            chatActivity.avatarContainer.setOccupyStatusBar(false);
        }
        if (chatActivity.contentView != null) {
            chatActivity.contentView.setOccupyStatusBar(false);
        }
    }

    private void applyMainTabsHeaderLayout() {
        ChatActivity chatActivity = getChatActivity();
        ChatAvatarContainer avatarContainer = chatActivity != null ? chatActivity.avatarContainer : null;
        if (avatarContainer == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = avatarContainer.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            int leftMargin = AndroidUtilities.dp(ExteraConfig.getNewChatHeaderStyle() ? 12 : 0);
            if (marginLayoutParams.leftMargin != leftMargin) {
                marginLayoutParams.leftMargin = leftMargin;
                avatarContainer.setLayoutParams(marginLayoutParams);
            }
        }
    }

    private void showMarkAllReadDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(LocaleController.getString(R.string.FeedMarkAllRead));
        builder.setMessage(LocaleController.getString(R.string.FeedMarkAllReadConfirm));
        builder.setPositiveButton(LocaleController.getString(R.string.MarkAsRead), (dialog, which) -> {
            markAllRead();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.FeedMarkAllReadDone)).show();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    public void markAllRead() {
        ChatActivity chatActivity = getChatActivity();
        if (chatActivity != null) {
            chatActivity.markFeedAsRead();
        } else {
            FeedController.getInstance(currentAccount).markAllRead();
        }
    }

    @Override
    public void onParentScrollToTop() {
        ChatActivity chatActivity = getChatActivity();
        if (chatActivity != null) {
            chatActivity.onPageDownClicked(false);
        }
    }

    private void setupChatTitle() {
        ChatActivity chatActivity = getChatActivity();
        if (chatActivity == null || chatActivity.avatarContainer == null) {
            return;
        }
        chatActivity.avatarContainer.setTitle(LocaleController.getString(R.string.Feed));
        chatActivity.avatarContainer.setFeedAvatar();
        updateFeedSubtitle();
    }

    private void updateFeedSubtitle() {
        FeedController feedController = FeedController.getInstance(currentAccount);
        setFeedSubtitle(feedController.getIncludedChannelCount());
        feedController.loadChannels((channels, includedCount, failed, configGeneration) -> setFeedSubtitle(failed ? feedController.getIncludedChannelCount() : includedCount));
    }

    private void setFeedSubtitle(int count) {
        ChatActivity chatActivity = getChatActivity();
        ChatAvatarContainer avatarContainer = chatActivity != null ? chatActivity.avatarContainer : null;
        if (avatarContainer == null) {
            return;
        }
        avatarContainer.setSubtitle(count < 0 ? LocaleController.getString(R.string.Loading) : LocaleController.formatPluralString("Channels", count));
        View subtitleTextView = avatarContainer.getSubtitleTextView();
        if (subtitleTextView != null) {
            subtitleTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public BlurredBackgroundSourceRenderNode getGlassSource() {
        ChatActivity chatActivity = getChatActivity();
        return chatActivity != null ? chatActivity.getGlassSource() : null;
    }

    @Override
    public void setParentTabsGlassInvalidationCallback(Runnable callback) {
        parentTabsGlassInvalidationCallback = callback;
    }

    private void invalidateParentTabsGlass() {
        if (parentTabsGlassInvalidationCallback != null) {
            parentTabsGlassInvalidationCallback.run();
        }
    }
}
