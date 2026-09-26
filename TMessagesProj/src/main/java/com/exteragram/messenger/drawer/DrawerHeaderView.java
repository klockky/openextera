package com.exteragram.messenger.drawer;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.badges.BadgesController;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.DialogsActivity;

public class DrawerHeaderView extends FrameLayout {

    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;
    private static final int COLOR_KEY_SUBTITLE = Theme.key_windowBackgroundWhiteGrayText2;
    private static final int COLOR_KEY_ICON = Theme.key_windowBackgroundWhiteGrayIcon;
    private static final int COLOR_KEY_STATUS = Theme.key_profile_verifiedBackground;

    private static final int PROXY_STATE_HIDDEN = 0;
    private static final int PROXY_STATE_ICON = 1;
    private static final int PROXY_STATE_PING = 2;

    private final AvatarDrawable avatarDrawable;
    private final BackupImageView avatarView;
    private final FrameLayout themeToggleBg;
    private final RLottieDrawable sunDrawable;
    private final RLottieImageView themeToggleView;
    private final FrameLayout proxyButton;
    private final ImageView proxyIcon;
    private final AnimatedTextView proxyTextView;
    private final SimpleTextView nameView;
    private final SimpleTextView subtitleView;
    private final ImageView chevronView;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable premiumStatusDrawable;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable exteraBadgeDrawable;

    private boolean chevronExpanded;
    private int lastProxyState = -1;
    private int lastProxyColor = -1;

    private Runnable onChevronClick;
    private Runnable onThemeToggle;
    private Runnable onThemeToggleLongClick;
    private Runnable onNavigateToProfile;
    private Runnable onStatusClick;
    private Runnable onBadgeClick;
    private Runnable onProxyClick;

