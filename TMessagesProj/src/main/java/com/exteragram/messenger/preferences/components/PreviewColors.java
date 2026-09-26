package com.exteragram.messenger.preferences.components;

import org.telegram.ui.ActionBar.Theme;

public final class PreviewColors {

    private PreviewColors() {
    }

    private static float getBackgroundAlpha() {
        return Theme.isCurrentThemeDark() ? 0.05f : 0.035f;
    }

    public static int getBackgroundColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), getBackgroundAlpha());
    }

    public static int getOutlineColor() {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), getBackgroundAlpha() + 0.085f);
    }

    public static int getMockColor(boolean selected) {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), selected ? 0.4f : 0.2f);
    }
}
