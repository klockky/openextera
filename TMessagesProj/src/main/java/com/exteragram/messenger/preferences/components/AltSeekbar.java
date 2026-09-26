package com.exteragram.messenger.preferences.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.ui.MaterialSliderUiHelper;
import com.google.android.material.slider.Slider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class AltSeekbar extends FrameLayout {

    private final AnimatedTextView headerValue;
    protected final TextView leftTextView;
    protected final TextView rightTextView;
    public SeekBarView seekBarView;
    public Slider slider;

    private final int min;
    private final int max;
    private final OnDrag onDrag;

    protected float currentValue;
    private int roundedValue;
    private int vibro = -1;

    public interface OnDrag {
        void run(float progress);
    }

    public AltSeekbar(Context context, OnDrag onDrag, int min, int max, String title, String left, String right) {
        super(context);
        this.onDrag = onDrag;
        this.max = max;
        this.min = min;

        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        TextView headerTextView = new TextView(context);
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerTextView.setTypeface(AndroidUtilities.bold());
        headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        headerTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        headerTextView.setText(title);
        headerLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        headerValue = new AnimatedTextView(context, false, true, true) {
            final Drawable backgroundDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(4), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.15f));

            @Override
            protected void onDraw(Canvas canvas) {
                backgroundDrawable.setBounds(0, 0, (int) (getPaddingLeft() + getDrawable().getCurrentWidth() + getPaddingRight()), getMeasuredHeight());
                backgroundDrawable.draw(canvas);
                super.onDraw(canvas);
            }
        };
        headerValue.setAnimationProperties(0.45f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
        headerValue.setAllowCancel(true);
        headerValue.setTypeface(AndroidUtilities.bold());
        headerValue.setPadding(AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2), AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2));
        headerValue.setTextSize(AndroidUtilities.dp(12));
        headerValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        headerLayout.addView(headerValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 17, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

        addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 21, 17, 21, 0));

        FrameLayout valuesLayout = new FrameLayout(context);

        leftTextView = new TextView(context);
        leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        leftTextView.setGravity(Gravity.LEFT);
        leftTextView.setText(left);
        valuesLayout.addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        rightTextView = new TextView(context);
        rightTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        rightTextView.setGravity(Gravity.RIGHT);
        rightTextView.setText(right);
        valuesLayout.addView(rightTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        addView(valuesLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 21, 52, 21, 0));

        initSlider();
    }

    public boolean useExactEndpointHaptic() {
        return false;
    }

    private void updateValues() {
        int middle = (max - min) / 2 + min;
        float rightThreshold = middle * 1.5f - min * 0.5f;
        float leftThreshold = (min + middle) * 0.5f;
        int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int blueColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
        if (currentValue >= rightThreshold) {
            rightTextView.setTextColor(ColorUtils.blendARGB(grayColor, blueColor, (currentValue - rightThreshold) / (max - rightThreshold)));
            leftTextView.setTextColor(grayColor);
        } else if (currentValue <= leftThreshold) {
            leftTextView.setTextColor(ColorUtils.blendARGB(grayColor, blueColor, (currentValue - leftThreshold) / (min - leftThreshold)));
            rightTextView.setTextColor(grayColor);
        } else {
            leftTextView.setTextColor(grayColor);
            rightTextView.setTextColor(grayColor);
        }
    }

    public void setProgress(float progress) {
        currentValue = clamp(progress);
        roundedValue = Math.round(currentValue);
        if (slider != null) {
            MaterialSliderUiHelper.setValue(slider, currentValue);
        } else if (seekBarView != null) {
            seekBarView.setProgress((currentValue - min) / (max - min));
        }
        headerValue.cancelAnimation();
        headerValue.setText(getTextForHeader(), true);
        checkEndpointHaptic(currentValue);
        updateValues();
    }

    public void updateHeader(float progress) {
        currentValue = clamp(progress);
        roundedValue = Math.round(currentValue);
        CharSequence text = getTextForHeader();
        if (!TextUtils.equals(headerValue.getText(), text)) {
            headerValue.setText(text, true);
        }
        checkEndpointHaptic(currentValue);
        updateValues();
    }

    private float clamp(float value) {
        return MathUtils.clamp(value, min, max);
    }

    private void checkEndpointHaptic(float value) {
        int endpoint;
        if (useExactEndpointHaptic()) {
            endpoint = value <= min ? min : value >= max ? max : -1;
        } else {
            endpoint = roundedValue == min || roundedValue == max ? roundedValue : -1;
        }
        if (endpoint == -1) {
            vibro = -1;
        } else if (endpoint != vibro) {
            vibro = endpoint;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public CharSequence getTextForHeader() {
        CharSequence text;
        if (roundedValue == min) {
            text = leftTextView.getText();
        } else if (roundedValue == max) {
            text = rightTextView.getText();
        } else {
            text = String.valueOf(roundedValue);
        }
        return text.toString().toUpperCase();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(112), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AltSeekbar)) {
            return false;
        }
        AltSeekbar other = (AltSeekbar) o;
        return Objects.equals(headerValue, other.headerValue)
                && Objects.equals(leftTextView, other.leftTextView)
                && Objects.equals(rightTextView, other.rightTextView)
                && Objects.equals(seekBarView, other.seekBarView)
                && Objects.equals(slider, other.slider)
                && min == other.min
                && max == other.max
                && Float.compare(currentValue, other.currentValue) == 0
                && roundedValue == other.roundedValue
                && vibro == other.vibro;
    }

    private void initSlider() {
        if (ExteraConfig.getNewSliderStyle()) {
            slider = MaterialSliderUiHelper.create(getContext());
            MaterialSliderUiHelper.applyContinuousStyle(slider);
            slider.addOnChangeListener((s, value, fromUser) -> {
                if (fromUser) {
                    onDrag.run(value);
                }
                if (Math.round(value) != roundedValue) {
                    setProgress(value);
                }
            });
            MaterialSliderUiHelper.applyColors(slider, Theme.getColor(Theme.key_player_progress), Theme.getColor(Theme.key_player_progressBackground));
            slider.setValueFrom(min);
            slider.setValueTo(max);
            MaterialSliderUiHelper.setValue(slider, currentValue);
            addView(slider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP, 7, 68, 7, 6));
        } else {
            seekBarView = new SeekBarView(getContext(), true, null);
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate((stop, progress) -> {
                float value = min + (max - min) * progress;
                onDrag.run(value);
                if (Math.round(value) != roundedValue) {
                    setProgress(value);
                }
            });
            addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP, 6, 68, 6, 0));
        }
        setProgress(currentValue);
    }

    public void updateStyle() {
        if (slider != null) {
            removeView(slider);
            slider = null;
        }
        if (seekBarView != null) {
            removeView(seekBarView);
            seekBarView = null;
        }
        initSlider();
    }
}
