package com.exteragram.messenger.export.output;

import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;

public abstract class FileManager {

    public static final File defaultSavePath = new File(
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "exteraGram"),
            "DataExport_" + new SimpleDateFormat("yyyy-MM-dd_HH_mm").format(Calendar.getInstance().getTime())
    );

    public static void copyAssets() {
        copyAssets("js", false);
        copyAssets("css", false);
        copyAssets("images", true);
    }

    public static void copyAssets(String folder, boolean noMedia) {
        AssetManager assets = ApplicationLoader.applicationContext.getAssets();
        String[] files;
        try {
            files = assets.list("extera/export/" + folder + "/");
        } catch (IOException e) {
            Log.e("exteraGram", "Failed to get asset file list", e);
            files = null;
        }
        if (files == null) {
            FileLog.e("export: failed to copy assets");
            return;
        }
        for (String filename : files) {
            try {
                InputStream in = assets.open("extera/export/" + folder + "/" + filename);
                File outFile = new File(defaultSavePath + "/" + folder + "/", filename);
                outFile.createNewFile();
                AndroidUtilities.copyFile(in, new FileOutputStream(outFile));
            } catch (IOException e) {
                Log.e("exteraGram", "Failed to copy asset file: " + filename, e);
                throw new RuntimeException("exteraGram assets exception: ", e);
            }
        }
        if (noMedia) {
            AndroidUtilities.createEmptyFile(new File(defaultSavePath + "/" + folder, ".nomedia"));
        }
    }

    public static String fileNameFromUserString(String name) {
        HashSet<Character> bad = new HashSet<>();
        bad.add('‎'); // LRM
        bad.add('‏'); // RLM
        bad.add('‪'); // LRE
        bad.add('‫'); // RLE
        bad.add('‭'); // LRO
        bad.add('‮'); // RLO
        bad.add('⁦'); // LRI
        bad.add('⁧'); // RLI
        bad.add('/');
        bad.add('\\');
        bad.add('<');
        bad.add('>');
        bad.add(':');
        bad.add('"');
        bad.add('|');
        bad.add('?');
        bad.add('*');

        StringBuilder result = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c < ' ' || bad.contains(c)) {
                result.append('_');
            } else {
                result.append(c);
            }
        }
        if (result.length() == 0 || result.charAt(result.length() - 1) == ' ' || result.charAt(result.length() - 1) == '.') {
            result.append('_');
        }
        return result.toString();
    }

    public static String readFileContent(File file) {
        try {
            FileInputStream stream = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            stream.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e("exteraGram", "failed to read: " + e);
            return null;
        }
    }
}
