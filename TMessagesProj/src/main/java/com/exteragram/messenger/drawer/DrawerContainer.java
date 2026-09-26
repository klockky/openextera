package com.exteragram.messenger.drawer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.utils.AppUtils;
import com.exteragram.messenger.utils.ui.AccountsUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ProxyListActivity;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.ui.ThemeActivity;

public class DrawerContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int COLOR_KEY_DRAWER_BACKGROUND = Theme.key_windowBackgroundWhite;
    private static final int COLOR_KEY_POPUP_ACCENT = Theme.key_windowBackgroundWhiteBlueIcon;

    private static final float PROGRESS_EPSILON = 0.001f;

    private static final FloatPropertyCompat<DrawerContainer> DRAWER_OFFSET = new FloatPropertyCompat<DrawerContainer>("drawerOffset") {
        @Override
        public float getValue(DrawerContainer container) {
            return container.getDrawerOffset();
        }

        @Override
        public void setValue(DrawerContainer container, float value) {
            container.setDrawerOffset(value);
        }
    };

    private final FrameLayout drawerPanel;
    private final FrameLayout bulletinContainer;
    private final DrawerHeaderView headerView;
    private final DrawerAccountPickerView accountPickerView;
    private final DrawerMenuView menuView;

    private final Paint scrimPaint = new Paint();
    private final Rect rect = new Rect();
    private final Path clipPath = new Path();
    private final float[] radii = new float[8];
    private float cachedTopRightRadius = -1;
    private float cachedBottomRightRadius = -1;

    private int drawerWidth;
    private float progress;
    private boolean isOpen;
    private boolean isAnimating;
    private SpringAnimation springAnimation;
    private ValueAnimator standardAnimator;

    private boolean tracking;
    private boolean startedEdgeSwipe;
    private boolean tapClosePending;
    private boolean animationInterruptedByTouch;
    private float startX;
    private float startY;
    private float startProgress;
    private VelocityTracker velocityTracker;

    private boolean predictiveBackInProgress;
    private float predictiveBackStartProgress;

    private View navigationTranslationTarget;
    private boolean notificationsRegistered;
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;

    public DrawerContainer(Context context) {
        super(context);
        setVisibility(View.GONE);
        setTag("drawer_container");
        drawerWidth = calculateDrawerWidth();

        drawerPanel = new FrameLayout(context);
        drawerPanel.setBackgroundColor(Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND));
        drawerPanel.setTranslationX(-drawerWidth);
        addView(drawerPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT));
        FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        panelParams.width = drawerWidth;
        drawerPanel.setLayoutParams(panelParams);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bulletinContainer = new FrameLayout(context);
        drawerPanel.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        headerView = new DrawerHeaderView(context);
        contentLayout.addView(headerView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(160)));

        accountPickerView = new DrawerAccountPickerView(context);
        contentLayout.addView(accountPickerView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        menuView = new DrawerMenuView(context);
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0);
        menuParams.weight = 1f;
        contentLayout.addView(menuView, menuParams);

        setupCallbacks();
        headerView.setChevronExpanded(accountPickerView.isExpanded());
    }

    private void setupCallbacks() {
        headerView.setOnChevronClick(() -> {
            accountPickerView.toggleExpand();
            headerView.setChevronExpanded(accountPickerView.isExpanded());
        });
        headerView.setOnThemeToggle(this::toggleDayNightTheme);
        headerView.setOnThemeToggleLongClick(() -> {
            closeDrawer(true);
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment fragment = getLastFragment();
                if (fragment != null) {
                    fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                }
            }, 200);
        });
        headerView.setOnNavigateToProfile(() -> {
            closeDrawer(true);
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment fragment = getLastFragment();
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
                args.putBoolean("my_profile", true);
                if (fragment != null) {
                    fragment.presentFragment(new ProfileActivity(args));
                }
            }, 200);
        });
        headerView.setOnStatusClick(this::showStatusSelect);
        headerView.setOnBadgeClick(this::showBadgeSelect);
        headerView.setOnProxyClick(() -> {
            closeDrawer(true);
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment fragment = getLastFragment();
                if (fragment != null) {
                    fragment.presentFragment(new ProxyListActivity());
                }
            }, 200);
        });
        accountPickerView.setOnAccountSelected(() -> closeDrawer(true));
        accountPickerView.setOnAccountLongClick((account, view) -> showAccountPreview(account));
        menuView.setOnItemClick(() -> closeDrawer(true));
    }

    private void toggleDayNightTheme() {
        if (DialogsActivity.switchingTheme) {
            return;
        }
        int[] pos = headerView.getThemeTogglePosition();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (activeTheme.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }
        boolean toDark = dayThemeName.equals(activeTheme.getKey());
        Theme.ThemeInfo themeInfo = toDark ? Theme.getTheme(nightThemeName) : Theme.getTheme(dayThemeName);
        if (themeInfo == null) {
            return;
        }
        DialogsActivity.switchingTheme = true;
        headerView.animateThemeToggle(toDark);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, headerView.getThemeToggleView(), null, null, false, null);
        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            Theme.turnOffAutoNight(BulletinFactory.of(fragment), () -> fragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT)));
        }
    }

    private void showStatusSelect() {
        if (selectAnimatedEmojiDialog != null) {
            return;
        }
        BaseFragment fragment = getLastFragment();
        if (fragment == null) {
            return;
        }
        final int account = UserConfig.selectedAccount;
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null || !MessagesController.getInstance(account).isPremiumUser(user)) {
            return;
        }
        SimpleTextView nameView = headerView.getNameView();
        int[] location = new int[2];
        nameView.getLocationOnScreen(location);
        int drawableX = location[0] + nameView.getRightDrawableX();
        int popupWidth = getPopupWidth();
        int popupX = MathUtils.clamp(drawableX - popupWidth / 2, 0, AndroidUtilities.displaySize.x - popupWidth);
        int popupY = location[1] + nameView.getHeight();

        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        SelectAnimatedEmojiDialog dialog = new SelectAnimatedEmojiDialog(fragment, getContext(), true, Math.max(0, drawableX - popupX), SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, true, null, 16) {
            @Override
            public void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                TLRPC.EmojiStatus emojiStatus;
                if (gift != null) {
                    TLRPC.TL_inputEmojiStatusCollectible status = new TLRPC.TL_inputEmojiStatusCollectible();
                    status.collectible_id = gift.id;
                    if (until != null) {
                        status.flags |= 1;
                        status.until = until;
                    }
                    emojiStatus = status;
                } else if (documentId == null) {
                    emojiStatus = new TLRPC.TL_emojiStatusEmpty();
                } else {
                    TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
                    status.document_id = documentId;
                    if (until != null) {
                        status.flags |= 1;
                        status.until = until;
                    }
                    emojiStatus = status;
                }
                MessagesController.getInstance(account).updateEmojiStatus(0, emojiStatus, gift);
                headerView.updateUserInfo();
                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }
        };
        dialog.setExpireDateHint(DialogObject.getEmojiStatusUntil(user.emoji_status));
        long emojiStatusId = DialogObject.getEmojiStatusDocumentId(user.emoji_status);
        dialog.setSelected(emojiStatusId != 0 ? emojiStatusId : null);
        dialog.setSaveState(3);
        popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(dialog, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                selectAnimatedEmojiDialog = null;
            }
        };
        int[] containerLocation = new int[2];
        getLocationOnScreen(containerLocation);
        popup[0].showAsDropDown(this, popupX, popupY - containerLocation[1] - AndroidUtilities.dp(16), Gravity.TOP | Gravity.LEFT);
        popup[0].dimBehind();
    }

    private void showBadgeSelect() {
        BaseFragment fragment = getLastFragment();
        if (fragment == null) {
            return;
        }
        BadgesController badgesController = BadgesController.INSTANCE;
        if (!badgesController.canChangeBadge()) {
            showCurrentBadgeBulletin(fragment);
            return;
        }
        if (selectAnimatedEmojiDialog != null) {
            return;
        }
        SimpleTextView nameView = headerView.getNameView();
        if (nameView.getRightDrawable2() == null) {
            return;
        }
        int[] location = new int[2];
        nameView.getLocationOnScreen(location);
        int drawableX = location[0] + nameView.rightDrawable2X;
        int popupWidth = getPopupWidth();
        int popupX = MathUtils.clamp(drawableX - popupWidth / 2, 0, AndroidUtilities.displaySize.x - popupWidth);
        int popupY = location[1] + nameView.getHeight();
        BadgeDTO defaultBadge = badgesController.getDefaultBadge();
        int account = UserConfig.selectedAccount;

        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        SelectAnimatedEmojiDialog dialog = new SelectAnimatedEmojiDialog(fragment, getContext(), true, Math.max(0, drawableX - popupX), SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, true, null, 16, Theme.getColor(COLOR_KEY_POPUP_ACCENT), true) {
            @Override
            public void onEmojiSelected(View emojiView, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until, String slug) {
                long badgeDocumentId;
                if (documentId == null) {
                    badgeDocumentId = defaultBadge != null ? defaultBadge.getDocumentId() : 0;
                } else {
                    badgeDocumentId = documentId;
                }
                BadgeDTO badge = new BadgeDTO(badgeDocumentId, TextUtils.isEmpty(slug) ? null : slug);
                headerView.updateUserInfo(badge);
                accountPickerView.loadAccounts(badge);
                BadgesController.INSTANCE.updateBadge(badge, result -> AndroidUtilities.runOnUIThread(() -> {
                    if (result == null || !result.equals("ok")) {
                        BulletinFactory.of(bulletinContainer, fragment.getResourceProvider()).createErrorBulletin(LocaleController.getString(R.string.UnknownError)).show();
                    }
                    headerView.updateUserInfo();
                    accountPickerView.loadAccounts();
                }));
                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }
        };
        if (defaultBadge != null) {
            dialog.setDefaultBadge(defaultBadge.getDocumentId());
        }
        dialog.useAccentForPlus = true;
        BadgeDTO currentBadge = badgesController.getBadge(UserConfig.getInstance(account).getCurrentUser());
        if (currentBadge == null || defaultBadge != null && currentBadge.getDocumentId() == defaultBadge.getDocumentId()) {
            dialog.setSelected(0L);
        } else {
            dialog.setSelected(currentBadge.getDocumentId());
        }
        popup[0] = selectAnimatedEmojiDialog = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(dialog, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                selectAnimatedEmojiDialog = null;
            }
        };
        int[] containerLocation = new int[2];
        getLocationOnScreen(containerLocation);
        popup[0].showAsDropDown(this, popupX, popupY - containerLocation[1] - AndroidUtilities.dp(16), Gravity.TOP | Gravity.LEFT);
        popup[0].dimBehind();
    }

    private void showCurrentBadgeBulletin(BaseFragment fragment) {
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        if (user == null) {
            return;
        }
        BadgesController.INSTANCE.showBadgeBulletin(fragment, user, null, UserConfig.selectedAccount, bulletinContainer, false);
    }

    private void showAccountPreview(int account) {
        ViewParent parent = getParent();
        if (!(parent instanceof DrawerLayoutContainer)) {
            return;
        }
        DrawerLayoutContainer drawerLayoutContainer = (DrawerLayoutContainer) parent;
        INavigationLayout layout = drawerLayoutContainer.getParentActionBarLayout();
        if (layout == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean("drawer_account_preview", true);
        MainTabsActivity fragment = new MainTabsActivity(args) {
            @Override
            public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
                super.onTransitionAnimationEnd(isOpen, backward);
                if (!isOpen && backward) {
                    restoreDrawerAbovePreview();
                }
            }

            @Override
            public void onPreviewOpenAnimationEnd() {
                super.onPreviewOpenAnimationEnd();
                restoreDrawerAbovePreview();
                closeDrawer(false);
                if (account != UserConfig.selectedAccount) {
                    AccountsUiHelper.switchTo(account);
                }
            }
        };
        fragment.setCurrentAccount(account);
        fragment.prepareDialogsActivity(args);
        if (layout.presentFragment(new INavigationLayout.NavigationParams(fragment).setPreview(true).setCheckPresentFromDelegate(false))) {
            drawerLayoutContainer.setDrawCurrentPreviewFragmentAbove(true);
        }
    }

    private void restoreDrawerAbovePreview() {
        ViewParent parent = getParent();
        if (parent instanceof DrawerLayoutContainer) {
            ((DrawerLayoutContainer) parent).setDrawCurrentPreviewFragmentAbove(false);
        }
    }

    private void updateDrawerWidth() {
        drawerWidth = calculateDrawerWidth();
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        layoutParams.width = drawerWidth;
        drawerPanel.setLayoutParams(layoutParams);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateDrawerWidth();
        setProgress(progress);
    }

    public boolean isDrawerOpen() {
        return progress > PROGRESS_EPSILON || isAnimating || predictiveBackInProgress;
    }

    public boolean startPredictiveBack() {
        if (predictiveBackInProgress || tracking || startedEdgeSwipe || getVisibility() != View.VISIBLE) {
            return false;
        }
        if (isAnimating) {
            cancelAnimations();
        }
        if (progress <= PROGRESS_EPSILON) {
            return false;
        }
        predictiveBackInProgress = true;
        predictiveBackStartProgress = progress;
        tapClosePending = false;
        super.setVisibility(View.VISIBLE);
        return true;
    }

    public void updatePredictiveBackProgress(float backProgress) {
        if (predictiveBackInProgress) {
            setProgress(predictiveBackStartProgress * (1f - Math.max(0f, Math.min(1f, backProgress)) * 0.5f));
        }
    }

    public void cancelPredictiveBack() {
        if (predictiveBackInProgress) {
            predictiveBackInProgress = false;
            isOpen = predictiveBackStartProgress > PROGRESS_EPSILON;
            animateProgress(isOpen ? 1f : 0f, true, 0);
        }
    }

    public void commitPredictiveBack() {
        if (!predictiveBackInProgress) {
            closeDrawer(true);
            return;
        }
        predictiveBackInProgress = false;
        isOpen = false;
        if (progress <= PROGRESS_EPSILON) {
            onCloseComplete();
        } else {
            animateProgress(0f, true, 0);
        }
    }

    public void toggleDrawer() {
        if (isDrawerOpen()) {
            closeDrawer(true);
        } else {
            openDrawer(true);
        }
    }

    public void openDrawer(boolean animated) {
        if (!ExteraConfig.getNavigationDrawer()) {
            onCloseComplete();
            return;
        }
        if (progress >= 0.999f && !isAnimating) {
            isOpen = true;
            setProgress(1f);
            return;
        }
        isOpen = true;
        updateDrawerWidth();
        super.setVisibility(View.VISIBLE);
        applyDrawerPanelPadding();
        refreshContents();
        if (animated) {
            animateProgress(1f);
        } else {
            setProgress(1f);
        }
    }

    public void closeDrawer(boolean animated) {
        if (progress <= PROGRESS_EPSILON && !isAnimating) {
            isOpen = false;
            onCloseComplete();
            return;
        }
        isOpen = false;
        if (animated) {
            animateProgress(0f);
        } else {
            setProgress(0f);
            onCloseComplete();
        }
    }

    private void refreshContents() {
        headerView.updateUserInfo();
        accountPickerView.loadAccounts();
        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            menuView.rebuildMenu(UserConfig.selectedAccount, fragment);
        } else {
            menuView.clearMenu();
        }
    }

    private void refreshAccountViews(int account, boolean reloadAccounts) {
        if (account == UserConfig.selectedAccount) {
            headerView.updateUserInfo();
        }
        if (reloadAccounts) {
            accountPickerView.loadAccounts();
        }
    }

    private void refreshAccountViews(int account, int mask) {
        boolean updateHeader = (mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_PHONE) != 0 || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0;
        boolean updateAccounts = (mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0;
        if (updateHeader && account == UserConfig.selectedAccount) {
            headerView.updateUserInfo();
        }
        if (updateAccounts) {
            accountPickerView.loadAccounts();
        }
    }

    private void setProgress(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        syncDrawerState();
        invalidate();
    }

    private void syncDrawerState() {
        if (progress <= PROGRESS_EPSILON && !isAnimating && !tracking && !startedEdgeSwipe && !predictiveBackInProgress) {
            applyClosedState();
            return;
        }
        drawerPanel.setTranslationX(-drawerWidth * (1f - progress));
        translateNavigationLayout(progress <= PROGRESS_EPSILON ? 0 : getNavigationLayoutTranslation(progress));
        if (getVisibility() != View.VISIBLE) {
            super.setVisibility(View.VISIBLE);
        }
    }

    private void applyClosedState() {
        progress = 0f;
        drawerPanel.setTranslationX(-drawerWidth);
        translateNavigationLayout(0);
        if (getVisibility() != View.GONE) {
            super.setVisibility(View.GONE);
        }
        tapClosePending = false;
    }

    private float getDrawerOffset() {
        return drawerWidth * progress;
    }

    private void setDrawerOffset(float offset) {
        float clamped = Math.max(0f, Math.min(drawerWidth, offset));
        setProgress(drawerWidth != 0 ? clamped / drawerWidth : 0f);
    }

    private float getNavigationLayoutTranslation(float progress) {
        if (ExteraConfig.getImmersiveDrawerAnimation()) {
            return drawerWidth * progress;
        }
        return drawerWidth * progress * 0.3f;
    }

    private void translateNavigationLayout(float translationX) {
        ViewParent parent = getParent();
        if (!(parent instanceof DrawerLayoutContainer)) {
            resetNavigationTranslationTarget();
            return;
        }
        DrawerLayoutContainer drawerLayoutContainer = (DrawerLayoutContainer) parent;
        INavigationLayout layout = drawerLayoutContainer.getParentActionBarLayout();
        if (layout == null) {
            resetNavigationTranslationTarget();
            return;
        }
        View target = resolveNavigationTranslationTarget(drawerLayoutContainer, layout);
        if (navigationTranslationTarget != null && navigationTranslationTarget != target) {
            navigationTranslationTarget.setTranslationX(0);
        }
        navigationTranslationTarget = target;
        if (target != null) {
            target.setTranslationX(translationX);
        }
    }

    private View resolveNavigationTranslationTarget(DrawerLayoutContainer drawerLayoutContainer, INavigationLayout layout) {
        ViewGroup layoutView = layout.getView();
        ViewParent layoutParent = layoutView.getParent();
        if (layoutParent instanceof View) {
            View parentView = (View) layoutParent;
            if (parentView.getParent() == drawerLayoutContainer) {
                return parentView;
            }
        }
        return layoutView;
    }

    private void resetNavigationTranslationTarget() {
        if (navigationTranslationTarget != null) {
            navigationTranslationTarget.setTranslationX(0);
            navigationTranslationTarget = null;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (progress <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        float scrimProgress = Math.max(0f, Math.min(1f, progress));
        int alpha, red = 0, green = 0, blue = 0;
        if (!ExteraConfig.getImmersiveDrawerAnimation() || AndroidUtilities.isTablet()) {
            alpha = (int) (scrimProgress * 102);
        } else {
            alpha = (int) (scrimProgress * 160);
            int color = Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND);
            red = Color.red(color);
            green = Color.green(color);
            blue = Color.blue(color);
        }
        scrimPaint.setColor(Color.argb(alpha, red, green, blue));
        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == drawerPanel && !ExteraConfig.getImmersiveDrawerAnimation()) {
            float topRightRadius = cachedTopRightRadius < 0 ? AndroidUtilities.dp(24) : cachedTopRightRadius;
            float bottomRightRadius = cachedBottomRightRadius < 0 ? AndroidUtilities.dp(24) : cachedBottomRightRadius;
            radii[0] = radii[1] = 0;
            radii[2] = radii[3] = topRightRadius;
            radii[4] = radii[5] = bottomRightRadius;
            radii[6] = radii[7] = 0;
            RectF bounds = AndroidUtilities.rectTmp;
            bounds.set(child.getX(), child.getY(), child.getX() + child.getWidth(), child.getY() + child.getHeight());
            clipPath.rewind();
            clipPath.addRoundRect(bounds, radii, Path.Direction.CW);
            int saveCount = canvas.save();
            canvas.clipPath(clipPath);
            boolean result = super.drawChild(canvas, child, drawingTime);
            canvas.restoreToCount(saveCount);
            return result;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void animateProgress(float target) {
        animateProgress(target, false, 0);
    }

    private void animateProgress(float target, boolean fast, float velocity) {
        cancelAnimations();
        isAnimating = true;
        final float targetOffset = drawerWidth * target;
        if (ExteraConfig.getSpringSwipeback()) {
            springAnimation = new SpringAnimation(this, DRAWER_OFFSET);
            springAnimation.setSpring(new SpringForce(targetOffset).setStiffness(fast ? 1500f : 950f).setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY));
            if (velocity != 0) {
                springAnimation.setStartVelocity(velocity);
            }
            springAnimation.addEndListener((animation, canceled, value, endVelocity) -> {
                if (springAnimation == animation) {
                    springAnimation = null;
                }
                if (canceled) {
                    return;
                }
                isAnimating = false;
                setDrawerOffset(targetOffset);
                if (target == 0) {
                    onCloseComplete();
                }
            });
            springAnimation.animateToFinalPosition(targetOffset);
            return;
        }
        standardAnimator = ValueAnimator.ofFloat(getDrawerOffset(), targetOffset);
        standardAnimator.setDuration(getAnimationDuration(targetOffset, fast));
        standardAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        standardAnimator.addUpdateListener(animation -> setDrawerOffset((Float) animation.getAnimatedValue()));
        standardAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (standardAnimator == animation) {
                    standardAnimator = null;
                }
                if (canceled) {
                    return;
                }
                isAnimating = false;
                setDrawerOffset(targetOffset);
                if (target == 0) {
                    onCloseComplete();
                }
            }
        });
        standardAnimator.start();
    }

    private long getAnimationDuration(float targetOffset, boolean fast) {
        if (!fast) {
            return 300;
        }
        float distance = getDrawerOffset();
        if (targetOffset > distance) {
            distance = drawerWidth - distance;
        }
        return Math.max((long) (250f / Math.max(drawerWidth, 1) * distance), 100);
    }

    private void cancelAnimations() {
        if (springAnimation != null) {
            SpringAnimation animation = springAnimation;
            springAnimation = null;
            animation.cancel();
        }
        if (standardAnimator != null) {
            ValueAnimator animator = standardAnimator;
            standardAnimator = null;
            animator.cancel();
        }
        isAnimating = false;
        setProgress(progress);
        if (!isOpen && progress <= PROGRESS_EPSILON && !tracking && !startedEdgeSwipe) {
            onCloseComplete();
        }
    }

    private void onCloseComplete() {
        isOpen = false;
        tracking = false;
        startedEdgeSwipe = false;
        animationInterruptedByTouch = false;
        predictiveBackInProgress = false;
        predictiveBackStartProgress = 0;
        setProgress(0);
        tapClosePending = false;
        dismissSelectionPopup();
        menuView.clearMenu();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (isClosingAnimationInProgress()) {
            return !shouldPassClosingTouchThrough(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (isAnimating) {
                cancelAnimations();
                animationInterruptedByTouch = true;
            }
            startX = event.getX();
            startY = event.getY();
            startProgress = progress;
            tracking = false;
            float panelEdge = drawerPanel.getTranslationX() + drawerWidth;
            tapClosePending = event.getX() > panelEdge;
            return event.getX() > panelEdge;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - startX;
            if (shouldStartVisibleDrawerTracking(dx, Math.abs(event.getY() - startY))) {
                beginVisibleDrawerTracking(event, dx);
                return true;
            }
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isClosingAnimationInProgress()) {
            return !shouldPassClosingTouchThrough(event);
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            if (isAnimating) {
                cancelAnimations();
                animationInterruptedByTouch = true;
            }
            startX = event.getX();
            startY = event.getY();
            startProgress = progress;
            tracking = false;
            tapClosePending = event.getX() > drawerPanel.getTranslationX() + drawerWidth;
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (!tracking) {
                float dx = event.getX() - startX;
                if (shouldStartVisibleDrawerTracking(dx, Math.abs(event.getY() - startY))) {
                    beginVisibleDrawerTracking(event, dx);
                }
            }
            if (tracking) {
                setProgress(Math.max(0f, Math.min(1f, startProgress + (event.getX() - startX) / drawerWidth)));
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (tracking) {
                finishTracking();
                return true;
            }
            if (action == MotionEvent.ACTION_UP && tapClosePending) {
                tapClosePending = false;
                closeDrawer(true);
                return true;
            }
            tapClosePending = false;
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean result = super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (animationInterruptedByTouch && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            animationInterruptedByTouch = false;
            settleInterruptedAnimation();
        }
        return result;
    }

    private void settleInterruptedAnimation() {
        if (isAnimating || tracking || startedEdgeSwipe || predictiveBackInProgress) {
            return;
        }
        float target = isOpen ? 1f : 0f;
        if (Math.abs(progress - target) <= PROGRESS_EPSILON) {
            setProgress(target);
            if (!isOpen) {
                onCloseComplete();
            }
            return;
        }
        animateProgress(target, true, 0);
    }

    private boolean isClosingAnimationInProgress() {
        return isAnimating && !isOpen;
    }

    private boolean shouldPassClosingTouchThrough(MotionEvent event) {
        return event != null && event.getAction() == MotionEvent.ACTION_DOWN && event.getX() > drawerPanel.getTranslationX() + drawerWidth;
    }

    private boolean shouldStartVisibleDrawerTracking(float dx, float absDy) {
        if (dx < 0) {
            return Math.abs(dx) >= absDy && Math.abs(dx) >= getDrawerCloseTouchSlop();
        }
        return startProgress < 0.999f && dx > 0 && dx / 3f > absDy && dx >= getDrawerOpenTouchSlop();
    }

    public boolean handleEdgeSwipeIntercept(MotionEvent event) {
        if (!ExteraConfig.getNavigationDrawer()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            startX = event.getX();
            startY = event.getY();
            startProgress = progress;
            startedEdgeSwipe = false;
            tracking = false;
            if (canStartClosedDrawerSwipe(event)) {
                startedEdgeSwipe = true;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.clear();
                velocityTracker.addMovement(event);
            }
            return false;
        }
        if (startedEdgeSwipe) {
            if (velocityTracker != null) {
                velocityTracker.addMovement(event);
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - startX;
                float dy = event.getY() - startY;
                if (shouldBlockClosedDrawerSwipe(dx, dy)) {
                    startedEdgeSwipe = false;
                    return false;
                }
                if (shouldStartClosedDrawerTracking(dx, Math.abs(dy))) {
                    beginClosedDrawerTracking(event, dx);
                    return true;
                }
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                startedEdgeSwipe = false;
            }
        }
        return false;
    }

    public boolean handleEdgeSwipeTouch(MotionEvent event) {
        if (!ExteraConfig.getNavigationDrawer()) {
            return false;
        }
        if (!startedEdgeSwipe && !tracking) {
            return false;
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        int action = event.getAction();
        if (action == MotionEvent.ACTION_MOVE) {
            if (tracking) {
                setProgress(Math.max(0f, Math.min(1f, startProgress + (event.getX() - startX) / drawerWidth)));
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (tracking) {
                finishTracking();
            }
            startedEdgeSwipe = false;
        }
        return true;
    }

    private boolean shouldBlockClosedDrawerSwipe(float dx, float dy) {
        float absDy = Math.abs(dy);
        float slop = AndroidUtilities.touchSlop;
        if (slop <= 0) {
            slop = getDrawerOpenTouchSlop();
        }
        return absDy >= slop && absDy > Math.abs(dx);
    }

    private boolean shouldStartClosedDrawerTracking(float dx, float absDy) {
        return dx > 0 && dx / 3f > absDy && Math.abs(dx) >= getDrawerOpenTouchSlop();
    }

    private void beginVisibleDrawerTracking(MotionEvent event, float dx) {
        tracking = true;
        tapClosePending = false;
        if (isAnimating) {
            cancelAnimations();
        }
        offsetTrackingStart(event, dx);
        resetTrackingVelocity(event);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void beginClosedDrawerTracking(MotionEvent event, float dx) {
        tracking = true;
        tapClosePending = false;
        if (isAnimating) {
            cancelAnimations();
        }
        super.setVisibility(View.VISIBLE);
        applyDrawerPanelPadding();
        refreshContents();
        offsetTrackingStart(event, dx);
        resetTrackingVelocity(event);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void offsetTrackingStart(MotionEvent event, float dx) {
        startX += Math.signum(dx) * getTrackingTouchSlop(dx);
        startY = event.getY();
        startProgress = progress;
    }

    private void resetTrackingVelocity(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(event);
    }

    private float getTrackingTouchSlop(float dx) {
        return dx < 0 ? getDrawerCloseTouchSlop() : getDrawerOpenTouchSlop();
    }

    private float getDrawerOpenTouchSlop() {
        return AndroidUtilities.getPixelsInCM(0.2f, true);
    }

    private float getDrawerCloseTouchSlop() {
        return AndroidUtilities.getPixelsInCM(0.4f, true);
    }

    private void finishTracking() {
        float velX = 0;
        float velY = 0;
        if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000);
            velX = velocityTracker.getXVelocity();
            velY = velocityTracker.getYVelocity();
        }
        int swipeVelocity = AppUtils.getSwipeVelocity();
        boolean enoughProgress = progress >= 1f / (isOpen ? 1.25f : 5f);
        boolean flingOpen = velX >= swipeVelocity && Math.abs(velX) >= Math.abs(velY);
        boolean flingClose = velX < 0 && Math.abs(velX) >= swipeVelocity;
        if ((enoughProgress || flingOpen) && !flingClose) {
            boolean fast = !isOpen && Math.abs(velX) >= swipeVelocity;
            isOpen = true;
            animateProgress(1f, fast, velX);
        } else {
            boolean fast = isOpen && Math.abs(velX) >= swipeVelocity;
            isOpen = false;
            animateProgress(0f, fast, velX);
        }
        recycleVelocityTracker();
        tracking = false;
        startedEdgeSwipe = false;
        tapClosePending = false;
    }

    private boolean canOpen(MotionEvent event) {
        BaseFragment fragment = getLastFragment();
        if (fragment instanceof DialogsActivity) {
            return ((DialogsActivity) fragment).canOpenDrawerBySwipe(event);
        }
        return false;
    }

    private boolean canStartClosedDrawerSwipe(MotionEvent event) {
        if (!canOpen(event)) {
            return false;
        }
        ViewParent parent = getParent();
        if (!(parent instanceof DrawerLayoutContainer)) {
            return false;
        }
        INavigationLayout layout = ((DrawerLayoutContainer) parent).getParentActionBarLayout();
        if (layout == null || layout.getFragmentStack().size() != 1 || !layout.allowSwipe()) {
            return false;
        }
        BaseFragment lastFragment = layout.getLastFragment();
        if (lastFragment != null && lastFragment.getLastSheet() != null && lastFragment.getLastSheet().attachedToParent()) {
            return false;
        }
        ViewGroup layoutView = layout.getView();
        if (layoutView == null) {
            return false;
        }
        layoutView.getHitRect(rect);
        return rect.contains((int) event.getX(), (int) event.getY()) && findScrollingChild(layoutView, event.getX() - rect.left, event.getY() - rect.top) == null;
    }

    private View findScrollingChild(ViewGroup parent, float x, float y) {
        for (int i = 0, count = parent.getChildCount(); i < count; i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (!rect.contains((int) x, (int) y)) {
                continue;
            }
            if (child.canScrollHorizontally(-1)) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View scrollingChild = findScrollingChild((ViewGroup) child, x - rect.left, y - rect.top);
                if (scrollingChild != null) {
                    return scrollingChild;
                }
            }
        }
        return null;
    }

    private BaseFragment getLastFragment() {
        ViewParent parent = getParent();
        if (!(parent instanceof DrawerLayoutContainer)) {
            return null;
        }
        INavigationLayout layout = ((DrawerLayoutContainer) parent).getParentActionBarLayout();
        if (layout == null) {
            return null;
        }
        BaseFragment lastFragment = layout.getLastFragment();
        return lastFragment instanceof MainTabsActivity ? ((MainTabsActivity) lastFragment).getCurrentVisibleFragment() : lastFragment;
    }

    private void dismissSelectionPopup() {
        if (selectAnimatedEmojiDialog != null) {
            selectAnimatedEmojiDialog.dismiss();
            selectAnimatedEmojiDialog = null;
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    public void dispose() {
        cancelAnimations();
        onCloseComplete();
        recycleVelocityTracker();
        accountPickerView.dispose();
        unregisterNotifications();
        resetNavigationTranslationTarget();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (!ExteraConfig.getImmersiveDrawerAnimation()) {
            float defaultRadius = AndroidUtilities.dp(24);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RoundedCorner topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
                RoundedCorner bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
                cachedTopRightRadius = topRight != null ? Math.max(defaultRadius, topRight.getRadius() / 2f) : defaultRadius;
                cachedBottomRightRadius = bottomRight != null ? Math.max(defaultRadius, bottomRight.getRadius() / 2f) : defaultRadius;
            } else {
                cachedTopRightRadius = defaultRadius;
                cachedBottomRightRadius = defaultRadius;
            }
        }
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerNotifications();
        Bulletin.addDelegate(bulletinContainer, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return AndroidUtilities.navigationBarHeight;
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Bulletin.removeDelegate(bulletinContainer);
        cancelAnimations();
        onCloseComplete();
        dismissSelectionPopup();
        recycleVelocityTracker();
        accountPickerView.dispose();
        resetNavigationTranslationTarget();
        unregisterNotifications();
    }

    private void registerNotifications() {
        if (notificationsRegistered) {
            return;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter notificationCenter = NotificationCenter.getInstance(a);
            notificationCenter.addObserver(this, NotificationCenter.mainUserInfoChanged);
            notificationCenter.addObserver(this, NotificationCenter.userEmojiStatusUpdated);
            notificationCenter.addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            notificationCenter.addObserver(this, NotificationCenter.updateInterfaces);
            notificationCenter.addObserver(this, NotificationCenter.appDidLogout);
            notificationCenter.addObserver(this, NotificationCenter.attachMenuBotsDidLoad);
            notificationCenter.addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        NotificationCenter globalInstance = NotificationCenter.getGlobalInstance();
        globalInstance.addObserver(this, NotificationCenter.didSetNewTheme);
        globalInstance.addObserver(this, NotificationCenter.themeAccentListUpdated);
        globalInstance.addObserver(this, NotificationCenter.notificationsCountUpdated);
        globalInstance.addObserver(this, NotificationCenter.reloadInterface);
        globalInstance.addObserver(this, NotificationCenter.pluginMenuItemsUpdated);
        globalInstance.addObserver(this, NotificationCenter.proxySettingsChanged);
        globalInstance.addObserver(this, NotificationCenter.proxyPingUpdated);
        notificationsRegistered = true;
    }

    private void unregisterNotifications() {
        if (!notificationsRegistered) {
            return;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter notificationCenter = NotificationCenter.getInstance(a);
            notificationCenter.removeObserver(this, NotificationCenter.mainUserInfoChanged);
            notificationCenter.removeObserver(this, NotificationCenter.userEmojiStatusUpdated);
            notificationCenter.removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            notificationCenter.removeObserver(this, NotificationCenter.updateInterfaces);
            notificationCenter.removeObserver(this, NotificationCenter.appDidLogout);
            notificationCenter.removeObserver(this, NotificationCenter.attachMenuBotsDidLoad);
            notificationCenter.removeObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        NotificationCenter globalInstance = NotificationCenter.getGlobalInstance();
        globalInstance.removeObserver(this, NotificationCenter.didSetNewTheme);
        globalInstance.removeObserver(this, NotificationCenter.themeAccentListUpdated);
        globalInstance.removeObserver(this, NotificationCenter.notificationsCountUpdated);
        globalInstance.removeObserver(this, NotificationCenter.reloadInterface);
        globalInstance.removeObserver(this, NotificationCenter.pluginMenuItemsUpdated);
        globalInstance.removeObserver(this, NotificationCenter.proxySettingsChanged);
        globalInstance.removeObserver(this, NotificationCenter.proxyPingUpdated);
        notificationsRegistered = false;
    }

    public void onAccountChanged() {
        refreshContents();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mainUserInfoChanged || id == NotificationCenter.userEmojiStatusUpdated || id == NotificationCenter.currentUserPremiumStatusChanged) {
            refreshAccountViews(account, true);
        } else if (id == NotificationCenter.updateInterfaces) {
            if (args.length > 0 && args[0] instanceof Integer) {
                refreshAccountViews(account, (int) (Integer) args[0]);
            }
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
        } else if (id == NotificationCenter.didSetNewTheme) {
            updateColors();
        } else if (id == NotificationCenter.themeAccentListUpdated) {
            AndroidUtilities.runOnUIThread(this::updateColors);
        } else if (id == NotificationCenter.notificationsCountUpdated) {
            accountPickerView.updateUnreadCounters();
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
        } else if (id == NotificationCenter.reloadInterface) {
            headerView.updateUserInfo();
            accountPickerView.updateUnreadCounters();
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
            updateColors();
        } else if (id == NotificationCenter.attachMenuBotsDidLoad) {
            if (account == UserConfig.selectedAccount && isOpen) {
                refreshContents();
            }
        } else if (id == NotificationCenter.pluginMenuItemsUpdated) {
            if (isOpen) {
                refreshContents();
            }
        } else if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.proxyPingUpdated || id == NotificationCenter.didUpdateConnectionState) {
            headerView.updateProxyStatus();
        } else if (id == NotificationCenter.appDidLogout) {
            refreshAccountViews(account, true);
            if (isOpen) {
                closeDrawer(false);
            }
        }
    }

    private void updateColors() {
        drawerPanel.setBackgroundColor(Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND));
        headerView.updateColors();
        accountPickerView.updateColors();
        menuView.updateColors();
        invalidate();
    }

    private int calculateDrawerWidth() {
        return Math.min(AndroidUtilities.dp(300), AndroidUtilities.displaySize.x - AndroidUtilities.dp(56));
    }

    private void applyDrawerPanelPadding() {
        drawerPanel.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
    }

    private int getPopupWidth() {
        return (int) Math.min(AndroidUtilities.dp(324), AndroidUtilities.displaySize.x * 0.95f);
    }
}
