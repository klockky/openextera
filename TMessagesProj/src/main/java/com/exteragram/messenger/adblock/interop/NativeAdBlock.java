package com.exteragram.messenger.adblock.interop;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.adblock.data.BlockResult;
import com.exteragram.messenger.adblock.data.FilterListMetadata;
import com.exteragram.messenger.adblock.data.UrlCosmeticResources;

// JNI bridge to libetgadblock.so (adblock-rust). Method names and signatures must match the native library.
public class NativeAdBlock {

    public static native long createFilterSet(String[] rules);

    public static native FilterListMetadata addFilters(long filterSetPtr, String filePath);

    public static native void destroyFilterSet(long filterSetPtr);

    public static native long createEngine(long filterSetPtr);

    public static native void destroyEngine(long enginePtr);

    public static native void useResources(long enginePtr, String[] names, String[][] aliases, String[] kinds, String[] contents);

    public static native BlockResult shouldBlock(long enginePtr, String url, String sourceUrl, String resourceType);

    public static native UrlCosmeticResources getCosmeticResources(long enginePtr, String url);

    public static native String[] getHiddenSelectors(long enginePtr, String[] classes, String[] ids, String[] exceptions);

    public static boolean loadLibraries() {
        try {
            System.loadLibrary("etgadblock");
            return true;
        } catch (Throwable e) {
            ExteraConfig.setEnableAdBlock(false);
            return false;
        }
    }
}
