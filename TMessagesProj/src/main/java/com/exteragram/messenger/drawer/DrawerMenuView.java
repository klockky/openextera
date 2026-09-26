package com.exteragram.messenger.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.MainMenuItem;
import com.exteragram.messenger.utils.chats.MainMenuHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

public class DrawerMenuView extends ScrollView {

    private static final float DIVIDER_HEIGHT_DP = 1f / AndroidUtilities.density;
    private static final int COLOR_KEY_BACKGROUND = Theme.key_windowBackgroundWhite;

    private final LinearLayout container;
    private final Paint topGradientPaint = new Paint();
    private LinearGradient topGradient;
    private int lastGradientColor;
    private Runnable onItemClick;

    public DrawerMenuView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8) + AndroidUtilities.navigationBarHeight);
        addView(container, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        updateGradient();
    }

    public void setOnItemClick(Runnable onItemClick) {
        this.onItemClick = onItemClick;
    }

    public void clearMenu() {
        container.removeAllViews();
    }

    public void rebuildMenu(int account, BaseFragment fragment) {
        clearMenu();
        MainMenuHelper.MenuContext menuContext = MainMenuHelper.createMenuContext(account, fragment);
        boolean hasItems = false;
        boolean pendingDivider = false;
        for (int i = 0; i < ExteraConfig.getMainMenuLayout().size(); i++) {
            Integer id = ExteraConfig.getMainMenuLayout().get(i);
            if (id == MainMenuItem.DIVIDER.getId()) {
                if (hasItems) {
                    pendingDivider = true;
                }
                continue;
            }
            List<MainMenuHelper.MenuItemInfo> items = MainMenuHelper.resolveDrawerMenuItems(id, menuContext);
            if (items.isEmpty()) {
                continue;
            }
            if (pendingDivider) {
                View divider = new View(getContext());
                divider.setBackgroundColor(Theme.getDividerColor(null));
                container.addView(divider, createDividerLayoutParams());
                pendingDivider = false;
            }
            for (MainMenuHelper.MenuItemInfo item : items) {
                DrawerMenuItemView itemView = new DrawerMenuItemView(getContext());
                itemView.setMenuItem(id, account, item.iconRes(), item.text());
                itemView.setOnClickListener(v -> {
                    if (onItemClick != null) {
                        onItemClick.run();
                    }
                    if (item.onClick() != null) {
                        item.onClick().run();
                    }
                });
                if (item.onLongClick() != null) {
                    itemView.setOnLongClickListener(v -> {
                        if (onItemClick != null) {
                            onItemClick.run();
                        }
                        item.onLongClick().run();
                        return true;
                    });
                }
                container.addView(itemView);
            }
            hasItems = true;
        }
    }

    public void updateUnreadCounters(int account) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof DrawerMenuItemView) {
                ((DrawerMenuItemView) child).updateUnreadCounter(account);
            }
        }
    }

    public void updateColors() {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof DrawerMenuItemView) {
                ((DrawerMenuItemView) child).updateColors();
            } else {
                child.setBackgroundColor(Theme.getDividerColor(null));
            }
        }
        updateGradient();
    }

    private void updateGradient() {
        int color = Theme.getColor(COLOR_KEY_BACKGROUND);
        if (topGradient == null || color != lastGradientColor) {
            lastGradientColor = color;
            topGradient = new LinearGradient(0, 0, 0, AndroidUtilities.dp(16), new int[]{color, color & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            topGradientPaint.setShader(topGradient);
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (getScrollY() > 0) {
            canvas.save();
            canvas.translate(0, getScrollY());
            canvas.drawRect(0, 0, getWidth(), AndroidUtilities.dp(16), topGradientPaint);
            canvas.restore();
        }
    }

    private static LinearLayout.LayoutParams createDividerLayoutParams() {
        return LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, DIVIDER_HEIGHT_DP, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 12, 8, 12, 8);
    }
}
