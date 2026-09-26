package com.exteragram.messenger.utils.ui;

import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

import me.vkryl.android.animator.BoolAnimator;

public abstract class SwitchUiHelper {

    // Material 3 icon paths in a 24x24 grid: two line segments each (x1, y1, x2, y2)
    private static final float[] MATERIAL3_ICON_CROSS = {6f, 6f, 18f, 18f, 6f, 18f, 18f, 6f};
    private static final float[] MATERIAL3_ICON_CHECK = {4.5f, 13.1f, 9f, 17.6f, 9f, 17.6f, 19.8f, 6.8f};
    private static final Interpolator MATERIAL3_THUMB_INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private static final double[] colorLab = new double[3];

    public static boolean isMaterial3SwitchStyle() {
        return ExteraConfig.getNewSwitchStyle();
    }

    private static float scaled(float value) {
        return AndroidUtilities.dpf2(value * 0.8125f);
    }

    public static int getOverlayPadding() {
        if (isMaterial3SwitchStyle()) {
            return AndroidUtilities.dp(5);
        }
        return 0;
    }

    public static int getStateLayerRadius() {
        return isMaterial3SwitchStyle() ? (int) scaled(20f) : AndroidUtilities.dp(18);
    }

    public static <K> SpringAnimation createThumbSpring(K object, FloatPropertyCompat<K> property) {
        return new SpringAnimation(object, property)
                .setMinimumVisibleChange(0.001f)
                .setSpring(new SpringForce().setDampingRatio(0.65f).setStiffness(510f));
    }

    public static BoolAnimator createThumbPressedAnimator(View view) {
        return new BoolAnimator(view, CubicBezierInterpolator.Standard, 100L);
    }

    public static void setTrackBounds(RectF rect, int width, int height) {
        float halfWidth = scaled(26f);
        float halfHeight = scaled(16f);
        float centerX = width / 2f;
        float centerY = height / 2f;
        rect.set(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
    }

    public static float getTrackOutlineWidth() {
        return scaled(2f);
    }

    public static int getUnselectedTrackColor(int colorKey, Theme.ResourcesProvider resourcesProvider) {
        if (!isMaterial3SwitchStyle()) {
            return Theme.getColor(colorKey, resourcesProvider);
        }
        return trackAtTone(colorKey, resourcesProvider, 90f, 22f);
    }

    public static int getUnselectedThumbColor(int trackColorKey, int thumbColorKey, Theme.ResourcesProvider resourcesProvider) {
        if (!isMaterial3SwitchStyle()) {
            return Theme.getColor(thumbColorKey, resourcesProvider);
        }
        int thumbColor = trackAtTone(trackColorKey, resourcesProvider, 50f, 60f);
        if (Theme.isCurrentThemeMonet(resourcesProvider)) {
            return thumbColor;
        }
        return ColorUtils.blendARGB(trackAtTone(trackColorKey, resourcesProvider, 90f, 22f), thumbColor, isLightSurface(resourcesProvider) ? 0.55f : 0.8f);
    }

    public static int getSelectedThumbColor(int colorKey, Theme.ResourcesProvider resourcesProvider) {
        if (isMaterial3SwitchStyle() && Build.VERSION.SDK_INT >= 31 && Theme.isCurrentThemeMonet(resourcesProvider)) {
            return Theme.getColor(Theme.key_switchTrackBlueThumbChecked, resourcesProvider);
        }
        return Theme.getColor(colorKey, resourcesProvider);
    }

    private static boolean isLightSurface(Theme.ResourcesProvider resourcesProvider) {
        ColorUtils.colorToLAB(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), colorLab);
        return colorLab[0] > 50.0;
    }

    private static int trackAtTone(int colorKey, Theme.ResourcesProvider resourcesProvider, float lightTone, float darkTone) {
        float tone = isLightSurface(resourcesProvider) ? lightTone : darkTone;
        ColorUtils.colorToLAB(Theme.getColor(colorKey, resourcesProvider), colorLab);
        double chroma = Math.hypot(colorLab[1], colorLab[2]);
        if (chroma > 32.0) {
            double factor = 32.0 / chroma;
            colorLab[1] *= factor;
            colorLab[2] *= factor;
        }
        return ColorUtils.LABToColor(tone, colorLab[1], colorLab[2]);
    }

    public static float getThumbCenterX(int width, float progress) {
        return width / 2f + (progress * 2f - 1f) * scaled(10f);
    }

    public static void setThumbBounds(RectF rect, float centerX, float centerY, float progress, boolean checking, float iconProgress, float pressedProgress) {
        float edgeRadius = AndroidUtilities.lerp(8f, 12f, iconProgress);
        float t = (float) (Math.acos(1f - Utilities.clamp01(progress) * 2f) / Math.PI);
        if (!checking) {
            t = 1f - t;
        }
        float split = checking ? 0.6f : 0.4f;
        float fromRadius = checking ? edgeRadius : 12f;
        float toRadius = checking ? 12f : edgeRadius;
        float radius;
        float stretch;
        if (t < split) {
            stretch = MATERIAL3_THUMB_INTERPOLATOR.getInterpolation(t / split);
            radius = AndroidUtilities.lerp(fromRadius, 11f, stretch);
        } else {
            float value = MATERIAL3_THUMB_INTERPOLATOR.getInterpolation((t - split) / (1f - split));
            radius = AndroidUtilities.lerp(11f, toRadius, value);
            stretch = 1f - value;
        }
        float extraWidth = stretch * 5f;
        if (pressedProgress > 0f) {
            radius = AndroidUtilities.lerp(radius, 14f, pressedProgress);
            extraWidth *= 1f - pressedProgress;
        }
        float scaledRadius = scaled(radius);
        float scaledExtraWidth = scaled(extraWidth);
        rect.set(centerX - scaledExtraWidth - scaledRadius, centerY - scaledRadius, centerX + scaledExtraWidth + scaledRadius, centerY + scaledRadius);
    }

    public static float getThumbIconScale(int width, int height) {
        return scaled(13.333333f) / Math.max(width, height);
    }

    private static float iconGrid(float value) {
        return scaled(value * 16f / 24f);
    }

    public static float getIconStrokeWidth() {
        return iconGrid(2f);
    }

    public static void setCheckIconLines(float[] lines, float centerX, float centerY, float progress) {
        float clamped = Utilities.clamp01(progress);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = iconGrid(AndroidUtilities.lerp(MATERIAL3_ICON_CROSS[i], MATERIAL3_ICON_CHECK[i], clamped) - 12f) + (i % 2 == 0 ? centerX : centerY);
        }
    }

    public static float getClockHandLength(boolean hour) {
        return iconGrid(hour ? 8.5f : 6.8f);
    }
}
