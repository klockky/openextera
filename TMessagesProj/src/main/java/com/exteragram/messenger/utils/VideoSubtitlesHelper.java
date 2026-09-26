package com.exteragram.messenger.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

public abstract class VideoSubtitlesHelper {

    private static final String MIME_VTT = "text/vtt";
    private static final String MIME_SRT = "application/x-subrip";

    public enum LoadError {
        NONE,
        UNSUPPORTED_FORMAT,
        LOAD_FAILED
    }

    public static final class SubtitleState {
        private final String path;
        private final String mimeType;
        private final String label;

        public SubtitleState(String path, String mimeType, String label) {
            this.path = path;
            this.mimeType = mimeType;
            this.label = label;
        }

        public String path() {
            return path;
        }

        public String mimeType() {
            return mimeType;
        }

        public String label() {
            return label;
        }

        public boolean isValid() {
            if (TextUtils.isEmpty(path) || TextUtils.isEmpty(mimeType)) {
                return false;
            }
            File file = new File(path);
            return file.exists() && file.length() > 0;
        }

        public String getDisplayName() {
            if (!TextUtils.isEmpty(label)) {
                return label;
            }
            return new File(path).getName();
        }

        public VideoPlayer.ExternalSubtitle toExternalSubtitle() {
            return new VideoPlayer.ExternalSubtitle(Uri.fromFile(new File(path)), mimeType, getDisplayName());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SubtitleState)) return false;
            SubtitleState that = (SubtitleState) o;
            return Objects.equals(path, that.path) && Objects.equals(mimeType, that.mimeType) && Objects.equals(label, that.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, mimeType, label);
        }

        @Override
        public String toString() {
            return "SubtitleState[path=" + path + ", mimeType=" + mimeType + ", label=" + label + "]";
        }
    }

    public static final class SubtitleLoadResult {
        private final SubtitleState subtitleState;
        private final LoadError error;

        public SubtitleLoadResult(SubtitleState subtitleState, LoadError error) {
            this.subtitleState = subtitleState;
            this.error = error;
        }

        public SubtitleState subtitleState() {
            return subtitleState;
        }

        public LoadError error() {
            return error;
        }

        public boolean isSuccess() {
            return subtitleState != null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SubtitleLoadResult)) return false;
            SubtitleLoadResult that = (SubtitleLoadResult) o;
            return Objects.equals(subtitleState, that.subtitleState) && error == that.error;
        }

        @Override
        public int hashCode() {
            return Objects.hash(subtitleState, error);
        }

        @Override
        public String toString() {
            return "SubtitleLoadResult[subtitleState=" + subtitleState + ", error=" + error + "]";
        }
    }

    public static boolean areSame(SubtitleState a, SubtitleState b) {
        if (a == b) {
            return true;
        }
        return a != null && b != null
                && TextUtils.equals(a.path, b.path)
                && TextUtils.equals(a.mimeType, b.mimeType)
                && TextUtils.equals(a.label, b.label);
    }

    public static TextView createSubtitlesView(Context context) {
        TextView textView = new TextView(context);
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(0xffffffff);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, AndroidUtilities.isTablet() ? 20 : 16);
        textView.setMaxLines(3);
        textView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        textView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), 0xcc000000));
        textView.setShadowLayer(AndroidUtilities.dp(2), 0, AndroidUtilities.dp(1), 0x99000000);
        textView.setVisibility(View.GONE);
        return textView;
    }

    public static String buildVideoKey(MessageObject messageObject, String path, Uri uri, TL_iv.PageBlock pageBlock) {
        if (messageObject != null && messageObject.getDocument() != null) {
            return "doc_" + messageObject.getDocument().id;
        }
        if (!TextUtils.isEmpty(path)) {
            return makePathKey(path);
        }
        if (uri != null) {
            return makeUriKey(uri);
        }
        if (pageBlock instanceof TL_iv.pageBlockVideo) {
            return "page_video_" + ((TL_iv.pageBlockVideo) pageBlock).video_id;
        }
        return null;
    }

    public static SubtitleLoadResult loadFromPickerIntent(Intent intent) {
        return loadFromUri(intent != null ? intent.getData() : null);
    }

    public static Intent createPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{MIME_SRT, MIME_VTT});
        return intent;
    }

    public static SubtitleLoadResult loadFromUri(Uri uri) {
        if (uri == null) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }
        String mimeType = resolveMimeType(uri);
        if (TextUtils.isEmpty(mimeType)) {
            return new SubtitleLoadResult(null, LoadError.UNSUPPORTED_FORMAT);
        }
        String extension = MIME_VTT.equals(mimeType) ? "vtt" : "srt";
        String localPath = null;
        try {
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                localPath = uri.getPath();
            } else {
                String path = AndroidUtilities.getPath(uri);
                if (TextUtils.isEmpty(path) || path.startsWith("content://")) {
                    localPath = MediaController.copyFileToCache(uri, extension);
                } else {
                    localPath = path;
                }
            }
        } catch (Exception ignore) {
        }
        if (TextUtils.isEmpty(localPath)) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }
        File file = new File(localPath);
        if (!file.exists() || file.length() <= 0) {
            return new SubtitleLoadResult(null, LoadError.LOAD_FAILED);
        }
        return new SubtitleLoadResult(new SubtitleState(localPath, mimeType, file.getName()), LoadError.NONE);
    }

    public static SubtitleState restore(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        SharedPreferences preferences = getPreferences();
        String path = preferences.getString("path_" + key, null);
        String mime = preferences.getString("mime_" + key, null);
        String label = preferences.getString("label_" + key, null);
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(mime)) {
            return null;
        }
        SubtitleState state = new SubtitleState(path, mime, label);
        if (state.isValid()) {
            return state;
        }
        clear(key);
        return null;
    }

    public static void save(String key, SubtitleState state) {
        if (TextUtils.isEmpty(key) || state == null || !state.isValid()) {
            return;
        }
        getPreferences().edit()
                .putString("path_" + key, state.path())
                .putString("mime_" + key, state.mimeType())
                .putString("label_" + key, state.getDisplayName())
                .apply();
    }

    public static void clear(String key) {
        if (TextUtils.isEmpty(key)) {
            return;
        }
        getPreferences().edit()
                .remove("path_" + key)
                .remove("mime_" + key)
                .remove("label_" + key)
                .apply();
    }

    public static String makeUriKey(Uri uri) {
        if (uri == null) {
            return null;
        }
        return makePathKey(uri.toString());
    }

    public static String makePathKey(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        return "uri_" + Utilities.MD5(path);
    }

    private static String resolveMimeType(Uri uri) {
        String type;
        try {
            type = ApplicationLoader.applicationContext.getContentResolver().getType(uri);
        } catch (Exception e) {
            type = null;
        }
        if (!TextUtils.isEmpty(type)) {
            String lowerType = type.toLowerCase(Locale.US);
            if (MIME_VTT.equals(lowerType) || lowerType.contains("vtt")) {
                return MIME_VTT;
            }
            if (MIME_SRT.equals(lowerType) || lowerType.contains("subrip") || lowerType.contains("srt")) {
                return MIME_SRT;
            }
        }
        String extension = getSubtitleExtension(uri);
        if ("vtt".equals(extension)) {
            return MIME_VTT;
        }
        if ("srt".equals(extension)) {
            return MIME_SRT;
        }
        return null;
    }

    private static String getSubtitleExtension(Uri uri) {
        String path = AndroidUtilities.getPath(uri);
        if (TextUtils.isEmpty(path)) {
            path = uri.getPath();
        }
        if (TextUtils.isEmpty(path)) {
            path = uri.toString();
        }
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        String lowerPath = path.toLowerCase(Locale.US);
        if (lowerPath.endsWith(".srt")) {
            return "srt";
        }
        if (lowerPath.endsWith(".vtt")) {
            return "vtt";
        }
        return null;
    }

    private static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("video_external_subtitles", Context.MODE_PRIVATE);
    }
}
