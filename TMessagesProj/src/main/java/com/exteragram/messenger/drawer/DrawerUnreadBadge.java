package com.exteragram.messenger.drawer;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

final class DrawerUnreadBadge {

    private final RectF rect = new RectF();
    private int counter;
    private boolean visible;
    private String text;
    private int textWidth;
    private int badgeWidth;
    private int defaultTextPaddingEnd = Integer.MIN_VALUE;

    public void bind(int counter, TextView textView) {
        this.counter = counter;
        update(textView);
    }

    public void update(TextView textView) {
        rememberDefaultTextPadding(textView);
        visible = false;
        text = null;
        textWidth = 0;
        badgeWidth = 0;
        if (counter <= 0) {
            restoreTextPadding(textView);
            return;
        }
        visible = true;
        text = Integer.toString(counter);
        textWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(text));
        badgeWidth = Math.max(AndroidUtilities.dp(10), textWidth) + AndroidUtilities.dp(14);
        applyTextPadding(textView, defaultTextPaddingEnd + badgeWidth + AndroidUtilities.dp(12));
    }

    public void draw(View view, Canvas canvas) {
        if (!visible) {
            return;
        }
        float top = AndroidUtilities.dp(12.5f);
        float left = view.getMeasuredWidth() - AndroidUtilities.dp(16.5f) - badgeWidth;
        rect.set(left, top, left + badgeWidth, top + AndroidUtilities.dp(23));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(11.5f), AndroidUtilities.dp(11.5f), Theme.dialogs_countGrayPaint);
        canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2f, top + AndroidUtilities.dp(16), Theme.dialogs_countTextPaint);
    }

    private void rememberDefaultTextPadding(TextView textView) {
        if (defaultTextPaddingEnd != Integer.MIN_VALUE) {
            return;
        }
        defaultTextPaddingEnd = textView.getPaddingEnd();
    }

    private void restoreTextPadding(TextView textView) {
        applyTextPadding(textView, defaultTextPaddingEnd == Integer.MIN_VALUE ? 0 : defaultTextPaddingEnd);
    }

    private void applyTextPadding(TextView textView, int paddingEnd) {
        if (textView.getPaddingEnd() == paddingEnd) {
            return;
        }
        textView.setPaddingRelative(textView.getPaddingStart(), textView.getPaddingTop(), paddingEnd, textView.getPaddingBottom());
    }
}
