package org.telegram.ui.ActionBar;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;

import com.exteragram.messenger.utils.ui.MonetUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.io.File;
import java.util.ArrayList;

public abstract class MonetAccentHelper {

    private static final int[] ACCENT_IDS = {91, 90, 89};
    private static final String FALLBACK_PATTERN_SLUG = "__monet_default_pattern__";

    private static Bitmap fallbackPatternBitmap;
    private static int fallbackPatternWidth;
    private static int fallbackPatternHeight;

    public static void appendAccentOptions(Theme.ThemeInfo themeInfo) {
        if (!isSupported() || themeInfo == null || themeInfo.themeAccentsMap == null || themeInfo.themeAccents == null) {
            return;
        }
        for (int i = 0; i < ACCENT_IDS.length; i++) {
            ensureAccent(themeInfo, ACCENT_IDS[i], i);
        }
    }

    public static boolean refresh(Theme.ThemeInfo themeInfo) {
        if (themeInfo == null) {
            return false;
        }
        boolean needReloadPatterns = refreshAccents(themeInfo);
        refreshPreviewColors(themeInfo);
        return needReloadPatterns;
    }

    public static boolean isMonetAccent(Theme.ThemeAccent accent) {
        if (accent == null) {
            return false;
        }
        for (int id : ACCENT_IDS) {
            if (accent.id == id) {
                return true;
            }
        }
        return false;
    }

    public static boolean canEditAccent(Theme.ThemeAccent accent) {
        return accent != null && accent.id >= 100 && !accent.isDefault && !isMonetAccent(accent);
    }

    public static boolean hasRemotePatternWallpaper(Theme.ThemeAccent accent) {
        return accent != null && !TextUtils.isEmpty(accent.patternSlug) && !isFallbackPattern(accent);
    }

    public static boolean isFallbackPattern(Theme.ThemeAccent accent) {
        return accent != null && FALLBACK_PATTERN_SLUG.equals(accent.patternSlug);
    }

    public static MotionBackgroundDrawable createFallbackPatternDrawable(int color1, int color2, int color3, int color4, int rotation, int intensity, boolean preview, int phase) {
        MotionBackgroundDrawable drawable;
        if (color2 == 0) {
            drawable = new MotionBackgroundDrawable(color1, color1, color1, color1, rotation, preview);
        } else if (color3 == 0 && color4 == 0) {
            drawable = new MotionBackgroundDrawable(color1, color2, color1, color2, rotation, preview);
        } else {
            drawable = new MotionBackgroundDrawable(color1, color2, color3, color4, rotation, preview);
        }
        drawable.setPhase(phase);
        drawable.setPatternBitmap(intensity, getFallbackPatternBitmap());
        drawable.setPatternColorFilter(drawable.getPatternColor());
        return drawable;
    }

    public static int countLeadingMonetAccents(ArrayList<Theme.ThemeAccent> accents) {
        if (accents == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0, N = accents.size(); i < N && isMonetAccent(accents.get(i)); i++) {
            count++;
        }
        return count;
    }

    private static boolean isSupported() {
        return MonetUtils.isSupported();
    }

    private static void ensureAccent(Theme.ThemeInfo themeInfo, int id, int index) {
        Theme.ThemeAccent accent = themeInfo.themeAccentsMap.get(id);
        if (accent == null) {
            accent = new Theme.ThemeAccent();
            accent.id = id;
            accent.parentTheme = themeInfo;
            themeInfo.themeAccentsMap.put(id, accent);
            themeInfo.themeAccents.add(accent);
            themeInfo.defaultAccentCount++;
        }
        fillAccentValues(themeInfo, accent, index, false);
    }

    private static boolean refreshAccents(Theme.ThemeInfo themeInfo) {
        if (!isSupported() || themeInfo.themeAccentsMap == null) {
            return false;
        }
        boolean needReloadPatterns = false;
        for (int i = 0; i < ACCENT_IDS.length; i++) {
            Theme.ThemeAccent accent = themeInfo.themeAccentsMap.get(ACCENT_IDS[i]);
            if (accent != null) {
                needReloadPatterns |= fillAccentValues(themeInfo, accent, i, true);
            }
        }
        return needReloadPatterns;
    }

    private static boolean fillAccentValues(Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent, int index, boolean refresh) {
        File oldWallpaper = accent.getPathToWallpaper();
        int accentColor = MonetUtils.getSystemAccentColor(index, themeInfo.isDark());
        Theme.ThemeAccent sourceAccent = getSourceAccent(themeInfo, accent.id);

        accent.accentColor = accentColor;
        accent.accentColor2 = 0;
        accent.myMessagesAccentColor = accentColor;
        accent.myMessagesGradientAccentColor1 = 0;
        accent.myMessagesGradientAccentColor2 = 0;
        accent.myMessagesGradientAccentColor3 = 0;
        accent.myMessagesAnimated = false;
        fillWallpaperValues(themeInfo, accent, sourceAccent, accentColor);

        if (sourceAccent != null && !TextUtils.isEmpty(sourceAccent.patternSlug)) {
            accent.patternSlug = sourceAccent.patternSlug;
            accent.patternIntensity = sourceAccent.patternIntensity;
            accent.patternMotion = sourceAccent.patternMotion;
        } else if (themeInfo.firstAccentIsDefault) {
            accent.patternSlug = FALLBACK_PATTERN_SLUG;
            accent.patternIntensity = 0.34f;
            accent.patternMotion = false;
        } else {
            accent.patternSlug = "";
            accent.patternIntensity = 0f;
            accent.patternMotion = false;
        }

        if (refresh) {
            deleteCachedWallpaper(oldWallpaper);
            deleteCachedWallpaper(accent.getPathToWallpaper());
        }
        return refresh && hasRemotePatternWallpaper(accent);
    }

