package com.exteragram.messenger.preferences.chats.components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.TextPaint;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable;
import com.exteragram.messenger.preferences.components.PreviewColors;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

public abstract class StickerShapeCell extends LinearLayout implements CustomPreferenceCell {

    private final StickerShape[] stickerShape = new StickerShape[3];

    public abstract void updateStickerPreview();

    public StickerShapeCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(10), AndroidUtilities.dp(13), 0);

        for (int i = 0; i < 3; i++) {
            final int shape = i;
            stickerShape[i] = new StickerShape(context, i == 1, i == 2);
            ScaleStateListAnimator.apply(stickerShape[i], 0.03f, 1.5f);
            addView(stickerShape[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, 8, 0, 8, 0));
            stickerShape[i].setOnClickListener(v -> {
                for (StickerShape s : stickerShape) {
                    s.setSelected(v == s, true);
                }
                ExteraConfig.setStickerShape(shape);
                updateStickerPreview();
            });
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (int i = 0; i < 3; i++) {
            stickerShape[i].invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(130), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof StickerShapeCell && stickerShape == ((StickerShapeCell) o).stickerShape;
    }

    public static class StickerShape extends FrameLayout {

        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final PreviewBackgroundDrawable backgroundDrawable = new PreviewBackgroundDrawable(10);
        private final boolean isRounded;
        private final boolean isRoundedAsMsg;
        private float progress;

        public StickerShape(Context context, boolean rounded, boolean roundedAsMsg) {
            super(context);
            setWillNotDraw(false);
            isRounded = rounded;
            isRoundedAsMsg = roundedAsMsg;
            textPaint.setTextSize(AndroidUtilities.dp(13));

            int current = ExteraConfig.getStickerShape();
            boolean selected = !rounded && !roundedAsMsg && current == 0 || rounded && current == 1 || roundedAsMsg && current == 2;
            setSelected(selected, false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(80));
            backgroundDrawable.draw(canvas);

            String text = LocaleController.getString(isRounded ? R.string.StickerShapeRounded : isRoundedAsMsg ? R.string.StickerShapeRoundedMsg : R.string.Default);
            canvas.drawText(text, (getMeasuredWidth() - (int) Math.ceil(textPaint.measureText(text))) >> 1, AndroidUtilities.dp(102), textPaint);

            rect.set(AndroidUtilities.dp(10), AndroidUtilities.dp(10), getMeasuredWidth() - AndroidUtilities.dp(10), AndroidUtilities.dp(70));
            Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(false));
            if (!isRounded && !isRoundedAsMsg) {
                canvas.drawRoundRect(rect, 0, 0, Theme.dialogs_onlineCirclePaint);
            } else if (isRounded) {
                canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Theme.dialogs_onlineCirclePaint);
            } else {
                Rect bounds = new Rect();
                rect.round(bounds);
                int radius = AndroidUtilities.dp(SharedConfig.bubbleRadius);
                float r = radius;
                float smallR = radius / 3;
                ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(new float[]{r, r, r, r, r, r, smallR, smallR}, null, null));
                shapeDrawable.getPaint().setColor(Theme.dialogs_onlineCirclePaint.getColor());
                shapeDrawable.setBounds(bounds);
                shapeDrawable.draw(canvas);
            }
        }

        private void setProgress(float value) {
            progress = value;
            textPaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Theme.getColor(Theme.key_windowBackgroundWhiteValueText), value));
            textPaint.setTypeface(value >= 0.5f ? AndroidUtilities.bold() : AndroidUtilities.regular());
            backgroundDrawable.setSelectionProgress(value);
            invalidate();
        }

        private void setSelected(boolean selected, boolean animated) {
            float to = selected ? 1 : 0;
            if (to == progress && animated) {
                return;
            }
            if (animated) {
                ValueAnimator animator = ValueAnimator.ofFloat(progress, to).setDuration(250);
                animator.setInterpolator(Easings.easeInOutQuad);
                animator.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
                animator.start();
            } else {
                setProgress(to);
            }
        }
    }
}
