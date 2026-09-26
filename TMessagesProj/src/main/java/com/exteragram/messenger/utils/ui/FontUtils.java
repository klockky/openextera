package com.exteragram.messenger.utils.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Locale;

public abstract class FontUtils {

    private static final int WEIGHT_REGULAR = 400;
    private static final int WEIGHT_MEDIUM = 500;
    private static final int WEIGHT_BOLD = 700;
    private static final int WEIGHT_EXTRA_BOLD = 800;

    private static final int CANVAS_SIZE = AndroidUtilities.dp(20);
    private static final Paint PAINT = new Paint();
    private static final String TEST_TEXT;

    public static boolean loadSystemEmojiFailed = false;
    private static Typeface systemEmojiTypeface;

    private static volatile Boolean mediumWeightSupported = null;
    private static volatile Boolean italicSupported = null;
    private static volatile Boolean usePixelGoogleSans = null;

    private static Typeface systemGoogleSans = null;
    private static boolean systemGoogleSansLoaded = false;
    private static Typeface systemGoogleSansMedium = null;
    private static boolean systemGoogleSansMediumLoaded = false;

    static {
        PAINT.setTextSize(CANVAS_SIZE);
        PAINT.setAntiAlias(true);
        PAINT.setSubpixelText(false);
        PAINT.setFakeBoldText(false);

        String language = "en";
        try {
            if (LocaleController.getInstance() != null && LocaleController.getInstance().getCurrentLocale() != null) {
                language = LocaleController.getInstance().getCurrentLocale().getLanguage();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (Arrays.asList("zh", "ja", "ko").contains(language)) {
            TEST_TEXT = "你好";
        } else if (Arrays.asList("ar", "fa").contains(language)) {
            TEST_TEXT = "مرحبا";
        } else if ("iw".equals(language)) {
            TEST_TEXT = "שלום";
        } else if ("th".equals(language)) {
            TEST_TEXT = "สวัสดี";
        } else if ("hi".equals(language)) {
            TEST_TEXT = "नमस्ते";
        } else if (Arrays.asList("ru", "uk", "ky", "be", "sr").contains(language)) {
            TEST_TEXT = "Привет";
        } else {
            TEST_TEXT = "R";
        }
    }

    public static boolean isMediumWeightSupported() {
        if (mediumWeightSupported == null) {
            synchronized (FontUtils.class) {
                if (mediumWeightSupported == null) {
                    mediumWeightSupported = supportsMediumWeight();
                    FileLog.d("mediumWeightSupported = " + mediumWeightSupported);
                }
            }
        }
        return mediumWeightSupported;
    }

    public static boolean isItalicSupported() {
        if (italicSupported == null) {
            synchronized (FontUtils.class) {
                if (italicSupported == null) {
                    italicSupported = rendersDifferently(createWeightedSansTypeface(WEIGHT_REGULAR, false), createWeightedSansTypeface(WEIGHT_REGULAR, true));
                    FileLog.d("italicSupported = " + italicSupported);
                }
            }
        }
        return italicSupported;
    }

    private static boolean rendersDifferently(Typeface first, Typeface second) {
        Canvas canvas = new Canvas();
        Bitmap firstBitmap = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        Bitmap secondBitmap = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        synchronized (PAINT) {
            canvas.setBitmap(firstBitmap);
            PAINT.setTypeface(first);
            canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);
            canvas.setBitmap(secondBitmap);
            PAINT.setTypeface(second);
            canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);
            PAINT.setTypeface(null);
        }
        boolean different = !firstBitmap.sameAs(secondBitmap);
        AndroidUtilities.recycleBitmaps(Arrays.asList(firstBitmap, secondBitmap));
        return different;
    }

    private static boolean differsFromRegular(Typeface typeface, boolean italic) {
        return rendersDifferently(createWeightedSansTypeface(WEIGHT_REGULAR, italic), typeface);
    }

    private static boolean supportsMediumWeight() {
        if (Build.VERSION.SDK_INT >= 28 && differsFromRegular(createWeightedSansTypeface(WEIGHT_MEDIUM, false), false)) {
            return true;
        }
        return differsFromRegular(Typeface.create("sans-serif-medium", Typeface.NORMAL), false);
    }

    private static boolean isGooglePixelDevice() {
        if (!"google".equalsIgnoreCase(Build.MANUFACTURER)) {
            return false;
        }
        String model = Build.MODEL;
        return model != null && model.toLowerCase(Locale.US).startsWith("pixel");
    }

