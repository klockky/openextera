package com.exteragram.messenger.adblock;

import android.text.TextUtils;
import android.webkit.JavascriptInterface;

import androidx.annotation.Keep;

import org.telegram.ui.web.BotWebViewContainer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SelectorsObserver {

    private final BotWebViewContainer.MyWebView webView;
    private final Set<String> filtered = Collections.synchronizedSet(new HashSet<>());
    private final Object lock = new Object();
    private volatile AdBlockClient.CosmeticHide cosmeticHide;

    public SelectorsObserver(BotWebViewContainer.MyWebView webView) {
        this.webView = webView;
    }

    public void setCosmeticHide(AdBlockClient.CosmeticHide cosmeticHide) {
        synchronized (lock) {
            filtered.clear();
            this.cosmeticHide = cosmeticHide;
        }
    }

    @Keep
    @JavascriptInterface
    public void onElementsFound(String json) {
        synchronized (lock) {
            if (cosmeticHide == null) {
                return;
            }
            String script = AdBlockClient.getCosmeticHideContinuous(cosmeticHide, filtered, json);
            if (!TextUtils.isEmpty(script)) {
                webView.post(() -> webView.evaluateJS(script));
            }
        }
    }
}
