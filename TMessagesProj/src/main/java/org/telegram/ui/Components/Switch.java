/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.StateSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Keep;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.exteragram.messenger.utils.ui.SwitchUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;

import me.vkryl.android.animator.BoolAnimator;

public class Switch extends View {

    private static final FloatPropertyCompat<Switch> PROGRESS_PROPERTY = new FloatPropertyCompat<Switch>("progress") {
        @Override
        public float getValue(Switch object) {
            return object.getProgress();
        }

        @Override
        public void setValue(Switch object, float value) {
            object.setProgress(value);
        }
    };

    private final BoolAnimator animatorIconVisibility = new BoolAnimator(this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L, true);
    private final BoolAnimator animatorPressed;
    private final float[] iconLines = new float[8];

    private RectF rectF;

    private float progress;
    private ObjectAnimator checkAnimator;
    private SpringAnimation checkSpringAnimator;
    private ObjectAnimator iconAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private Paint paint;
    private Paint paint2;
    private Paint outlinePaint;

    private int drawIconType;
    private float iconProgress = 1.0f;

    private OnCheckedChangeListener onCheckedChangeListener;

    private int trackColorKey = Theme.key_fill_RedNormal;
    private int trackCheckedColorKey = Theme.key_switch2TrackChecked;
    private int thumbColorKey = Theme.key_windowBackgroundWhite;
    private int thumbCheckedColorKey = Theme.key_windowBackgroundWhite;

    private Drawable iconDrawable;
    private int lastIconColor;

    private boolean drawRipple;
    private RippleDrawable rippleDrawable;
    private Paint ripplePaint;
    private int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private int colorSet;

    private boolean bitmapsCreated;
    private Bitmap[] overlayBitmap;
    private Canvas[] overlayCanvas;
    private Bitmap overlayMaskBitmap;
    private Canvas overlayMaskCanvas;
    private float overlayCx;
    private float overlayCy;
    private float overlayRad;
    private Paint overlayEraserPaint;
    private Paint overlayMaskPaint;

    private Theme.ResourcesProvider resourcesProvider;

