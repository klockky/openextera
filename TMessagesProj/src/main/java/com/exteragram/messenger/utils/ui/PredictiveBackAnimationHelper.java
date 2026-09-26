package com.exteragram.messenger.utils.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ViewPagerActivity;

import java.util.List;

public final class PredictiveBackAnimationHelper {

    private static final float TARGET_SCALE = 0.85f;
    private static final int POST_COMMIT_DURATION = 375;

    private final RectF startClosingRect = new RectF();
    private final RectF targetClosingRect = new RectF();
    private final RectF currentClosingRect = new RectF();
    private final RectF startEnteringRect = new RectF();
    private final RectF targetEnteringRect = new RectF();
    private final RectF currentEnteringRect = new RectF();
    private final RectF commitStartClosingRect = new RectF();
    private final RectF commitTargetClosingRect = new RectF();
    private final RectF commitStartEnteringRect = new RectF();
    private final RectF commitTargetEnteringRect = new RectF();

    private final Interpolator gestureInterpolator = new PathInterpolator(0.1f, 0.1f, 0f, 1f);
    private final Interpolator postCommitInterpolator = createEmphasizedInterpolator();
    private final Interpolator verticalMoveInterpolator = new DecelerateInterpolator();

    private float progress;
    private float interpolatedProgress;
    private float initialTouchY;
    private float closingAlpha = 1f;
    private float scrimAlphaMultiplier = 1f;
    private float startCornerRadius;
    private float targetCornerRadius;
    private float currentCornerRadius;

    public int getPostCommitDuration() {
        return POST_COMMIT_DURATION;
    }

