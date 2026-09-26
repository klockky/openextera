package com.exteragram.messenger.utils.ui;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

public abstract class WindowTransitionAnimationHelper {

    private static final float DURATION = 450f;

    public static float getDuration() {
        float scale;
        try {
            scale = Settings.Global.getFloat(ApplicationLoader.applicationContext.getContentResolver(), Settings.Global.TRANSITION_ANIMATION_SCALE, 1f);
        } catch (Exception e) {
            scale = 1f;
        }
        return Math.max(1f, scale * DURATION);
    }

    public static float getSlideDistance() {
        return AndroidUtilities.dp(96);
    }

    public static void apply(View enteringView, View exitingView, boolean opening, float progress) {
        float clamped = Utilities.clamp01(progress);
        float distance = AndroidUtilities.dp(96);
        float offset = Math.round(CubicBezierInterpolator.Emphasized.getInterpolation(clamped) * distance);
        if (enteringView != null) {
            enteringView.setTranslationX(opening ? distance - offset : offset - distance);
            enteringView.setAlpha(opening ? fade(clamped, 50f, 83f) : 1f);
        }
        if (exitingView != null) {
            exitingView.setTranslationX(opening ? -offset : offset);
            exitingView.setAlpha(opening ? 1f : 1f - fade(clamped, 35f, 83f));
        }
    }

    private static float fade(float progress, float delayMs, float durationMs) {
        return Utilities.clamp01((progress * DURATION - delayMs) / durationMs);
    }

    @RequiresApi(29)
    public static final class WindowEdgeExtension {

        private final RenderNode node = new RenderNode("windowEdgeExtension");
        private boolean captured;

        public boolean isCaptured() {
            return captured;
        }

        public boolean capture(View view, boolean leftEdge) {
            int width = view.getWidth();
            int height = view.getHeight();
            if (width <= 0 || height <= 0) {
                return false;
            }
            node.setPosition(0, 0, 1, height);
            node.setPivotX(0);
            node.setPivotY(0);
            RecordingCanvas canvas = node.beginRecording(1, height);
            try {
                canvas.translate(leftEdge ? 0 : -(width - 1), 0);
                view.draw(canvas);
            } finally {
                node.endRecording();
            }
            node.setUseCompositingLayer(true, null);
            captured = true;
            return true;
        }

        public void draw(Canvas canvas, float x, float width, float alpha) {
            if (!captured || width <= 0 || alpha <= 0 || !(canvas instanceof RecordingCanvas)) {
                return;
            }
            node.setScaleX(width);
            node.setTranslationX(x);
            node.setAlpha(alpha);
            canvas.drawRenderNode(node);
        }

        public void release() {
            if (captured) {
                captured = false;
                node.setUseCompositingLayer(false, null);
                node.discardDisplayList();
            }
        }
    }
}
