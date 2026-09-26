package com.exteragram.messenger.utils.chats;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.ScrimOptions;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;

public abstract class GlassMenuHelper {

    public static void captureBlur(BlurredBackgroundSourceBitmap source, BlurredBackgroundDrawableViewFactory factory, View view) {
        ScrimOptions.makeGlobalBlurBitmaps((bitmapBg, bitmapOptions) -> {
            source.setBitmap(bitmapOptions);
            Blur3Utils.checkBitmapSourceMatrixScale(source, view);
            factory.invalidateAllLinkedViews();
        });
    }

    public static boolean isEnabled(Theme.ResourcesProvider resourcesProvider) {
        return ExteraConfig.getGlassMessageMenu() && BlurredBackgroundProviderImpl.checkBlurEnabled(resourcesProvider);
    }

    public static boolean isEnabled(int account, Theme.ResourcesProvider resourcesProvider) {
        return ExteraConfig.getGlassMessageMenu() && BlurredBackgroundProviderImpl.checkBlurEnabled(account, resourcesProvider);
    }

    public static int separatorColor(boolean glass, Theme.ResourcesProvider resourcesProvider) {
        if (!glass) {
            return Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider);
        }
        boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        return Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), dark ? 0.03f : 0.06f);
    }

    public static BlurredBackgroundDrawable createPanelBackground(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, View view) {
        return factory.create(view, true)
                .setColorProvider(scrimMenuBackground(resourcesProvider))
                .setRadius(AndroidUtilities.dp(12))
                .setPadding(AndroidUtilities.dp(8))
                .setHasPadding(true);
    }

    public static BlurredBackgroundDrawable createFill(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, View view) {
        return factory.create(view, true).setColorProvider(scrimMenuBackgroundFill(resourcesProvider));
    }

    public static void applyToPopup(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout layout) {
        layout.setBackground(createPanelBackground(factory, resourcesProvider, layout));
        layout.setGlassBackgroundFactory(factory);
        if (layout.getSwipeBack() != null) {
            layout.getSwipeBack().setForegroundDrawable(createFill(factory, resourcesProvider, layout.getSwipeBack()));
        }
    }

    public static void applyToReusedMenu(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout layout, boolean glass) {
        if (layout == null) {
            return;
        }
        if (glass) {
            applyToPopup(factory, resourcesProvider, layout);
            applyToGaps(layout, separatorColor(true, resourcesProvider));
            return;
        }
        layout.setGlassBackgroundFactory(null);
        layout.setBackgroundDrawable(layout.getResources().getDrawable(R.drawable.popup_fixed_alert4).mutate());
        layout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
        if (layout.getSwipeBack() != null) {
            layout.getSwipeBack().setForegroundDrawable(null);
        }
        applyToGaps(layout, separatorColor(false, resourcesProvider), true);
    }

    public static void applyToReactions(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, ReactionsContainerLayout reactionsLayout) {
        reactionsLayout.setGlassBackground(factory, scrimMenuBackgroundFill(resourcesProvider));
    }

    public static void applyToGaps(View view, int color) {
        applyToGaps(view, color, false);
    }

    public static void applyToGaps(View view, int color, boolean dividerVisible) {
        if (view instanceof ActionBarPopupWindow.GapView) {
            ActionBarPopupWindow.GapView gapView = (ActionBarPopupWindow.GapView) view;
            gapView.setColor(color);
            gapView.setDividerVisible(dividerVisible);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToGaps(group.getChildAt(i), color, dividerVisible);
            }
        }
    }

    public static void draw(BlurredBackgroundDrawable drawable, Canvas canvas, RectF rect, float radius, int alpha) {
        draw(drawable, canvas, rect, radius, alpha, 0, 0);
    }

    public static void draw(BlurredBackgroundDrawable drawable, Canvas canvas, RectF rect, float radius, int alpha, float offsetX, float offsetY) {
        boolean translate = offsetX != 0 || offsetY != 0;
        if (translate) {
            canvas.save();
            canvas.translate(-offsetX, -offsetY);
        }
        drawable.setRadius(radius);
        drawable.setAlpha(alpha);
        drawable.setBounds(Math.round(rect.left + offsetX), Math.round(rect.top + offsetY), Math.round(rect.right + offsetX), Math.round(rect.bottom + offsetY));
        drawable.draw(canvas);
        if (translate) {
            canvas.restore();
        }
    }

    private static BlurredBackgroundProviderBuilder scrimMenuGlass(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((provider, dark) -> {
                    int color = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
                    if (LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
                        return Theme.multAlpha(color, dark ? 0.85f : 0.825f);
                    }
                    return color;
                })
                .setStrokeColorTop(0, 0)
                .setStrokeColorBottom(0, 0)
                .setStrokeColorFull(0, 0)
                .setStrokeWidth(0, 0);
    }

    public static BlurredBackgroundProvider scrimMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return scrimMenuGlass(resourcesProvider)
                .setShadowColor(0x26000000, 0)
                .setShadowLayer(AndroidUtilities.dpf2(4), 0, 0)
                .setShadowAlwaysVisible()
                .build();
    }

    public static BlurredBackgroundProvider scrimMenuBackgroundFill(Theme.ResourcesProvider resourcesProvider) {
        return scrimMenuGlass(resourcesProvider).setShadowColor(0, 0).build();
    }
}
