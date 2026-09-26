package com.exteragram.messenger.badges;

import android.content.SharedPreferences;

import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.FileLog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CachedRemoteSet {

    private final String remoteKey;
    private final Set<Long> defaultSet;

    private volatile Set<Long> cachedValues;
    private volatile boolean listenerInitialized = false;
    private SharedPreferences.OnSharedPreferenceChangeListener changeListener;

    public CachedRemoteSet(String remoteKey, Set<Long> defaultSet) {
        this.remoteKey = remoteKey;
        this.defaultSet = Collections.unmodifiableSet(new HashSet<>(defaultSet));
        initializeListener();
    }

    private void initializeListener() {
        if (listenerInitialized) {
            return;
        }
        RemoteUtils.initCached();
        if (RemoteUtils.sharedPreferences != null) {
            changeListener = (sharedPreferences, key) -> {
                if (remoteKey.equals(key)) {
                    cachedValues = null;
                }
            };
            RemoteUtils.sharedPreferences.registerOnSharedPreferenceChangeListener(changeListener);
            listenerInitialized = true;
        }
    }

    private Set<Long> getSet() {
        if (!listenerInitialized) {
            initializeListener();
        }
        Set<Long> values = cachedValues;
        if (values == null) {
            Set<Long> source;
            SharedPreferences preferences = RemoteUtils.sharedPreferences;
            if (preferences != null && preferences.contains(remoteKey)) {
                Set<String> stringSet = preferences.getStringSet(remoteKey, Collections.emptySet());
                source = new HashSet<>(stringSet.size());
                for (String value : stringSet) {
                    try {
                        source.add(Long.parseLong(value));
                    } catch (NumberFormatException e) {
                        FileLog.e("Failed to parse long from remote config for key " + remoteKey + ": " + value, e);
                    }
                }
            } else {
                source = defaultSet;
            }
            values = ConcurrentHashMap.newKeySet(source.size());
            values.addAll(source);
            cachedValues = values;
        }
        return Collections.unmodifiableSet(values);
    }

    public boolean contains(long id) {
        return getSet().contains(id);
    }
}
