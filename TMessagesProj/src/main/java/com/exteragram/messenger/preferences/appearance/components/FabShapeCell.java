package com.exteragram.messenger.preferences.appearance.components;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable;
import com.exteragram.messenger.preferences.components.PreviewColors;
import com.exteragram.messenger.utils.ui.FabUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.Arrays;

public abstract class FabShapeCell extends LinearLayout implements CustomPreferenceCell {

    private final FabShape[] fabShape = new FabShape[2];

    public abstract void rebuildFragments();

    public FabShapeCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(15), AndroidUtilities.dp(13), AndroidUtilities.dp(21));

        for (int i = 0; i < 2; i++) {
            boolean squareFab = i == 1;
            fabShape[i] = new FabShape(context, squareFab);
            ScaleStateListAnimator.apply(fabShape[i], 0.03f, 1.5f);
            addView(fabShape[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, 8, 0, 8, 0));
            fabShape[i].setOnClickListener(v -> {
                for (FabShape shape : fabShape) {
                    shape.setSelected(v == shape, true);
                }
                ExteraConfig.setSquareFab(squareFab);
                rebuildFragments();
            });
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (FabShape shape : fabShape) {
            shape.invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof FabShapeCell) {
            return Arrays.equals(fabShape, ((FabShapeCell) o).fabShape);
        }
        return false;
    }

    public static class FabShape extends FrameLayout {

        private final PreviewBackgroundDrawable backgroundDrawable = new PreviewBackgroundDrawable(12);
        private final RectF rect = new RectF();
        private final boolean squareFab;
        private float progress;

        public FabShape(Context context, boolean squareFab) {
            super(context);
            setWillNotDraw(false);
            this.squareFab = squareFab;
            setBackground(backgroundDrawable);
            setSelected(squareFab == ExteraConfig.getSquareFab(), false);
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(Canvas canvas) {
            int avatarX = AndroidUtilities.dp(22);
            int avatarRadius = avatarX / 2;
            int rowY = AndroidUtilities.dp(21);
            for (int row = 0; row < 2; row++) {
                rowY += AndroidUtilities.dp(row == 0 ? 0 : 32);
                Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(false));
                float avatarSize = avatarRadius * 2;
                canvas.drawRoundRect(avatarX - avatarRadius, rowY - avatarRadius, avatarX + avatarRadius, rowY + avatarRadius,
                        ExteraConfig.getAvatarCorners(avatarSize, true), ExteraConfig.getAvatarCorners(avatarSize, true), Theme.dialogs_onlineCirclePaint);
                for (int line = 0; line < 2; line++) {
                    Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(line == 0));
                    int offset = line * 10;
                    rect.set(AndroidUtilities.dp(41), rowY - AndroidUtilities.dp(7 - offset), getMeasuredWidth() - AndroidUtilities.dp(line == 0 ? 70 : 55), rowY - AndroidUtilities.dp(3 - offset));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
                }
            }

            Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            rect.set(getMeasuredWidth() - AndroidUtilities.dp(42), getMeasuredHeight() - AndroidUtilities.dp(12), getMeasuredWidth() - AndroidUtilities.dp(12), getMeasuredHeight() - AndroidUtilities.dp(42));
            float fabRadius = AndroidUtilities.dp(squareFab ? 9 : 100);
            canvas.drawRoundRect(rect, fabRadius, fabRadius, Theme.dialogs_onlineCirclePaint);

            Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.filled_fab_compose_32);
            if (icon != null) {
                icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN));
                float scale = rect.width() / AndroidUtilities.dp(FabUiHelper.getFabSizeDp());
                int width = Math.round(icon.getIntrinsicWidth() * scale);
                int height = Math.round(icon.getIntrinsicHeight() * scale);
                int left = (int) (rect.centerX() - width / 2f);
                int top = (int) (rect.centerY() - height / 2f);
                icon.setBounds(left, top, left + width, top + height);
                icon.draw(canvas);
            }
        }

        private void setProgress(float progress) {
            this.progress = progress;
            backgroundDrawable.setSelectionProgress(progress);
        }

        private void setSelected(boolean selected, boolean animated) {
            float target = selected ? 1f : 0f;
            if (target == progress && animated) {
                return;
            }
            if (animated) {
                ValueAnimator animator = ValueAnimator.ofFloat(progress, target).setDuration(250);
                animator.setInterpolator(Easings.easeInOutQuad);
                animator.addUpdateListener(animation -> setProgress((float) animation.getAnimatedValue()));
                animator.start();
            } else {
                setProgress(target);
            }
        }
    }
}
