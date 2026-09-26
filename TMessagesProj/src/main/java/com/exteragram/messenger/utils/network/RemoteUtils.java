package com.exteragram.messenger.utils.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.exteragram.messenger.utils.AppUtils;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class RemoteUtils {

    // TODO(openextera): disabled, exteraSquad infrastructure (remote-config channel fetching)
    private static final boolean REMOTE_CONFIG_ENABLED = false;

    private static final long CONFIG_CHANNEL_ID = 2227431611L;
    private static final String CONFIG_CHANNEL_INVITE = "XS6GEcz5ZXMu82UvXQc";
    private static final String LAST_FETCH_KEY = "__last_fetch_attempt_time";

    private static final long CONFIG_REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(10);
    private static final long CONFIG_RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(30);
    private static final long MESSAGES_REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

    public static SharedPreferences sharedPreferences;

    private static volatile long lastFailedFetchTime;
    private static final AtomicBoolean fetchInProgress = new AtomicBoolean();

    private static final Object messagesRequestLock = new Object();
    private static int messagesRequestGeneration;
    private static Runnable messagesTimeoutRunnable;
    private static ArrayList<Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error>> pendingMessagesCallbacks;

    private static SharedPreferences getPrefs() {
        if (sharedPreferences == null) {
            initCached();
        }
        return sharedPreferences;
    }

    public static void initCached() {
        if (sharedPreferences == null) {
            sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("exteraremoteconfig", Context.MODE_PRIVATE);
        }
    }

    public static void init() {
        initCached();
        long now = System.currentTimeMillis();
        if (Math.abs(now - sharedPreferences.getLong(LAST_FETCH_KEY, 0)) < CONFIG_REFRESH_INTERVAL) {
            return;
        }
        if (lastFailedFetchTime == 0 || Math.abs(now - lastFailedFetchTime) >= CONFIG_RETRY_INTERVAL) {
            loadConfig();
        }
    }

    public static void forceRefresh() {
        initCached();
        lastFailedFetchTime = 0;
        loadConfig();
    }

    private static void loadConfig() {
        if (!REMOTE_CONFIG_ENABLED) {
            return;
        }
        if (fetchInProgress.compareAndSet(false, true)) {
            getMessages(RemoteUtils::onConfigMessagesLoaded);
        }
    }

    private static void onConfigMessagesLoaded(TLRPC.messages_Messages res, TLRPC.TL_error error) {
        fetchInProgress.set(false);
        if (error != null || res == null) {
            lastFailedFetchTime = System.currentTimeMillis();
            return;
        }
        HashSet<String> keys = new HashSet<>();
        boolean found = false;
        for (int i = res.messages.size() - 1; i >= 0; i--) {
            TLRPC.Message message = res.messages.get(i);
            if (!(message instanceof TLRPC.TL_message) || !message.message.startsWith("remote_config")) {
                continue;
            }
            String[] lines = message.message.split("\n");
            if (lines.length > 1) {
                for (String line : lines) {
                    String[] pair = line.split("=", 2);
                    if (pair.length != 2) {
                        continue;
                    }
                    String key = pair[0].trim();
                    String value = pair[1].trim();
                    if (!value.equals("null")) {
                        updateValue(key, value);
                        keys.add(key);
                    }
                }
            }
            found = true;
        }
        if (!found || keys.isEmpty()) {
            lastFailedFetchTime = System.currentTimeMillis();
            return;
        }
        lastFailedFetchTime = 0;
        sharedPreferences.edit().putLong(LAST_FETCH_KEY, System.currentTimeMillis()).apply();
        removeOldPreferences(keys);
    }

    private static void removeOldPreferences(Set<String> keys) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (String key : sharedPreferences.getAll().keySet()) {
            if (!keys.contains(key) && !LAST_FETCH_KEY.equals(key)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    private static void updateValue(String key, String value) {
        if (areValuesEqual(sharedPreferences.getAll().get(key), parseConfigValue(value))) {
            return;
        }
        saveToPreferences(key, value);
    }

    private static boolean areValuesEqual(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
    }

    private static void saveToPreferences(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        saveConfigValueToPreferences(editor, key, parseConfigValue(value));
        editor.apply();
    }

    private static Object parseConfigValue(String value) {
        if (value.matches("-?\\d+")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return Float.parseFloat(value);
            }
        }
        if (value.matches("-?\\d+(\\.\\d+)")) {
            return Float.parseFloat(value);
        }
        if (value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            String content = value.substring(1, value.length() - 1);
            if (content.isEmpty()) {
                return new HashSet<String>();
            }
            return new HashSet<>(Arrays.asList(content.split(",\\s*")));
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void saveConfigValueToPreferences(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Set) {
            editor.putStringSet(key, (Set<String>) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        }
    }

    public static void getMessages(Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback) {
        synchronized (messagesRequestLock) {
            if (pendingMessagesCallbacks != null) {
                pendingMessagesCallbacks.add(callback);
                return;
            }
            final int generation = ++messagesRequestGeneration;
            pendingMessagesCallbacks = new ArrayList<>();
            pendingMessagesCallbacks.add(callback);

            messagesTimeoutRunnable = () -> {
                TLRPC.TL_error error = new TLRPC.TL_error();
                error.code = 408;
                error.text = "REQUEST_TIMEOUT";
                deliverMessagesResult(generation, null, error);
            };
            AndroidUtilities.runOnUIThread(messagesTimeoutRunnable, MESSAGES_REQUEST_TIMEOUT);

            if (ApplicationLoader.applicationHandler == null) {
                TLRPC.TL_error error = new TLRPC.TL_error();
                error.code = 503;
                error.text = "APP_NOT_READY";
                deliverMessagesResult(generation, null, error);
                return;
            }

            final AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);
            final TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.offset_id = 0;
            req.limit = 100;
            final Runnable sendRequest = () -> accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error != null || response == null) {
                    deliverMessagesResult(generation, null, error);
                } else {
                    deliverMessagesResult(generation, (TLRPC.messages_Messages) response, null);
                }
            });
            AndroidUtilities.runOnUIThread(() -> {
                req.peer = accountInstance.getMessagesController().getInputPeer(-CONFIG_CHANNEL_ID);
                if (req.peer.access_hash != 0) {
                    sendRequest.run();
                    return;
                }
                ChatUtils.getInstance().resolveChannel(CONFIG_CHANNEL_INVITE, chat -> {
                    if (chat != null && chat.id == CONFIG_CHANNEL_ID) {
                        TLRPC.TL_inputPeerChannel peer = new TLRPC.TL_inputPeerChannel();
                        peer.channel_id = chat.id;
                        peer.access_hash = chat.access_hash;
                        req.peer = peer;
                        sendRequest.run();
                    } else {
                        TLRPC.TL_error error = new TLRPC.TL_error();
                        error.code = 400;
                        error.text = "CHANNEL_RESOLVE_FAILED";
                        deliverMessagesResult(generation, null, error);
                    }
                });
            });
        }
    }

    private static void deliverMessagesResult(int generation, TLRPC.messages_Messages res, TLRPC.TL_error error) {
        synchronized (messagesRequestLock) {
            if (generation != messagesRequestGeneration || pendingMessagesCallbacks == null) {
                return;
            }
            ArrayList<Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error>> callbacks = pendingMessagesCallbacks;
            Runnable timeout = messagesTimeoutRunnable;
            pendingMessagesCallbacks = null;
            messagesTimeoutRunnable = null;
            if (timeout != null) {
                AndroidUtilities.cancelRunOnUIThread(timeout);
            }
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).run(res, error);
            }
        }
    }

    public static Integer getIntConfigValue(String key, int defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            Object value = prefs.getAll().get(key);
            if (value instanceof String) {
                return Integer.parseInt((String) value);
            }
            if (value instanceof Long) {
                return ((Long) value).intValue();
            }
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception e) {
            AppUtils.log("Error getting int config value for key: " + key, e);
        }
        return defaultValue;
    }

    public static Float getFloatConfigValue(String key, float defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            Object value = prefs.getAll().get(key);
            if (value instanceof String) {
                return Float.parseFloat((String) value);
            }
            if (value instanceof Float) {
                return (Float) value;
            }
            if (value instanceof Long) {
                return ((Long) value).floatValue();
            }
            if (value instanceof Integer) {
                return ((Integer) value).floatValue();
            }
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
        }
        return defaultValue;
    }

    public static Boolean getBooleanConfigValue(String key, boolean defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            try {
                return prefs.getBoolean(key, defaultValue);
            } catch (ClassCastException e) {
                Object value = prefs.getAll().get(key);
                return value instanceof String ? Boolean.parseBoolean((String) value) : defaultValue;
            }
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public static Set<String> getStringSetConfigValue(String key, Set<String> defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                Object value = prefs.getAll().get(key);
                if (value instanceof Set) {
                    return (Set<String>) value;
                }
                if (value instanceof String) {
                    return new HashSet<>(Arrays.asList(((String) value).split(",\\s*")));
                }
            }
            return defaultValue;
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
            return defaultValue;
        }
    }

    public static String getStringConfigValue(String key, String defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                Object value = prefs.getAll().get(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
            return defaultValue;
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
            return defaultValue;
        }
    }
}