    public DrawerHeaderView(Context context) {
        super(context);

        avatarDrawable = new AvatarDrawable();
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(ExteraConfig.getAvatarCorners(72));
        addView(avatarView, LayoutHelper.createFrame(72, 72, Gravity.LEFT | Gravity.TOP, 16, 16, 0, 0));
        avatarView.setOnClickListener(v -> {
            if (onNavigateToProfile != null) {
                onNavigateToProfile.run();
            }
        });

        themeToggleBg = new FrameLayout(context);
        ScaleStateListAnimator.apply(themeToggleBg);
        themeToggleBg.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), getThemeToggleBackgroundColor()));
        addView(themeToggleBg, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 0, 16, 16, 0));

        sunDrawable = new RLottieDrawable(R.raw.sun, AndroidUtilities.dp(24), AndroidUtilities.dp(24), true, null);
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        themeToggleView = new RLottieImageView(context);
        themeToggleView.setAnimation(sunDrawable);
        themeToggleView.setScaleType(ImageView.ScaleType.CENTER);
        themeToggleBg.addView(themeToggleView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        setThemeToggleStaticState(Theme.isCurrentThemeDark());
        themeToggleBg.setOnClickListener(v -> {
            resetThemeTogglePressAnimation();
            if (onThemeToggle != null) {
                onThemeToggle.run();
            }
        });
        themeToggleBg.setOnLongClickListener(v -> {
            if (onThemeToggleLongClick == null) {
                return false;
            }
            onThemeToggleLongClick.run();
            return true;
        });
        updateThemeToggleColors();

        proxyButton = new FrameLayout(context);
        ScaleStateListAnimator.apply(proxyButton);
        proxyButton.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), getThemeToggleBackgroundColor()));
        addView(proxyButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.RIGHT | Gravity.TOP, 0, 16, 60, 0));
        proxyButton.setOnClickListener(v -> {
            if (onProxyClick != null) {
                onProxyClick.run();
            }
        });

        LinearLayout proxyLayout = new LinearLayout(context);
        proxyLayout.setOrientation(LinearLayout.HORIZONTAL);
        proxyLayout.setGravity(Gravity.CENTER);
        proxyLayout.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
        proxyButton.addView(proxyLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        proxyIcon = new ImageView(context);
        proxyIcon.setScaleType(ImageView.ScaleType.CENTER);
        proxyLayout.addView(proxyIcon, LayoutHelper.createLinear(24, 24));

        proxyTextView = new AnimatedTextView(context, true, true, true);
        proxyTextView.setTextSize(AndroidUtilities.dp(13));
        proxyTextView.adaptWidth = true;
        proxyTextView.setTypeface(AndroidUtilities.bold());
        proxyTextView.setTextColor(Theme.getColor(COLOR_KEY_ICON));
        proxyTextView.setPadding(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(4), 0);
        proxyTextView.setVisibility(View.GONE);
        proxyLayout.addView(proxyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        FrameLayout infoLayout = new FrameLayout(context);
        addView(infoLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 100, 0, 0));
        infoLayout.setOnClickListener(v -> {
            if (onChevronClick != null) {
                onChevronClick.run();
            }
        });

        nameView = new SimpleTextView(context);
        nameView.setTextSize(15);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameView.setEllipsizeByGradient(true);
        nameView.setCanHideRightDrawable(false);
        nameView.setRightDrawableOutside(true);
        nameView.setClickable(false);
        infoLayout.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.LEFT | Gravity.TOP, 16, 0, 64, 0));

        premiumStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, AndroidUtilities.dp(22));
        exteraBadgeDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, AndroidUtilities.dp(22));
        nameView.setRightDrawable(premiumStatusDrawable);
        nameView.setRightDrawable2(exteraBadgeDrawable);
        nameView.setRightDrawableOnClick(v -> {
            if (onStatusClick != null) {
                onStatusClick.run();
            }
        });
        nameView.setRightDrawable2OnClick(v -> {
            if (onBadgeClick != null) {
                onBadgeClick.run();
            }
        });

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(Theme.getColor(COLOR_KEY_SUBTITLE));
        subtitleView.setMaxLines(1);
        subtitleView.setClickable(false);
        infoLayout.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 16, 26, 64, 0));

        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.msg_expand);
        chevronView.setScaleType(ImageView.ScaleType.CENTER);
        chevronView.setColorFilter(createColorFilter(COLOR_KEY_SUBTITLE));
        infoLayout.addView(chevronView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 22, 0));
    }

    public void setOnChevronClick(Runnable onChevronClick) {
        this.onChevronClick = onChevronClick;
    }

    public void setOnThemeToggle(Runnable onThemeToggle) {
        this.onThemeToggle = onThemeToggle;
    }

    public void setOnThemeToggleLongClick(Runnable onThemeToggleLongClick) {
        this.onThemeToggleLongClick = onThemeToggleLongClick;
    }

    public void setOnNavigateToProfile(Runnable onNavigateToProfile) {
        this.onNavigateToProfile = onNavigateToProfile;
    }

    public void setOnStatusClick(Runnable onStatusClick) {
        this.onStatusClick = onStatusClick;
    }

    public void setOnBadgeClick(Runnable onBadgeClick) {
        this.onBadgeClick = onBadgeClick;
    }

    public void setOnProxyClick(Runnable onProxyClick) {
        this.onProxyClick = onProxyClick;
    }

    public SimpleTextView getNameView() {
        return nameView;
    }

    public RLottieImageView getThemeToggleView() {
        return themeToggleView;
    }

    public void updateUserInfo() {
        updateUserInfo(null);
    }

    public void updateUserInfo(BadgeDTO badge) {
        int account = UserConfig.selectedAccount;
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null) {
            return;
        }
        avatarDrawable.setInfo(account, user);
        avatarView.setRoundRadius(ExteraConfig.getAvatarCorners(72));
        avatarView.getImageReceiver().setCurrentAccount(account);
        avatarView.setForUserOrChat(user, avatarDrawable);
        nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
        premiumStatusDrawable.setCurrentAccount(account);
        exteraBadgeDrawable.setCurrentAccount(account);

        String username = DialogObject.getPublicUsername(user);
        if (username != null && !username.isEmpty()) {
            subtitleView.setText("@" + username);
        } else if (user.phone != null && !user.phone.isEmpty()) {
            if (ExteraConfig.getHidePhoneNumber()) {
                subtitleView.setText(LocaleController.getString(R.string.MobileHidden));
            } else {
                subtitleView.setText(PhoneFormat.getInstance().format("+" + user.phone));
            }
        } else {
            subtitleView.setText(LocaleController.getString(R.string.NumberUnknown));
        }

        long emojiStatusId = DialogObject.getEmojiStatusDocumentId(user.emoji_status);
        boolean isPremium = MessagesController.getInstance(account).isPremiumUser(user);
        int statusColor = Theme.getColor(COLOR_KEY_STATUS);
        if (emojiStatusId != 0) {
            premiumStatusDrawable.set(emojiStatusId, true);
        } else if (isPremium) {
            premiumStatusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, true);
        } else {
            premiumStatusDrawable.set((Drawable) null, true);
        }
        premiumStatusDrawable.setParticles(DialogObject.isEmojiStatusCollectible(user.emoji_status), true);
        premiumStatusDrawable.setColor(statusColor);

        if (badge == null) {
            badge = BadgesController.INSTANCE.getBadge(user);
        }
        applyNameDrawables(emojiStatusId != 0 || isPremium ? premiumStatusDrawable : null, updateBadgeDrawable(badge, true, true, statusColor));
        updateProxyStatus();
    }

    public void updateProxyStatus() {
        boolean proxyEnabled = SharedConfig.isProxyEnabled();
        int connectionState = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        boolean connected = connectionState == ConnectionsManager.ConnectionStateConnected || connectionState == ConnectionsManager.ConnectionStateUpdating;
        long ping = 0;
        int state;
        SharedConfig.ProxyInfo proxyInfo = SharedConfig.currentProxy;
        if (proxyEnabled && proxyInfo != null && connected) {
            ping = Utilities.clamp(proxyInfo.ping, 9999L, 0L);
            state = ping > 0 ? PROXY_STATE_PING : PROXY_STATE_ICON;
        } else if (SharedConfig.proxyList.isEmpty()) {
            state = PROXY_STATE_HIDDEN;
        } else {
            state = PROXY_STATE_ICON;
        }
        if (state != lastProxyState) {
            TransitionManager.beginDelayedTransition(this, new ChangeBounds().setDuration(150));
            lastProxyState = state;
        }
        if (state == PROXY_STATE_HIDDEN) {
            proxyButton.setVisibility(View.GONE);
            return;
        }
        proxyButton.setVisibility(View.VISIBLE);
        if (state == PROXY_STATE_PING) {
            proxyTextView.setVisibility(View.VISIBLE);
            proxyTextView.setText(LocaleController.formatString(R.string.NavigationDrawerProxyPingShort, ping), true);
        } else {
            proxyTextView.setVisibility(View.GONE);
        }
        boolean active = proxyEnabled && connected;
        int color = Theme.getColor(active ? Theme.key_windowBackgroundWhiteGreenText : COLOR_KEY_ICON);
        if (color != lastProxyColor) {
            lastProxyColor = color;
            proxyButton.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.multAlpha(color, 0.075f)));
            proxyIcon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            proxyTextView.setTextColor(color);
        }
        proxyIcon.setImageResource(active ? R.drawable.drawer_proxy_on : R.drawable.drawer_proxy_off);
    }

    private Drawable updateBadgeDrawable(BadgeDTO badge, boolean animated, boolean particles, int color) {
        if (badge == null) {
            clearBadgeDrawables(animated);
            return null;
        }
        exteraBadgeDrawable.set(badge.getDocumentId(), animated);
        exteraBadgeDrawable.setParticles(particles, animated);
        exteraBadgeDrawable.setColor(color);
        return exteraBadgeDrawable;
    }

    private void clearBadgeDrawables(boolean animated) {
        exteraBadgeDrawable.set((Drawable) null, animated);
        exteraBadgeDrawable.setParticles(false, animated);
        exteraBadgeDrawable.setColor(null);
    }

    public void setChevronExpanded(boolean expanded) {
        if (chevronExpanded == expanded) {
            return;
        }
        chevronExpanded = expanded;
        chevronView.animate().cancel();
        chevronView.animate().rotation(expanded ? 180 : 0).setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    public int[] getThemeTogglePosition() {
        int[] position = new int[2];
        themeToggleBg.getLocationInWindow(position);
        position[0] += themeToggleBg.getMeasuredWidth() / 2;
        position[1] += themeToggleBg.getMeasuredHeight() / 2;
        return position;
    }

    public void animateThemeToggle(boolean dark) {
        syncThemeToggle(dark, true);
    }

    public void updateColors() {
        nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        subtitleView.setTextColor(Theme.getColor(COLOR_KEY_SUBTITLE));
        chevronView.setColorFilter(createColorFilter(COLOR_KEY_SUBTITLE));
        themeToggleBg.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), getThemeToggleBackgroundColor()));
        lastProxyState = -1;
        lastProxyColor = -1;
        updateUserInfo();
        if (!themeToggleView.isPlaying() && !DialogsActivity.switchingTheme) {
            syncThemeToggle(false);
        }
        if (!DialogsActivity.switchingTheme || Theme.isCurrentThemeDark()) {
            updateThemeToggleColors();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        premiumStatusDrawable.attach();
        exteraBadgeDrawable.attach();
        if (themeToggleView.isPlaying() || DialogsActivity.switchingTheme) {
            return;
        }
        syncThemeToggle(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        premiumStatusDrawable.detach();
        exteraBadgeDrawable.detach();
    }

    private void updateThemeToggleColors() {
        applyThemeToggleColors(sunDrawable, Theme.getColor(COLOR_KEY_ICON));
        themeToggleView.setColorFilter(createColorFilter(COLOR_KEY_ICON));
        themeToggleView.invalidate();
    }

    private void applyThemeToggleColors(RLottieDrawable drawable, int color) {
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        drawable.beginApplyLayerColors();
        drawable.setLayerColor("Sunny", color);
        drawable.setLayerColor("Path 6", color);
        drawable.setLayerColor("Path", color);
        drawable.setLayerColor("Path 5", color);
        drawable.commitApplyLayerColors();
    }

    private void syncThemeToggle(boolean animated) {
        syncThemeToggle(Theme.isCurrentThemeDark(), animated);
    }

    private void syncThemeToggle(boolean dark, boolean animated) {
        if (sunDrawable.getFramesCount() <= 0) {
            return;
        }
        int currentFrame = getThemeToggleCurrentFrame(dark);
        int endFrame = getThemeToggleEndFrame(dark);
        if (animated) {
            sunDrawable.setCustomEndFrame(endFrame);
            themeToggleView.playAnimation();
        } else if (!isAttachedToWindow()) {
            setThemeToggleStaticState(dark);
        } else {
            sunDrawable.stop();
            sunDrawable.setCurrentFrame(currentFrame, false, true);
            sunDrawable.setCustomEndFrame(currentFrame);
            themeToggleView.invalidate();
        }
    }

    private int getThemeToggleCurrentFrame(boolean dark) {
        return dark ? sunDrawable.getFramesCount() - 1 : 0;
    }

    private int getThemeToggleEndFrame(boolean dark) {
        return dark ? sunDrawable.getFramesCount() : 0;
    }

    private void setThemeToggleStaticState(boolean dark) {
        sunDrawable.stop();
        sunDrawable.setCurrentFrame(getThemeToggleCurrentFrame(dark));
        sunDrawable.setCustomEndFrame(getThemeToggleEndFrame(dark));
        themeToggleView.invalidate();
    }

    private void applyNameDrawables(Drawable statusDrawable, Drawable badgeDrawable) {
        if (statusDrawable != null && statusDrawable == nameView.getRightDrawable2()) {
            nameView.setRightDrawable2(null);
        }
        nameView.setRightDrawable(statusDrawable);
        nameView.setRightDrawable2(badgeDrawable);
    }

    private static PorterDuffColorFilter createColorFilter(int colorKey) {
        return new PorterDuffColorFilter(Theme.getColor(colorKey), PorterDuff.Mode.SRC_IN);
    }

    private static int getThemeToggleBackgroundColor() {
        return Theme.multAlpha(Theme.getColor(COLOR_KEY_ICON), 0.075f);
    }

    private void resetThemeTogglePressAnimation() {
        themeToggleBg.setPressed(false);
        themeToggleBg.setScaleX(1f);
        themeToggleBg.setScaleY(1f);
    }
}
