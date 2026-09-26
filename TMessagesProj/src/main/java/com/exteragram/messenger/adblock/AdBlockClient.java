package com.exteragram.messenger.adblock;

import android.text.TextUtils;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.adblock.data.BlockResult;
import com.exteragram.messenger.adblock.data.UrlCosmeticResources;
import com.exteragram.messenger.adblock.interop.AdBlock;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class AdBlockClient {

    public static CosmeticHide getCosmeticHide(String url) {
        UrlCosmeticResources resources = AdBlock.getCosmeticResources(url);
        if (resources == null) {
            FileLog.e("err cosmetic: " + url);
            return null;
        }
        FileLog.d("hideSelectors: " + Arrays.toString(resources.getHideSelectors()));
        FileLog.d("proceduralActions: " + Arrays.toString(resources.getProceduralActions()));
        FileLog.d("exceptions: " + Arrays.toString(resources.getExceptions()));
        FileLog.d("injectedScript: " + resources.getInjectedScript() + " genericHide: " + resources.isGenericHide());
        return new CosmeticHide(createHideScript(resources.getHideSelectors()), resources.getInjectedScript(), resources.getExceptions(), resources.isGenericHide());
    }

    public static String getCosmeticHideContinuous(CosmeticHide cosmeticHide, Set<String> alreadyHidden, String json) {
        ClassesAndIds classesAndIds = ExteraConfig.getGSON().fromJson(json, ClassesAndIds.class);
        if (classesAndIds == null) {
            return null;
        }
        String[] classes = classesAndIds.getClasses();
        String[] ids = classesAndIds.getIds();
        if (classes.length == 0 && ids.length == 0) {
            return null;
        }
        String[] selectors = AdBlock.getHiddenSelectors(classes, ids, cosmeticHide.exceptions);
        if (selectors == null || selectors.length == 0) {
            return null;
        }
        ArrayList<String> newSelectors = new ArrayList<>();
        for (String selector : selectors) {
            if (!alreadyHidden.contains(selector)) {
                newSelectors.add(selector);
                alreadyHidden.add(selector);
            }
        }
        if (newSelectors.isEmpty()) {
            return null;
        }
        return createHideScript(newSelectors.toArray(new String[0]));
    }

    public static BlockResult isAdRequest(WebResourceRequest request, String pageUrl) {
        return AdBlock.getBlockResult(request.getUrl().toString(), pageUrl, getRequestType(request, pageUrl));
    }

    public static String getRequestType(WebResourceRequest request, String pageUrl) {
        if ("OPTIONS".equals(request.getMethod())) {
            return "beacon";
        }
        String url = request.getUrl().toString();
        Map<String, String> headers = request.getRequestHeaders();
        if (request.isForMainFrame() && url.equals(pageUrl)) {
            return "main_frame";
        }
        if (url.startsWith("ws")) {
            return "websocket";
        }
        if (headers != null && "XMLHttpRequest".equals(headers.get("X-Requested-With"))) {
            return "xhr";
        }
        String extension = getRequestExtension(url);
        if ("js".equals(extension)) {
            return "script";
        }
        if ("css".equals(extension)) {
            return "stylesheet";
        }
        if ("otf".equals(extension) || "ttf".equals(extension) || "ttc".equals(extension) || "woff".equals(extension) || "woff2".equals(extension)) {
            return "font";
        }
        if (!"php".equals(extension)) {
            String mime = getRequestMime(extension);
            if (!"application/octet-stream".equals(mime)) {
                return getRequestTypeFromMime(mime);
            }
        }
        String accept = headers != null ? headers.get("Accept") : null;
        if (TextUtils.isEmpty(accept) || "*/*".equals(accept)) {
            return "other";
        }
        int comma = accept.indexOf(',');
        if (comma > 0) {
            accept = accept.substring(0, comma).trim();
        }
        return getRequestTypeFromMime(accept);
    }

    private static String getRequestExtension(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int query = url.indexOf('?');
        if (query > 0) {
            url = url.substring(0, query);
        }
        int slash = url.lastIndexOf('/');
        if (slash > 0) {
            url = url.substring(slash + 1);
        }
        int dot = url.lastIndexOf('.');
        if (dot <= 0 || dot == url.length() - 1) {
            return url.endsWith("js") ? "js" : null;
        }
        String extension = url.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!extension.isEmpty() && extension.length() <= 8) {
            return extension;
        }
        return null;
    }

    private static String getRequestMime(String extension) {
        if ("mhtml".equals(extension) || "mht".equals(extension)) {
            return "multipart/related";
        }
        if ("json".equals(extension)) {
            return "application/json";
        }
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return TextUtils.isEmpty(mime) ? "application/octet-stream" : mime;
    }

    private static String getRequestTypeFromMime(String mime) {
        if (TextUtils.isEmpty(mime)) {
            return "other";
        }
        if ("application/javascript".equals(mime) || "application/x-javascript".equals(mime) || "text/javascript".equals(mime) || "application/json".equals(mime)) {
            return "script";
        }
        if ("text/css".equals(mime)) {
            return "stylesheet";
        }
        if (mime.startsWith("image/")) {
            return "image";
        }
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            return "media";
        }
        return mime.startsWith("font/") ? "font" : "other";
    }

    private static String createHideScript(String[] selectors) {
        if (selectors == null || selectors.length == 0) {
            return null;
        }
        String css = String.join(",", selectors) + "{display: none !important;}";
        return "(function() {" +
                "var parent = document.getElementsByTagName('head').item(0);" +
                "var style = document.createElement('style');" +
                "style.type = 'text/css';" +
                "style.innerHTML = window.atob('" + Base64.getEncoder().encodeToString(css.getBytes()) + "');" +
                "parent.appendChild(style)" +
                "})()";
    }

    public static class CosmeticHide {
        private final String hideCss;
        private final String injectedScript;
        private final String[] exceptions;
        private final boolean genericHide;

        public CosmeticHide(String hideCss, String injectedScript, String[] exceptions, boolean genericHide) {
            this.hideCss = hideCss;
            this.injectedScript = injectedScript;
            this.exceptions = exceptions;
            this.genericHide = genericHide;
        }

        public String getHideCss() {
            return hideCss;
        }

        public String getInjectedScript() {
            return injectedScript;
        }

        public boolean isGenericHide() {
            return genericHide;
        }
    }

    public static class ClassesAndIds {
        private final String[] classes;
        private final String[] ids;

        public ClassesAndIds(String[] classes, String[] ids) {
            this.classes = classes;
            this.ids = ids;
        }

        public String[] getClasses() {
            return classes;
        }

        public String[] getIds() {
            return ids;
        }
    }
}
