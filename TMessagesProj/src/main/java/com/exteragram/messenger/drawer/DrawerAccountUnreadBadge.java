package com.exteragram.messenger.drawer;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

final class DrawerAccountUnreadBadge {

    private final RectF rect = new RectF();
    private int account = -1;
    private boolean visible;
    private String text;
    private int textWidth;
    private int countWidth;
    private int badgeWidth;

    public void bind(int account, SimpleTextView textView) {
        this.account = account;
        update(textView);
    }

    public void update(SimpleTextView textView) {
        visible = false;
        text = null;
        textWidth = 0;
        countWidth = 0;
        badgeWidth = 0;
        if (account < 0 || UserConfig.getActivatedAccountsCount() <= 1 || !NotificationsController.getInstance(account).showBadgeNumber) {
            textView.setRightPadding(0);
            return;
        }
        int unreadCount = MessagesStorage.getInstance(account).getMainUnreadCount();
        if (unreadCount <= 0) {
            textView.setRightPadding(0);
            return;
        }
        visible = true;
        text = Integer.toString(unreadCount);
        textWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(text));
        countWidth = Math.max(AndroidUtilities.dp(10), textWidth);
        badgeWidth = countWidth + AndroidUtilities.dp(14);
        textView.setRightPadding(badgeWidth + AndroidUtilities.dp(12));
    }

    public void draw(View view, Canvas canvas) {
        if (!visible) {
            return;
        }
        float height = AndroidUtilities.dp(23);
        float top = (view.getMeasuredHeight() - height) / 2f;
        float left = view.getMeasuredWidth() - AndroidUtilities.dp(12.5f) - badgeWidth;
        rect.set(left, top, left + badgeWidth, top + height);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(11.5f), AndroidUtilities.dp(11.5f), Theme.dialogs_countPaint);
        float textY = rect.centerY() - (Theme.dialogs_countTextPaint.descent() + Theme.dialogs_countTextPaint.ascent()) / 2f;
        canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2f, textY, Theme.dialogs_countTextPaint);
    }
}
