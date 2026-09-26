package com.exteragram.messenger.components;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Components.PopupSwipeBackLayout;

public class ChooseSubtitlesLayout {

    public final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
    private final ActionBarMenuSubItem disableItem;

    public interface Callback {
        void onChooseSubtitles();

        void onDisableSubtitles();
    }

    public ChooseSubtitlesLayout(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback) {
        layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        layout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), false, null);
        backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        View gap = new FrameLayout(context);
        gap.setMinimumWidth(AndroidUtilities.dp(196));
        gap.setBackgroundColor(0xff181818);
        layout.addView(gap);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        layoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
        layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(8);
        gap.setLayoutParams(layoutParams);

        ActionBarMenuSubItem chooseItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_folders, LocaleController.getString(R.string.ChooseSubtitles), false, null);
        chooseItem.setColors(0xfffafafa, 0xfffafafa);
        chooseItem.setSelectorColor(0x0fffffff);
        chooseItem.setOnClickListener(view -> callback.onChooseSubtitles());

        disableItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_cancel, LocaleController.getString(R.string.DisableSubtitles), false, null);
        disableItem.setColors(0xfffafafa, 0xfffafafa);
        disableItem.setSelectorColor(0x0fffffff);
        disableItem.setOnClickListener(view -> callback.onDisableSubtitles());
    }

    public void update(boolean hasSubtitles) {
        disableItem.setVisibility(hasSubtitles ? View.VISIBLE : View.GONE);
    }
}
