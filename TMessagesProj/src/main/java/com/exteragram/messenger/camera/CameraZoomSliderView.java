package com.exteragram.messenger.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.Arrays;
import java.util.Locale;

public abstract class CameraZoomSliderView extends View {

    private static final float CONTROL_HEIGHT = 48;
    private static final float TOUCH_AREA_HEIGHT = 64;
    private static final float TOGGLE_SIZE = 48;
    private static final float SELECTOR_SIZE = 44;
    private static final float SELECTOR_INSET = 2;
    private static final float CORNER_RADIUS = 24;
    private static final float HORIZONTAL_MARGIN = 8;
    private static final float EXPANDED_BACKGROUND_WIDTH = 288;
    private static final float EXPANDED_RULER_WIDTH = 258;
    private static final float TICK_SPACING = 8;
    private static final int TICKS_PER_OCTAVE = 5;
    private static final float MAX_STICKINESS = 0.7f;
    private static final float STICKY_VELOCITY_MIN = 100f;
    private static final float STICKY_VELOCITY_MAX = 1000f;
    private static final long AUTO_COLLAPSE_TIMEOUT = 1500L;
    private static final long MORPH_DURATION = 217L;
    private static final long MAX_ZOOM_ANIMATION_DURATION = 500L;
    private static final int NO_SEGMENT = Integer.MIN_VALUE;

