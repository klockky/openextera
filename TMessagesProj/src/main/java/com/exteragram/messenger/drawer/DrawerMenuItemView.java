package com.exteragram.messenger.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.exteragram.messenger.MainMenuItem;
import com.exteragram.messenger.feed.FeedController;
import com.exteragram.messenger.utils.ui.UIUtil;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerMenuItemView extends FrameLayout {

    private static final int COLOR_KEY_SELECTOR = Theme.key_listSelector;
    private static final int COLOR_KEY_ICON = Theme.key_windowBackgroundWhiteGrayIcon;
    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;

    private final ImageView iconView;
    private final TextView textView;
    private final DrawerUnreadBadge unreadBadge;
    private int layoutButtonId = Integer.MIN_VALUE;

    public DrawerMenuItemView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayoutParams(new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(48)));
        setBackground(createSelectorDrawable());
        UIUtil.applyScaleStateListAnimator(this, 16.0f, false, false, 2, 0.04f, 1.5f);

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setColorFilter(createIconColorFilter());
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 20, 0, 0, 0));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 68, 0, 16, 0));

        unreadBadge = new DrawerUnreadBadge();
    }

    public void setMenuItem(int layoutButtonId, int account, int iconRes, CharSequence text) {
        this.layoutButtonId = layoutButtonId;
        iconView.setImageResource(iconRes);
        textView.setText(text);
        updateUnreadCounter(account);
    }

    public void updateUnreadCounter(int account) {
        unreadBadge.bind(resolveUnreadCounter(account), textView);
        invalidate();
    }

    private int resolveUnreadCounter(int account) {
        if (layoutButtonId == MainMenuItem.ARCHIVE.getId()) {
            return MessagesStorage.getInstance(account).getArchiveUnreadCount();
        }
        if (layoutButtonId == MainMenuItem.FEED.getId()) {
            return FeedController.getInstance(account).getUnreadCount();
        }
        return 0;
    }

    public void updateColors() {
        setBackground(createSelectorDrawable());
        iconView.setColorFilter(createIconColorFilter());
        textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        unreadBadge.draw(this, canvas);
    }

    private static Drawable createSelectorDrawable() {
        return Theme.createRadSelectorDrawable(Theme.getColor(COLOR_KEY_SELECTOR), 12, 12);
    }

    private static PorterDuffColorFilter createIconColorFilter() {
        return new PorterDuffColorFilter(Theme.getColor(COLOR_KEY_ICON), PorterDuff.Mode.SRC_IN);
    }
}
