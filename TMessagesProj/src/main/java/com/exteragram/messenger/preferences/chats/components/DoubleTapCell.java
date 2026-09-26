package com.exteragram.messenger.preferences.chats.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewColors;
import com.exteragram.messenger.utils.chats.DoubleTapUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;

public class DoubleTapCell extends LinearLayout implements CustomPreferenceCell {

    private static final int[] ICON_WIDTH = {AndroidUtilities.dp(12), AndroidUtilities.dp(12)};

    private final RectF rect = new RectF();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] circleOutlinePaint = new Paint[2];
    private final MessageDrawable[] messages = new MessageDrawable[]{
            new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false),
            new MessageDrawable(MessageDrawable.TYPE_TEXT, true, false)
    };
    private final ValueAnimator[] animator = new ValueAnimator[2];
    private final ValueAnimator[] circleAnimator = new ValueAnimator[2];
    private final ValueAnimator[] circleSizeAnimator = new ValueAnimator[2];
    private final float[] circleSizeProgress = new float[4];
    private final float[] iconChangingProgress = new float[2];
    private final float[] circleProgress = new float[4];
    private final int[] actionIcon = new int[2];
    private final FrameLayout preview;

    public DoubleTapCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(10));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(1) / 2f);
        outlinePaint.setColor(PreviewColors.getOutlineColor());

        preview = new FrameLayout(context) {
            @SuppressLint("DrawAllocation")
            @Override
            protected void onDraw(Canvas canvas) {
                Rect bounds = new Rect();
                float stroke = outlinePaint.getStrokeWidth() / 2f;
                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        rect.set(AndroidUtilities.dp(8) + stroke, AndroidUtilities.dp(10) + stroke, getMeasuredWidth() / 2f - AndroidUtilities.dp(8) - stroke, AndroidUtilities.dp(75) - stroke);
                    } else {
                        canvas.translate(0, AndroidUtilities.dp(80));
                        rect.set(getMeasuredWidth() / 2f + stroke + AndroidUtilities.dp(8), AndroidUtilities.dp(5) + stroke, getMeasuredWidth() - AndroidUtilities.dp(8) - stroke, AndroidUtilities.dp(70) - stroke);
                    }
                    rect.round(bounds);
                    messages[i].setBounds(bounds);
                    Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getBackgroundColor());
                    messages[i].draw(canvas, Theme.dialogs_onlineCirclePaint);
                    messages[i].draw(canvas, outlinePaint);

                    for (int j = 0; j < 2; j++) {
                        int index = i + j * 2;
                        circleOutlinePaint[j] = new Paint(Paint.ANTI_ALIAS_FLAG);
                        circleOutlinePaint[j].setStyle(Paint.Style.STROKE);
                        circleOutlinePaint[j].setColor(ColorUtils.blendARGB(0, PreviewColors.getMockColor(true), circleProgress[index]));
                        circleOutlinePaint[j].setStrokeWidth(AndroidUtilities.dp(1.5f) * circleProgress[index] * circleProgress[index]);
                        float cx = (i == 0 ? 1 : 3) * getMeasuredWidth() / 4f;
                        float cy = getMeasuredHeight() / 4f + AndroidUtilities.dpf2(i == 0 ? 3 : -2);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(25 - j * 6) * circleSizeProgress[index], circleOutlinePaint[j]);
                    }

                    Drawable drawable = ContextCompat.getDrawable(context, actionIcon[i]);
                    if (drawable == null) {
                        continue;
                    }
                    if (i == 0) {
                        drawable.setBounds(
                                getMeasuredWidth() / 4 - ICON_WIDTH[i],
                                (int) (getMeasuredHeight() / 4 - ICON_WIDTH[i] + AndroidUtilities.dpf2(3)),
                                getMeasuredWidth() / 4 + ICON_WIDTH[i],
                                (int) (getMeasuredHeight() / 4 + ICON_WIDTH[i] + AndroidUtilities.dpf2(3))
                        );
                    } else {
                        drawable.setBounds(
                                getMeasuredWidth() * 3 / 4 - ICON_WIDTH[i],
                                (int) (getMeasuredHeight() / 4 - ICON_WIDTH[i] - AndroidUtilities.dpf2(2)),
                                getMeasuredWidth() * 3 / 4 + ICON_WIDTH[i],
                                (int) (getMeasuredHeight() / 4 + ICON_WIDTH[i] - AndroidUtilities.dpf2(2))
                        );
                    }
                    int inset = AndroidUtilities.dp(4 - iconChangingProgress[i] * 4);
                    Rect iconBounds = drawable.getBounds();
                    drawable.setBounds(iconBounds.left - inset, iconBounds.top - inset, iconBounds.right + inset, iconBounds.bottom + inset);
                    drawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0, Theme.getColor(Theme.key_chats_menuItemIcon), iconChangingProgress[i]), PorterDuff.Mode.MULTIPLY));
                    drawable.draw(canvas);
                }
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateIcons(0, false);
    }

    /**
     * @param skip 0 - update both, 1 - update only incoming, 2 - update only outgoing
     */
    @SuppressLint("Recycle")
    public void updateIcons(int skip, boolean animated) {
        for (int i = 0; i < 2; i++) {
            if (i == 0 && skip == 2 || i == 1 && skip == 1) {
                continue;
            }
            final int side = i;
            if (animated) {
                for (int j = 0; j < 2; j++) {
                    final int circle = j;
                    circleSizeAnimator[circle] = ValueAnimator.ofFloat(0, 1).setDuration(1300);
                    circleSizeAnimator[circle].setStartDelay(60L * circle);
                    circleSizeAnimator[circle].setInterpolator(Easings.easeInOutQuad);
                    circleSizeAnimator[circle].addUpdateListener(a -> {
                        circleSizeProgress[circle * 2 + side] = (float) a.getAnimatedValue();
                        invalidate();
                    });

                    circleAnimator[circle] = ValueAnimator.ofFloat(0, 1).setDuration(700);
                    circleAnimator[circle].setStartDelay(80L * circle + 150);
                    circleAnimator[circle].setInterpolator(Easings.easeInOutQuad);
                    circleAnimator[circle].addUpdateListener(a -> {
                        circleProgress[circle * 2 + side] = (float) a.getAnimatedValue();
                        invalidate();
                    });
                    circleAnimator[circle].addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            circleAnimator[circle].setFloatValues(1, 0);
                            circleAnimator[circle].setDuration(700);
                            circleAnimator[circle].removeAllListeners();
                            circleAnimator[circle].start();
                        }
                    });
                    circleSizeAnimator[circle].start();
                    circleAnimator[circle].start();
                }

                animator[side] = ValueAnimator.ofFloat(1, 0).setDuration(250);
                animator[side].setInterpolator(Easings.easeInOutQuad);
                animator[side].addUpdateListener(a -> {
                    iconChangingProgress[side] = (float) a.getAnimatedValue();
                    invalidate();
                });
                animator[side].addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        actionIcon[side] = getActionIcon(side);
                        animator[side].setFloatValues(0, 1);
                        animator[side].removeAllListeners();
                        animator[side].addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            }
                        });
                        animator[side].start();
                    }
                });
                animator[side].start();
            } else {
                circleSizeProgress[side] = 0;
                circleProgress[side] = 0;
                iconChangingProgress[side] = 1;
                actionIcon[side] = getActionIcon(side);
                invalidate();
            }
        }
    }

    private static int getActionIcon(int side) {
        boolean outgoing = side == 1;
        return DoubleTapUtils.getDoubleTapActionIcon(outgoing ? ExteraConfig.getDoubleTapActionOutOwner() : ExteraConfig.getDoubleTapAction(), outgoing);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (preview != null) {
            preview.invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(170), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof DoubleTapCell && actionIcon == ((DoubleTapCell) o).actionIcon;
    }
}