    public static Drawable getTransitionBackground(List<BaseFragment> fragmentsStack, BaseFragment fragment) {
        if (fragmentsStack != null && fragmentsStack.size() > 1) {
            fragment = fragmentsStack.get(fragmentsStack.size() - 2);
        }
        while (fragment instanceof ViewPagerActivity) {
            BaseFragment visibleFragment = ((ViewPagerActivity) fragment).getCurrentVisibleFragment();
            if (visibleFragment == null || visibleFragment == fragment) {
                break;
            }
            fragment = visibleFragment;
        }
        if (fragment instanceof ProfileActivity) {
            return new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, fragment.getResourceProvider()));
        }
        View fragmentView = fragment != null ? fragment.fragmentView : null;
        Drawable background = fragmentView != null ? fragmentView.getBackground() : null;
        if (background instanceof ColorDrawable && Color.alpha(((ColorDrawable) background).getColor()) == 0) {
            background = null;
        }
        if (background != null) {
            return background;
        }
        return new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, fragment != null ? fragment.getResourceProvider() : null));
    }

    public static void drawTransitionBackground(Canvas canvas, Drawable drawable, int width, int height, Rect tmpRect) {
        if (drawable == null) {
            return;
        }
        drawable.copyBounds(tmpRect);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        drawable.setBounds(tmpRect);
    }

    public void start(int width, int height, float touchY, boolean alignRight, float cornerRadius) {
        initialTouchY = touchY;
        progress = 0f;
        interpolatedProgress = 0f;
        closingAlpha = 1f;
        scrimAlphaMultiplier = 1f;
        startCornerRadius = cornerRadius;
        targetCornerRadius = Math.max(cornerRadius, AndroidUtilities.dp(40));
        currentCornerRadius = cornerRadius;

        startClosingRect.set(0, 0, Math.max(1, width), Math.max(1, height));
        targetClosingRect.set(startClosingRect);
        scaleCentered(targetClosingRect, TARGET_SCALE);
        if (alignRight) {
            targetClosingRect.offset(startClosingRect.right - targetClosingRect.right - AndroidUtilities.dp(8), 0);
        }
        currentClosingRect.set(startClosingRect);

        startEnteringRect.set(startClosingRect);
        scaleCentered(startEnteringRect, Utilities.clamp((startClosingRect.height() - cornerRadius * 2f) / startClosingRect.height(), 0.95f, TARGET_SCALE));
        startEnteringRect.offset(-Math.max(startEnteringRect.width() * (1f - TARGET_SCALE), AndroidUtilities.dp(96)), 0);
        targetEnteringRect.set(startEnteringRect);
        scaleCentered(targetEnteringRect, TARGET_SCALE);
        currentEnteringRect.set(startEnteringRect);
    }

    public void update(float progress, float touchY) {
        this.progress = Utilities.clamp01(progress);
        interpolatedProgress = gestureInterpolator.getInterpolation(this.progress);
        closingAlpha = 1f;
        scrimAlphaMultiplier = 1f;
        interpolate(currentClosingRect, startClosingRect, targetClosingRect, interpolatedProgress);
        float yOffset = getYOffset(currentClosingRect.height(), touchY);
        currentClosingRect.offset(0, yOffset);
        interpolate(currentEnteringRect, startEnteringRect, targetEnteringRect, interpolatedProgress);
        currentEnteringRect.offset(0, yOffset);
        currentCornerRadius = AndroidUtilities.lerp(startCornerRadius, targetCornerRadius, interpolatedProgress);
    }

    private float getYOffset(float currentHeight, float touchY) {
        float height = startClosingRect.height();
        float deltaY = touchY - initialTouchY;
        float maxDelta = height / 2f;
        float direction = deltaY < 0 ? -1f : 1f;
        float fraction = verticalMoveInterpolator.getInterpolation(Math.min(maxDelta, Math.abs(deltaY)) / maxDelta);
        return direction * fraction * Math.max(0f, (height - currentHeight) / 2f - AndroidUtilities.dp(8));
    }

    public void getClosingRect(RectF rect) {
        rect.set(currentClosingRect);
    }

    public void getEnteringRect(RectF rect) {
        rect.set(currentEnteringRect);
    }

    public float getClosingScale() {
        return currentClosingRect.width() / startClosingRect.width();
    }

    public float getEnteringScale() {
        return currentEnteringRect.width() / startClosingRect.width();
    }

    public float getClosingAlpha() {
        return closingAlpha;
    }

    public int getScrimAlpha(boolean dark) {
        return (int) ((dark ? 0.8f : 0.2f) * 255f * scrimAlphaMultiplier);
    }

    public void setScrimAlphaMultiplier(float multiplier) {
        scrimAlphaMultiplier = Utilities.clamp01(multiplier);
    }

    public float getCornerRadius() {
        return currentCornerRadius;
    }

    public float getProgress() {
        return progress;
    }

    public float getSlideDistance() {
        return interpolatedProgress * AndroidUtilities.dp(336);
    }

    public void prepareCommit() {
        commitStartClosingRect.set(currentClosingRect);
        commitStartEnteringRect.set(currentEnteringRect);
        commitTargetEnteringRect.set(startClosingRect);
        commitTargetClosingRect.set(startClosingRect);
        commitTargetClosingRect.offset(currentClosingRect.left + AndroidUtilities.dp(96), 0);
    }

    public void updateCommitProgress(float progress) {
        float clamped = Utilities.clamp01(progress);
        float interpolated = postCommitInterpolator.getInterpolation(clamped);
        closingAlpha = Math.max(1f - 5f * clamped, 0f);
        scrimAlphaMultiplier = 1f - clamped;
        interpolate(currentClosingRect, commitStartClosingRect, commitTargetClosingRect, interpolated);
        interpolate(currentEnteringRect, commitStartEnteringRect, commitTargetEnteringRect, interpolated);
        currentCornerRadius = AndroidUtilities.lerp(targetCornerRadius, startCornerRadius, interpolated);
    }

    public void reset() {
        startClosingRect.setEmpty();
        targetClosingRect.setEmpty();
        currentClosingRect.setEmpty();
        startEnteringRect.setEmpty();
        targetEnteringRect.setEmpty();
        currentEnteringRect.setEmpty();
        commitStartClosingRect.setEmpty();
        commitTargetClosingRect.setEmpty();
        commitStartEnteringRect.setEmpty();
        commitTargetEnteringRect.setEmpty();
        progress = 0f;
        interpolatedProgress = 0f;
        closingAlpha = 1f;
        scrimAlphaMultiplier = 1f;
        initialTouchY = 0f;
        startCornerRadius = 0f;
        targetCornerRadius = 0f;
        currentCornerRadius = 0f;
    }

    private static void interpolate(RectF out, RectF from, RectF to, float t) {
        out.set(
                AndroidUtilities.lerp(from.left, to.left, t),
                AndroidUtilities.lerp(from.top, to.top, t),
                AndroidUtilities.lerp(from.right, to.right, t),
                AndroidUtilities.lerp(from.bottom, to.bottom, t)
        );
    }

    private static void scaleCentered(RectF rect, float scale) {
        float centerX = rect.centerX();
        float centerY = rect.centerY();
        float halfWidth = rect.width() * scale / 2f;
        float halfHeight = rect.height() * scale / 2f;
        rect.set(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
    }

    private static Interpolator createEmphasizedInterpolator() {
        Path path = new Path();
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
    }
}
