package com.exteragram.messenger.feed.ads;

import android.os.SystemClock;
import android.text.TextUtils;

import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

public final class FeedAdController {

    // TODO(openextera): exteraSquad infrastructure — @exteraFeedAds channel
    private static final String ADS_CHANNEL_USERNAME = "exteraFeedAds";
    private static final long ADS_CHANNEL_ID = 3514621311L;
    private static final int HISTORY_LIMIT = 75;
    private static final long RELOAD_INTERVAL = 30 * 60 * 1000L;
    private static final long RETRY_INTERVAL = 60 * 1000L;

    private static final FeedAdController[] instances = new FeedAdController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] locks = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    public static FeedAdController getInstance(int account) {
        FeedAdController instance = instances[account];
        if (instance != null) {
            return instance;
        }
        synchronized (locks[account]) {
            instance = instances[account];
            if (instance == null) {
                instances[account] = instance = new FeedAdController(account);
            }
        }
        return instance;
    }

    public final int currentAccount;
    private final ArrayList<FeedAd> allAds = new ArrayList<>();
    private ArrayList<FeedAd> eligibleAds = new ArrayList<>();
    private final ArrayList<FeedAd> rotation = new ArrayList<>();
    private final ArrayList<Runnable> pendingLoadCallbacks = new ArrayList<>();
    private int rotationIndex;
    private boolean loading;
    private long lastLoadTime;
    private boolean lastLoadFailed;

    private FeedAdController(int account) {
        currentAccount = account;
    }

    public boolean isEnabled() {
        return RemoteUtils.getBooleanConfigValue("feed_ads_enabled", true) && !eligibleAds.isEmpty();
    }

    public int getFirstAfter() {
        return Math.max(1, RemoteUtils.getIntConfigValue("feed_ad_first_after", 30));
    }

    public int getMinTrailing() {
        return Math.max(0, RemoteUtils.getIntConfigValue("feed_ad_min_trailing", 2));
    }

    public int getBaseEvery() {
        return Math.max(1, RemoteUtils.getIntConfigValue("feed_ad_every", 30));
    }

    public int getEffectiveEvery() {
        int baseEvery = getBaseEvery();
        int poolSize = eligibleAds.size();
        if (poolSize <= 1) {
            return baseEvery * Math.max(1, RemoteUtils.getIntConfigValue("feed_ad_spacing_pool1", 3));
        } else if (poolSize == 2) {
            return baseEvery * Math.max(1, RemoteUtils.getIntConfigValue("feed_ad_spacing_pool2", 2));
        }
        return baseEvery;
    }

    public FeedAd nextAd() {
        if (eligibleAds.isEmpty()) {
            return null;
        }
        if (rotation.isEmpty() || rotationIndex >= rotation.size()) {
            reshuffleRotation();
        }
        return rotation.get(rotationIndex++);
    }

    private void reshuffleRotation() {
        rotation.clear();
        for (FeedAd ad : eligibleAds) {
            for (int i = 0; i < Math.max(1, ad.weight); i++) {
                rotation.add(ad);
            }
        }
        Collections.shuffle(rotation);
        rotationIndex = 0;
    }

    public void ensureLoaded(Runnable onLoaded) {
        if (!loading && lastLoadTime != 0 && SystemClock.elapsedRealtime() - lastLoadTime < (lastLoadFailed ? RETRY_INTERVAL : RELOAD_INTERVAL)) {
            recomputeEligible();
            if (onLoaded != null) {
                onLoaded.run();
            }
            return;
        }
        if (onLoaded != null) {
            pendingLoadCallbacks.add(onLoaded);
        }
        if (loading) {
            return;
        }
        loading = true;
        fetchHistory((res, error) -> {
            loading = false;
            lastLoadTime = SystemClock.elapsedRealtime();
            lastLoadFailed = error != null || res == null;
            if (!lastLoadFailed) {
                allAds.clear();
                allAds.addAll(FeedAdParser.parse(res));
                recomputeEligible();
            }
            ArrayList<Runnable> callbacks = new ArrayList<>(pendingLoadCallbacks);
            pendingLoadCallbacks.clear();
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).run();
            }
        });
    }

    public void recomputeEligible() {
        ArrayList<FeedAd> eligible = new ArrayList<>(allAds.size());
        for (int i = 0; i < allAds.size(); i++) {
            if (isEligible(allAds.get(i))) {
                eligible.add(allAds.get(i));
            }
        }
        boolean same = eligible.size() == eligibleAds.size();
        for (int i = 0; same && i < eligible.size(); i++) {
            same = TextUtils.equals(eligible.get(i).id, eligibleAds.get(i).id) && eligible.get(i).weight == eligibleAds.get(i).weight;
        }
        eligibleAds = eligible;
        if (!same) {
            rotation.clear();
            rotationIndex = 0;
            return;
        }
        if (rotation.isEmpty()) {
            return;
        }
        HashMap<String, FeedAd> byId = new HashMap<>();
        for (int i = 0; i < eligible.size(); i++) {
            byId.put(eligible.get(i).id, eligible.get(i));
        }
        for (int i = 0; i < rotation.size(); i++) {
            FeedAd fresh = byId.get(rotation.get(i).id);
            if (fresh != null) {
                rotation.set(i, fresh);
            }
        }
    }

    private boolean isEligible(FeedAd ad) {
        if (!matchesLocale(ad.locales)) {
            return false;
        }
        boolean premium = UserConfig.getInstance(currentAccount).isPremium();
        if (ad.premium == FeedAdParser.MATCH_HAS && !premium || ad.premium == FeedAdParser.MATCH_NONE && premium) {
            return false;
        }
        boolean hasBadge = BadgesController.INSTANCE.hasBadge();
        return !(ad.badge == FeedAdParser.MATCH_HAS && !hasBadge) && !(ad.badge == FeedAdParser.MATCH_NONE && hasBadge);
    }

    private boolean matchesLocale(Set<String> locales) {
        if (locales == null || locales.isEmpty()) {
            return true;
        }
        LocaleController.LocaleInfo localeInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        return localeInfo == null || contains(locales, localeInfo.getLangCode()) || contains(locales, localeInfo.shortName) || contains(locales, localeInfo.baseLangCode);
    }

    private static boolean contains(Set<String> set, String value) {
        return value != null && set.contains(value.toLowerCase());
    }

    private void fetchHistory(Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback) {
        AccountInstance accountInstance = AccountInstance.getInstance(currentAccount);
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = accountInstance.getMessagesController().getInputPeer(-ADS_CHANNEL_ID);
        req.offset_id = 0;
        req.limit = HISTORY_LIMIT;
        Runnable send = () -> accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                callback.run(null, error);
            } else {
                callback.run((TLRPC.messages_Messages) response, null);
            }
        }));
        if (req.peer != null && req.peer.access_hash != 0) {
            send.run();
        } else {
            ChatUtils.getInstance(currentAccount).resolveChannel(ADS_CHANNEL_USERNAME, chat -> {
                if (chat != null && chat.id == ADS_CHANNEL_ID) {
                    TLRPC.TL_inputPeerChannel peer = new TLRPC.TL_inputPeerChannel();
                    peer.channel_id = chat.id;
                    peer.access_hash = chat.access_hash;
                    req.peer = peer;
                    send.run();
                } else {
                    callback.run(null, null);
                }
            });
        }
    }
}
