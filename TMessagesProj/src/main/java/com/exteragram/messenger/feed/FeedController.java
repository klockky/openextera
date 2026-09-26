package com.exteragram.messenger.feed;

import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.collection.LongSparseArray;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Feed: a merged timeline of posts from all subscribed channels, built from the local message cache
 * and presented through ChatActivity in hashtag-search mode.
 */
public class FeedController implements NotificationCenter.NotificationCenterDelegate {

    public interface ChannelsCallback {
        void onChannels(ArrayList<TLRPC.Chat> channels, int includedCount, boolean failed, int configGeneration);
    }

    public static final class SavedScrollPosition {
        public final long dialogId;
        public final int messageId;
        public final int offsetTop;

        private SavedScrollPosition(long dialogId, int messageId, int offsetTop) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.offsetTop = offsetTop;
        }
    }

    private static final int LOAD_TYPE_INITIAL = 0;
    private static final int LOAD_TYPE_NEWER = 1;
    private static final int LOAD_TYPE_OLDER = 2;
    private static final int CHUNK_SIZE = 30;
    private static final int MAX_BACKFILL_ROUNDS = 3;
    private static final int MAX_STALE_RETRIES = 3;

    private static final FeedController[] Instance = new FeedController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < lockObjects.length; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static FeedController peekInstance(int account) {
        return Instance[account];
    }

    public static FeedController getInstance(int account) {
        FeedController localInstance = Instance[account];
        if (localInstance != null) {
            return localInstance;
        }
        synchronized (lockObjects[account]) {
            localInstance = Instance[account];
            if (localInstance == null) {
                Instance[account] = localInstance = new FeedController(account);
            }
        }
        return localInstance;
    }

    public final int currentAccount;
    private final FeedStore store = new FeedStore();
    private final FeedUnreadTracker unreadTracker;
    private final FeedTimelineLoader loader;
    private final FeedBackfillCoordinator backfill;
    private final ArrayList<int[]> initialLoadWaiters = new ArrayList<>();
    private final int closedRefreshGuid = ConnectionsManager.generateClassGuid();
    private final Runnable closedRefreshRunnable = this::runClosedRefresh;

    private int sessionGeneration;
    private int configGeneration;
    private boolean loading;
    private boolean loadingNewer;
    private boolean olderPagingBoundsDirty;
    private boolean newerPagingBoundsDirty;
    private int heldGuid;
    private int heldLoadIndex;
    private int attemptRounds;
    private int staleEnumerationRetries;
    private boolean hasChannels;
    private boolean hasIncludedChannels;
    private int cachedIncludedChannelCount = -1;
    private boolean initialUnreadScrollPending = true;
    private SavedScrollPosition drawerScrollPosition;
    private int uiActiveClients;
    private int resumedUiClients;
    private boolean closedRefreshScheduled;

    private FeedController(int account) {
        currentAccount = account;
        unreadTracker = new FeedUnreadTracker(account, store.getMessages());
        loader = new FeedTimelineLoader(account);
        backfill = new FeedBackfillCoordinator(account, this::onBackfillRoundFinished);
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter notificationCenter = NotificationCenter.getInstance(account);
            notificationCenter.addObserver(this, NotificationCenter.messagesDidLoad);
            notificationCenter.addObserver(this, NotificationCenter.loadingMessagesFailed);
            notificationCenter.addObserver(this, NotificationCenter.messagesDeleted);
            notificationCenter.addObserver(this, NotificationCenter.historyCleared);
            notificationCenter.addObserver(this, NotificationCenter.didReceiveNewMessages);
            FeedChannelRegistry.getInstance(account).addListener(this::onFeedChannelsChanged);
        });
    }

    private void onFeedChannelsChanged(HashSet<Long> added, HashSet<Long> removed) {
        loader.invalidateChannelCache();
        for (Long dialogId : removed) {
            deleteHistory(dialogId, Integer.MAX_VALUE);
        }
        if (added.isEmpty()) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, false);
        } else {
            reconcileChannelSet(truncated -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, truncated));
        }
    }

    public FeedStore getStore() {
        return store;
    }

    public ArrayList<MessageObject> getMessages() {
        return store.getMessages();
    }

    public boolean isLoading() {
        return loading || loadingNewer;
    }

    public boolean hasMessagesForDialog(long dialogId) {
        return store.hasMessagesForDialog(dialogId);
    }

    public boolean hasChannels() {
        return hasChannels;
    }

    public boolean hasIncludedChannels() {
        return hasIncludedChannels;
    }

    public int getIncludedChannelCount() {
        return cachedIncludedChannelCount;
    }

    public void setUiActive(boolean active) {
        if (active) {
            if (++uiActiveClients > 1) {
                return;
            }
            if (closedRefreshScheduled) {
                AndroidUtilities.cancelRunOnUIThread(closedRefreshRunnable);
                closedRefreshScheduled = false;
            }
            if (loadingNewer) {
                cancelLoads();
            }
        } else {
            if (uiActiveClients == 0) {
                return;
            }
            if (--uiActiveClients == 0) {
                cancelLoads();
                trimForInactiveCache();
            }
        }
    }

    private boolean isUiActive() {
        return uiActiveClients > 0;
    }

    public void setUiResumed(boolean resumed) {
        if (resumed) {
            resumedUiClients++;
        } else if (resumedUiClients > 0) {
            resumedUiClients--;
        }
    }

    public void clear() {
        sessionGeneration++;
        configGeneration = FeedConfig.getInstance(currentAccount).getGeneration();
        unreadTracker.clear();
        drawerScrollPosition = null;
        store.clear();
        loading = false;
        loadingNewer = false;
        olderPagingBoundsDirty = false;
        newerPagingBoundsDirty = false;
        attemptRounds = 0;
        staleEnumerationRetries = 0;
        initialLoadWaiters.clear();
        backfill.cancel();
        backfill.clearExhausted();
        if (closedRefreshScheduled) {
            AndroidUtilities.cancelRunOnUIThread(closedRefreshRunnable);
            closedRefreshScheduled = false;
        }
    }

    public void cancelLoads() {
        sessionGeneration++;
        loading = false;
        loadingNewer = false;
        olderPagingBoundsDirty = false;
        newerPagingBoundsDirty = false;
        attemptRounds = 0;
        staleEnumerationRetries = 0;
        initialLoadWaiters.clear();
        backfill.cancel();
    }

    private static int getInactiveCacheCap() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return 300;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 1000;
            default:
                return 600;
        }
    }

    public void trimForInactiveCache() {
        if (isUiActive() || store.isEmpty()) {
            return;
        }
        store.trim(getInactiveCacheCap());
    }

    public boolean isIncludedChannelPost(long dialogId) {
        if (!DialogObject.isChatDialog(dialogId) || FeedConfig.getInstance(currentAccount).isExcluded(dialogId)) {
            return false;
        }
        return isEligibleChannel(MessagesController.getInstance(currentAccount).getChat(-dialogId));
    }

    public static boolean isEligibleChannel(TLRPC.Chat chat) {
        return chat != null && ChatObject.isChannelAndNotMegaGroup(chat) && !ChatObject.isCommunity(chat) && !ChatObject.isNotInChat(chat);
    }

    public boolean consumeInitialUnreadScroll() {
        boolean pending = initialUnreadScrollPending;
        initialUnreadScrollPending = false;
        return pending;
    }

    public int getUnreadCount() {
        return ExteraConfig.getShowFeedUnreadCounter() ? unreadTracker.getUnreadCount() : 0;
    }

    public void onPostSeen(long dialogId, int realId) {
        unreadTracker.onPostSeen(dialogId, realId);
    }

    public void markAllRead() {
        unreadTracker.markAllRead();
    }

    public int findFirstUnreadIndex(ArrayList<MessageObject> messages) {
        return unreadTracker.findFirstUnreadIndex(messages);
    }

    public int countUnreadBelow(ArrayList<MessageObject> messages, int index) {
        return unreadTracker.countUnreadBelow(messages, index);
    }

    public void saveDrawerScrollPosition(long dialogId, int messageId, int offsetTop) {
        if (dialogId == 0 || messageId <= 0) {
            return;
        }
        drawerScrollPosition = new SavedScrollPosition(dialogId, messageId, offsetTop);
    }

    public SavedScrollPosition getDrawerScrollPosition() {
        return drawerScrollPosition;
    }

    public boolean hasNoSyntheticIds() {
        return store.hasNoSyntheticIds();
    }

    public MessageObject getMessage(long dialogId, int id) {
        return store.getMessage(dialogId, id);
    }

    public int resolveRealMessageId(long dialogId, int id) {
        return store.resolveRealMessageId(dialogId, id);
    }

    public long resolveRealDialogId(int id) {
        return store.resolveRealDialogId(id);
    }

    /**
     * @return true if cached rows will be delivered, false if a load was started (or queued)
     */
    public boolean loadInitial(int classGuid, int loadIndex) {
        ensureCurrentConfig();
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        int generation = feedConfig.getGeneration();
        int cacheEpoch = loader.getChannelCacheEpoch();
        if (store.isEmpty()) {
            if (!loadMore(classGuid, loadIndex)) {
                initialLoadWaiters.add(new int[]{classGuid, loadIndex});
            }
            return false;
        }
        ArrayList<MessageObject> visibleMessages = store.getVisibleMessages();
        for (MessageObject messageObject : visibleMessages) {
            messageObject.viewsReloaded = false;
        }
        if (visibleMessages.isEmpty() && !store.isEndReached()) {
            if (!loadMore(classGuid, loadIndex)) {
                initialLoadWaiters.add(new int[]{classGuid, loadIndex});
            }
            return false;
        }
        int session = sessionGeneration;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(feedConfig, session, true);
            AndroidUtilities.runOnUIThread(() -> {
                if (session != sessionGeneration) {
                    return;
                }
                if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch)) {
                    postFeedResults(classGuid, loadIndex, new ArrayList<>(), LOAD_TYPE_INITIAL, false, true);
                } else {
                    applyEnumeration(enumeration);
                    postFeedResults(classGuid, loadIndex, visibleMessages, LOAD_TYPE_INITIAL, false, enumeration.failed);
                }
                postFeedCount(classGuid);
            });
        });
        return true;
    }

    private void ensureCurrentConfig() {
        if (configGeneration != FeedConfig.getInstance(currentAccount).getGeneration()) {
            applyConfigChange(truncated -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, truncated));
        }
    }

    public void markConfigApplied() {
        configGeneration = FeedConfig.getInstance(currentAccount).getGeneration();
    }

    public void applyConfigChange(Utilities.Callback<Boolean> callback) {
        reconcileChannelSet(callback);
    }

    private void reconcileChannelSet(Utilities.Callback<Boolean> callback) {
        int session = sessionGeneration;
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        int generation = feedConfig.getGeneration();
        int cacheEpoch = loader.getChannelCacheEpoch();
        if (store.isEmpty()) {
            loadChannels((channels, includedCount, failed, configGen) -> {
                if (!failed) {
                    configGeneration = configGen;
                }
                if (callback != null) {
                    callback.run(false);
                }
            });
            return;
        }
        HashSet<Long> loadedDialogIds = store.getLoadedDialogIds();
        HashSet<Long> hiddenDialogIds = store.getHiddenSnapshot();
        FeedTimelineLoader.Cursor newest = new FeedTimelineLoader.Cursor();
        FeedTimelineLoader.Cursor oldest = new FeedTimelineLoader.Cursor();
        newest.set(store.getNewestCursor().date, store.getNewestCursor().uid, store.getNewestCursor().mid);
        oldest.set(store.getOldestCursor().date, store.getOldestCursor().uid, store.getOldestCursor().mid);
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(feedConfig, session, true);
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> onReconcileFailed(session, enumeration, feedConfig, generation, cacheEpoch, callback));
                return;
            }
            // channels whose rows are not in the store yet (new or previously hidden)
            ArrayList<Long> windowDialogIds = new ArrayList<>();
            for (FeedTimelineLoader.ChannelSnapshot channel : enumeration.included) {
                if (!loadedDialogIds.contains(channel.dialogId) || hiddenDialogIds.contains(channel.dialogId)) {
                    windowDialogIds.add(channel.dialogId);
                }
            }
            FeedTimelineLoader.WindowPage window = windowDialogIds.isEmpty() ? null : loader.loadChannelWindow(windowDialogIds, newest, oldest);
            if (window != null && window.failed) {
                AndroidUtilities.runOnUIThread(() -> onReconcileFailed(session, enumeration, feedConfig, generation, cacheEpoch, callback));
                return;
            }
            ArrayList<MessageObject> windowMessages = window != null ? createMessageObjects(window.messages, window.users, window.chats) : null;
            boolean channelsAdded = !windowDialogIds.isEmpty();
            AndroidUtilities.runOnUIThread(() -> {
                if (session != sessionGeneration) {
                    return;
                }
                if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch) && canRetryStaleEnumeration()) {
                    reconcileChannelSet(callback);
                    return;
                }
                applyEnumeration(enumeration);
                configGeneration = enumeration.configGeneration;
                HashSet<Long> included = new HashSet<>();
                for (FeedTimelineLoader.ChannelSnapshot channel : enumeration.included) {
                    included.add(channel.dialogId);
                }
                store.applyIncludedDialogs(included);
                boolean truncated = window != null && window.truncated;
                if (window != null && !truncated && !windowMessages.isEmpty()) {
                    MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    messagesController.putUsers(window.users, true);
                    messagesController.putChats(window.chats, true);
                    store.mergeRows(windowMessages);
                }
                if (channelsAdded) {
                    store.setEndReached(false);
                    if (loading) {
                        olderPagingBoundsDirty = true;
                    }
                    if (loadingNewer) {
                        newerPagingBoundsDirty = true;
                    }
                }
                if (callback != null) {
                    callback.run(truncated);
                }
            });
        });
    }

    private void onReconcileFailed(int session, FeedTimelineLoader.ChannelEnumeration enumeration, FeedConfig feedConfig, int generation, int cacheEpoch, Utilities.Callback<Boolean> callback) {
        if (session != sessionGeneration) {
            return;
        }
        if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch) && canRetryStaleEnumeration()) {
            reconcileChannelSet(callback);
        } else if (callback != null) {
            callback.run(false);
        }
    }

    public void refreshReadState(Runnable onDone) {
        int session = sessionGeneration;
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        int generation = feedConfig.getGeneration();
        int cacheEpoch = loader.getChannelCacheEpoch();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(feedConfig, session, true);
            AndroidUtilities.runOnUIThread(() -> {
                if (session != sessionGeneration) {
                    return;
                }
                if (isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch)) {
                    applyEnumeration(enumeration);
                }
                if (onDone != null) {
                    onDone.run();
                }
            });
        });
    }

    public boolean loadMore(int classGuid, int loadIndex) {
        ensureCurrentConfig();
        if (loading || store.isEndReached() && !store.getOldestCursor().isEmpty()) {
            return false;
        }
        loading = true;
        heldGuid = classGuid;
        heldLoadIndex = loadIndex;
        attemptRounds = 0;
        runAttempt();
        return true;
    }

    private void runAttempt() {
        int classGuid = heldGuid;
        int loadIndex = heldLoadIndex;
        int session = sessionGeneration;
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        int generation = feedConfig.getGeneration();
        int cacheEpoch = loader.getChannelCacheEpoch();
        boolean firstPage = store.getOldestCursor().isEmpty();
        FeedTimelineLoader.Cursor cursor = new FeedTimelineLoader.Cursor();
        cursor.set(store.getOldestCursor().date, store.getOldestCursor().uid, store.getOldestCursor().mid);
        HashSet<Long> exhausted = backfill.getExhaustedSnapshot();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(feedConfig, session, false);
            ArrayList<FeedTimelineLoader.ChannelSnapshot> included = enumeration.included;
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch) && canRetryStaleEnumeration()) {
                        attemptRounds = 0;
                        runAttempt();
                    } else {
                        failOlderLoad(classGuid, loadIndex);
                    }
                });
                return;
            }
            if (included.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch)) {
                        if (canRetryStaleEnumeration()) {
                            olderPagingBoundsDirty = false;
                            attemptRounds = 0;
                            runAttempt();
                        } else {
                            failOlderLoad(classGuid, loadIndex);
                        }
                        return;
                    }
                    applyEnumeration(enumeration);
                    olderPagingBoundsDirty = false;
                    unreadTracker.clear();
                    loading = false;
                    store.setEndReached(true);
                    postFeedResults(classGuid, loadIndex, new ArrayList<>(), LOAD_TYPE_OLDER);
                    postFeedCount(classGuid);
                    flushInitialLoadWaiters();
                });
                return;
            }
            FeedTimelineLoader.OlderPage page = loader.loadOlderPage(included, cursor, exhausted);
            ArrayList<MessageObject> messageObjects = createMessageObjects(page.messages, page.users, page.chats);
            AndroidUtilities.runOnUIThread(() -> onOlderPageLoaded(session, enumeration, feedConfig, generation, cacheEpoch, page, classGuid, loadIndex, firstPage, messageObjects));
        });
    }

    private void onOlderPageLoaded(int session, FeedTimelineLoader.ChannelEnumeration enumeration, FeedConfig feedConfig, int generation, int cacheEpoch, FeedTimelineLoader.OlderPage page, int classGuid, int loadIndex, boolean firstPage, ArrayList<MessageObject> messageObjects) {
        if (session != sessionGeneration) {
            return;
        }
        if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch) && canRetryStaleEnumeration()) {
            olderPagingBoundsDirty = false;
            attemptRounds = 0;
            runAttempt();
            return;
        }
        if (olderPagingBoundsDirty) {
            olderPagingBoundsDirty = false;
            attemptRounds = 0;
            runAttempt();
            return;
        }
        applyEnumeration(enumeration);
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        pruneStaleExclusions(FeedConfig.getInstance(currentAccount), messagesController);
        if (page.failed) {
            failOlderLoad(classGuid, loadIndex);
            return;
        }
        store.getOldestCursor().set(page.last.date, page.last.uid, page.last.mid);
        if (firstPage && !page.first.isEmpty()) {
            store.getNewestCursor().set(page.first.date, page.first.uid, page.first.mid);
        }
        messagesController.putUsers(page.users, true);
        messagesController.putChats(page.chats, true);
        ArrayList<MessageObject> added = store.appendMessages(messageObjects, false);
        if (added.isEmpty() && page.lastChunkRowCount == CHUNK_SIZE) {
            runAttempt();
            return;
        }
        boolean endReached = !page.hasIncomplete && page.lastChunkRowCount < CHUNK_SIZE;
        if (!added.isEmpty() || endReached || page.backfillCandidates.isEmpty() || attemptRounds >= MAX_BACKFILL_ROUNDS) {
            loading = false;
            store.setEndReached(endReached);
            postFeedResults(classGuid, loadIndex, added, LOAD_TYPE_OLDER);
            postFeedCount(classGuid);
            flushInitialLoadWaiters();
            return;
        }
        // nothing cached locally, fetch older history of incomplete channels from the server
        attemptRounds++;
        backfill.startRound(page.backfillCandidates);
    }

    private void failOlderLoad(int classGuid, int loadIndex) {
        loading = false;
        postFeedResults(classGuid, loadIndex, new ArrayList<>(), LOAD_TYPE_OLDER, false, true);
        postFeedCount(classGuid);
        flushInitialLoadWaiters(true);
    }

    private void postFeedResults(int classGuid, int loadIndex, ArrayList<MessageObject> messages, int loadType) {
        postFeedResults(classGuid, loadIndex, messages, loadType, false, false);
    }

    private void postFeedResults(int classGuid, int loadIndex, ArrayList<MessageObject> messages, int loadType, boolean hasMore, boolean failed) {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDidLoad, 0L, messages.size(), messages, false, 0, 0, 0, 0, loadType, true, classGuid, loadIndex, 0, 0, ChatActivity.MODE_SEARCH, hasMore, failed);
    }

    private void postFeedCount(int classGuid) {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.hashtagSearchUpdated, classGuid, store.getCount(), store.isEndReached(), 0, 0, 0);
    }

    private boolean isEnumerationCurrent(FeedTimelineLoader.ChannelEnumeration enumeration, FeedConfig feedConfig, int generation, int cacheEpoch) {
        boolean current = loader.isEnumerationCurrent(enumeration)
            && enumeration.configGeneration == feedConfig.getGeneration()
            && enumeration.configGeneration == generation
            && enumeration.cacheEpoch == cacheEpoch;
        if (current) {
            staleEnumerationRetries = 0;
        }
        return current;
    }

    private boolean canRetryStaleEnumeration() {
        if (staleEnumerationRetries >= MAX_STALE_RETRIES) {
            staleEnumerationRetries = 0;
            return false;
        }
        staleEnumerationRetries++;
        return true;
    }

    private void applyEnumeration(FeedTimelineLoader.ChannelEnumeration enumeration) {
        if (enumeration.failed) {
            return;
        }
        hasChannels = enumeration.hasChannels;
        hasIncludedChannels = !enumeration.included.isEmpty();
        cachedIncludedChannelCount = enumeration.included.size();
        for (FeedTimelineLoader.ChannelSnapshot channel : enumeration.included) {
            int readMax = channel.readInboxMax;
            if (readMax <= 0 && channel.unreadCount <= 0) {
                readMax = channel.topMessage;
            }
            unreadTracker.applyReadInboxMax(channel.dialogId, readMax);
        }
    }

    private void flushInitialLoadWaiters() {
        flushInitialLoadWaiters(false);
    }

    private void flushInitialLoadWaiters(boolean failed) {
        if (initialLoadWaiters.isEmpty()) {
            return;
        }
        ArrayList<int[]> waiters = new ArrayList<>(initialLoadWaiters);
        initialLoadWaiters.clear();
        ArrayList<MessageObject> visibleMessages = store.getVisibleMessages();
        for (int[] waiter : waiters) {
            postFeedResults(waiter[0], waiter[1], visibleMessages, LOAD_TYPE_INITIAL, false, failed);
            postFeedCount(waiter[0]);
        }
    }

    private void onBackfillRoundFinished() {
        if (loading) {
            runAttempt();
        }
    }

    public boolean loadNewer(int classGuid, int loadIndex) {
        ensureCurrentConfig();
        if (loadingNewer || store.getNewestCursor().isEmpty()) {
            return false;
        }
        loadingNewer = true;
        runLoadNewer(classGuid, loadIndex);
        return true;
    }

    private void runLoadNewer(int classGuid, int loadIndex) {
        int session = sessionGeneration;
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        int generation = feedConfig.getGeneration();
        int cacheEpoch = loader.getChannelCacheEpoch();
        FeedTimelineLoader.Cursor cursor = new FeedTimelineLoader.Cursor();
        cursor.set(store.getNewestCursor().date, store.getNewestCursor().uid, store.getNewestCursor().mid);
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(feedConfig, session, false);
            ArrayList<FeedTimelineLoader.ChannelSnapshot> included = enumeration.included;
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch) && canRetryStaleEnumeration()) {
                        runLoadNewer(classGuid, loadIndex);
                    } else {
                        loadingNewer = false;
                        postNewerMessagesLoaded(classGuid, loadIndex, null, false, true);
                    }
                });
                return;
            }
            if (included.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (session != sessionGeneration) {
                        return;
                    }
                    newerPagingBoundsDirty = false;
                    if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch) && canRetryStaleEnumeration()) {
                        runLoadNewer(classGuid, loadIndex);
                    } else {
                        loadingNewer = false;
                        postNewerMessagesLoaded(classGuid, loadIndex, null, false);
                        postFeedCount(classGuid);
                    }
                });
                return;
            }
            FeedTimelineLoader.NewerPage page = loader.loadNewerPage(included, cursor);
            ArrayList<MessageObject> messageObjects = createMessageObjects(page.messages, page.users, page.chats);
            AndroidUtilities.runOnUIThread(() -> onNewerPageLoaded(session, enumeration, feedConfig, generation, cacheEpoch, classGuid, loadIndex, page, messageObjects));
        });
    }

    private void onNewerPageLoaded(int session, FeedTimelineLoader.ChannelEnumeration enumeration, FeedConfig feedConfig, int generation, int cacheEpoch, int classGuid, int loadIndex, FeedTimelineLoader.NewerPage page, ArrayList<MessageObject> messageObjects) {
        if (session != sessionGeneration) {
            return;
        }
        if (!isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch) && canRetryStaleEnumeration()) {
            newerPagingBoundsDirty = false;
            runLoadNewer(classGuid, loadIndex);
            return;
        }
        if (newerPagingBoundsDirty) {
            newerPagingBoundsDirty = false;
            if (store.getNewestCursor().isEmpty()) {
                loadingNewer = false;
                postNewerMessagesLoaded(classGuid, loadIndex, null, false);
                postFeedCount(classGuid);
                return;
            }
            runLoadNewer(classGuid, loadIndex);
            return;
        }
        loadingNewer = false;
        applyEnumeration(enumeration);
        if (page.failed) {
            postNewerMessagesLoaded(classGuid, loadIndex, null, false, true);
            return;
        }
        store.getNewestCursor().set(page.first.date, page.first.uid, page.first.mid);
        if (page.messages.isEmpty()) {
            postNewerMessagesLoaded(classGuid, loadIndex, null, page.hasMore);
            if (!page.hasMore) {
                postFeedCount(classGuid);
            }
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        messagesController.putUsers(page.users, true);
        messagesController.putChats(page.chats, true);
        postNewerMessagesLoaded(classGuid, loadIndex, store.appendMessages(messageObjects, true), page.hasMore);
        if (!page.hasMore) {
            postFeedCount(classGuid);
        }
        trimForInactiveCache();
    }

    private void postNewerMessagesLoaded(int classGuid, int loadIndex, ArrayList<MessageObject> messages, boolean hasMore) {
        postNewerMessagesLoaded(classGuid, loadIndex, messages, hasMore, false);
    }

    private void postNewerMessagesLoaded(int classGuid, int loadIndex, ArrayList<MessageObject> messages, boolean hasMore, boolean failed) {
        ArrayList<MessageObject> result = new ArrayList<>();
        int loadType = LOAD_TYPE_INITIAL;
        if (messages != null && !messages.isEmpty()) {
            result.addAll(messages);
            Collections.reverse(result);
            loadType = LOAD_TYPE_NEWER;
        }
        postFeedResults(classGuid, loadIndex, result, loadType, hasMore, failed);
    }

    private ArrayList<MessageObject> createMessageObjects(ArrayList<TLRPC.Message> messages, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats) {
        HashMap<Long, TLRPC.User> usersDict = new HashMap<>();
        HashMap<Long, TLRPC.Chat> chatsDict = new HashMap<>();
        for (TLRPC.User user : users) {
            usersDict.put(user.id, user);
        }
        for (TLRPC.Chat chat : chats) {
            chatsDict.put(chat.id, chat);
        }
        ArrayList<MessageObject> result = new ArrayList<>(messages.size());
        for (TLRPC.Message message : messages) {
            result.add(new MessageObject(currentAccount, message, null, usersDict, chatsDict, null, null, true, true, 0, false, false, false, 4));
        }
        return result;
    }

    public void loadChannels(ChannelsCallback callback) {
        loadChannels(false, callback);
    }

    public void loadChannels(boolean force, ChannelsCallback callback) {
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        int session = sessionGeneration;
        int generation = feedConfig.getGeneration();
        int cacheEpoch = loader.getChannelCacheEpoch();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(feedConfig, session, force);
            AndroidUtilities.runOnUIThread(() -> {
                if (session != sessionGeneration || !isEnumerationCurrent(enumeration, feedConfig, generation, cacheEpoch)) {
                    if (callback != null) {
                        callback.onChannels(new ArrayList<>(), 0, true, enumeration.configGeneration);
                    }
                    return;
                }
                applyEnumeration(enumeration);
                if (!enumeration.failed) {
                    MessagesController.getInstance(currentAccount).putChats(enumeration.channels, true);
                }
                if (callback != null) {
                    callback.onChannels(enumeration.channels, enumeration.included.size(), enumeration.failed, enumeration.configGeneration);
                }
            });
        });
    }

    private void pruneStaleExclusions(FeedConfig feedConfig, MessagesController messagesController) {
        HashSet<Long> stale = null;
        for (Long dialogId : feedConfig.getExcludedSnapshot()) {
            TLRPC.Chat chat = messagesController.getChat(-dialogId);
            if (chat != null && !isEligibleChannel(chat)) {
                if (stale == null) {
                    stale = new HashSet<>();
                }
                stale.add(dialogId);
            }
        }
        if (stale != null) {
            feedConfig.removeExcluded(stale);
            markConfigApplied();
        }
    }

    public void replaceMessage(MessageObject oldMessage, MessageObject newMessage) {
        store.replaceMessage(oldMessage, newMessage);
    }

    public ArrayList<Integer> deleteMessages(long dialogId, ArrayList<Integer> realIds) {
        boolean[] changed = new boolean[1];
        ArrayList<Integer> removed = store.deleteMessages(dialogId, realIds, changed);
        if (changed[0]) {
            onFeedRowsRemoved();
        }
        return removed;
    }

    public ArrayList<Integer> deleteHistory(long dialogId, int maxId) {
        boolean[] changed = new boolean[1];
        ArrayList<Integer> removed = store.deleteHistory(dialogId, maxId, changed);
        if (changed[0]) {
            onFeedRowsRemoved();
        }
        return removed;
    }

    private void onFeedRowsRemoved() {
        if (loading) {
            olderPagingBoundsDirty = true;
        }
        if (loadingNewer) {
            newerPagingBoundsDirty = true;
        }
    }

    public ArrayList<MessageObject> updateViews(LongSparseArray<SparseIntArray> views, LongSparseArray<SparseIntArray> forwards, LongSparseArray<SparseArray<TLRPC.MessageReplies>> replies, boolean updateReplies) {
        ArrayList<MessageObject> updated = new ArrayList<>();
        updateCounters(views, true, updated);
        updateCounters(forwards, false, updated);
        updateReplies(replies, updateReplies, updated);
        return updated;
    }

    private void updateCounters(LongSparseArray<SparseIntArray> counters, boolean views, ArrayList<MessageObject> updated) {
        if (counters == null) {
            return;
        }
        for (int i = 0; i < counters.size(); i++) {
            long dialogId = counters.keyAt(i);
            SparseIntArray array = counters.valueAt(i);
            for (int j = 0; j < array.size(); j++) {
                MessageObject messageObject = getMessage(dialogId, array.keyAt(j));
                if (messageObject == null) {
                    continue;
                }
                int value = array.valueAt(j);
                TLRPC.Message message = messageObject.messageOwner;
                if (views) {
                    if (value > message.views) {
                        message.views = value;
                        addUpdated(updated, messageObject);
                    }
                } else if (value > message.forwards) {
                    message.forwards = value;
                    addUpdated(updated, messageObject);
                }
            }
        }
    }

    private void updateReplies(LongSparseArray<SparseArray<TLRPC.MessageReplies>> repliesArray, boolean increment, ArrayList<MessageObject> updated) {
        if (repliesArray == null) {
            return;
        }
        for (int i = 0; i < repliesArray.size(); i++) {
            long dialogId = repliesArray.keyAt(i);
            SparseArray<TLRPC.MessageReplies> array = repliesArray.valueAt(i);
            for (int j = 0; j < array.size(); j++) {
                MessageObject messageObject = getMessage(dialogId, array.keyAt(j));
                TLRPC.MessageReplies replies = array.valueAt(j);
                if (messageObject == null || replies == null) {
                    continue;
                }
                TLRPC.Message message = messageObject.messageOwner;
                if (increment) {
                    if (message.replies == null) {
                        message.replies = new TLRPC.TL_messageReplies();
                    }
                    message.replies.replies += replies.replies;
                    for (int k = 0; k < replies.recent_repliers.size(); k++) {
                        message.replies.recent_repliers.remove(replies.recent_repliers.get(k));
                    }
                    message.replies.recent_repliers.addAll(0, replies.recent_repliers);
                    while (message.replies.recent_repliers.size() > 3) {
                        message.replies.recent_repliers.remove(0);
                    }
                } else if (message.replies == null || replies.replies_pts > message.replies.replies_pts || replies.read_max_id > message.replies.read_max_id || replies.max_id > message.replies.max_id) {
                    message.replies = replies;
                }
                messageObject.animateComments = true;
                addUpdated(updated, messageObject);
            }
        }
    }

    private static void addUpdated(ArrayList<MessageObject> updated, MessageObject messageObject) {
        if (!updated.contains(messageObject)) {
            updated.add(messageObject);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            backfill.onMessagesDidLoad(args);
        } else if (id == NotificationCenter.loadingMessagesFailed) {
            backfill.onLoadingMessagesFailed(args);
        } else if (id == NotificationCenter.messagesDeleted) {
            if (isUiActive() || (Boolean) args[2]) {
                return;
            }
            long channelId = (Long) args[1];
            if (channelId == 0) {
                return;
            }
            long dialogId = channelId > 0 ? -channelId : channelId;
            //noinspection unchecked
            deleteMessages(dialogId, (ArrayList<Integer>) args[0]);
        } else if (id == NotificationCenter.historyCleared) {
            if (isUiActive()) {
                return;
            }
            long dialogId = (Long) args[0];
            if (DialogObject.isChatDialog(dialogId)) {
                deleteHistory(dialogId, (Integer) args[1]);
            }
        } else if (id == NotificationCenter.didReceiveNewMessages) {
            if (isUiActive() || (Boolean) args[2] || store.isEmpty() || store.getNewestCursor().isEmpty() || !isIncludedChannelPost((Long) args[0])) {
                return;
            }
            scheduleClosedRefresh();
        }
    }

    private void scheduleClosedRefresh() {
        if (closedRefreshScheduled) {
            return;
        }
        closedRefreshScheduled = true;
        AndroidUtilities.runOnUIThread(closedRefreshRunnable, 1000);
    }

    private void runClosedRefresh() {
        closedRefreshScheduled = false;
        if (isUiActive() || loadingNewer || store.isEmpty() || store.getNewestCursor().isEmpty()) {
            return;
        }
        loadNewer(closedRefreshGuid, 0);
    }
}
