package com.exteragram.messenger.components;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

public class SearchPhotoPopupWrapper {

    public ActionBarPopupWindow.ActionBarPopupWindowLayout searchSwipeBackLayout;

    public SearchPhotoPopupWrapper(Context context, PopupSwipeBackLayout swipeBackLayout, Utilities.Callback<ReverseImageSearchSheet.Provider> callback) {
        searchSwipeBackLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        searchSwipeBackLayout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(searchSwipeBackLayout, R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), false, null);
        backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        FrameLayout gap = new FrameLayout(context);
        gap.setMinimumWidth(AndroidUtilities.dp(196));
        gap.setBackgroundColor(0xff181818);
        searchSwipeBackLayout.addView(gap);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.gravity = Gravity.RIGHT;
        }
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(8);
        gap.setLayoutParams(layoutParams);

        addProviderItem("Yandex", ReverseImageSearchSheet.Provider.YANDEX, callback);
        addProviderItem("Google", ReverseImageSearchSheet.Provider.GOOGLE, callback);
        addProviderItem("Bing", ReverseImageSearchSheet.Provider.BING, callback);
        addProviderItem("TinEye", ReverseImageSearchSheet.Provider.TINEYE, callback);

        FrameLayout gap2 = new FrameLayout(context);
        gap2.setMinimumWidth(AndroidUtilities.dp(196));
        gap2.setBackgroundColor(0xff181818);
        searchSwipeBackLayout.addView(gap2);
        gap2.setLayoutParams(layoutParams);

        LinkSpanDrawable.LinksTextView infoView = new LinkSpanDrawable.LinksTextView(context);
        infoView.setTag(R.id.fit_width_tag, 1);
        infoView.setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(8));
        infoView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        infoView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, new DarkThemeResourceProvider()));
        infoView.setMovementMethod(LinkMovementMethod.getInstance());
        infoView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, new DarkThemeResourceProvider()));
        infoView.setText(LocaleController.getString(R.string.SearchPhotoInfo));
        searchSwipeBackLayout.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8, 0, 0));
    }

    private void addProviderItem(String title, ReverseImageSearchSheet.Provider provider, Utilities.Callback<ReverseImageSearchSheet.Provider> callback) {
        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(searchSwipeBackLayout, 0, title, false, null);
        item.setColors(0xfffafafa, 0xfffafafa);
        item.setOnClickListener(view -> callback.run(provider));
        item.setSelectorColor(0x0fffffff);
    }
}
