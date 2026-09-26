package com.exteragram.messenger.utils.system;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.fingerprint.FingerprintManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.exteragram.messenger.utils.network.RemoteUtils;
import com.exteragram.messenger.utils.ui.UIUtil;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class SystemUtils {

    private static final int REQUEST_CODE_STORAGE = 4;
    private static final int REQUEST_CODE_EXTERNAL_APP = 500;

    private static final List<ExternalApp> externalApps = new ArrayList<>();

    public static Boolean isLensAvailable = null;
    public static String lensActivityName;

    private static volatile boolean lastExternalAudioOutput;
    private static volatile long lastExternalAudioOutputCheck = -1;

    public static boolean isPermissionGranted(String permission) {
        return ApplicationLoader.applicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermissions(Activity activity, int requestCode, String... permissions) {
        if (activity == null) {
            return;
        }
        activity.requestPermissions(permissions, requestCode);
    }

    public static boolean isVideoPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33) {
            return isPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO);
        }
        return isStoragePermissionGranted();
    }

    public static boolean isImagesAndVideoPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33) {
            return isImagesPermissionGranted() && isVideoPermissionGranted();
        }
        return isStoragePermissionGranted();
    }

    public static void requestImagesAndVideoPermission(Activity activity) {
        requestImagesAndVideoPermission(activity, REQUEST_CODE_STORAGE);
    }

    public static void requestImagesAndVideoPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(activity, requestCode, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            requestPermissions(activity, requestCode, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    public static boolean isImagesPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33) {
            return isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES);
        }
        return isStoragePermissionGranted();
    }

    public static void requestImagesPermission(Activity activity) {
        requestImagesPermission(activity, REQUEST_CODE_STORAGE);
    }

    public static void requestImagesPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(activity, requestCode, Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            requestPermissions(activity, requestCode, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    public static boolean isAudioPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33) {
            return isPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO);
        }
        return isStoragePermissionGranted();
    }

    public static void requestAudioPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(activity, REQUEST_CODE_STORAGE, Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            requestPermissions(activity, REQUEST_CODE_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    public static boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33) {
            return isImagesPermissionGranted() && isVideoPermissionGranted() && isAudioPermissionGranted();
        }
        return isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(activity, REQUEST_CODE_STORAGE, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            requestPermissions(activity, REQUEST_CODE_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    public static boolean hasBiometrics() {
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager biometricManager = ApplicationLoader.applicationContext.getSystemService(BiometricManager.class);
            if (biometricManager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
            }
            return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        }
        FingerprintManager fingerprintManager = ApplicationLoader.applicationContext.getSystemService(FingerprintManager.class);
        return fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints();
    }

    public static File getFileFromBitmap(Bitmap bitmap) throws IOException {
        File file = new File(AndroidUtilities.getCacheDir(), "temp.jpeg");
        file.createNewFile();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        }
        return file;
    }

    public static void addFileToClipboard(File file, Runnable callback) {
        try {
            Context context = ApplicationLoader.applicationContext;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            clipboard.setPrimaryClip(ClipData.newUri(context.getContentResolver(), "label", uri));
            callback.run();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean isAppInstalled(String packageName) {
        try {
            ApplicationLoader.applicationContext.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isLensAvailable() {
        if (isLensAvailable == null) {
            try {
                checkLensAvailability();
            } catch (Exception e) {
                FileLog.e(e);
                isLensAvailable = false;
            }
        }
        return isLensAvailable;
    }

    public static void checkLensAvailability() {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setDataAndType(Uri.parse("content://" + ApplicationLoader.getApplicationId() + ".provider"), "image/jpeg")
                .setPackage("com.google.android.googlequicksearchbox");
        List<ResolveInfo> activities = ApplicationLoader.applicationContext.getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo info : activities) {
            String name = info.activityInfo.name;
            if (name.contains("Lens")) {
                isLensAvailable = true;
                lensActivityName = name;
                break;
            }
        }
        if (isLensAvailable == null) {
            isLensAvailable = false;
        }
    }

    public static void shareImageWithGoogleLens(Activity activity, Uri uri) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_SEND)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setClassName("com.google.android.googlequicksearchbox", lensActivityName)
                    .setDataAndType(uri, "image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, uri));
        } catch (ActivityNotFoundException e) {
            FileLog.e(e);
        }
    }

    public static int getRoundVideoResolution() {
        return RemoteUtils.getIntConfigValue("round_video_resolution", 512);
    }

    public static int getRoundVideoBitrate() {
        return RemoteUtils.getIntConfigValue("round_video_bitrate", 1000);
    }

    public static int getRoundAudioBitrate() {
        return RemoteUtils.getIntConfigValue("round_audio_bitrate", 64);
    }

    public static int awm() {
        return R.raw.awm;
    }

    public static long getRoundVideoMaxDurationMs() {
        // 10 MB budget (in bits * ms) divided by total bitrate in bits per second
        long totalBitrate = Math.max(1, getRoundVideoBitrate()) + Math.max(0, getRoundAudioBitrate());
        return Utilities.clamp(10L * 1024 * 1024 * 8 * 1000 / (totalBitrate * 1024), 60_000L, 10_000L);
    }

    public static boolean hasExternalAudioOutput(AudioManager audioManager) {
        if (audioManager == null) {
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        long lastCheck = lastExternalAudioOutputCheck;
        if (lastCheck >= 0 && now - lastCheck < 500) {
            return lastExternalAudioOutput;
        }
        try {
            boolean hasExternal = false;
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int type = device.getType();
                if ((type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || type == AudioDeviceInfo.TYPE_WIRED_HEADSET) && device.isSink()) {
                    hasExternal = true;
                    break;
                }
            }
            lastExternalAudioOutput = hasExternal;
            lastExternalAudioOutputCheck = now;
            return hasExternal;
        } catch (Exception e) {
            FileLog.e(e);
            return lastExternalAudioOutput;
        }
    }

    public static List<ExternalApp> getExternalApps() {
        updateExternalApps();
        return externalApps;
    }

    private static void updateExternalApps() {
        if (!externalApps.isEmpty()) {
            externalApps.clear();
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tel:00000000000"));
        for (ResolveInfo info : ApplicationLoader.applicationContext.getPackageManager().queryIntentActivities(intent, 0)) {
            ActivityInfo activityInfo = info.activityInfo;
            addExternalApp(activityInfo.packageName, activityInfo.name);
        }
    }

    private static void addExternalApp(String packageName, String activityName) {
        if (isAppInstalled(packageName)) {
            externalApps.add(new ExternalApp(packageName, activityName));
        }
    }

    public static class ExternalApp {

        private final String packageName;
        private final String activityName;
        private final String appName;
        private final Drawable appIcon;

        public ExternalApp(String packageName, String activityName) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.appName = fetchName();
            this.appIcon = fetchIcon();
        }

        public String getName() {
            return appName;
        }

        public Drawable getIcon() {
            return appIcon;
        }

        private String fetchName() {
            PackageManager packageManager = ApplicationLoader.applicationContext.getPackageManager();
            try {
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
                return (String) packageManager.getApplicationLabel(applicationInfo);
            } catch (Exception e) {
                return LocaleController.getString(R.string.NumberUnknown);
            }
        }

        private Drawable fetchIcon() {
            try {
                Drawable icon = ApplicationLoader.applicationContext.getPackageManager().getApplicationIcon(packageName);
                return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), UIUtil.drawableToBitmap(icon, AndroidUtilities.dp(24), AndroidUtilities.dp(24)));
            } catch (Exception e) {
                return ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_media);
            }
        }

        public void open(Activity activity, String phone) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tel:" + phone));
                intent.setClassName(packageName, activityName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivityForResult(intent, REQUEST_CODE_EXTERNAL_APP);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }
}