    private int overrideColorProgress;
    private float overrideAlpha = 1.0f;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(Switch view, boolean isChecked);
    }

    public Switch(Context context) {
        this(context, null);
    }

    public Switch(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        animatorPressed = SwitchUiHelper.createThumbPressedAnimator(this);
        rectF = new RectF();

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setStrokeCap(Paint.Cap.ROUND);
        paint2.setStrokeWidth(AndroidUtilities.dp(2));
        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setStyle(Paint.Style.STROKE);

        setHapticFeedbackEnabled(true);
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    @Keep
    public void setIconProgress(float value) {
        if (iconProgress == value) {
            return;
        }
        iconProgress = value;
        invalidate();
    }

    @Keep
    public float getIconProgress() {
        return iconProgress;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
            checkAnimator = null;
        }
        if (checkSpringAnimator != null) {
            checkSpringAnimator.cancel();
            checkSpringAnimator = null;
        }
    }

    private void cancelIconAnimator() {
        if (iconAnimator != null) {
            iconAnimator.cancel();
            iconAnimator = null;
        }
    }

    public void setDrawIconType(int type) {
        drawIconType = type;
    }

    public void setDrawRipple(boolean value) {
        if (value == drawRipple) {
            return;
        }
        drawRipple = value;

        if (rippleDrawable == null) {
            ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ripplePaint.setColor(0xffffffff);
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{0}
            );
            rippleDrawable = new BaseCell.RippleDrawableSafe(colorStateList, null, null);
            rippleDrawable.setCallback(this);
        }
        rippleDrawable.setRadius(SwitchUiHelper.getStateLayerRadius());
        if (isChecked && colorSet != 2 || !isChecked && colorSet != 1) {
            int color = Theme.getColor(isChecked ? Theme.key_switchTrackBlueSelectorChecked : Theme.key_switchTrackBlueSelector, resourcesProvider);
            color = processColor(color);
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
            );
            rippleDrawable.setColor(colorStateList);
            colorSet = isChecked ? 2 : 1;
        }
        if (Build.VERSION.SDK_INT >= 28 && value) {
            rippleDrawable.setHotspot(isChecked ? 0 : AndroidUtilities.dp(100), AndroidUtilities.dp(18));
        }
        rippleDrawable.setState(value ? pressedState : StateSet.NOTHING);
        animatorPressed.setValue(value, attachedToWindow);
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || rippleDrawable != null && who == rippleDrawable;
    }

    protected int processColor(int color) {
        return color;
    }

    public void setColors(int track, int trackChecked, int thumb, int thumbChecked) {
        trackColorKey = track;
        trackCheckedColorKey = trackChecked;
        thumbColorKey = thumb;
        thumbCheckedColorKey = thumbChecked;
    }

    private void animateToCheckedState(boolean newCheckedState) {
        if (SwitchUiHelper.isMaterial3SwitchStyle()) {
            if (checkAnimator != null) {
                checkAnimator.cancel();
                checkAnimator = null;
            }
            if (checkSpringAnimator == null) {
                checkSpringAnimator = SwitchUiHelper.createThumbSpring(this, PROGRESS_PROPERTY);
            }
            checkSpringAnimator.animateToFinalPosition(newCheckedState ? 1f : 0f);
            return;
        }
        cancelCheckAnimator();
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(200);
        checkAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        checkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkAnimator = null;
            }
        });
        checkAnimator.start();
    }

    private void animateIcon(boolean newCheckedState) {
        iconAnimator = ObjectAnimator.ofFloat(this, "iconProgress", newCheckedState ? 1 : 0);
        iconAnimator.setDuration(200);
        iconAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                iconAnimator = null;
            }
        });
        iconAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        destroyBitmaps();
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        onCheckedChangeListener = listener;
    }

    public void setChecked(boolean checked, boolean animated) {
        setChecked(checked, drawIconType, animated);
    }

    public void setChecked(boolean checked, int iconType, boolean animated) {
        if (checked != isChecked) {
            isChecked = checked;
            if (attachedToWindow && animated) {
                animateToCheckedState(checked);
            } else {
                cancelCheckAnimator();
                setProgress(checked ? 1.0f : 0.0f);
            }
            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(this, checked);
            }
        }
        setDrawIconType(iconType, animated);
    }

    public void setIcon(int icon) {
        if (icon != 0) {
            iconDrawable = getResources().getDrawable(icon).mutate();
            if (iconDrawable != null) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = Theme.getColor(isChecked ? trackCheckedColorKey : trackColorKey, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            }
        } else {
            iconDrawable = null;
        }
        invalidate();
    }

    public void setIconVisible(boolean visible, boolean animated) {
        animatorIconVisibility.setValue(visible, animated);
    }

    public void setDrawIconType(int iconType, boolean animated) {
        if (drawIconType != iconType) {
            drawIconType = iconType;
            if (attachedToWindow && animated) {
                animateIcon(iconType == 0);
            } else {
                cancelIconAnimator();
                setIconProgress(iconType == 0 ? 1.0f : 0.0f);
            }
        }
    }

    public boolean hasIcon() {
        return iconDrawable != null;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setOverrideColor(int override) {
        if (overrideColorProgress == override) {
            return;
        }
        overrideColorProgress = override;
        overlayCx = 0;
        overlayCy = 0;
        overlayRad = 0;
        invalidate();
    }

    public void setOverrideColorProgress(float cx, float cy, float rad) {
        overlayCx = cx;
        overlayCy = cy;
        overlayRad = rad;
        invalidate();
    }

    private void checkBitmaps() {
        if (overrideColorProgress == 0) {
            return;
        }
        final int padding = SwitchUiHelper.getOverlayPadding() * 4;
        final int width = getMeasuredWidth() + padding;
        final int height = getMeasuredHeight() + padding;
        if (bitmapsCreated && overlayBitmap != null && overlayBitmap[0] != null && (overlayBitmap[0].getWidth() != width || overlayBitmap[0].getHeight() != height)) {
            destroyBitmaps();
        }
        if (bitmapsCreated || width <= 0 || height <= 0) {
            return;
        }
        try {
            overlayBitmap = new Bitmap[2];
            overlayCanvas = new Canvas[2];
            for (int a = 0; a < 2; a++) {
                overlayBitmap[a] = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                overlayCanvas[a] = new Canvas(overlayBitmap[a]);
            }
            overlayMaskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            overlayMaskCanvas = new Canvas(overlayMaskBitmap);

            overlayEraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overlayEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            overlayMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overlayMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            bitmapsCreated = true;
        } catch (Throwable ignore) {}
    }

    private void destroyBitmaps() {
        if (bitmapsCreated) {
            if (overlayBitmap != null) {
                for (int a = 0; a < overlayBitmap.length; a++) {
                    if (overlayBitmap[a] != null) {
                        overlayBitmap[a].recycle();
                        overlayBitmap[a] = null;
                    }
                }
                overlayBitmap = null;
            }
            if (overlayMaskBitmap != null) {
                overlayMaskBitmap.recycle();
                overlayMaskBitmap = null;
            }
        }
        overlayCanvas = null;
        overlayMaskCanvas = null;
        bitmapsCreated = false;
    }

    private static int blendColors(int color1, int color2, float progress) {
        final int red = (int) (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * progress);
        final int green = (int) (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * progress);
        final int blue = (int) (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * progress);
        final int alpha = (int) (Color.alpha(color1) + (Color.alpha(color2) - Color.alpha(color1)) * progress);
        return ((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
    }

    private float getColorProgress(int a) {
        if (overrideColorProgress == 1) {
            return a == 0 ? 0 : 1;
        } else if (overrideColorProgress == 2) {
            return a == 0 ? 1 : 0;
        }
        return Utilities.clamp01(progress);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }
        if (overrideColorProgress != 0) {
            checkBitmaps();
            if (!bitmapsCreated) {
                overrideColorProgress = 0;
            }
        }

        final boolean material3 = SwitchUiHelper.isMaterial3SwitchStyle();
        final int overlayPadding = SwitchUiHelper.getOverlayPadding();
        final int stateLayerRadius = SwitchUiHelper.getStateLayerRadius();
        final boolean useAlphaLayer = material3 && overrideAlpha < 1f;
        if (useAlphaLayer) {
            canvas.saveLayerAlpha(-stateLayerRadius, -stateLayerRadius, getMeasuredWidth() + stateLayerRadius, getMeasuredHeight() + stateLayerRadius, (int) (overrideAlpha * 255));
        }

        final int width = AndroidUtilities.dp(31);
        final int x;
        final float y;
        final float thumbX;
        final float thumbY;
        if (material3) {
            x = 0;
            y = 0;
            thumbX = SwitchUiHelper.getThumbCenterX(getMeasuredWidth(), progress);
            thumbY = getMeasuredHeight() / 2f;
        } else {
            x = (getMeasuredWidth() - width) / 2;
            y = (getMeasuredHeight() - AndroidUtilities.dpf2(14)) / 2;
            thumbX = x + AndroidUtilities.dp(8) + (int) (AndroidUtilities.dp(16) * progress);
            thumbY = getMeasuredHeight() / 2;
        }
        final int tx = (int) thumbX;
        final int ty = (int) thumbY;

        final int trackColor = processColor(SwitchUiHelper.getUnselectedTrackColor(trackColorKey, resourcesProvider));
        final int trackCheckedColor = processColor(Theme.getColor(trackCheckedColorKey, resourcesProvider));
        final int thumbColor = processColor(SwitchUiHelper.getUnselectedThumbColor(trackColorKey, thumbColorKey, resourcesProvider));
        final int thumbCheckedColor = processColor(SwitchUiHelper.getSelectedThumbColor(thumbCheckedColorKey, resourcesProvider));

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            final Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[0];
            final int offset = a == 0 ? 0 : overlayPadding;

            if (a == 1) {
                overlayBitmap[0].eraseColor(0);
                paint.setColor(0xff000000);
                overlayMaskCanvas.drawRect(overlayPadding, overlayPadding, overlayMaskBitmap.getWidth() - overlayPadding, overlayMaskBitmap.getHeight() - overlayPadding, paint);
                overlayMaskCanvas.drawCircle(overlayCx - getX() + overlayPadding, overlayCy - getY() + overlayPadding, overlayRad, overlayEraserPaint);
            }
            final float colorProgress = getColorProgress(a);

            if (a == 0 && iconDrawable != null && lastIconColor != (isChecked ? trackCheckedColor : trackColor)) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = (isChecked ? trackCheckedColor : trackColor), PorterDuff.Mode.MULTIPLY));
            }

            final int color = blendColors(trackColor, trackCheckedColor, colorProgress);
            paint.setColor(color);
            paint2.setColor(color);

            if (material3) {
                SwitchUiHelper.setTrackBounds(rectF, getMeasuredWidth(), getMeasuredHeight());
                rectF.offset(offset, offset);
                final float radius = rectF.height() / 2f;
                canvasToDraw.drawRoundRect(rectF, radius, radius, paint);
                final float outlineAlpha = 1f - colorProgress;
                if (outlineAlpha > 0) {
                    final float outlineWidth = SwitchUiHelper.getTrackOutlineWidth();
                    outlinePaint.setColor(Theme.multAlpha(thumbColor, outlineAlpha));
                    outlinePaint.setStrokeWidth(outlineWidth);
                    rectF.inset(outlineWidth / 2f, outlineWidth / 2f);
                    canvasToDraw.drawRoundRect(rectF, radius - outlineWidth / 2f, radius - outlineWidth / 2f, outlinePaint);
                }
            } else {
                rectF.set(x, y, x + width, y + AndroidUtilities.dpf2(14));
                canvasToDraw.drawRoundRect(rectF, AndroidUtilities.dpf2(7), AndroidUtilities.dpf2(7), paint);
                canvasToDraw.drawCircle(tx + offset, ty + offset, AndroidUtilities.dpf2(10), paint);
            }

            if (a == 0 && rippleDrawable != null) {
                rippleDrawable.setBounds(tx - stateLayerRadius, ty - stateLayerRadius, tx + stateLayerRadius, ty + stateLayerRadius);
                rippleDrawable.draw(canvasToDraw);
            } else if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, -overlayPadding, -overlayPadding, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[0], -overlayPadding, -overlayPadding, null);
        }

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            final Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[1];
            final int offset = a == 0 ? 0 : overlayPadding;

            if (a == 1) {
                overlayBitmap[1].eraseColor(0);
            }
            paint.setColor(blendColors(thumbColor, thumbCheckedColor, getColorProgress(a)));

            if (material3) {
                final float iconVisibility;
                if (drawIconType == 1 || drawIconType == 2 || iconAnimator != null) {
                    iconVisibility = 1f;
                } else if (iconDrawable != null) {
                    iconVisibility = animatorIconVisibility.getFloatValue();
                } else {
                    iconVisibility = 0f;
                }
                SwitchUiHelper.setThumbBounds(rectF, thumbX + offset, thumbY + offset, progress, isChecked, iconVisibility, animatorPressed.getFloatValue());
                final float radius = rectF.height() / 2f;
                canvasToDraw.drawRoundRect(rectF, radius, radius, paint);
            } else {
                canvasToDraw.drawCircle(tx + offset, ty + offset, AndroidUtilities.dp(8), paint);
            }

            if (a == 0) {
                if (iconDrawable != null) {
                    float factor = animatorIconVisibility.getFloatValue();
                    if (factor > 0) {
                        final int iconWidth = iconDrawable.getIntrinsicWidth();
                        final int iconHeight = iconDrawable.getIntrinsicHeight();
                        if (material3) {
                            factor *= SwitchUiHelper.getThumbIconScale(iconWidth, iconHeight);
                        }
                        canvas.save();
                        canvas.translate(thumbX - iconWidth / 2f, thumbY - iconHeight / 2f);
                        canvas.scale(factor, factor, iconWidth / 2f, iconHeight / 2f);
                        iconDrawable.setBounds(0, 0, iconWidth, iconHeight);
                        iconDrawable.draw(canvas);
                        canvas.restore();
                    }
                } else if (drawIconType == 1 && material3) {
                    paint2.setStrokeWidth(SwitchUiHelper.getIconStrokeWidth());
                    SwitchUiHelper.setCheckIconLines(iconLines, thumbX, thumbY, progress);
                    canvasToDraw.drawLines(iconLines, paint2);
                } else if (drawIconType == 1) {
                    paint2.setStrokeWidth(AndroidUtilities.dp(2));
                    final int iconX = (int) (tx - (AndroidUtilities.dp(10.8f) - AndroidUtilities.dp(1.3f) * progress));
                    final int iconY = (int) (ty - (AndroidUtilities.dp(8.5f) - AndroidUtilities.dp(0.5f) * progress));
                    int startX2 = (int) AndroidUtilities.dpf2(4.6f) + iconX;
                    int startY2 = (int) (AndroidUtilities.dpf2(9.5f) + iconY);
                    int endX2 = startX2 + AndroidUtilities.dp(2);
                    int endY2 = startY2 + AndroidUtilities.dp(2);

                    int startX = (int) AndroidUtilities.dpf2(7.5f) + iconX;
                    int startY = (int) AndroidUtilities.dpf2(5.4f) + iconY;
                    int endX = startX + AndroidUtilities.dp(7);
                    int endY = startY + AndroidUtilities.dp(7);

                    startX = (int) (startX + (startX2 - startX) * progress);
                    startY = (int) (startY + (startY2 - startY) * progress);
                    endX = (int) (endX + (endX2 - endX) * progress);
                    endY = (int) (endY + (endY2 - endY) * progress);
                    canvasToDraw.drawLine(startX, startY, endX, endY, paint2);

                    startX = (int) AndroidUtilities.dpf2(7.5f) + iconX;
                    startY = (int) AndroidUtilities.dpf2(12.5f) + iconY;
                    endX = startX + AndroidUtilities.dp(7);
                    endY = startY - AndroidUtilities.dp(7);
                    canvasToDraw.drawLine(startX, startY, endX, endY, paint2);
                } else if (drawIconType == 2 || iconAnimator != null) {
                    paint2.setAlpha((int) (255 * (1.0f - iconProgress)));
                    paint2.setStrokeWidth(material3 ? SwitchUiHelper.getIconStrokeWidth() : AndroidUtilities.dp(2));
                    final float hourHand = material3 ? SwitchUiHelper.getClockHandLength(true) : AndroidUtilities.dp(5);
                    canvasToDraw.drawLine(thumbX, thumbY, thumbX, thumbY - hourHand, paint2);
                    canvasToDraw.save();
                    canvasToDraw.rotate(-90 * iconProgress, thumbX, thumbY);
                    final float minuteHand = material3 ? SwitchUiHelper.getClockHandLength(false) : AndroidUtilities.dp(4);
                    canvasToDraw.drawLine(thumbX, thumbY, thumbX + minuteHand, thumbY, paint2);
                    canvasToDraw.restore();
                }
            }
            if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, -overlayPadding, -overlayPadding, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[1], -overlayPadding, -overlayPadding, null);
        }
        if (useAlphaLayer) {
            canvas.restore();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(isChecked);
    }

    @Override
    public void setAlpha(float alpha) {
        if (SwitchUiHelper.isMaterial3SwitchStyle()) {
            overrideAlpha = alpha;
            super.setAlpha(1.0f);
            invalidate();
        } else {
            overrideAlpha = 1.0f;
            super.setAlpha(alpha);
        }
    }
}