    private static void fillWallpaperValues(Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent, Theme.ThemeAccent sourceAccent, int accentColor) {
        if (sourceAccent == null || !hasWallpaperColors(sourceAccent)) {
            accent.backgroundOverrideColor = 0;
            accent.backgroundGradientOverrideColor1 = 0;
            accent.backgroundGradientOverrideColor2 = 0;
            accent.backgroundGradientOverrideColor3 = 0;
            accent.backgroundRotation = 45;
            return;
        }
        accent.backgroundOverrideColor = changeWallpaperColor(themeInfo, accentColor, sourceAccent.backgroundOverrideColor);
        accent.backgroundGradientOverrideColor1 = changeWallpaperColor(themeInfo, accentColor, sourceAccent.backgroundGradientOverrideColor1);
        accent.backgroundGradientOverrideColor2 = changeWallpaperColor(themeInfo, accentColor, sourceAccent.backgroundGradientOverrideColor2);
        accent.backgroundGradientOverrideColor3 = changeWallpaperColor(themeInfo, accentColor, sourceAccent.backgroundGradientOverrideColor3);
        accent.backgroundRotation = sourceAccent.backgroundRotation;
    }

    private static boolean hasWallpaperColors(Theme.ThemeAccent accent) {
        return accent.backgroundOverrideColor != 0 || accent.backgroundGradientOverrideColor1 != 0 || accent.backgroundGradientOverrideColor2 != 0 || accent.backgroundGradientOverrideColor3 != 0;
    }

    private static long changeWallpaperColor(Theme.ThemeInfo themeInfo, int accentColor, long color) {
        if (color == 0 || color == 0x100000000L) {
            return 0;
        }
        final int baseColor = themeInfo.accentBaseColor;
        if (accentColor == 0 || baseColor == 0 || accentColor == baseColor) {
            return color;
        }
        float[] baseHsv = new float[3];
        float[] accentHsv = new float[3];
        Color.colorToHSV(baseColor, baseHsv);
        Color.colorToHSV(accentColor, accentHsv);
        return Theme.changeColorAccent(baseHsv, accentHsv, (int) color, themeInfo.isDark(), (int) color);
    }

    private static void deleteCachedWallpaper(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static Theme.ThemeAccent getSourceAccent(Theme.ThemeInfo themeInfo, int accentId) {
        if (themeInfo.themeAccentsMap == null || themeInfo.themeAccents == null) {
            return null;
        }
        Theme.ThemeAccent defaultAccent = themeInfo.themeAccentsMap.get(themeInfo.firstAccentIsDefault ? Theme.DEFALT_THEME_ACCENT_ID : 0);
        if (defaultAccent != null && defaultAccent.id != accentId) {
            return defaultAccent;
        }
        for (int i = 0, N = themeInfo.themeAccents.size(); i < N; i++) {
            Theme.ThemeAccent accent = themeInfo.themeAccents.get(i);
            if (!isMonetAccent(accent) && accent.id != accentId) {
                return accent;
            }
        }
        return null;
    }

    private static void refreshPreviewColors(Theme.ThemeInfo themeInfo) {
        if (!isSupported() || !themeInfo.isMonet()) {
            return;
        }
        if ("Monet Light".equals(themeInfo.name)) {
            themeInfo.setPreviewBackgroundColor(MonetUtils.getColor("n1_10"));
            themeInfo.setPreviewInColor(MonetUtils.getColor("n1_50"));
            themeInfo.setPreviewOutColor(MonetUtils.getColor("a1_600"));
        } else if ("Monet Dark".equals(themeInfo.name)) {
            themeInfo.setPreviewBackgroundColor(MonetUtils.getColor("n1_900"));
            themeInfo.setPreviewInColor(MonetUtils.getColor("n1_800"));
            themeInfo.setPreviewOutColor(MonetUtils.getColor("a1_200"));
        } else if ("Monet Black".equals(themeInfo.name)) {
            themeInfo.setPreviewBackgroundColor(MonetUtils.getColor("mBlack"));
            themeInfo.setPreviewInColor(MonetUtils.getColor("n1_800"));
            themeInfo.setPreviewOutColor(MonetUtils.getColor("a1_200"));
        }
    }

    private static Bitmap getFallbackPatternBitmap() {
        final int width = Math.max(1, Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y));
        final int height = Math.max(1, Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y));
        synchronized (MonetAccentHelper.class) {
            if (fallbackPatternBitmap == null || fallbackPatternBitmap.isRecycled() || fallbackPatternWidth != width || fallbackPatternHeight != height) {
                fallbackPatternBitmap = SvgHelper.getBitmap(R.raw.default_pattern, width, height, Color.BLACK);
                fallbackPatternWidth = width;
                fallbackPatternHeight = height;
            }
            return fallbackPatternBitmap;
        }
    }
}
