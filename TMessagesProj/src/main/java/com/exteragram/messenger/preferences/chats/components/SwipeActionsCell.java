package com.exteragram.messenger.preferences.chats.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.utils.chats.SwipeAction;
import com.exteragram.messenger.utils.chats.SwipeActionsHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;
import java.util.List;

public class SwipeActionsCell extends FrameLayout implements CustomPreferenceCell {

    private static final long STEP_DURATION = 1100;

    private final List<SwipeAction> actions = new ArrayList<>();
    private final SwipeActionsHelper helper;
    private final ChatMessageCell messageCell;
    private final Drawable monetBackgroundDrawable;
    private final Drawable shadowDrawable;

    private Drawable backgroundDrawable;
    private BackgroundGradientDrawable.Disposable backgroundGradientDisposable;
    private ValueAnimator cycle;
    private List<SwipeAction> pending;
    private boolean looped;
    private int selected;
    private float slide;

    public SwipeActionsCell(Context context) {
        super(context);
        setWillNotDraw(false);

        monetBackgroundDrawable = new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray));
        shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
        helper = new SwipeActionsHelper(this, null);

        TLRPC.TL_message message = new TLRPC.TL_message();
        message.message = LocaleController.getString(R.string.SwipeActionsPreviewMessage);
        message.date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;
        message.dialog_id = 1;
        message.flags = 259;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        message.id = 1;
        message.out = false;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = 0;

        TLRPC.TL_message replyMessage = new TLRPC.TL_message();
        replyMessage.message = LocaleController.getString(R.string.SwipeActionsPreviewReply);
        replyMessage.date = message.date;
        replyMessage.dialog_id = 1;
        replyMessage.flags = 259;
        replyMessage.from_id = new TLRPC.TL_peerUser();
        replyMessage.from_id.user_id = message.from_id.user_id;
        replyMessage.id = 2;
        replyMessage.media = new TLRPC.TL_messageMediaEmpty();
        replyMessage.out = true;
        replyMessage.peer_id = new TLRPC.TL_peerUser();
        replyMessage.peer_id.user_id = 1;

        message.reply_to = new TLRPC.TL_messageReplyHeader();
        message.reply_to.flags |= 16;
        message.reply_to.reply_to_msg_id = replyMessage.id;
        message.flags |= 8;

        MessageObject messageObject = new MessageObject(UserConfig.selectedAccount, message, true, false);
        messageObject.replyMessageObject = new MessageObject(UserConfig.selectedAccount, replyMessage, true, false);
        messageObject.customReplyName = LocaleController.getString(R.string.FromYou);
        messageObject.resetLayout();
        messageObject.eventId = 1;

        messageCell = new ChatMessageCell(context, UserConfig.selectedAccount);
        messageCell.isChat = false;
        messageCell.setFullyDraw(true);
        messageCell.setMessageObject(messageObject, null, false, false, false);
        addView(messageCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        updateActions();
    }

    public void updateActions() {
        List<SwipeAction> enabled = SwipeAction.enabled();
        if (enabled.equals(actions) && looped == ExteraConfig.getSwipeActionsLoop()) {
            return;
        }
        if (cycle == null || actions.isEmpty() || enabled.isEmpty()) {
            applyActions(enabled);
        } else {
            pending = enabled;
        }
    }

    private void applyActions(List<SwipeAction> list) {
        actions.clear();
        actions.addAll(list);
        looped = ExteraConfig.getSwipeActionsLoop();
        selected = 0;
        helper.start(actions);
        helper.select(0);
        if (actions.contains(SwipeAction.REACTION)) {
            String emoticon = SwipeAction.quickReactionEmoticon(UserConfig.selectedAccount);
            if (emoticon != null) {
                ReactionsLayoutInBubble.VisibleReaction reaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(emoticon);
                helper.setReaction(UserConfig.selectedAccount, reaction.emojicon, reaction.documentId);
            }
        }
        restartCycle();
    }

    private void restartCycle() {
        if (cycle != null) {
            cycle.cancel();
            cycle = null;
        }
        slide = 0;
        messageCell.setSlidingOffset(0);
        if (actions.isEmpty() || !isAttachedToWindow()) {
            invalidate();
            return;
        }
        final int steps = actions.size() + (looped && actions.size() > 1 ? 1 : 0);
        cycle = ValueAnimator.ofFloat(0, 1);
        cycle.setDuration((steps + 2) * STEP_DURATION);
        cycle.setInterpolator(new LinearInterpolator());
        cycle.setRepeatCount(ValueAnimator.INFINITE);
        cycle.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            float step = 1f / (steps + 2);
            if (value < step) {
                slide = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(value / step);
            } else if (value > 1f - step) {
                slide = 1f - CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation((value - (1f - step)) / step);
            } else {
                slide = 1f;
            }
            if (value >= step && value <= 1f - step) {
                int index = Math.max(0, Math.min(steps - 1, (int) ((value - step) / step))) % actions.size();
                if (index != selected) {
                    selected = index;
                    helper.select(index);
                }
            }
            messageCell.setSlidingOffset(-slide * AndroidUtilities.dp(72));
            invalidate();
        });
        cycle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                if (pending != null) {
                    final List<SwipeAction> next = pending;
                    pending = null;
                    AndroidUtilities.runOnUIThread(() -> applyActions(next));
                } else {
                    selected = 0;
                    helper.start(actions);
                    helper.select(0);
                }
            }
        });
        cycle.start();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (actions.isEmpty()) {
            return;
        }
        float offset = -slide * AndroidUtilities.dp(72);
        helper.draw(canvas, offset, false, messageCell.getTop() + messageCell.getMeasuredHeight() / 2f, messageCell.getBackgroundDrawableRight() + offset);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable newDrawable = Theme.isCurrentThemeMonet() ? monetBackgroundDrawable : Theme.getCachedWallpaperNonBlocking();
        if (newDrawable != backgroundDrawable && newDrawable != null) {
            if (backgroundGradientDisposable != null) {
                backgroundGradientDisposable.dispose();
                backgroundGradientDisposable = null;
            }
            backgroundDrawable = newDrawable;
        }
        if (backgroundDrawable != null) {
            backgroundDrawable.setAlpha(backgroundDrawable == monetBackgroundDrawable ? 150 : 255);
            drawWallpaper(canvas, backgroundDrawable);
        }
        shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        shadowDrawable.draw(canvas);
    }

    private void drawWallpaper(Canvas canvas, Drawable drawable) {
        if (drawable instanceof ColorDrawable || drawable instanceof GradientDrawable || drawable instanceof MotionBackgroundDrawable) {
            drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            if (drawable instanceof BackgroundGradientDrawable) {
                backgroundGradientDisposable = ((BackgroundGradientDrawable) drawable).drawExactBoundsSize(canvas, this);
            } else {
                drawable.draw(canvas);
            }
        } else if (drawable instanceof BitmapDrawable) {
            canvas.save();
            if (((BitmapDrawable) drawable).getTileModeX() == Shader.TileMode.REPEAT) {
                float scale = 2.0f / AndroidUtilities.density;
                canvas.scale(scale, scale);
                drawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
            } else {
                float scale = Math.max((float) getMeasuredWidth() / drawable.getIntrinsicWidth(), (float) getMeasuredHeight() / drawable.getIntrinsicHeight());
                int width = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                int height = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                int x = (getMeasuredWidth() - width) / 2;
                int y = (getMeasuredHeight() - height) / 2;
                canvas.clipRect(0, 0, width, getMeasuredHeight());
                drawable.setBounds(x, y, x + width, y + height);
            }
            drawable.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(150), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (cycle == null) {
            restartCycle();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (cycle != null) {
            cycle.cancel();
            cycle = null;
        }
        helper.detach();
        if (backgroundGradientDisposable != null) {
            backgroundGradientDisposable.dispose();
            backgroundGradientDisposable = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof SwipeActionsCell && actions.equals(((SwipeActionsCell) o).actions);
    }
}
