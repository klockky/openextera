package com.exteragram.messenger.export.ui;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;

public abstract class AndroidPickerUtils {

    public static String getPath(Context context, Uri uri) {
        if (!DocumentsContract.isDocumentUri(context, uri)) {
            return null;
        }
        if (AndroidUtilities.isExternalStorageDocument(uri)) {
            String documentId = DocumentsContract.getDocumentId(uri);
            String[] split = documentId.split(":");
            String type = split[0];
            if ("primary".equalsIgnoreCase(type)) {
                if (split.length > 1) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
                return Environment.getExternalStorageDirectory() + "/";
            }
            if ("home".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/Documents/" + (split.length > 1 ? split[1] : "");
            }
            if (new File("storage/" + documentId.replace(":", "/")).exists()) {
                return "/storage/" + documentId.replace(":", "/");
            }
            String path = "";
            for (String storage : AndroidSDUtils.getStorageDirectories(context)) {
                path = split[1].startsWith("/") ? storage + split[1] : storage + "/" + split[1];
            }
            if (path.contains(type)) {
                return "storage/" + documentId.replace(":", "/");
            }
            if (path.startsWith("/storage/") || path.startsWith("storage/")) {
                return path;
            }
            if (path.startsWith("/")) {
                return "/storage" + path;
            }
            return "/storage/" + path;
        }
        try {
            if (AndroidUtilities.isDownloadsDocument(uri)) {
                long id;
                try {
                    String documentId = DocumentsContract.getDocumentId(uri);
                    if (documentId.startsWith("raw:")) {
                        documentId = documentId.replaceFirst("raw:", "");
                        if (new File(documentId).exists()) {
                            return documentId;
                        }
                    }
                    if (documentId.startsWith("raw%3A%2F")) {
                        documentId = documentId.replaceFirst("raw%3A%2F", "");
                        if (new File(documentId).exists()) {
                            return documentId;
                        }
                    }
                    id = Long.parseLong(documentId);
                } catch (NumberFormatException e) {
                    id = ContentUris.parseId(uri);
                }
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id);
                return AndroidUtilities.getDataColumn(context, contentUri, null, null);
            }
            if (AndroidUtilities.isMediaDocument(uri)) {
                String[] split = DocumentsContract.getDocumentId(uri).split(":");
                String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                return AndroidUtilities.getDataColumn(context, contentUri, "_id=?", new String[]{split[1]});
            }
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
