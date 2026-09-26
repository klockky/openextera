package com.exteragram.messenger.utils.ui;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.math.MathUtils;

import com.google.android.material.R;
import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

import org.telegram.messenger.AndroidUtilities;

public abstract class MaterialSliderUiHelper {

    // Slider tick visibility modes
    private static final int TICK_VISIBILITY_AUTO_LIMIT = 0;
    private static final int TICK_VISIBILITY_HIDDEN = 2;

    public static Slider create(Context context) {
        Slider slider = new Slider(new ContextThemeWrapper(context, R.style.Theme_Material3_DayNight));
        slider.setTrackHeight(AndroidUtilities.dp(8));
        slider.setThumbHeight(AndroidUtilities.dp(24));
        slider.setThumbWidth(AndroidUtilities.dp(3));
        slider.setTrackStopIndicatorSize(0);
        slider.setHaloRadius(0);
        slider.setLabelBehavior(LabelFormatter.LABEL_GONE);
        return slider;
    }

    public static void applyContinuousStyle(Slider slider) {
        if (slider.getTickVisibilityMode() != TICK_VISIBILITY_HIDDEN) {
            slider.setTickVisibilityMode(TICK_VISIBILITY_HIDDEN);
        }
        if (slider.getContinuousModeTickCount() != 0) {
            slider.setContinuousModeTickCount(0);
        }
    }

    public static void applyDiscreteStyle(Slider slider, int tickCount) {
        if (slider.getTickVisibilityMode() != TICK_VISIBILITY_AUTO_LIMIT) {
            slider.setTickVisibilityMode(TICK_VISIBILITY_AUTO_LIMIT);
        }
        if (slider.getTickActiveRadius() != AndroidUtilities.dp(2)) {
            slider.setTickActiveRadius(AndroidUtilities.dp(2));
        }
        if (slider.getTickInactiveRadius() != AndroidUtilities.dp(2)) {
            slider.setTickInactiveRadius(AndroidUtilities.dp(2));
        }
        if (slider.getContinuousModeTickCount() != tickCount) {
            slider.setContinuousModeTickCount(tickCount);
        }
    }

    public static void setValue(Slider slider, float value) {
        float clamped = MathUtils.clamp(value, slider.getValueFrom(), slider.getValueTo());
        if (Math.abs(slider.getValue() - clamped) > 1e-4f) {
            slider.setValue(clamped);
        }
    }

    public static void applyColors(Slider slider, int activeColor, int inactiveColor) {
        if (!hasColor(slider.getTrackActiveTintList(), activeColor)) {
            slider.setTrackActiveTintList(ColorStateList.valueOf(activeColor));
        }
        if (!hasColor(slider.getThumbTintList(), activeColor)) {
            slider.setThumbTintList(ColorStateList.valueOf(activeColor));
        }
        if (!hasColor(slider.getTrackInactiveTintList(), inactiveColor)) {
            slider.setTrackInactiveTintList(ColorStateList.valueOf(inactiveColor));
        }
    }

    public static void applyDiscreteColors(Slider slider, int activeColor, int inactiveColor, int tickColor) {
        applyColors(slider, activeColor, inactiveColor);
        if (!hasColor(slider.getTickActiveTintList(), tickColor)) {
            slider.setTickActiveTintList(ColorStateList.valueOf(tickColor));
        }
        if (!hasColor(slider.getTickInactiveTintList(), tickColor)) {
            slider.setTickInactiveTintList(ColorStateList.valueOf(tickColor));
        }
    }

    private static boolean hasColor(ColorStateList colorStateList, int color) {
        return colorStateList != null && colorStateList.getDefaultColor() == color;
    }
}
