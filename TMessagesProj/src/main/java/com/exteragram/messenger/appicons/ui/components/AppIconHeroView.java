package com.exteragram.messenger.appicons.ui.components;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.appicons.AppIcon;
import com.exteragram.messenger.appicons.AppIconController;
import com.exteragram.messenger.appicons.AppIconPreviewLoader;
import com.exteragram.messenger.utils.text.LocaleUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class AppIconHeroView extends LinearLayout implements Theme.Colorable {

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final AppIconPreviewView previewView;
    private final AnimatedTextView titleView;
    private final AnimatedTextView subtitleView;

    private AppIcon icon;
    private float collapse;
    private int linkColor;
    private ValueAnimator linkColorAnimator;
    private ClickableSpan pressedLink;
    private CharSequence subtitleLinks;
    private Runnable previewReadyListener;
    private int previewSizeDp = 128;

    public AppIconHeroView(Context context, BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = fragment.getResourceProvider();
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipToPadding(false);
        setClipChildren(false);

        previewView = new AppIconPreviewView(context, resourcesProvider);
        previewView.setCrossfade(true);
        previewView.setOnPreviewReady(this::onPreviewReady);
        addView(previewView, LayoutHelper.createLinear(128, 128, Gravity.CENTER_HORIZONTAL));

        titleView = addText(22, AndroidUtilities.bold(), 28, 18);
        subtitleView = addText(14, AndroidUtilities.regular(), 20, 6);
        updateColors();

        boolean hasSubtitles = false;
        for (AppIcon appIcon : AppIconController.getAvailableIcons()) {
            hasSubtitles |= subtitleOf(appIcon) != null;
        }
        subtitleView.setVisibility(hasSubtitles ? VISIBLE : GONE);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private static CharSequence subtitleOf(AppIcon icon) {
        CharSequence description = icon.getDescription();
        return !TextUtils.isEmpty(description) ? description : icon.getAuthor();
    }

    @Override
    public void updateColors() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        titleView.setTextColor(color);
        subtitleView.setTextColor(ColorUtils.setAlphaComponent(color, 179));
        updateLinkColor(false);
    }

    private void updateLinkColor(boolean animated) {
        int target = AppIconPreviewLoader.getAccentTextColor(icon, Theme.key_windowBackgroundWhiteLinkText, resourcesProvider);
        if (linkColor == target) {
            return;
        }
        if (linkColorAnimator != null) {
            linkColorAnimator.cancel();
            linkColorAnimator = null;
        }
        int from = linkColor;
        if (!animated || from == 0) {
            linkColor = target;
            subtitleView.invalidate();
            return;
        }
        linkColorAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(280);
        linkColorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        linkColorAnimator.addUpdateListener(animation -> {
            linkColor = ColorUtils.blendARGB(from, target, (float) animation.getAnimatedValue());
            subtitleView.invalidate();
        });
        linkColorAnimator.start();
    }

    private AnimatedTextView addText(int textSizeDp, Typeface typeface, int heightDp, int topMarginDp) {
        AnimatedTextView textView = new AnimatedTextView(getContext(), true, true, false);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(typeface);
        textView.setTextSize(AndroidUtilities.dp(textSizeDp));
        textView.setIncludeFontPadding(false);
        textView.setAllowCancel(true);
        textView.setAnimationProperties(0.35f, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, heightDp, Gravity.CENTER_HORIZONTAL, 24, topMarginDp, 24, 0));
        return textView;
    }

    public void setOnPreviewReady(Runnable listener) {
        previewReadyListener = listener;
    }

    private void onPreviewReady() {
        updateLinkColor(true);
        if (previewReadyListener != null) {
            previewReadyListener.run();
        }
    }

    public void set(AppIcon icon) {
        if (this.icon == icon) {
            return;
        }
        boolean animated = this.icon != null;
        this.icon = icon;
        previewView.setIcon(icon);
        updateLinkColor(animated);
        titleView.setText(icon.getTitle(), animated);
        CharSequence subtitle = subtitleOf(icon);
        subtitleLinks = subtitle == null ? "" : LocaleUtils.formatWithUsernames(subtitle, fragment);
        subtitleView.setText(colorizeLinks(subtitleLinks), animated);
    }

    private CharSequence colorizeLinks(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return text;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        for (ClickableSpan span : builder.getSpans(0, builder.length(), ClickableSpan.class)) {
            int start = builder.getSpanStart(span);
            int end = builder.getSpanEnd(span);
            builder.removeSpan(span);
            builder.setSpan(new CharacterStyle() {
                @Override
                public void updateDrawState(TextPaint paint) {
                    paint.setColor(Theme.multAlpha(linkColor, paint.getAlpha() / 255f));
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    public boolean dispatchLinkTouch(MotionEvent event) {
        float x = event.getX() - getX() - subtitleView.getX();
        float y = event.getY() - getY() - subtitleView.getY();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedLink = hitLink(x, y);
            return pressedLink != null;
        }
        if (pressedLink == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_UP && pressedLink == hitLink(x, y)) {
            pressedLink.onClick(subtitleView);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pressedLink = null;
        }
        return true;
    }

    private ClickableSpan hitLink(float x, float y) {
        if (subtitleView.getVisibility() != VISIBLE || subtitleView.getAlpha() <= 0 || y < 0 || y > subtitleView.getHeight()) {
            return null;
        }
        if (!(subtitleLinks instanceof Spanned)) {
            return null;
        }
        Spanned spanned = (Spanned) subtitleLinks;
        TextPaint paint = subtitleView.getPaint();
        float offset = (subtitleView.getWidth() - subtitleView.getDrawable().getCurrentWidth()) / 2f;
        for (ClickableSpan span : spanned.getSpans(0, spanned.length(), ClickableSpan.class)) {
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            float left = paint.measureText(spanned, 0, start) + offset - AndroidUtilities.dp(4);
            float right = left + paint.measureText(spanned, start, end) + AndroidUtilities.dp(4) * 2;
            if (x >= left && x <= right) {
                return span;
            }
        }
        return null;
    }

    public void setCollapse(float collapse) {
        if (this.collapse == collapse) {
            return;
        }
        this.collapse = collapse;
        applyCollapse();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        applyCollapse();
    }

    private void applyCollapse() {
        float textProgress = Math.min(1f, collapse / 0.35f);
        float textTranslation = -AndroidUtilities.dp(12) * textProgress;
        titleView.setAlpha(1f - textProgress);
        titleView.setTranslationY(textTranslation);
        subtitleView.setAlpha(1f - textProgress);
        subtitleView.setTranslationY(textTranslation);

        int previewHeight = previewView.getMeasuredHeight();
        if (previewHeight <= 0) {
            return;
        }
        float halfHeight = previewHeight / 2f;
        float previewCenter = previewView.getTop() + halfHeight;
        float actionBarCenter = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() / 2f - getTop();
        float scale = AndroidUtilities.lerp(1f, AndroidUtilities.dp(40) / (float) previewHeight, collapse);
        previewView.setPivotX(previewView.getMeasuredWidth() / 2f);
        previewView.setPivotY(halfHeight);
        previewView.setScaleX(scale);
        previewView.setScaleY(scale);
        previewView.setTranslationY((actionBarCenter - previewCenter) * collapse);
    }

    public int getTextBlockHeight() {
        int height = AndroidUtilities.dp(46);
        return subtitleView.getVisibility() == VISIBLE ? height + AndroidUtilities.dp(26) : height;
    }

    public void setPreviewSizeDp(int sizeDp) {
        if (previewSizeDp == sizeDp) {
            return;
        }
        previewSizeDp = sizeDp;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) previewView.getLayoutParams();
        lp.width = lp.height = AndroidUtilities.dp(sizeDp);
        previewView.requestLayout();
    }

    public int getPreviewSize() {
        return AndroidUtilities.dp(previewSizeDp);
    }
}
