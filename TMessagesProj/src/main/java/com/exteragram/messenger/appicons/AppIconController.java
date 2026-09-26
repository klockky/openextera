package com.exteragram.messenger.appicons;

import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AppIconController {

    private static List<AppIcon> allIcons;
    private static AppIcon selectedIcon;

    private AppIconController() {
    }

    public static List<AppIcon> getAllIcons() {
        if (allIcons == null) {
            allIcons = Collections.unmodifiableList(Arrays.asList(GeneratedAppIcons.create()));
        }
        return allIcons;
    }

    public static List<AppIcon> getAvailableIcons() {
        ArrayList<AppIcon> icons = new ArrayList<>();
        for (AppIcon icon : getAllIcons()) {
            if (icon.isAvailable()) {
                icons.add(icon);
            }
        }
        return icons;
    }

    public static AppIcon findById(String id) {
        for (AppIcon icon : getAllIcons()) {
            if (icon.id.equals(id)) {
                return icon;
            }
        }
        return null;
    }

    public static AppIcon getDefaultIcon() {
        AppIcon icon = findById("default");
        return icon != null ? icon : getAllIcons().get(0);
    }

    public static boolean isEnabled(AppIcon icon) {
        Context context = ApplicationLoader.applicationContext;
        int state = context.getPackageManager().getComponentEnabledSetting(icon.getComponentName(context));
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon.isDefault();
    }

    public static AppIcon getSelectedIcon() {
        if (selectedIcon == null) {
            for (AppIcon icon : getAllIcons()) {
                if (isEnabled(icon)) {
                    selectedIcon = icon;
                    break;
                }
            }
            if (selectedIcon == null) {
                selectedIcon = getDefaultIcon();
            }
        }
        return selectedIcon;
    }

    public static void setIcon(AppIcon icon) {
        selectedIcon = icon;
        List<AppIcon> icons = getAllIcons();
        Utilities.globalQueue.postRunnable(() -> applyIcon(icon, icons));
    }

    private static void applyIcon(AppIcon icon, List<AppIcon> icons) {
        Context context = ApplicationLoader.applicationContext;
        PackageManager packageManager = context.getPackageManager();
        packageManager.setComponentEnabledSetting(icon.getComponentName(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (AppIcon other : icons) {
            if (other != icon) {
                packageManager.setComponentEnabledSetting(other.getComponentName(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
    }

    public static void fixLauncherIconIfNeeded() {
        List<AppIcon> icons = getAllIcons();
        AppIcon defaultIcon = getDefaultIcon();
        Utilities.globalQueue.postRunnable(() -> {
            for (AppIcon icon : icons) {
                if (isEnabled(icon)) {
                    AndroidUtilities.runOnUIThread(() -> selectedIcon = icon);
                    if (icon.isMonet()) {
                        // re-apply to refresh the dynamic monet colors
                        applyIcon(defaultIcon, icons);
                        applyIcon(icon, icons);
                    }
                    return;
                }
            }
            AndroidUtilities.runOnUIThread(() -> selectedIcon = defaultIcon);
            applyIcon(defaultIcon, icons);
        });
    }
}
