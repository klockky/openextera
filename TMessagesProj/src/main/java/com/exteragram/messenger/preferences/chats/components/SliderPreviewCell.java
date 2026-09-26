package com.exteragram.messenger.preferences.chats.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.AltSeekbar;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;

import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class SliderPreviewCell extends FrameLayout implements CustomPreferenceCell {

    public interface OnSliderChangedListener {
        void onChanged(float value);
    }

    private final int cellId;
    private final MessagesPreviewCell messagesCell;
    public final AltSeekbar seekBar;

    private int lastWidth;
    private OnSliderChangedListener listener;

    public SliderPreviewCell(INavigationLayout parentLayout, Context context, int cellId, int min, int max, float value, String title, String left, String right, boolean singleMessage) {
        super(context);
        this.cellId = cellId;
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setWillNotDraw(false);

        seekBar = new AltSeekbar(context, progress -> {
            if (listener != null) {
                listener.onChanged(progress);
            }
            invalidate();
        }, min, max, title, left, right);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (singleMessage) {
            messagesCell = new MessagesPreviewCell(context, parentLayout, 1);
        } else {
            messagesCell = new MessagesPreviewCell(context, parentLayout);
        }
        messagesCell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 0, ExteraConfig.getNewSliderStyle() ? 120 : 112, 0, 0));

        setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        seekBar.setProgress(value);
    }

    public SliderPreviewCell setListener(OnSliderChangedListener listener) {
        this.listener = listener;
        return this;
    }

    public OnSliderChangedListener getListener() {
        return listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth != width) {
            lastWidth = width;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        lastWidth = -1;
        messagesCell.refreshMessages();
        seekBar.invalidate();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SliderPreviewCell)) {
            return false;
        }
        SliderPreviewCell that = (SliderPreviewCell) o;
        return cellId == that.cellId && Objects.equals(messagesCell, that.messagesCell) && Objects.equals(seekBar, that.seekBar) && lastWidth == that.lastWidth;
    }
}
