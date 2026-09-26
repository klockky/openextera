package com.exteragram.messenger.components;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.PopupWindow;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.utils.ui.FolderIcons;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.concurrent.atomic.AtomicReference;

public abstract class IconSelectorAlert {

    private static final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnIconSelectedListener {
        void onIconSelected(String emoticon);
    }

    public static void show(BaseFragment fragment, View view, String selectedIcon, OnIconSelectedListener listener) {
        selectedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        Activity context = fragment.getParentActivity();

        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert3, null);
        Rect backgroundPaddings = new Rect();
        fragment.getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert3).mutate().getPadding(backgroundPaddings);
        popupLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        int[] location = new int[2];
        view.getLocationInWindow(location);
        int popupX = location[0] - AndroidUtilities.dp(8) - backgroundPaddings.left + view.getMeasuredWidth();
        int popupY = location[1] - AndroidUtilities.dp(8) - backgroundPaddings.top + view.getMeasuredHeight();

        AtomicReference<ActionBarPopupWindow> popupWindowRef = new AtomicReference<>();

        GridLayout gridLayout = new GridLayout(context);
        int columns = 6;
        while (AndroidUtilities.displaySize.x - popupX < columns * 48 + AndroidUtilities.dp(8)) {
            columns--;
        }
        gridLayout.setColumnCount(columns);

        for (String icon : FolderIcons.folderIcons.keySet().toArray(new String[0])) {
            FrameLayout button = new FrameLayout(context) {
                @Override
                @SuppressLint("DrawAllocation")
                protected void onDraw(Canvas canvas) {
                    int pad = AndroidUtilities.dp(6);
                    Drawable drawable = ContextCompat.getDrawable(context, FolderIcons.getTabIcon(icon));
                    drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(isSelected() ? Theme.key_windowBackgroundWhiteValueText : Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
                    drawable.setBounds(pad, pad, getMeasuredWidth() - pad, getMeasuredHeight() - pad);
                    if (isSelected()) {
                        RectF rect = AndroidUtilities.rectTmp;
                        rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        selectedPaint.setAlpha(40);
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(7), AndroidUtilities.dp(7), selectedPaint);
                    }
                    drawable.draw(canvas);
                    super.onDraw(canvas);
                }
            };
            button.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 7, 7));
            button.setSelected(icon.equals(selectedIcon));
            button.setOnClickListener(v -> {
                if (selectedIcon.equals(icon)) {
                    return;
                }
                if (popupWindowRef.get() != null) {
                    popupWindowRef.getAndSet(null).dismiss();
                }
                listener.onIconSelected(icon);
            });
            gridLayout.addView(button, LayoutHelper.createFrame(48, 48, Gravity.CENTER, 1, 1, 1, 1));
        }
        popupLayout.addView(gridLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 4, 4, 4));

        ActionBarPopupWindow popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popupWindowRef.set(popupWindow);
        popupWindow.setPauseNotifications(true);
        popupWindow.setDismissAnimationDuration(220);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setClippingEnabled(true);
        popupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        popupWindow.setFocusable(true);
        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        popupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, popupX, popupY);
        popupWindow.dimBehind();
    }
}
