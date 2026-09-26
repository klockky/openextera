package com.exteragram.messenger.adblock.interop;

import com.exteragram.messenger.adblock.backend.ScriptletsManager;
import com.exteragram.messenger.adblock.backend.SubscriptionsManager;
import com.exteragram.messenger.adblock.data.BlockResult;
import com.exteragram.messenger.adblock.data.UrlCosmeticResources;

import org.telegram.messenger.DispatchQueue;

import java.util.Collection;
import java.util.List;

public class AdBlock {

    private static final DispatchQueue queue = new DispatchQueue("adblock");
    private static final Object lock = new Object();

    private static long enginePtr = 0;
    private static long filterSetPtr = 0;

    public static void initialize() {
        queue.postRunnable(() -> {
            synchronized (lock) {
                if (enginePtr == 0) {
                    initializeInner();
                }
            }
        });
    }

    public static void destroy() {
        queue.postRunnable(() -> {
            synchronized (lock) {
                if (enginePtr != 0) {
                    NativeAdBlock.destroyEngine(enginePtr);
                    enginePtr = 0;
                }
                // the filter set is consumed by the engine
                filterSetPtr = 0;
            }
        });
    }

    public static void reload() {
        destroy();
        initialize();
    }

    private static void initializeInner() {
        filterSetPtr = NativeAdBlock.createFilterSet(new String[0]);
        for (String path : SubscriptionsManager.getInstance().getSubscriptionFilePaths()) {
            NativeAdBlock.addFilters(filterSetPtr, path);
        }
        long engine = NativeAdBlock.createEngine(filterSetPtr);

        Collection<ScriptletsManager.Scriptlet> scriptlets = ScriptletsManager.getInstance().iterScriptlets();
        int size = scriptlets.size();
        String[] names = new String[size];
        String[][] aliases = new String[size][];
        String[] kinds = new String[size];
        String[] contents = new String[size];
        int i = 0;
        for (ScriptletsManager.Scriptlet scriptlet : scriptlets) {
            names[i] = scriptlet.filename;
            List<String> scriptletAliases = scriptlet.aliases;
            aliases[i] = scriptletAliases != null ? scriptletAliases.toArray(new String[0]) : new String[0];
            kinds[i] = ScriptletsManager.getExtension(scriptlet.filename);
            contents[i] = scriptlet.content;
            i++;
        }
        NativeAdBlock.useResources(engine, names, aliases, kinds, contents);
        enginePtr = engine;
    }

    public static BlockResult getBlockResult(String url, String sourceUrl, String resourceType) {
        long engine = enginePtr;
        if (engine == 0) {
            return null;
        }
        return NativeAdBlock.shouldBlock(engine, url, sourceUrl, resourceType);
    }

    public static UrlCosmeticResources getCosmeticResources(String url) {
        long engine = enginePtr;
        if (engine == 0) {
            return null;
        }
        return NativeAdBlock.getCosmeticResources(engine, url);
    }

    public static String[] getHiddenSelectors(String[] classes, String[] ids, String[] exceptions) {
        long engine = enginePtr;
        if (engine == 0) {
            return null;
        }
        return NativeAdBlock.getHiddenSelectors(engine, classes, ids, exceptions);
    }
}
