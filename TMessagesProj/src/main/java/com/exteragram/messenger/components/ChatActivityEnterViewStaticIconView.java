package com.exteragram.messenger.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class ChatActivityEnterViewStaticIconView extends FrameLayout {

    private final ImageView[] buttonViews = new ImageView[2];
    private final int sizeDp;
    private AnimatorSet buttonsAnimation;
    private State currentState;

    public ChatActivityEnterViewStaticIconView(Context context, ChatActivityEnterView enterView) {
        this(context, enterView, 32);
    }

    public ChatActivityEnterViewStaticIconView(Context context, ChatActivityEnterView enterView, int sizeDp) {
        super(context);
        setWillNotDraw(false);
        this.sizeDp = sizeDp;
        for (int i = 0; i < 2; i++) {
            buttonViews[i] = new ImageView(context);
            buttonViews[i].setColorFilter(new PorterDuffColorFilter(enterView.getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            buttonViews[i].setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(buttonViews[i], LayoutHelper.createFrame(sizeDp, sizeDp, Gravity.CENTER));
        }
        buttonViews[0].setVisibility(VISIBLE);
        buttonViews[1].setVisibility(GONE);
        buttonViews[1].setScaleX(0.1f);
        buttonViews[1].setScaleY(0.1f);
    }

    public void setColorFilter(ColorFilter colorFilter) {
        buttonViews[0].setColorFilter(colorFilter);
        buttonViews[1].setColorFilter(colorFilter);
    }

    public State getCurrentState() {
        return currentState;
    }

    public void setState(State state, boolean animate) {
        if (animate && state == currentState) {
            return;
        }
        State fromState = currentState;
        currentState = state;
        if (!animate || fromState == null) {
            if (buttonsAnimation != null) {
                buttonsAnimation.cancel();
                buttonsAnimation = null;
            }
            buttonViews[0].setImageResource(currentState.resource);
            buttonViews[0].setAlpha(1.0f);
            buttonViews[0].setScaleX(1.0f);
            buttonViews[0].setScaleY(1.0f);
            buttonViews[0].setVisibility(VISIBLE);
            buttonViews[1].setVisibility(GONE);
        } else {
            if (buttonsAnimation != null) {
                buttonsAnimation.cancel();
            }
            buttonViews[1].setVisibility(VISIBLE);
            buttonViews[1].setImageResource(currentState.resource);
            buttonViews[0].setAlpha(1.0f);
            buttonViews[0].setScaleX(1.0f);
            buttonViews[0].setScaleY(1.0f);
            buttonViews[1].setAlpha(0.0f);
            buttonViews[1].setScaleX(0.1f);
            buttonViews[1].setScaleY(0.1f);

            buttonsAnimation = new AnimatorSet();
            buttonsAnimation.playTogether(
                    ObjectAnimator.ofFloat(buttonViews[0], View.SCALE_X, 0.1f),
                    ObjectAnimator.ofFloat(buttonViews[0], View.SCALE_Y, 0.1f),
                    ObjectAnimator.ofFloat(buttonViews[0], View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(buttonViews[1], View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(buttonViews[1], View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(buttonViews[1], View.ALPHA, 1.0f));
            buttonsAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(buttonsAnimation)) {
                        buttonsAnimation = null;
                        ImageView temp = buttonViews[1];
                        buttonViews[1] = buttonViews[0];
                        buttonViews[0] = temp;
                        buttonViews[1].setVisibility(INVISIBLE);
                        buttonViews[1].setAlpha(0.0f);
                        buttonViews[1].setScaleX(0.1f);
                        buttonViews[1].setScaleY(0.1f);
                    }
                }
            });
            buttonsAnimation.setDuration(200);
            buttonsAnimation.start();
        }
        switch (state) {
            case VOICE:
                setContentDescription(LocaleController.getString(R.string.AccDescrVoiceMessage));
                break;
            case VIDEO:
                setContentDescription(LocaleController.getString(R.string.AccDescrVideoMessage));
                break;
        }
    }

    public enum State {
        VOICE(R.drawable.input_mic_pressed),
        VIDEO(R.drawable.input_video_pressed),
        STICKER(R.drawable.msg_sticker),
        KEYBOARD(R.drawable.input_keyboard),
        SMILE(R.drawable.input_smile),
        GIF(R.drawable.msg_gif);

        final int resource;

        State(int resource) {
            this.resource = resource;
        }
    }
}
