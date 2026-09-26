package com.exteragram.messenger.export.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class AndroidSDUtils {

    private static final String EXTERNAL_STORAGE = System.getenv("EXTERNAL_STORAGE");
    private static final String SECONDARY_STORAGES = System.getenv("SECONDARY_STORAGE");
    private static final String EMULATED_STORAGE_TARGET = System.getenv("EMULATED_STORAGE_TARGET");

    @SuppressLint("SdCardPath")
    private static final String[] KNOWN_PHYSICAL_PATHS = {
            "/storage/sdcard0",
            "/storage/sdcard1",
            "/storage/extsdcard",
            "/storage/sdcard0/external_sdcard",
            "/mnt/extsdcard",
            "/mnt/sdcard/external_sd",
            "/mnt/sdcard/ext_sd",
            "/mnt/external_sd",
            "/mnt/media_rw/sdcard1",
            "/removable/microsd",
            "/mnt/emmc",
            "/storage/external_SD",
            "/storage/ext_sd",
            "/storage/removable/sdcard1",
            "/data/sdext",
            "/data/sdext2",
            "/data/sdext3",
            "/data/sdext4",
            "/sdcard1",
            "/sdcard2",
            "/storage/microsd"
    };

    public static String[] getStorageDirectories(Context context) {
        Set<String> result = new HashSet<>();
        if (!TextUtils.isEmpty(EMULATED_STORAGE_TARGET)) {
            result.add(getEmulatedStorageTarget());
        } else {
            result.addAll(getExternalStorage(context));
        }
        Collections.addAll(result, getAllSecondaryStorages());
        return result.toArray(new String[0]);
    }

    private static Set<String> getExternalStorage(Context context) {
        Set<String> result = new HashSet<>();
        for (File file : getExternalFilesDirs(context)) {
            if (file != null) {
                String absolutePath = file.getAbsolutePath();
                String path = absolutePath.substring(9, absolutePath.indexOf("Android/data"));
                path = path.substring(path.indexOf("/storage/") + 1);
                path = path.substring(0, path.indexOf("/"));
                if (!path.equals("emulated")) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    private static String getEmulatedStorageTarget() {
        String absolutePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String[] folders = absolutePath.split(File.separator);
        String lastFolder = folders[folders.length - 1];
        if (TextUtils.isEmpty(lastFolder) || !TextUtils.isDigitsOnly(lastFolder)) {
            lastFolder = "";
        }
        if (TextUtils.isEmpty(lastFolder)) {
            return EMULATED_STORAGE_TARGET;
        }
        return EMULATED_STORAGE_TARGET + File.separator + lastFolder;
    }

    private static String[] getAllSecondaryStorages() {
        if (!TextUtils.isEmpty(SECONDARY_STORAGES)) {
            return SECONDARY_STORAGES.split(File.pathSeparator);
        }
        return new String[0];
    }

    private static File[] getExternalFilesDirs(Context context) {
        return context.getExternalFilesDirs(null);
    }
}