    private static final double LOG_2 = Math.log(2.0d);
    private static final TimeInterpolator ZOOM_INTERPOLATOR = new ZoomLookupInterpolator();
    private static final TimeInterpolator MORPH_INTERPOLATOR = new CubicBezierInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    private static final PorterDuffXfermode XOR_XFERMODE = new PorterDuffXfermode(PorterDuff.Mode.XOR);
    private static final PorterDuffXfermode DST_OVER_XFERMODE = new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);
    private static final PorterDuffXfermode DST_IN_XFERMODE = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);

    private static final FloatPropertyCompat<CameraZoomSliderView> CONTROL_WIDTH = new FloatPropertyCompat<CameraZoomSliderView>("controlWidth") {
        @Override
        public float getValue(CameraZoomSliderView view) {
            return view.animatedControlWidth;
        }

        @Override
        public void setValue(CameraZoomSliderView view, float value) {
            view.animatedControlWidth = Math.max(0.0f, value);
            view.invalidate();
        }
    };

    private static final FloatPropertyCompat<CameraZoomSliderView> SELECTOR_OFFSET = new FloatPropertyCompat<CameraZoomSliderView>("selectorOffset") {
        @Override
        public float getValue(CameraZoomSliderView view) {
            return view.animatedSelectorOffset;
        }

        @Override
        public void setValue(CameraZoomSliderView view, float value) {
            view.animatedSelectorOffset = value;
            view.invalidate();
        }
    };

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toggleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint selectedToggleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulerLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint edgeFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private final RectF controlBounds = new RectF();
    private final RectF compactBounds = new RectF();
    private final RectF rulerBounds = new RectF();
    private final RectF compactTouchBounds = new RectF();
    private final RectF rulerTouchBounds = new RectF();
    private final RectF selectorBounds = new RectF();
    private final RectF bubbleBounds = new RectF();
    private final SparseArray<String> primaryLabels = new SparseArray<>();

    private float minZoom = 0.5f;
    private float maxZoom = 30.0f;
    private float zoom = 1.0f;
    private float[] toggleStops = {0.5f, 1.0f, 2.0f, 5.0f};
    private float[] rulerStops = {0.5f, 1.0f, 2.0f, 5.0f, 10.0f, 30.0f};
    private int[] rebuiltPrimaryTickIndices = new int[0];
    private int intervalCount;
    private int oneXTick = -1;

    private int protectionBackgroundColor = 0x99000000;
    private int primaryColor = 0xFFA8C7FA;
    private int minorTickColor = 0xFFE3E3E3;
    private int secondaryFixedColor = 0xFFD7E3F9;
    private int onSecondaryFixedColor = 0xFF283345;
    private int unselectedToggleColor = 0xFFFFFFFF;
    private float displayNormalizationFactor = 1.0f;

    private float animatedControlWidth;
    private float animatedSelectorOffset;
    private float pendingConfigurationSelectorX = Float.NaN;
    private final SpringAnimation widthSpring;
    private final SpringAnimation selectorSpring;
    private ValueAnimator expandedAnimator;
    private float expandedProgress;
    private ValueAnimator zoomAnimator;

    private boolean expanded;
    private boolean externalZoomGesture;
    private int selectedToggleIndex;
    private boolean selectedShowsStopValue;
    private int pressedToggleIndex = -1;
    private int lastDescribedZoom = Integer.MIN_VALUE;

    private final int touchSlop;
    private float tickSpacing;
    private float downX;
    private float downY;
    private float lastTouchX;
    private boolean movedPastSlop;
    private boolean compactGestureDown;
    private boolean dragStartedFromCompact;
    private boolean dragging;
    private float dragTick;
    private int stickyTick = -1;
    private float stickyDistance;
    private float stickyFactor;
    private int dragPrimarySegment = NO_SEGMENT;
    private VelocityTracker velocityTracker;

    private OnZoomChangeListener onZoomChangeListener;

    private final Runnable longPressRunnable = () -> {
        if (!compactGestureDown || movedPastSlop || expanded) {
            return;
        }
        dragStartedFromCompact = true;
        beginDrag(downX);
        setExpanded(true, true);
    };

    private final Runnable autoCollapseRunnable = () -> {
        if (!expanded || dragging || externalZoomGesture) {
            return;
        }
        setExpanded(false, true);
    };

    public interface OnZoomChangeListener {
        void onZoomChanged(float zoom);
    }

    public abstract boolean drawPillBackground(Canvas canvas, RectF bounds, float radius);

    public CameraZoomSliderView(Context context) {
        this(context, null);
    }

    public CameraZoomSliderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraZoomSliderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        tickSpacing = Math.max(1.0f, Math.round(AndroidUtilities.dp(TICK_SPACING)));
        widthSpring = createSpring(CONTROL_WIDTH, 1.0f, 3800.0f, AndroidUtilities.dp(0.1f));
        selectorSpring = createSpring(SELECTOR_OFFSET, 0.8f, 800.0f, 1.0f);
        configurePaints();
        rebuildScale();
        selectedToggleIndex = findToggleSegment(zoom);
        animatedSelectorOffset = getSelectorOffset(selectedToggleIndex);
        animatedControlWidth = getCompactWidth();
        setClickable(true);
        setFocusable(true);
        updateAccessibilityDescription();
    }

    private SpringAnimation createSpring(FloatPropertyCompat<CameraZoomSliderView> property, float dampingRatio, float stiffness, float minimumVisibleChange) {
        SpringAnimation animation = new SpringAnimation(this, property);
        animation.setSpring(new SpringForce().setDampingRatio(dampingRatio).setStiffness(stiffness));
        animation.setMinimumVisibleChange(minimumVisibleChange);
        return animation;
    }

    public void prepareZoomConfigurationTransition() {
        pendingConfigurationSelectorX = Float.NaN;
        if (!isLaidOut() || toggleStops.length == 0) {
            return;
        }
        updateLayoutBounds();
        if (compactBounds.isEmpty()) {
            return;
        }
        pendingConfigurationSelectorX = compactBounds.left + animatedSelectorOffset;
    }

    public void cancelZoomConfigurationTransition() {
        pendingConfigurationSelectorX = Float.NaN;
    }

    public void setZoomConfiguration(float minZoom, float maxZoom, float[] toggleStops, float[] rulerStops, float zoom, boolean animated) {
        if (!Float.isFinite(minZoom) || !Float.isFinite(maxZoom) || minZoom <= 0.0f || maxZoom <= minZoom) {
            throw new IllegalArgumentException("Zoom range must satisfy 0 < minZoom < maxZoom");
        }
        float previousSelectorX = pendingConfigurationSelectorX;
        pendingConfigurationSelectorX = Float.NaN;
        boolean animateSelector = animated && isLaidOut() && Float.isFinite(previousSelectorX);
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.toggleStops = sanitizeStops(toggleStops, minZoom, maxZoom);
        this.rulerStops = sanitizeStops(rulerStops, minZoom, maxZoom);
        this.zoom = Utilities.clamp(zoom, maxZoom, minZoom);
        rebuildScale();
        if (animateSelector) {
            selectedToggleIndex = findToggleSegment(this.zoom);
            float availableWidth = Math.max(0.0f, getWidth() - getPaddingLeft() - getPaddingRight());
            float maxControlWidth = Math.max(0.0f, availableWidth - AndroidUtilities.dp(HORIZONTAL_MARGIN) * 2.0f);
            float compactLeft = centeredChildLeft(availableWidth, Math.min(maxControlWidth, Math.round(getCompactWidth())));
            selectorSpring.cancel();
            animatedSelectorOffset = previousSelectorX - compactLeft;
            animateSelectorTo(selectedToggleIndex, true);
            updateTargetControlWidth(true);
        } else {
            syncSelectedToggle(false);
            updateTargetControlWidth(false);
        }
        updateAccessibilityDescription();
        requestLayout();
        invalidate();
    }

    public void setZoom(float zoom) {
        setZoom(zoom, false);
    }

    public void setZoom(float zoom, boolean animated) {
        float clamped = Utilities.clamp(zoom, maxZoom, minZoom);
        cancelZoomAnimator();
        if (animated && isLaidOut()) {
            animateZoomTo(clamped, false);
        } else {
            setZoomInternal(clamped, false, true);
        }
    }

    public float getZoom() {
        return zoom;
    }

    public float getMinimumZoom() {
        return minZoom;
    }

    public float getMaximumZoom() {
        return maxZoom;
    }

    public void setExternalZoomGestureActive(boolean active) {
        if (externalZoomGesture == active) {
            return;
        }
        externalZoomGesture = active;
        if (active) {
            setExpanded(true, true);
        } else {
            resetAutoCollapseTimeout();
        }
    }

    public void setExpanded(boolean expanded, boolean animated) {
        float targetProgress = expanded ? 1.0f : 0.0f;
        float targetWidth = expanded ? getExpandedBackgroundWidth() : getCompactWidth();
        if (this.expanded == expanded && Math.abs(expandedProgress - targetProgress) < 1.0E-4f && Math.abs(animatedControlWidth - targetWidth) < 0.1f) {
            if (expanded) {
                resetAutoCollapseTimeout();
            }
            return;
        }
        this.expanded = expanded;
        removeCallbacks(longPressRunnable);
        removeCallbacks(autoCollapseRunnable);
        animateExpandedProgress(targetProgress, animated);
        if (!animated || !isLaidOut()) {
            widthSpring.cancel();
            animatedControlWidth = targetWidth;
            invalidate();
        } else {
            widthSpring.animateToFinalPosition(targetWidth);
        }
        if (expanded && !dragging) {
            resetAutoCollapseTimeout();
        }
        updateAccessibilityDescription();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public void setColors(int protectionBackgroundColor, int minorTickColor, int primaryColor, int secondaryFixedColor, int onSecondaryFixedColor) {
        this.protectionBackgroundColor = protectionBackgroundColor;
        this.minorTickColor = minorTickColor;
        this.primaryColor = primaryColor;
        this.secondaryFixedColor = secondaryFixedColor;
        this.onSecondaryFixedColor = onSecondaryFixedColor;
        configurePaintColors();
        invalidate();
    }

    public void setToggleTextColor(int color) {
        unselectedToggleColor = color;
        invalidate();
    }

    public void setDisplayNormalizationFactor(float factor) {
        if (!Float.isFinite(factor) || factor <= 0.0f) {
            factor = 1.0f;
        }
        if (Math.abs(displayNormalizationFactor - factor) < 1.0E-4f) {
            return;
        }
        displayNormalizationFactor = factor;
        rebuildScale();
        syncSelectedToggle(false);
        updateAccessibilityDescription();
        invalidate();
    }

    public float[] getToggleStops() {
        return Arrays.copyOf(toggleStops, toggleStops.length);
    }

    public void setOnZoomChangeListener(OnZoomChangeListener listener) {
        onZoomChangeListener = listener;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = Math.round(Math.max(getExpandedBackgroundWidth(), getCompactWidth()) + AndroidUtilities.dp(HORIZONTAL_MARGIN) * 2.0f) + getPaddingLeft() + getPaddingRight();
        int height = Math.round(getBubbleHeight() + AndroidUtilities.dp(HORIZONTAL_MARGIN) * 2.0f + AndroidUtilities.dp(TOUCH_AREA_HEIGHT)) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(View.resolveSize(width, widthMeasureSpec), View.resolveSize(height, heightMeasureSpec));
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateLayoutBounds();
        boolean drewBackground = !controlBounds.isEmpty() && drawPillBackground(canvas, controlBounds, AndroidUtilities.dp(CORNER_RADIUS));
        float compactAlpha = Utilities.clamp01(1.0f - expandedProgress);
        float expandedAlpha = Utilities.clamp01(expandedProgress);
        int restoreCount = canvas.saveLayer(0.0f, 0.0f, getWidth(), getHeight(), null);
        if (compactAlpha > 0.001f) {
            int count = canvas.saveLayerAlpha(0.0f, 0.0f, getWidth(), getHeight(), Math.round(compactAlpha * 255.0f));
            drawToggleRow(canvas, 1.0f);
            canvas.restoreToCount(count);
        }
        if (expandedAlpha > 0.001f) {
            int count = canvas.saveLayerAlpha(0.0f, 0.0f, getWidth(), getHeight(), Math.round(expandedAlpha * 255.0f));
            drawBubble(canvas, 1.0f);
            drawRuler(canvas, 1.0f);
            canvas.restoreToCount(count);
        }
        if (!drewBackground) {
            drawProtectionBackground(canvas);
        }
        canvas.restoreToCount(restoreCount);
    }

    private void updateLayoutBounds() {
        float availableWidth = Math.max(0.0f, getWidth() - getPaddingLeft() - getPaddingRight());
        float maxControlWidth = Math.max(0.0f, availableWidth - AndroidUtilities.dp(HORIZONTAL_MARGIN) * 2.0f);
        float bottom = getHeight() - getPaddingBottom();
        float top = bottom - AndroidUtilities.dp(TOUCH_AREA_HEIGHT);
        float controlBottom = top + AndroidUtilities.dp(CONTROL_HEIGHT);
        float expandedWidth = Math.min(maxControlWidth, Math.round(getExpandedBackgroundWidth()));
        float compactWidth = Math.min(maxControlWidth, Math.round(getCompactWidth()));
        if (animatedControlWidth <= 0.0f) {
            animatedControlWidth = expanded ? expandedWidth : compactWidth;
        }
        float controlWidth = Math.min(maxControlWidth, Math.round(animatedControlWidth));
        float controlLeft = centeredChildLeft(availableWidth, controlWidth);
        controlBounds.set(controlLeft, top, controlLeft + controlWidth, controlBottom);
        float compactLeft = centeredChildLeft(availableWidth, compactWidth);
        compactBounds.set(compactLeft, top, compactLeft + compactWidth, controlBottom);
        float rulerWidth = Math.min(maxControlWidth, getExpandedRulerWidth());
        float rulerLeft = centeredChildLeft(availableWidth, rulerWidth);
        rulerBounds.set(rulerLeft, top, rulerLeft + rulerWidth, bottom);
        compactTouchBounds.set(compactBounds);
        compactTouchBounds.bottom = bottom;
        rulerTouchBounds.set(rulerBounds);
    }

    private float centeredChildLeft(float availableWidth, float childWidth) {
        return getPaddingLeft() + (int) ((availableWidth - childWidth) / 2.0f);
    }

    private void drawProtectionBackground(Canvas canvas) {
        if (controlBounds.isEmpty()) {
            return;
        }
        backgroundPaint.setColor(protectionBackgroundColor);
        backgroundPaint.setXfermode(DST_OVER_XFERMODE);
        canvas.drawRoundRect(controlBounds, AndroidUtilities.dp(CORNER_RADIUS), AndroidUtilities.dp(CORNER_RADIUS), backgroundPaint);
        backgroundPaint.setXfermode(null);
    }

    private void drawToggleRow(Canvas canvas, float alpha) {
        if (toggleStops.length == 0 || compactBounds.isEmpty()) {
            return;
        }
        float inset = AndroidUtilities.dp(SELECTOR_INSET);
        float size = AndroidUtilities.dp(SELECTOR_SIZE);
        float left = compactBounds.left + Math.round(animatedSelectorOffset) + inset;
        float top = compactBounds.top + inset;
        selectorBounds.set(left, top, left + size, top + size);
        drawToggleLabels(canvas, alpha, unselectedToggleColor);
        if (expanded) {
            return;
        }
        selectorPaint.setColor(Theme.multAlpha(secondaryFixedColor, alpha));
        selectorPaint.setXfermode(XOR_XFERMODE);
        canvas.drawOval(selectorBounds, selectorPaint);
        selectorPaint.setColor(Theme.multAlpha(onSecondaryFixedColor, alpha));
        selectorPaint.setXfermode(DST_OVER_XFERMODE);
        canvas.drawOval(selectorBounds, selectorPaint);
        selectorPaint.setXfermode(null);
    }

    private void drawToggleLabels(Canvas canvas, float alpha, int color) {
        for (int i = 0; i < toggleStops.length; i++) {
            Paint paint = i == selectedToggleIndex ? selectedToggleTextPaint : toggleTextPaint;
            paint.setColor(Theme.multAlpha(color, alpha));
            float x = compactBounds.left + (i + 0.5f) * AndroidUtilities.dp(TOGGLE_SIZE);
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            int textHeight = fm.descent - fm.ascent;
            float y = compactBounds.top + (int) ((compactBounds.height() - textHeight) / 2.0f) - fm.ascent;
            canvas.drawText(getToggleLabel(i), x, y, paint);
        }
    }

    private void drawRuler(Canvas canvas, float alpha) {
        if (rulerBounds.isEmpty()) {
            return;
        }
        int restoreCount = canvas.saveLayer(rulerBounds.left, controlBounds.top, rulerBounds.right, controlBounds.bottom, null);
        canvas.clipRect(rulerBounds.left, controlBounds.top, rulerBounds.right, controlBounds.bottom);
        float centerX = rulerBounds.centerX();
        float currentTick = zoomToTick(zoom);
        float tickBottom = controlBounds.bottom - AndroidUtilities.dp(22.0f);
        float markerBottom = controlBounds.bottom - AndroidUtilities.dp(23.0f);
        float halfTicks = (rulerBounds.width() / 2.0f) / tickSpacing;
        int firstTick = Math.max(0, (int) Math.floor(currentTick - halfTicks) - 1);
        int lastTick = Math.min(intervalCount, (int) Math.ceil(halfTicks + currentTick) + 1);
        Paint.FontMetricsInt fm = rulerLabelPaint.getFontMetricsInt();
        float labelBaseline = controlBounds.bottom - AndroidUtilities.dp(4.0f) - (fm.descent - fm.ascent) - fm.ascent;
        for (int tick = firstTick; tick <= lastTick; tick++) {
            float x = (tick - currentTick) * tickSpacing + centerX;
            String label = primaryLabels.get(tick);
            boolean primary = label != null;
            tickPaint.setColor(Theme.multAlpha(primary ? primaryColor : minorTickColor, alpha));
            tickPaint.setStrokeWidth(AndroidUtilities.dp(1.0f));
            canvas.drawLine(x, tickBottom - AndroidUtilities.dp(primary ? 12.0f : 6.0f), x, tickBottom, tickPaint);
            if (primary) {
                rulerLabelPaint.setColor(Theme.multAlpha(primaryColor, alpha));
                canvas.drawText(label, x, labelBaseline, rulerLabelPaint);
            }
        }
        markerPaint.setColor(Theme.multAlpha(primaryColor, alpha));
        markerPaint.setStrokeWidth(AndroidUtilities.dp(4.0f));
        canvas.drawLine(centerX, markerBottom - AndroidUtilities.dp(12.0f), centerX, markerBottom, markerPaint);
        edgeFadePaint.setShader(new LinearGradient(
            rulerBounds.left, 0.0f, rulerBounds.right, 0.0f,
            new int[]{0x33000000, 0xFF000000, 0xFF000000, 0x33000000},
            new float[]{0.0f, 1.0f / 3.0f, 2.0f / 3.0f, 1.0f},
            Shader.TileMode.CLAMP
        ));
        edgeFadePaint.setXfermode(DST_IN_XFERMODE);
        canvas.drawRect(rulerBounds.left, controlBounds.top, rulerBounds.right, controlBounds.bottom, edgeFadePaint);
        edgeFadePaint.setShader(null);
        edgeFadePaint.setXfermode(null);
        canvas.restoreToCount(restoreCount);
    }

    private void drawBubble(Canvas canvas, float alpha) {
        String text = formatBubble(zoom);
        if (text.isEmpty()) {
            return;
        }
        Paint.FontMetricsInt fm = bubbleTextPaint.getFontMetricsInt();
        float width = Math.max(AndroidUtilities.dp(40.0f), (float) Math.ceil(bubbleTextPaint.measureText(text))) + AndroidUtilities.dp(4.0f) * 2.0f;
        float height = getBubbleHeight();
        float margin = AndroidUtilities.dp(8.0f);
        float centerX = controlBounds.centerX();
        float halfHeight = height / 2.0f;
        float centerY = controlBounds.top - margin - halfHeight;
        float halfWidth = width / 2.0f;
        bubbleBounds.set(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
        float clampedAlpha = Utilities.clamp01(alpha);
        if (clampedAlpha <= 0.001f) {
            return;
        }
        bubblePaint.setColor(secondaryFixedColor);
        bubbleTextPaint.setColor(onSecondaryFixedColor);
        float baseline = bubbleBounds.top + AndroidUtilities.dp(6.0f) - fm.ascent;
        int restoreCount = canvas.saveLayerAlpha(0.0f, 0.0f, getWidth(), getHeight(), Math.round(clampedAlpha * 255.0f));
        canvas.drawRoundRect(bubbleBounds, halfHeight, halfHeight, bubblePaint);
        canvas.drawText(text, bubbleBounds.centerX(), baseline, bubbleTextPaint);
        canvas.restoreToCount(restoreCount);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || toggleStops.length == 0) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            updateLayoutBounds();
            boolean wasExpanded = expanded;
            if (!(wasExpanded ? rulerTouchBounds : compactTouchBounds).contains(event.getX(), event.getY())) {
                return false;
            }
            downX = event.getX();
            downY = event.getY();
            lastTouchX = downX;
            movedPastSlop = false;
            compactGestureDown = !wasExpanded;
            dragStartedFromCompact = false;
            pressedToggleIndex = (wasExpanded || !compactBounds.contains(downX, downY)) ? -1 : findToggleIndexAt(downX);
            setPressed(true);
            requestParentIntercept(false);
            obtainVelocityTracker(event);
            if (wasExpanded) {
                beginDrag(downX);
            } else {
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }
        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (dragging) {
                float distance = (float) Math.hypot(event.getX() - downX, event.getY() - downY);
                if (!movedPastSlop && distance > touchSlop) {
                    movedPastSlop = true;
                }
                moveDrag(event.getX());
                return true;
            }
            if (!compactGestureDown) {
                return true;
            }
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (!movedPastSlop && Math.hypot(dx, dy) > touchSlop) {
                movedPastSlop = true;
                removeCallbacks(longPressRunnable);
                if (Math.abs(dx) >= Math.abs(dy)) {
                    dragStartedFromCompact = true;
                    beginDrag(downX);
                    setExpanded(true, true);
                    moveDrag(event.getX());
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            removeCallbacks(longPressRunnable);
            if (dragging) {
                if (!movedPastSlop && !dragStartedFromCompact) {
                    setZoomFromRulerTap(event.getX());
                }
                finishDrag(true);
            } else if (!movedPastSlop && pressedToggleIndex >= 0) {
                int index = findToggleIndexAt(event.getX());
                if (compactBounds.contains(event.getX(), event.getY()) && index == pressedToggleIndex) {
                    selectToggle(index);
                }
                clearTouchState();
                performClick();
            } else {
                clearTouchState();
            }
            recycleVelocityTracker();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPressRunnable);
            if (dragging) {
                finishDrag(false);
            } else {
                clearTouchState();
            }
            recycleVelocityTracker();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void beginDrag(float x) {
        if (stopZoomAnimator()) {
            syncSelectedToggle(true);
        }
        dragging = true;
        lastTouchX = x;
        dragTick = zoomToTick(zoom);
        dragPrimarySegment = NO_SEGMENT;
        stickyTick = -1;
        stickyDistance = 0.0f;
        stickyFactor = 0.0f;
        removeCallbacks(autoCollapseRunnable);
    }

    private void moveDrag(float x) {
        float delta = x - lastTouchX;
        lastTouchX = x;
        if (Math.abs(delta) < 1.0E-4f) {
            return;
        }
        float tick = dragTick;
        float velocity = getCurrentXVelocity();
        if (stickyTick >= 0) {
            float remaining = Math.max(0.0f, tickSpacing * stickyFactor - stickyDistance);
            float absDelta = Math.abs(delta);
            if (absDelta <= remaining) {
                stickyDistance += absDelta;
                setDragTick(stickyTick);
                return;
            }
            delta = Math.copySign(absDelta - remaining, delta);
            tick = stickyTick;
            stickyTick = -1;
            stickyDistance = 0.0f;
            stickyFactor = 0.0f;
        }
        if (rebuiltPrimaryTickIndices.length > 0) {
            int segment = findPrimarySegment(tick, delta);
            int previousSegment = dragPrimarySegment;
            if (previousSegment == NO_SEGMENT) {
                dragPrimarySegment = segment;
            } else if (segment != previousSegment) {
                dragPrimarySegment = segment;
                float stickiness = calculateStickiness(velocity);
                if (stickiness > 0.0f) {
                    int boundaryTick = Math.max(previousSegment, segment);
                    float stickyRange = tickSpacing * stickiness;
                    float distanceToBoundary = Math.abs(tick - boundaryTick) * tickSpacing;
                    if (distanceToBoundary < stickyRange) {
                        float remaining = stickyRange - distanceToBoundary;
                        float absDelta = Math.abs(delta);
                        if (absDelta <= remaining) {
                            stickyTick = boundaryTick;
                            stickyDistance = distanceToBoundary + absDelta;
                            stickyFactor = stickiness;
                            setDragTick(boundaryTick);
                            return;
                        }
                        delta = Math.copySign(absDelta - remaining, delta);
                        tick = boundaryTick;
                    }
                }
            }
        }
        setDragTick(Utilities.clamp(tick - delta / tickSpacing, intervalCount, 0.0f));
    }

    private void setDragTick(float tick) {
        float clamped = Utilities.clamp(tick, intervalCount, 0.0f);
        dragTick = clamped;
        setTickInternal(clamped, true);
    }

    private void finishDrag(boolean click) {
        dragging = false;
        compactGestureDown = false;
        dragStartedFromCompact = false;
        stickyTick = -1;
        stickyDistance = 0.0f;
        stickyFactor = 0.0f;
        dragPrimarySegment = NO_SEGMENT;
        pressedToggleIndex = -1;
        setPressed(false);
        requestParentIntercept(true);
        resetAutoCollapseTimeout();
        if (click) {
            performClick();
        }
    }

    private void setZoomFromRulerTap(float x) {
        animateZoomTo(tickToZoom(zoomToTick(zoom) + (x - rulerBounds.centerX()) / tickSpacing), true);
    }

    private void selectToggle(int index) {
        if (index < 0 || index >= toggleStops.length) {
            return;
        }
        if (index == selectedToggleIndex && selectedShowsStopValue) {
            return;
        }
        animateZoomTo(toggleStops[index], true, index);
    }

    private void clearTouchState() {
        dragging = false;
        compactGestureDown = false;
        dragStartedFromCompact = false;
        movedPastSlop = false;
        pressedToggleIndex = -1;
        stickyTick = -1;
        stickyDistance = 0.0f;
        stickyFactor = 0.0f;
        dragPrimarySegment = NO_SEGMENT;
        setPressed(false);
        requestParentIntercept(true);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return (expanded ? SeekBar.class : View.class).getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(getAccessibilityClassName());
        info.setContentDescription(formatBubble(zoom));
        if (expanded) {
            info.setScrollable(true);
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, minZoom, maxZoom, zoom));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            return;
        }
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            setExpanded(!expanded, true);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            cancelZoomAnimator();
            setTickInternal(zoomToTick(zoom) + 1.0f, true);
            resetAutoCollapseTimeout();
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            cancelZoomAnimator();
            setTickInternal(zoomToTick(zoom) - 1.0f, true);
            resetAutoCollapseTimeout();
            return true;
        }
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && arguments != null && arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) {
            cancelZoomAnimator();
            setZoomInternal(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE), true, true);
            resetAutoCollapseTimeout();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        cancelTransientSprings();
        tickSpacing = Math.max(1.0f, Math.round(AndroidUtilities.dp(TICK_SPACING)));
        configurePaints();
        rebuildScale();
        settleTransientAnimationValues();
        requestLayout();
        invalidate();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        cancelTransientSprings();
        settleTransientAnimationValues();
        if (!expanded || dragging) {
            return;
        }
        resetAutoCollapseTimeout();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelZoomConfigurationTransition();
        removeCallbacks(longPressRunnable);
        removeCallbacks(autoCollapseRunnable);
        animate().cancel();
        cancelZoomAnimator();
        cancelTransientSprings();
        settleTransientAnimationValues();
        clearTouchState();
        recycleVelocityTracker();
    }

    private void animateExpandedProgress(float target, boolean animated) {
        if (expandedAnimator != null) {
            expandedAnimator.cancel();
            expandedAnimator = null;
        }
        if (!animated || !isLaidOut()) {
            expandedProgress = target;
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(expandedProgress, target);
        expandedAnimator = animator;
        animator.setDuration(MORPH_DURATION);
        animator.setInterpolator(MORPH_INTERPOLATOR);
        animator.addUpdateListener(a -> {
            expandedProgress = Utilities.clamp01((float) a.getAnimatedValue());
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (expandedAnimator == animation) {
                    expandedAnimator = null;
                }
            }
        });
        animator.start();
    }

    private void cancelTransientSprings() {
        if (expandedAnimator != null) {
            expandedAnimator.cancel();
            expandedAnimator = null;
        }
        widthSpring.cancel();
        selectorSpring.cancel();
    }

    private void settleTransientAnimationValues() {
        expandedProgress = expanded ? 1.0f : 0.0f;
        animatedControlWidth = expanded ? getExpandedBackgroundWidth() : getCompactWidth();
        animatedSelectorOffset = getSelectorOffset(selectedToggleIndex);
    }

    private void rebuildScale() {
        float octaves = (float) (Math.log(maxZoom / minZoom) / LOG_2);
        float normalization = displayNormalizationFactor;
        if (minZoom < normalization && maxZoom >= normalization) {
            oneXTick = Math.max(3, Math.round((float) (Math.log(normalization / minZoom) / LOG_2) * TICKS_PER_OCTAVE));
            int upperTicks = maxZoom > normalization ? Math.max(1, Math.round((float) (Math.log(maxZoom / normalization) / LOG_2) * TICKS_PER_OCTAVE)) : 0;
            intervalCount = oneXTick + upperTicks;
        } else {
            oneXTick = -1;
            intervalCount = Math.max(1, Math.round(octaves * TICKS_PER_OCTAVE));
        }
        primaryLabels.clear();
        int[] indices = new int[rulerStops.length];
        int count = 0;
        for (float stop : rulerStops) {
            if (stop < minZoom || stop > maxZoom) {
                continue;
            }
            int tick = Math.round(zoomToTick(stop));
            primaryLabels.put(tick, formatRuler(stop));
            boolean duplicate = false;
            for (int i = 0; i < count; i++) {
                if (indices[i] == tick) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                indices[count++] = tick;
            }
        }
        rebuiltPrimaryTickIndices = Arrays.copyOf(indices, count);
        Arrays.sort(rebuiltPrimaryTickIndices);
    }

    private void setTickInternal(float tick, boolean notify) {
        setZoomInternal(tickToZoom(tick), notify, true);
    }

    private float tickToZoom(float tick) {
        float clamped = Utilities.clamp(tick, intervalCount, 0.0f);
        if (clamped <= 0.0f) {
            return minZoom;
        }
        if (clamped >= intervalCount) {
            return maxZoom;
        }
        double base;
        double factor;
        if (oneXTick < 0) {
            float progress = clamped / intervalCount;
            base = minZoom;
            factor = Math.exp(Math.log(maxZoom / minZoom) * progress);
        } else {
            if (clamped == oneXTick) {
                return displayNormalizationFactor;
            }
            if (clamped <= oneXTick) {
                float progress = clamped / oneXTick;
                base = minZoom;
                factor = Math.exp(Math.log(displayNormalizationFactor / minZoom) * progress);
            } else {
                float progress = (clamped - oneXTick) / (intervalCount - oneXTick);
                base = displayNormalizationFactor;
                factor = Math.exp(Math.log(maxZoom / displayNormalizationFactor) * progress);
            }
        }
        return (float) (base * factor);
    }

    private void setZoomInternal(float zoom, boolean notify, boolean syncToggle) {
        this.zoom = Utilities.clamp(zoom, maxZoom, minZoom);
        if (syncToggle) {
            syncSelectedToggle(!expanded);
        }
        updateAccessibilityDescription();
        invalidate();
        if (notify && onZoomChangeListener != null) {
            onZoomChangeListener.onZoomChanged(this.zoom);
        }
    }

    private float zoomToTick(float zoom) {
        float clamped = Utilities.clamp(zoom, maxZoom, minZoom);
        if (clamped <= minZoom) {
            return 0.0f;
        }
        if (clamped >= maxZoom) {
            return intervalCount;
        }
        if (oneXTick >= 0) {
            if (clamped == displayNormalizationFactor) {
                return oneXTick;
            }
            if (clamped <= displayNormalizationFactor) {
                float progress = (float) (Math.log(clamped / minZoom) / Math.log(displayNormalizationFactor / minZoom));
                return progress * oneXTick;
            }
            float progress = (float) (Math.log(clamped / displayNormalizationFactor) / Math.log(maxZoom / displayNormalizationFactor));
            return oneXTick + progress * (intervalCount - oneXTick);
        }
        float progress = (float) (Math.log(clamped / minZoom) / Math.log(maxZoom / minZoom));
        return progress * intervalCount;
    }

    private void syncSelectedToggle(boolean animated) {
        selectedShowsStopValue = false;
        int segment = findToggleSegment(zoom);
        if (segment < 0) {
            selectedToggleIndex = -1;
            animatedSelectorOffset = 0.0f;
        } else if (selectedToggleIndex != segment) {
            selectedToggleIndex = segment;
            animateSelectorTo(segment, animated);
        } else if (!animated) {
            selectorSpring.cancel();
            animatedSelectorOffset = getSelectorOffset(segment);
        }
    }

    private int findToggleSegment(float zoom) {
        if (toggleStops.length == 0) {
            return -1;
        }
        int segment = 0;
        for (int i = 1; i < toggleStops.length && zoom >= toggleStops[i]; i++) {
            segment = i;
        }
        return segment;
    }

    private void animateSelectorTo(int index, boolean animated) {
        float offset = getSelectorOffset(index);
        if (!animated || !isLaidOut()) {
            selectorSpring.cancel();
            animatedSelectorOffset = offset;
            invalidate();
            return;
        }
        selectorSpring.animateToFinalPosition(offset);
    }

    private void animateZoomTo(float zoom, boolean notify) {
        animateZoomTo(zoom, notify, -1);
    }

    private void animateZoomTo(float zoom, boolean notify, int toggleIndex) {
        boolean fromToggle = toggleIndex >= 0 && toggleIndex < toggleStops.length;
        if (fromToggle) {
            stopZoomAnimator();
        } else {
            cancelZoomAnimator(true);
        }
        float target = Utilities.clamp(zoom, maxZoom, minZoom);
        if (fromToggle) {
            selectedToggleIndex = toggleIndex;
            selectedShowsStopValue = true;
            animateSelectorTo(toggleIndex, true);
        }
        if (Math.abs(target - this.zoom) < 1.0E-4f) {
            setZoomInternal(target, notify, !fromToggle);
            return;
        }
        float from = this.zoom;
        long duration = Math.min(MAX_ZOOM_ANIMATION_DURATION, (long) Math.rint(Math.max(from, target) / Math.min(from, target) * MAX_ZOOM_ANIMATION_DURATION / 3.0f));
        ValueAnimator animator = ValueAnimator.ofFloat(from, target);
        zoomAnimator = animator;
        animator.setDuration(duration);
        animator.setInterpolator(ZOOM_INTERPOLATOR);
        animator.addUpdateListener(a -> setZoomInternal((float) a.getAnimatedValue(), notify, !fromToggle));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled && Math.abs(CameraZoomSliderView.this.zoom - target) > 1.0E-4f) {
                    setZoomInternal(target, notify, !fromToggle);
                }
                if (zoomAnimator == animation) {
                    zoomAnimator = null;
                    syncSelectedToggle(true);
                }
            }
        });
        animator.start();
    }

    private boolean stopZoomAnimator() {
        ValueAnimator animator = zoomAnimator;
        if (animator == null) {
            return false;
        }
        zoomAnimator = null;
        animator.cancel();
        return true;
    }

    private void cancelZoomAnimator() {
        cancelZoomAnimator(false);
    }

    private void cancelZoomAnimator(boolean animateSelector) {
        if (stopZoomAnimator()) {
            syncSelectedToggle(animateSelector);
        }
    }

    private int findToggleIndexAt(float x) {
        if (x < compactBounds.left || x > compactBounds.right || toggleStops.length == 0) {
            return -1;
        }
        return Math.max(0, Math.min(toggleStops.length - 1, (int) ((x - compactBounds.left) / AndroidUtilities.dp(TOGGLE_SIZE))));
    }

    private int findPrimarySegment(float tick, float delta) {
        int[] indices = rebuiltPrimaryTickIndices;
        if (indices.length == 0) {
            return NO_SEGMENT;
        }
        float first = indices[0];
        if (tick < first || (tick == first && delta >= 0.0f)) {
            return -1;
        }
        for (int i = 0; i < indices.length - 1; i++) {
            float next = indices[i + 1];
            if (delta < 0.0f ? tick < next : tick <= next) {
                return indices[i];
            }
        }
        return indices[indices.length - 1];
    }

    private float calculateStickiness(float velocity) {
        float absVelocity = Math.abs(velocity);
        if (absVelocity <= STICKY_VELOCITY_MIN) {
            return MAX_STICKINESS;
        }
        if (absVelocity >= STICKY_VELOCITY_MAX) {
            return 0.0f;
        }
        return (STICKY_VELOCITY_MAX - absVelocity) / (STICKY_VELOCITY_MAX - STICKY_VELOCITY_MIN) * MAX_STICKINESS;
    }

    private void obtainVelocityTracker(MotionEvent event) {
        recycleVelocityTracker();
        velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);
    }

    private float getCurrentXVelocity() {
        if (velocityTracker == null) {
            return 0.0f;
        }
        velocityTracker.computeCurrentVelocity(1000);
        return velocityTracker.getXVelocity();
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void updateTargetControlWidth(boolean animated) {
        float targetWidth = expanded ? getExpandedBackgroundWidth() : getCompactWidth();
        if (!animated || !isLaidOut()) {
            widthSpring.cancel();
            animatedControlWidth = targetWidth;
            invalidate();
            return;
        }
        widthSpring.animateToFinalPosition(targetWidth);
    }

    private void resetAutoCollapseTimeout() {
        removeCallbacks(autoCollapseRunnable);
        AccessibilityManager accessibilityManager = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        boolean touchExploration = accessibilityManager != null && accessibilityManager.isTouchExplorationEnabled();
        if (!expanded || dragging || externalZoomGesture || touchExploration) {
            return;
        }
        postDelayed(autoCollapseRunnable, AUTO_COLLAPSE_TIMEOUT);
    }

    private void requestParentIntercept(boolean allow) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allow);
        }
    }

    private void configurePaints() {
        backgroundPaint.setStyle(Paint.Style.FILL);
        selectorPaint.setStyle(Paint.Style.FILL);
        bubblePaint.setStyle(Paint.Style.FILL);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        markerPaint.setStrokeCap(Paint.Cap.ROUND);
        toggleTextPaint.setTextAlign(Paint.Align.CENTER);
        toggleTextPaint.setTextSize(AndroidUtilities.dp(14.0f));
        toggleTextPaint.setTypeface(AndroidUtilities.bold());
        selectedToggleTextPaint.setTextAlign(Paint.Align.CENTER);
        selectedToggleTextPaint.setTextSize(AndroidUtilities.dp(16.0f));
        selectedToggleTextPaint.setTypeface(AndroidUtilities.bold());
        rulerLabelPaint.setTextAlign(Paint.Align.CENTER);
        rulerLabelPaint.setTextSize(AndroidUtilities.dp(11.0f));
        rulerLabelPaint.setTypeface(AndroidUtilities.bold());
        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);
        bubbleTextPaint.setTextSize(AndroidUtilities.dp(16.0f));
        bubbleTextPaint.setTypeface(AndroidUtilities.bold());
        configurePaintColors();
    }

    private void configurePaintColors() {
        tickPaint.setColor(minorTickColor);
        markerPaint.setColor(primaryColor);
        rulerLabelPaint.setColor(primaryColor);
        selectorPaint.setColor(secondaryFixedColor);
        bubblePaint.setColor(secondaryFixedColor);
        bubbleTextPaint.setColor(onSecondaryFixedColor);
    }

    private String getToggleLabel(int index) {
        if (index == selectedToggleIndex) {
            return formatBubble(selectedShowsStopValue ? toggleStops[index] : zoom);
        }
        return formatToggle(toggleStops[index]);
    }

    private String formatBubble(float zoom) {
        return formatZoomNumber(zoom) + "×";
    }

    private String formatToggle(float zoom) {
        return formatZoomNumber(zoom);
    }

    private String formatRuler(float zoom) {
        return formatZoomNumber(zoom);
    }

    private String formatZoomNumber(float zoom) {
        float value = normalizeDisplayZoom(zoom);
        Locale locale = Locale.getDefault();
        if (value % 1.0f == 0.0f) {
            return String.format(locale, "%.0f", value);
        }
        String text = String.format(locale, "%.1f", value);
        return text.startsWith("0") ? text.substring(1) : text;
    }

    private float normalizeDisplayZoom(float zoom) {
        float normalized = zoom / displayNormalizationFactor;
        float tenths = normalized * 10.0f;
        if (normalized < 1.0f) {
            return (float) Math.floor(tenths) / 10.0f;
        }
        float floor = (float) Math.floor(tenths);
        if (floor % 5.0f == 0.0f) {
            tenths = floor;
        } else {
            float ceil = (float) Math.ceil(tenths);
            if (ceil % 5.0f == 0.0f) {
                tenths = ceil;
            }
        }
        float rounded = (float) Math.rint(tenths) / 10.0f;
        return (rounded % 1.0f == 0.0f || rounded >= 8.0f) ? (float) Math.rint(normalized) : rounded;
    }

    private void updateAccessibilityDescription() {
        int described = Math.round(normalizeDisplayZoom(zoom) * 10.0f);
        if (described == lastDescribedZoom) {
            return;
        }
        lastDescribedZoom = described;
        setContentDescription(formatBubble(zoom));
    }

    private float getExpandedBackgroundWidth() {
        return AndroidUtilities.dp(EXPANDED_BACKGROUND_WIDTH);
    }

    private float getCompactWidth() {
        return AndroidUtilities.dp(TOGGLE_SIZE) * toggleStops.length;
    }

    private float getExpandedRulerWidth() {
        return AndroidUtilities.dp(EXPANDED_RULER_WIDTH);
    }

    private float getSelectorOffset(int index) {
        return AndroidUtilities.dp(TOGGLE_SIZE) * Math.max(0, index);
    }

    private float getBubbleHeight() {
        Paint.FontMetricsInt fm = bubbleTextPaint.getFontMetricsInt();
        return (fm.descent - fm.ascent) + AndroidUtilities.dp(6.0f) * 2.0f;
    }

    private static float[] sanitizeStops(float[] stops, float minZoom, float maxZoom) {
        if (stops == null || stops.length == 0) {
            return new float[0];
        }
        float[] filtered = new float[stops.length];
        int count = 0;
        for (float stop : stops) {
            if (Float.isFinite(stop) && stop >= minZoom && stop <= maxZoom) {
                filtered[count++] = stop;
            }
        }
        float[] sorted = Arrays.copyOf(filtered, count);
        Arrays.sort(sorted);
        if (sorted.length < 2) {
            return sorted;
        }
        int unique = 1;
        for (int i = 1; i < sorted.length; i++) {
            if (Float.compare(sorted[i], sorted[unique - 1]) != 0) {
                sorted[unique++] = sorted[i];
            }
        }
        return Arrays.copyOf(sorted, unique);
    }

    public static final class ZoomLookupInterpolator implements TimeInterpolator {
        private static final float STEP = 0.005f;
        private static final float[] VALUES = {0.0f, 8.0E-4f, 0.0016f, 0.0024f, 0.0032f, 0.0057f, 0.0083f, 0.0109f, 0.0134f, 0.0171f, 0.0218f, 0.0266f, 0.0313f, 0.036f, 0.0431f, 0.0506f, 0.0581f, 0.0656f, 0.0733f, 0.0835f, 0.0937f, 0.1055f, 0.1179f, 0.1316f, 0.1466f, 0.1627f, 0.181f, 0.2003f, 0.2226f, 0.2468f, 0.2743f, 0.306f, 0.3408f, 0.3852f, 0.4317f, 0.4787f, 0.5177f, 0.5541f, 0.5834f, 0.6123f, 0.6333f, 0.6542f, 0.6739f, 0.6887f, 0.7035f, 0.7183f, 0.7308f, 0.7412f, 0.7517f, 0.7621f, 0.7725f, 0.7805f, 0.7879f, 0.7953f, 0.8027f, 0.8101f, 0.8175f, 0.823f, 0.8283f, 0.8336f, 0.8388f, 0.8441f, 0.8494f, 0.8546f, 0.8592f, 0.863f, 0.8667f, 0.8705f, 0.8743f, 0.878f, 0.8818f, 0.8856f, 0.8893f, 0.8927f, 0.8953f, 0.898f, 0.9007f, 0.9034f, 0.9061f, 0.9087f, 0.9114f, 0.9141f, 0.9168f, 0.9194f, 0.9218f, 0.9236f, 0.9255f, 0.9274f, 0.9293f, 0.9312f, 0.9331f, 0.935f, 0.9368f, 0.9387f, 0.9406f, 0.9425f, 0.9444f, 0.946f, 0.9473f, 0.9486f, 0.9499f, 0.9512f, 0.9525f, 0.9538f, 0.9551f, 0.9564f, 0.9577f, 0.959f, 0.9603f, 0.9616f, 0.9629f, 0.9642f, 0.9654f, 0.9663f, 0.9672f, 0.968f, 0.9689f, 0.9697f, 0.9706f, 0.9715f, 0.9723f, 0.9732f, 0.9741f, 0.9749f, 0.9758f, 0.9766f, 0.9775f, 0.9784f, 0.9792f, 0.9801f, 0.9808f, 0.9813f, 0.9819f, 0.9824f, 0.9829f, 0.9835f, 0.984f, 0.9845f, 0.985f, 0.9856f, 0.9861f, 0.9866f, 0.9872f, 0.9877f, 0.9882f, 0.9887f, 0.9893f, 0.9898f, 0.9903f, 0.9909f, 0.9914f, 0.9917f, 0.992f, 0.9922f, 0.9925f, 0.9928f, 0.9931f, 0.9933f, 0.9936f, 0.9939f, 0.9942f, 0.9944f, 0.9947f, 0.995f, 0.9953f, 0.9955f, 0.9958f, 0.9961f, 0.9964f, 0.9966f, 0.9969f, 0.9972f, 0.9975f, 0.9977f, 0.9979f, 0.9981f, 0.9982f, 0.9983f, 0.9984f, 0.9986f, 0.9987f, 0.9988f, 0.9989f, 0.9991f, 0.9992f, 0.9993f, 0.9994f, 0.9995f, 0.9995f, 0.9996f, 0.9996f, 0.9997f, 0.9997f, 0.9997f, 0.9998f, 0.9998f, 0.9998f, 0.9999f, 0.9999f, 1.0f, 1.0f};

        private ZoomLookupInterpolator() {
        }

        @Override
        public float getInterpolation(float input) {
            if (input <= 0.0f) {
                return 0.0f;
            }
            if (input >= 1.0f) {
                return 1.0f;
            }
            int index = Math.min((int) (200.0f * input), 199);
            float fraction = (input - index * STEP) / STEP;
            float start = VALUES[index];
            return start + fraction * (VALUES[index + 1] - start);
        }
    }
}
