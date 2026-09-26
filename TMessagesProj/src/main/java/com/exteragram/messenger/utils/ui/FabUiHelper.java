package com.exteragram.messenger.utils.ui;

import com.exteragram.messenger.ExteraConfig;

public abstract class FabUiHelper {

    public static boolean isMaterial3Fab() {
        return ExteraConfig.getNewFabStyle();
    }

    public static int getFabSizeDp() {
        return isMaterial3Fab() ? 56 : 48;
    }

    public static int getFabBottomMarginDp() {
        return isMaterial3Fab() ? 16 : 14;
    }

    public static int getSubFabBottomMarginDp() {
        return isMaterial3Fab() ? 24 : 14;
    }

    public static float getSubFabPaddingDp() {
        return isMaterial3Fab() ? 8f : 5.66f;
    }

    public static float getFabElevationDp() {
        return isMaterial3Fab() ? 6f : 0.5f;
    }

    public static float getSubFabShadowRadiusDp() {
        return isMaterial3Fab() ? 6f : 2.667f;
    }

    public static float getSubFabShadowDyDp() {
        return isMaterial3Fab() ? 2f : 0.85f;
    }

    public static int getSubFabShadowColor() {
        return isMaterial3Fab() ? 0x30000000 : 0x20000000;
    }

    public static float getFabCornerRadiusDp() {
        if (ExteraConfig.getSquareFab()) {
            return UIUtil.getFabSquareCornerRadiusDp(getFabSizeDp());
        }
        return getFabSizeDp() / 2f;
    }

    public static float getSubFabBackgroundRadiusDp() {
        if (ExteraConfig.getSquareFab()) {
            return isMaterial3Fab() ? 12f : 10f;
        }
        return (getFabSizeDp() - getSubFabPaddingDp() * 2f) / 2f;
    }
}
