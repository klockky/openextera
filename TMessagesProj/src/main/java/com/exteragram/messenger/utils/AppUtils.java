package com.exteragram.messenger.utils;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Keep;

import com.exteragram.messenger.utils.text.LocaleUtils;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Calendar;

public class AppUtils {

    private static Gson gson;

    public static Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .serializeSpecialFloatingPointValues()
                    .addSerializationExclusionStrategy(new ExclusionStrategy() {
                        @Override
                        public boolean shouldSkipField(FieldAttributes f) {
                            return isAndroidPackage(f.getDeclaringClass());
                        }

                        @Override
                        public boolean shouldSkipClass(Class<?> clazz) {
                            return isAndroidPackage(clazz);
                        }
                    })
                    .create();
        }
        return gson;
    }

    private static boolean isAndroidPackage(Class<?> clazz) {
        if (clazz.getPackage() == null) {
            return false;
        }
        String name = clazz.getPackage().getName();
        return name.startsWith("android.") || name.startsWith("androidx.");
    }

    public static int getNotificationColor() {
        Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
        int color = activeTheme.hasAccentColors() ? activeTheme.getAccentColor(activeTheme.currentAccentId) : 0;
        if (color == 0) {
            color = Theme.getColor(Theme.key_actionBarDefault) | 0xff000000;
        }
        float brightness = AndroidUtilities.computePerceivedBrightness(color);
        if (brightness >= 0.721f || brightness <= 0.279f) {
            color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader) | 0xff000000;
        }
        return color;
    }

    public static boolean isWinter() {
        int month = Calendar.getInstance().get(Calendar.MONTH);
        return month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY;
    }

    public static int getSwipeVelocity() {
        Point size = AndroidUtilities.displaySize;
        return size.x > size.y ? 1250 : 850;
    }

    public static boolean isAppModified() {
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), PackageManager.GET_SIGNATURES);
            String signature = Base64.encodeToString(MessageDigest.getInstance("MD5").digest(info.signatures[0].toByteArray()), Base64.DEFAULT).trim();
            // TODO(openextera): official exteraGram package/signature check, always "modified" for our builds
            return !(BuildConfig.APPLICATION_ID.equals(info.packageName) && "VdBS+IkXbbu+mQuHS4vyXw==".equals(signature));
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
    }

    @Keep
    public static void log(String message) {
        logInternal(message, null, 5);
    }

    @Keep
    public static void log(Throwable throwable) {
        logInternal("", throwable, 5);
    }

    @Keep
    public static void log(String message, Throwable throwable) {
        logInternal(message, throwable, 5);
    }

    private static void logInternal(String message, Throwable throwable, int depth) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement element = stackTrace[Math.max(3, Math.min(depth, stackTrace.length - 1))];
        String className = element.getClassName();
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }
        if (className.contains("$")) {
            className = className.substring(className.lastIndexOf('$') + 1);
        }
        String tag = "[" + className + "]";
        String text = String.format("[%s] %s", element.getMethodName(), message);
        if (throwable != null) {
            Log.e(tag, text, throwable);
        } else {
            Log.d(tag, text);
        }
    }

    @Keep
    public static void printObjectDetails(Object object) {
        if (object == null) {
            return;
        }
        try {
            logInternal(object.getClass().getName() + ": " + getGson().toJson(object), null, 6);
        } catch (Exception e) {
            logInternal(object.getClass().getName(), e, 6);
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignore) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @Keep
    public static Object getPrivateField(Object object, String name) {
        try {
            Field field = findField(object.getClass(), name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(object);
            }
        } catch (Exception e) {
            logInternal(object.getClass().getName(), e, 6);
        }
        return null;
    }

    @Keep
    public static void setPrivateField(Object object, String name, Object value) {
        try {
            Field field = findField(object.getClass(), name);
            if (field != null) {
                field.setAccessible(true);
                field.set(object, value);
            }
        } catch (Exception e) {
            logInternal(object.getClass().getName(), e, 6);
        }
    }

    @Keep
    public static Object getPrivateStaticField(Class<?> clazz, String name) {
        try {
            Field field = findField(clazz, name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(null);
            }
        } catch (Exception e) {
            logInternal(clazz.getName(), e, 6);
        }
        return null;
    }

    @Keep
    public static void setPrivateStaticField(Class<?> clazz, String name, Object value) {
        try {
            Field field = findField(clazz, name);
            if (field != null) {
                field.setAccessible(true);
                field.set(null, value);
            }
        } catch (Exception e) {
            logInternal(clazz.getName(), e, 6);
        }
    }

    public static String getVersionText() {
        StringBuilder sb = new StringBuilder();
        sb.append(LocaleUtils.getAppName());
        sb.append(" ");
        if (BuildVars.IS_LITE_VERSION) {
            sb.append("Lite ");
        }
        sb.append(BuildVars.BUILD_VERSION_STRING);
        try {
            PackageInfo info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            sb.append(" (").append(info.versionCode).append(")");
        } catch (PackageManager.NameNotFoundException e) {
            FileLog.e(e);
        }
        if (isAppModified()) {
            sb.append("\nbased on @exteraGram");
        }
        return sb.toString();
    }
}
