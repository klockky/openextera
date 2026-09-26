package com.exteragram.messenger.pillstack.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.List;

public class PillStackView extends FrameLayout {

    private final List<BasePill> pills = new ArrayList<>();
    private final float touchSlop;
    private int currentIndex = 0;

    private ValueAnimator currentAnimator;
    private float currentSwipeProgress = 0f;
    private boolean isSwiping;
    private boolean isSwipingUp = false;
    private float startX;
    private float startY;

    private boolean maybeClick;
    private boolean longClickPerformed;
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!maybeClick || isSwiping || pills.isEmpty()) {
                return;
            }
            longClickPerformed = pills.get(currentIndex).onPillLongClicked();
            if (longClickPerformed) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                maybeClick = false;
                for (int i = 0; i < pills.size(); i++) {
                    pills.get(i).setPressed(false);
                }
            }
        }
    };

    private float visibilityFactor = -1f;
    private boolean stackOnScreen = true;

    public PillStackView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
    }

    public void addPill(BasePill pill) {
        pills.add(pill);
        addView(pill);
        if (pills.size() - 1 != currentIndex) {
            pill.setAlpha(0f);
            pill.setScaleX(0.8f);
            pill.setScaleY(0.8f);
            pill.setVisibility(View.GONE);
        } else {
            pill.setVisibility(View.VISIBLE);
            pill.onPillSelected();
        }
        pill.onStackVisibilityChanged(stackOnScreen);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (stackOnScreen == isVisible) {
            return;
        }
        stackOnScreen = isVisible;
        for (BasePill pill : pills) {
            pill.onStackVisibilityChanged(isVisible);
        }
    }

    public int getPillsCount() {
        return pills.size();
    }

    public void setCurrentIndex(int index) {
        if (index < 0 || index >= pills.size() || index == currentIndex) {
            return;
        }
        BasePill oldPill = pills.get(currentIndex);
        oldPill.setVisibility(View.GONE);
        oldPill.onPillUnselected();
        currentIndex = index;
        BasePill newPill = pills.get(index);
        newPill.setVisibility(View.VISIBLE);
        newPill.setAlpha(1f);
        newPill.setScaleX(1f);
        newPill.setScaleY(1f);
        newPill.setTranslationY(0);
        newPill.onPillSelected();
        requestLayout();
    }

    public void clearPills() {
        if (!pills.isEmpty() && currentIndex < pills.size()) {
            pills.get(currentIndex).onPillUnselected();
        }
        pills.clear();
        removeAllViews();
        currentIndex = 0;
    }

    public void setVisibilityFactor(float factor) {
        if (visibilityFactor == factor) {
            return;
        }
        visibilityFactor = factor;
        if (factor > 0.01f) {
            if (getVisibility() != View.VISIBLE) {
                setVisibility(View.VISIBLE);
            }
            setAlpha(visibilityFactor);
            setScaleX(AndroidUtilities.lerp(0.6f, 1f, visibilityFactor));
            setScaleY(AndroidUtilities.lerp(0.6f, 1f, visibilityFactor));
        } else {
            setVisibility(View.GONE);
        }
    }

    public void updateColors() {
        for (BasePill pill : pills) {
            pill.updateColors();
        }
    }

    private void startSwiping(MotionEvent event) {
        isSwiping = true;
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }
        float offset = currentSwipeProgress * getHeight();
        startY = event.getRawY() - (isSwipingUp ? -offset : offset);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (pills.isEmpty()) {
            return super.onInterceptTouchEvent(event);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = event.getRawX();
            startY = event.getRawY();
            isSwiping = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dy = event.getRawY() - startY;
            if (Math.abs(dy) > touchSlop && pills.size() > 1) {
                startSwiping(event);
                return true;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pills.isEmpty()) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                startX = event.getRawX();
                startY = event.getRawY();
                isSwiping = false;
                maybeClick = true;
                longClickPerformed = false;
                removeCallbacks(longPressRunnable);
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                BasePill pill = pills.get(currentIndex);
                pill.setPressed(true);
                pill.drawableHotspotChanged(event.getX() - pill.getLeft(), event.getY() - pill.getTop());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (longClickPerformed) {
                    return true;
                }
                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;
                if (Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop) {
                    maybeClick = false;
                    removeCallbacks(longPressRunnable);
                }
                if (isSwiping) {
                    handleSwipeProgress(dy);
                    return true;
                }
                if (Math.abs(dy) > touchSlop && pills.size() > 1) {
                    startSwiping(event);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(longPressRunnable);
                if (isSwiping) {
                    finishSwipe(event.getRawY() - startY);
                    isSwiping = false;
                } else if (maybeClick && !longClickPerformed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    pills.get(currentIndex).onPillClicked();
                }
                for (int i = 0; i < pills.size(); i++) {
                    pills.get(i).setPressed(false);
                }
                maybeClick = false;
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private int getTargetIndex(boolean up) {
        return up ? currentIndex + 1 : currentIndex - 1;
    }

    private void handleSwipeProgress(float dy) {
        int height = getHeight();
        if (pills.size() <= 1 || height <= 0) {
            return;
        }
        isSwipingUp = dy < 0;
        float progress = Math.abs(dy) / height;
        int targetIndex = getTargetIndex(isSwipingUp);
        if (!PillStackConfig.getInfiniteScrolling() && (targetIndex >= pills.size() || targetIndex < 0)) {
            currentSwipeProgress = progress;
        } else {
            currentSwipeProgress = Math.min(progress, 1f);
        }
        applyProgress(currentSwipeProgress, isSwipingUp);
    }

    private void applyProgress(float progress, boolean up) {
        BasePill currentPill = pills.get(currentIndex);
        int targetIndex = getTargetIndex(up);
        if (PillStackConfig.getInfiniteScrolling()) {
            if (targetIndex >= pills.size()) {
                targetIndex = 0;
            }
            if (targetIndex < 0) {
                targetIndex = pills.size() - 1;
            }
        }
        for (int i = 0; i < pills.size(); i++) {
            if (i != currentIndex && i != targetIndex && pills.get(i).getVisibility() != View.GONE) {
                pills.get(i).setVisibility(View.GONE);
            }
        }
        if (!PillStackConfig.getInfiniteScrolling() && (targetIndex >= pills.size() || targetIndex < 0)) {
            float overscroll = getHeight() * (float) (1.0 - 1.0 / (progress * 0.18f + 1.0));
            currentPill.setTranslationY(up ? -overscroll : overscroll);
            currentPill.setAlpha(1f);
            return;
        }
        progress = Math.min(progress, 1f);
        BasePill targetPill = pills.get(targetIndex);
        if (targetPill.getVisibility() != View.VISIBLE) {
            targetPill.setVisibility(View.VISIBLE);
        }
        float translation = getHeight() * progress;
        currentPill.setTranslationY(up ? -translation : translation);
        currentPill.setAlpha(1f - progress);
        float scaleOffset = 0.2f * progress;
        currentPill.setScaleX(1f - scaleOffset);
        currentPill.setScaleY(1f - scaleOffset);
        targetPill.setScaleX(0.8f + scaleOffset);
        targetPill.setScaleY(0.8f + scaleOffset);
        targetPill.setAlpha(progress);
        float targetStart = up ? getHeight() : -getHeight();
        targetPill.setTranslationY(targetStart - progress * targetStart);
    }

    private void finishSwipe(float dy) {
        int height = getHeight();
        if (height <= 0) {
            cancelSwipe(isSwipingUp);
            return;
        }
        boolean canMove = true;
        if (!PillStackConfig.getInfiniteScrolling()) {
            int targetIndex = getTargetIndex(isSwipingUp);
            if (targetIndex >= pills.size() || targetIndex < 0) {
                canMove = false;
            }
        }
        if (Math.abs(dy) > height * 0.25f && canMove) {
            animateToNextPill(isSwipingUp);
        } else {
            cancelSwipe(isSwipingUp);
        }
    }

    private void animateToNextPill(boolean up) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }
        currentAnimator = ValueAnimator.ofFloat(currentSwipeProgress, 1f);
        currentAnimator.setDuration(250);
        currentAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        currentAnimator.addUpdateListener(animation -> applyProgress((Float) animation.getAnimatedValue(), up));
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                BasePill oldPill = pills.get(currentIndex);
                oldPill.setVisibility(View.GONE);
                oldPill.setPressed(false);
                oldPill.setScaleX(1f);
                oldPill.setScaleY(1f);
                oldPill.onPillUnselected();
                currentIndex = getTargetIndex(up);
                if (PillStackConfig.getInfiniteScrolling()) {
                    if (currentIndex >= pills.size()) {
                        currentIndex = 0;
                    }
                    if (currentIndex < 0) {
                        currentIndex = pills.size() - 1;
                    }
                }
                for (int i = 0; i < pills.size(); i++) {
                    if (i != currentIndex) {
                        pills.get(i).setVisibility(View.GONE);
                    }
                }
                BasePill newPill = pills.get(currentIndex);
                newPill.setVisibility(View.VISIBLE);
                newPill.setScaleX(1f);
                newPill.setScaleY(1f);
                newPill.setTranslationY(0);
                newPill.setAlpha(1f);
                newPill.onPillSelected();
                currentSwipeProgress = 0f;
                PillStackConfig.saveLastActivePillId(newPill.getPillId());
            }
        });
        currentAnimator.start();
    }

    private void cancelSwipe(boolean up) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }
        currentAnimator = ValueAnimator.ofFloat(currentSwipeProgress, 0f);
        currentAnimator.setDuration(200);
        currentAnimator.addUpdateListener(animation -> applyProgress((Float) animation.getAnimatedValue(), up));
        currentAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                for (int i = 0; i < pills.size(); i++) {
                    if (i != currentIndex) {
                        BasePill pill = pills.get(i);
                        pill.setVisibility(View.GONE);
                        pill.setPressed(false);
                        pill.setScaleX(1f);
                        pill.setScaleY(1f);
                    }
                }
                BasePill pill = pills.get(currentIndex);
                pill.setTranslationY(0);
                pill.setAlpha(1f);
                pill.setScaleX(1f);
                pill.setScaleY(1f);
                currentSwipeProgress = 0f;
            }
        });
        currentAnimator.start();
    }
}
