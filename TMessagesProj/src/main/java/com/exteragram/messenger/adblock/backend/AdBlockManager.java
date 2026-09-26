package com.exteragram.messenger.adblock.backend;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.adblock.interop.AdBlock;
import com.exteragram.messenger.adblock.interop.NativeAdBlock;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.FileLog;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AdBlockManager {

    private static final String[] FILTERS = {
            "https://ublockorigin.github.io/uAssetsCDN/filters/filters.min.txt",
            "https://ublockorigin.github.io/uAssetsCDN/filters/badware.min.txt",
            "https://ublockorigin.github.io/uAssetsCDN/filters/privacy.min.txt",
            "https://ublockorigin.github.io/uAssetsCDN/filters/unbreak.min.txt",
            "https://ublockorigin.github.io/uAssetsCDN/filters/quick-fixes.min.txt",
            "https://filters.adtidy.org/extension/ublock/filters/11.txt",
            "https://filters.adtidy.org/extension/ublock/filters/2_without_easylist.txt",
            "https://cdn.jsdelivr.net/gh/uBlockOrigin/uAssetsCDN@main/thirdparties/easylist.txt",
            "https://cdn.jsdelivr.net/gh/dimisa-RUAdList/RUAdListCDN@main/lists/ruadlist.ubo.min.txt"
    };

    public static void initialize() {
        if (!RemoteUtils.getBooleanConfigValue("use_adblock", false) || !ExteraConfig.getEnableAdBlock() || !NativeAdBlock.loadLibraries()) {
            return;
        }
        if (ScriptletsManager.getInstance().isDownloaded()) {
            continueInitialize();
            return;
        }
        ScriptletsManager.getInstance().download(new ScriptletsManager.DownloadCallback() {
            @Override
            public void onProgress(int downloaded, int total) {
                FileLog.d("scriptlet download progress: " + downloaded + "/" + total);
                if (downloaded == total) {
                    continueInitialize();
                }
            }

            @Override
            public void onError() {
                FileLog.e("unable to download all scriptlets");
                continueInitialize();
            }
        });
    }

    private static void continueInitialize() {
        if (SubscriptionsManager.getInstance().getSubscriptions().isEmpty()) {
            addDefaultFilters();
        } else {
            SubscriptionsManager.getInstance().initialize(AdBlock::reload);
        }
    }

    private static void addDefaultFilters() {
        AtomicInteger completed = new AtomicInteger(0);
        for (String url : FILTERS) {
            SubscriptionsManager.getInstance().subscribe(url, success -> {
                if (success) {
                    FileLog.d("filter loaded: " + url);
                } else {
                    FileLog.e("filter failed to load: " + url);
                }
                if (completed.incrementAndGet() == FILTERS.length) {
                    FileLog.d("all filters loaded");
                    AdBlock.reload();
                }
            });
        }
    }
}
