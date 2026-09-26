package com.exteragram.messenger.utils.ui;

import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.config.BottomNavigationBar;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.MainTabsLayout;

public abstract class MainTabsUiHelper {

    public static float getMaterial3MainTabIconTopDp() {
        return 10f;
    }

    public static boolean isMaterial3NavigationBar() {
        return ExteraConfig.getNewNavigationBarStyle();
    }

    public static int getTabsViewHeightDp() {
        return isMaterial3NavigationBar() ? 64 : 72;
    }

    public static int getTabsFabOffsetDp() {
        return 64;
    }

    public static int getAdditionalNavigationBarHeight(boolean hasTabs) {
        if (!hasTabs || BottomNavigationBar.hidden() || BottomNavigationBar.floating()) {
            return 0;
        }
        return AndroidUtilities.dp(getTabsViewHeightDp());
    }

    public static int getFloatingTabsPadding(boolean hasTabs) {
        if (hasTabs && BottomNavigationBar.floating()) {
            return AndroidUtilities.dp(getTabsViewHeightDp());
        }
        return 0;
    }

    public static int getTabsFabOffset(boolean hasTabs) {
        if (hasTabs && BottomNavigationBar.visible()) {
            return AndroidUtilities.dp(getTabsFabOffsetDp());
        }
        return 0;
    }

    private static int getTabsViewHeight(int bottomInset) {
        if (isMaterial3NavigationBar()) {
            return AndroidUtilities.dp(64) + bottomInset;
        }
        return AndroidUtilities.dp(72);
    }

    private static int getTabsInnerPaddingVertical() {
        if (isMaterial3NavigationBar()) {
            return 0;
        }
        return AndroidUtilities.dp(12);
    }

    public static void applyTabsLayoutStyle(MainTabsLayout tabsLayout) {
        boolean material3 = isMaterial3NavigationBar();
        int horizontalPadding = material3 ? 0 : AndroidUtilities.dp(12);
        int verticalPadding = getTabsInnerPaddingVertical();
        tabsLayout.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        tabsLayout.setMaxWidth(material3 ? 0 : AndroidUtilities.dp(344));
        tabsLayout.setFillAvailableWidth(material3);
        tabsLayout.setSwipeSelectionEnabled(!material3);
        tabsLayout.setDrawTopDivider(material3);
    }

    public static void applyTabsBottomInset(MainTabsLayout tabsLayout, View container, int left, int right, int bottom) {
        int height = getTabsViewHeight(bottom);
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) tabsLayout.getLayoutParams();
        if (layoutParams.height != height) {
            layoutParams.height = height;
            tabsLayout.setLayoutParams(layoutParams);
        }
        int paddingBottom = getTabsInnerPaddingVertical() + (isMaterial3NavigationBar() ? bottom : 0);
        if (tabsLayout.getPaddingBottom() != paddingBottom) {
            tabsLayout.setPadding(tabsLayout.getPaddingLeft(), tabsLayout.getPaddingTop(), tabsLayout.getPaddingRight(), paddingBottom);
        }
        container.setPadding(left, 0, right, isMaterial3NavigationBar() ? 0 : bottom);
    }

    public static Drawable createMainTabsScrimBackground(Theme.ResourcesProvider resourcesProvider, boolean fromBottom) {
        return createMainTabsScrimBackground(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), fromBottom);
    }

    private static Drawable createMainTabsScrimBackground(int color, boolean fromBottom) {
        ShapeDrawable drawable;
        if (fromBottom) {
            drawable = Theme.createCircleDrawable(AndroidUtilities.dp(40), color);
        } else {
            drawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(28), color);
        }
        drawable.getPaint().setShadowLayer(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(1), Theme.multAlpha(Color.BLACK, 0.15f));
        if (!isMaterial3NavigationBar()) {
            return drawable;
        }
        return new InsetDrawable(drawable, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
    }

    public static float getBackgroundRadius() {
        if (isMaterial3NavigationBar()) {
            return 0f;
        }
        return AndroidUtilities.dp(28);
    }

    public static int getBackgroundInset() {
        if (isMaterial3NavigationBar()) {
            return 0;
        }
        return AndroidUtilities.dp(7.666f);
    }

    public static float getMaterial3MainTabAvatarTopDp() {
        return getMaterial3MainTabIconTopDp() + 1f;
    }

    public static void applyMaterial3MainTabStyle(TextView textView) {
        textView.setIncludeFontPadding(false);
        textView.setLetterSpacing(0.04166667f);
        textView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 42, 0, 0));
    }

    public static <K> SpringAnimation createMaterial3IndicatorSizeSpring(K object, FloatPropertyCompat<K> property) {
        return new SpringAnimation(object, property)
                .setMinimumVisibleChange(0.01f)
                .setSpring(new SpringForce(0f).setDampingRatio(0.8f).setStiffness(380f));
    }

    public static <K> SpringAnimation createMaterial3ColorSpring(K object, FloatPropertyCompat<K> property) {
        return new SpringAnimation(object, property)
                .setMinimumVisibleChange(0.01f)
                .setSpring(new SpringForce(0f).setDampingRatio(1f).setStiffness(1600f));
    }

    public static float getMainTabCounterCenterY(boolean material3) {
        return material3 ? AndroidUtilities.dp(getMaterial3MainTabIconTopDp() + 6f) : AndroidUtilities.dpf2(10f);
    }

    public static float getSelectedBackgroundScale(float progress) {
        return AndroidUtilities.lerp(0.6f, 1f, progress);
    }

    public static int getMainTabSelectedIndicatorColor(int color, float alpha) {
        return Theme.multAlpha(color, alpha * 0.125f);
    }

    public static void setMainTabSelectedIndicatorBounds(RectF rect, float width, int height, float progress) {
        float indicatorWidth = Math.min(AndroidUtilities.dp(56), Math.max(0f, width - AndroidUtilities.dp(4) * 2f)) * Math.max(0f, progress);
        float indicatorHeight = Math.min(AndroidUtilities.dp(32), height);
        float left = (width - indicatorWidth) / 2f;
        float top = AndroidUtilities.dp(6);
        rect.set(left, top, left + indicatorWidth, indicatorHeight + top);
    }

    public static void applyBackgroundStroke(BlurredBackgroundProviderBuilder builder) {
        if (isMaterial3NavigationBar()) {
            builder.setStrokeColorTop(0, 0)
                    .setStrokeColorBottom(0, 0)
                    .setStrokeColorFull(0, 0)
                    .setStrokeWidth(0f, 0f);
        } else {
            builder.setStrokeColorTop(0x11000000, 0x06FFFFFF)
                    .setStrokeColorBottom(0x20000000, 0x11FFFFFF)
                    .setStrokeWidth(AndroidUtilities.dpf2(0.4f), AndroidUtilities.dpf2(0.4f));
        }
    }

    public static void setBlurBounds(RectF rect, View view, int bottomInset) {
        int bottom;
        int top;
        if (isMaterial3NavigationBar()) {
            bottom = view.getMeasuredHeight();
            top = bottom - AndroidUtilities.dp(64) - bottomInset;
        } else {
            bottom = view.getMeasuredHeight() - bottomInset - AndroidUtilities.dp(8);
            top = bottom - AndroidUtilities.dp(56);
        }
        rect.set(0, top, view.getMeasuredWidth(), bottom);
    }
}