    private static boolean shouldUsePixelGoogleSans() {
        if (!isGooglePixelDevice()) {
            return false;
        }
        if (usePixelGoogleSans == null) {
            synchronized (FontUtils.class) {
                if (usePixelGoogleSans == null) {
                    usePixelGoogleSans = hasSimilarMetrics(Typeface.create("sans-serif", Typeface.NORMAL), getFontFromAssets("fonts/rregular.ttf"));
                    FileLog.d("usePixelGoogleSans = " + usePixelGoogleSans);
                }
            }
        }
        return usePixelGoogleSans;
    }

    private static boolean hasSimilarMetrics(Typeface first, Typeface second) {
        final String sample = "Hamburgefontsiv0123456789";
        Paint paint = new Paint();
        paint.setTextSize(100);
        float[] firstWidths = new float[sample.length()];
        float[] secondWidths = new float[sample.length()];
        paint.setTypeface(first);
        paint.getTextWidths(sample, firstWidths);
        paint.setTypeface(second);
        paint.getTextWidths(sample, secondWidths);
        for (int i = 0; i < sample.length(); i++) {
            float width = secondWidths[i];
            if (width == 0 || Math.abs(firstWidths[i] - width) / width > 0.01f) {
                return false;
            }
        }
        return true;
    }

    private static Typeface getBaseSystemTypeface() {
        if (!shouldUsePixelGoogleSans()) {
            return Typeface.create("sans-serif", Typeface.NORMAL);
        }
        if (!systemGoogleSansLoaded) {
            systemGoogleSansLoaded = true;
            for (String alias : new String[]{"google-sans-text", "google-sans"}) {
                Typeface typeface = Typeface.create(alias, Typeface.NORMAL);
                if (rendersDifferently(typeface, Typeface.DEFAULT)) {
                    systemGoogleSans = typeface;
                    FileLog.d("system google sans alias = " + alias);
                    return systemGoogleSans;
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                File file = getGoogleSansFromSystemApi();
                if (file != null) {
                    systemGoogleSans = Typeface.createFromFile(file);
                    FileLog.d("system google sans file = " + file.getAbsolutePath());
                }
            }
        }
        return systemGoogleSans != null ? systemGoogleSans : Typeface.create("sans-serif", Typeface.NORMAL);
    }

    private static Typeface getSystemGoogleSansMedium() {
        if (!shouldUsePixelGoogleSans()) {
            return null;
        }
        if (!systemGoogleSansMediumLoaded) {
            systemGoogleSansMediumLoaded = true;
            for (String alias : new String[]{"variable-title-medium-emphasized", "variable-title-medium"}) {
                Typeface typeface = Typeface.create(alias, Typeface.NORMAL);
                if (rendersDifferently(typeface, Typeface.DEFAULT)) {
                    systemGoogleSansMedium = typeface;
                    FileLog.d("system google sans medium alias = " + alias);
                    return systemGoogleSansMedium;
                }
            }
        }
        return systemGoogleSansMedium;
    }

    private static Typeface createWeightedSansTypeface(int weight, boolean italic) {
        if (weight >= WEIGHT_MEDIUM && weight < WEIGHT_BOLD) {
            Typeface medium = getSystemGoogleSansMedium();
            if (medium != null) {
                if (Build.VERSION.SDK_INT >= 28) {
                    return Typeface.create(medium, weight, italic);
                }
                return italic ? Typeface.create(medium, Typeface.ITALIC) : medium;
            }
        }
        Typeface base = getBaseSystemTypeface();
        if (Build.VERSION.SDK_INT >= 28) {
            return Typeface.create(base, weight, italic);
        }
        if (weight >= WEIGHT_BOLD) {
            return Typeface.create(base, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
        }
        return italic ? Typeface.create(base, Typeface.ITALIC) : base;
    }

    private static Typeface resolveSansTypeface(int weight, boolean italic) {
        if (Build.VERSION.SDK_INT >= 28) {
            Typeface typeface = createWeightedSansTypeface(weight, italic);
            if (weight == WEIGHT_REGULAR || differsFromRegular(typeface, italic)) {
                return typeface;
            }
        }
        Typeface base = getBaseSystemTypeface();
        if (weight >= WEIGHT_EXTRA_BOLD) {
            Typeface black = Typeface.create("sans-serif-black", italic ? Typeface.ITALIC : Typeface.NORMAL);
            if (differsFromRegular(black, italic)) {
                return black;
            }
            return Typeface.create(base, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
        }
        if (weight >= WEIGHT_MEDIUM) {
            if (weight < WEIGHT_BOLD) {
                Typeface googleSansMedium = getSystemGoogleSansMedium();
                if (googleSansMedium != null && differsFromRegular(googleSansMedium, italic)) {
                    return googleSansMedium;
                }
            }
            Typeface medium = Typeface.create("sans-serif-medium", italic ? Typeface.ITALIC : Typeface.NORMAL);
            if (differsFromRegular(medium, italic)) {
                return medium;
            }
            return Typeface.create(base, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
        }
        return Typeface.create(base, italic ? Typeface.ITALIC : Typeface.NORMAL);
    }

    public static Typeface getSystemTypeface(String assetPath) {
        switch (assetPath) {
            case "fonts/rregular.ttf":
                return resolveSansTypeface(WEIGHT_REGULAR, false);
            case "fonts/ritalic.ttf":
                return resolveSansTypeface(WEIGHT_REGULAR, true);
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM:
                return resolveSansTypeface(WEIGHT_MEDIUM, false);
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC:
                return resolveSansTypeface(WEIGHT_MEDIUM, true);
            case "fonts/rextrabold.ttf":
                return resolveSansTypeface(WEIGHT_EXTRA_BOLD, false);
            case "fonts/rcondensedbold.ttf":
                return Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case AndroidUtilities.TYPEFACE_ROBOTO_MONO:
                return Typeface.MONOSPACE;
            default:
                return null;
        }
    }

    public static File getSystemEmojiFontPath() {
        if (Build.VERSION.SDK_INT >= 29) {
            File file = getFontFromSystemApi();
            if (file != null) {
                return file;
            }
        }
        File fallback = getFontFallback();
        return fallback != null ? fallback : getFontFromFontsXml();
    }

    @RequiresApi(29)
    private static File getGoogleSansFromSystemApi() {
        try {
            for (Font font : SystemFonts.getAvailableFonts()) {
                File file = font.getFile();
                if (file == null) {
                    continue;
                }
                String name = file.getName().toLowerCase();
                boolean isGoogleSans = name.contains("googlesanstext") || name.contains("google-sans-text") || name.contains("googlesans") || name.contains("google-sans");
                if (isGoogleSans && !name.contains("medium") && !name.contains("bold") && !name.contains("italic") && !name.contains("condensed")) {
                    return file;
                }
            }
            return null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    @RequiresApi(29)
    private static File getFontFromSystemApi() {
        try {
            File emojiFile = null;
            for (Font font : SystemFonts.getAvailableFonts()) {
                File file = font.getFile();
                if (file == null) {
                    continue;
                }
                String name = file.getName().toLowerCase();
                if (name.contains("samsungcoloremoji")) {
                    return file;
                }
                if (emojiFile == null && name.contains("emoji")) {
                    emojiFile = file;
                }
            }
            return emojiFile;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static File getFontFallback() {
        String[] paths = {"/system/fonts/SamsungColorEmoji.ttf", "/system/fonts/NotoColorEmoji.ttf", "/system/fonts/AndroidEmoji.ttf"};
        for (String path : paths) {
            File file = new File(path);
            if (file.exists()) {
                FileLog.d("emoji font file fallback = " + path);
                return file;
            }
        }
        return null;
    }

    private static File getFontFromFontsXml() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/system/etc/fonts.xml"))) {
            boolean ignoredFamily = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("<family") && trimmed.contains("ignore=\"true\"")) {
                    ignoredFamily = true;
                } else if (trimmed.startsWith("</family>")) {
                    ignoredFamily = false;
                } else if (trimmed.startsWith("<font") && !ignoredFamily) {
                    int start = trimmed.indexOf(">");
                    int end = trimmed.indexOf("<", 1);
                    if (start > 0 && end > 0) {
                        String fontName = trimmed.substring(start + 1, end);
                        if (fontName.toLowerCase().contains("emoji")) {
                            File file = new File("/system/fonts/" + fontName);
                            if (file.exists()) {
                                FileLog.d("emoji font file fonts.xml = " + fontName);
                                return file;
                            }
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static Typeface getSystemEmojiTypeface() {
        if (!loadSystemEmojiFailed && systemEmojiTypeface == null) {
            File fontPath = getSystemEmojiFontPath();
            if (fontPath != null) {
                systemEmojiTypeface = Typeface.createFromFile(fontPath);
            }
            if (systemEmojiTypeface == null) {
                loadSystemEmojiFailed = true;
            }
        }
        return systemEmojiTypeface;
    }

    public static Typeface getFontFromAssets(String assetPath) {
        if (Build.VERSION.SDK_INT >= 26) {
            Typeface.Builder builder = new Typeface.Builder(ApplicationLoader.applicationContext.getAssets(), assetPath);
            if (assetPath.contains("medium")) {
                builder.setWeight(WEIGHT_BOLD);
            }
            if (assetPath.contains("italic")) {
                builder.setItalic(true);
            }
            return builder.build();
        }
        return Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), assetPath);
    }
}
