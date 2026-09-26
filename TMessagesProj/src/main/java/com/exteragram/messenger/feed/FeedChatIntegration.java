package com.exteragram.messenger.feed;

import androidx.collection.LongSparseArray;

import com.exteragram.messenger.feed.ads.FeedAdController;
import com.exteragram.messenger.feed.ads.FeedAdInjector;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Glue between {@link FeedController} and the ChatActivity that displays the feed:
 * unread divider, pagedown counter, date headers, ads, reactions refresh, hiding channels.
 */
public class FeedChatIntegration {

    public interface Host {
        BaseFragment getFragment();

        ArrayList<MessageObject> getMessages();

        boolean isListReady();

        boolean isFirstLoadComplete();

        boolean isListScrollIdle();

        boolean isScrollAnimationRunning();

        boolean canScrollToNewer();

        int getDistanceToNewerPx();

        int getLastVisibleMessageIndex();

        int getNewestVisibleMessageIndex();

        ScrollAnchor captureScrollAnchor();

        void restoreScrollAnchor(ScrollAnchor anchor);

        void scrollToMessage(int index, int offset);

        void scrollToMessageAnimated(int index, int offset);

        int nextStableId();

        int stableIdForDateHeader(int dateKey);

        void notifyAllMessagesChanged();

        void notifyMessageInserted(int index);

        void notifyMessageRemoved(int index);

        void invalidateVisiblePart();

        void onFeedListChanged();

        void materializeRow(MessageObject messageObject);

        void dematerializeRow(MessageObject messageObject);

        void deleteRows(ArrayList<Integer> ids);

        void reloadFeed();

        void requestOlderFeedPage();

        void showEmptyFeedState();

        void showEmptyFeedProgress();

        boolean isPagedownButtonVisible();

        void setPagedownButtonVisible(boolean visible);

        void setPagedownCount(int count);
    }

    public static final class ScrollAnchor {
        public final MessageObject row;
        public final int offsetTop;

        public ScrollAnchor(MessageObject row, int offsetTop) {
            this.row = row;
            this.offsetTop = offsetTop;
        }
    }

    private static final int PAGEDOWN_SCROLL_THRESHOLD = AndroidUtilities.dp(100);
    private static final int NEAR_NEWEST_THRESHOLD = AndroidUtilities.dp(160);
    private static final long REACTIONS_REFRESH_INTERVAL = 15000;

    private final int currentAccount;
    private final Host host;
    private final boolean restoreDrawerScrollPosition;
    private final FeedAdInjector adInjector;
    private Runnable channelsChangedCallback;
    private boolean destroyed;
    private boolean viewportActive;
    private boolean initialScrollApplied;
    private boolean readyToMarkAsRead;
    private boolean pendingDividerScroll;
    private ScrollAnchor pendingInitialScrollRestore;
    private boolean scrollPreservedNewerToUnread;
    private int preserveScrollLoadIndex = -1;
    private MessageObject unreadDivider;
    private int lastPagedownCount = -1;
    private boolean pagedownShownByScroll;
    private int totalScrollDy;
    private long pendingHideDialogId;

    private boolean settleAtNewestScheduled;
    private final Runnable settleAtNewestRunnable = this::settleAtNewestNow;

    private final int reactionsRequestGuid = ConnectionsManager.generateClassGuid();
    private final LongSparseArray<ArrayList<Integer>> pendingReactionIds = new LongSparseArray<>();
    private boolean reactionsRefreshScheduled;
    private final Runnable reactionsRefreshRunnable = this::flushReactionsRefresh;

    public FeedChatIntegration(int currentAccount, Host host, boolean restoreDrawerScrollPosition) {
        this.currentAccount = currentAccount;
        this.host = host;
        this.restoreDrawerScrollPosition = restoreDrawerScrollPosition;
        this.adInjector = new FeedAdInjector(currentAccount, host);
    }

    private void settleAtNewestNow() {
        settleAtNewestScheduled = false;
        if (destroyed || !viewportActive || !host.isListReady() || host.isScrollAnimationRunning() || host.canScrollToNewer()) {
            return;
        }
        settleUnreadDivider();
    }

    public void refreshAds() {
        adInjector.refresh(unreadDivider);
        requestPendingInitialPosition();
    }

    public void setChannelsChangedCallback(Runnable callback) {
        channelsChangedCallback = callback;
    }

    public void notifyChannelsChanged() {
        if (channelsChangedCallback != null) {
            channelsChangedCallback.run();
        }
    }

    public void resetUiState() {
        resetMetadataRefresh();
        initialScrollApplied = false;
        readyToMarkAsRead = false;
        pendingDividerScroll = false;
        pendingInitialScrollRestore = null;
        scrollPreservedNewerToUnread = false;
        preserveScrollLoadIndex = -1;
        unreadDivider = null;
        lastPagedownCount = -1;
        pagedownShownByScroll = false;
        totalScrollDy = 0;
        pendingHideDialogId = 0;
        if (settleAtNewestScheduled) {
            AndroidUtilities.cancelRunOnUIThread(settleAtNewestRunnable);
            settleAtNewestScheduled = false;
        }
        adInjector.clear();
    }

    private boolean hasMaterializedPostRows() {
        ArrayList<MessageObject> messages = host.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                return true;
            }
        }
        return false;
    }

    public void onMessagesLoaded() {
        if (host.getFragment().isPaused() || !host.isListReady() || !hasMaterializedPostRows()) {
            return;
        }
        if (!initialScrollApplied) {
            initialScrollApplied = true;
            FeedController feedController = FeedController.getInstance(currentAccount);
            boolean scrollToUnread = feedController.consumeInitialUnreadScroll();
            FeedController.SavedScrollPosition savedPosition = restoreDrawerScrollPosition ? feedController.getDrawerScrollPosition() : null;
            MessageObject savedRow = savedPosition != null ? feedController.getMessage(savedPosition.dialogId, savedPosition.messageId) : null;
            if (savedRow != null && host.getMessages().contains(savedRow)) {
                applyUnreadDivider(false);
                refreshAds();
                pendingInitialScrollRestore = new ScrollAnchor(savedRow, savedPosition.offsetTop);
                requestPendingInitialPosition();
            } else {
                applyUnreadDivider(scrollToUnread || host.getDistanceToNewerPx() <= NEAR_NEWEST_THRESHOLD);
            }
        }
        FeedAdController.getInstance(currentAccount).ensureLoaded(this::refreshAds);
    }

    public void onHostResumed() {
        if (!host.getMessages().isEmpty()) {
            onMessagesLoaded();
        }
        requestPendingInitialPosition();
    }

    private boolean hasPendingInitialPosition() {
        return pendingInitialScrollRestore != null || pendingDividerScroll;
    }

    private void requestPendingInitialPosition() {
        if (host.getFragment().isPaused() || !host.isListReady()) {
            return;
        }
        if (pendingInitialScrollRestore != null) {
            host.restoreScrollAnchor(pendingInitialScrollRestore);
            return;
        }
        if (pendingDividerScroll) {
            int index = unreadDivider == null ? -1 : host.getMessages().indexOf(unreadDivider);
            if (index < 0) {
                pendingDividerScroll = false;
                readyToMarkAsRead = true;
            } else {
                host.scrollToMessage(index, AndroidUtilities.dp(48));
            }
        }
    }

    public void setViewportActive(boolean active) {
        if (viewportActive == active) {
            return;
        }
        viewportActive = active;
        if (!active) {
            if (settleAtNewestScheduled) {
                AndroidUtilities.cancelRunOnUIThread(settleAtNewestRunnable);
                settleAtNewestScheduled = false;
            }
            cancelPendingReactionsRefresh();
            return;
        }
        onHostResumed();
        if (!host.getMessages().isEmpty()) {
            onVisiblePartInvalidated();
        }
    }

    public boolean canMarkVisibleAsRead() {
        return viewportActive && !host.getFragment().isPaused() && initialScrollApplied && readyToMarkAsRead && !pendingDividerScroll && pendingInitialScrollRestore == null && !BaseFragment.hasSheets(host.getFragment());
    }

    public void onPostCellVisible(MessageObject messageObject, boolean fullyVisible, boolean bottomVisible) {
        if (messageObject == null || messageObject.isSponsored()) {
            return;
        }
        requestReactionsRefresh(messageObject);
        if (canMarkVisibleAsRead() && (fullyVisible || bottomVisible)) {
            FeedController.getInstance(currentAccount).onPostSeen(messageObject.getDialogId(), messageObject.getRealId());
        }
    }

    private void requestReactionsRefresh(MessageObject messageObject) {
        if (destroyed || !viewportActive || messageObject.messageOwner == null) {
            return;
        }
        int realId = messageObject.getRealId();
        long dialogId = messageObject.getDialogId();
        if (realId <= 0 || dialogId == 0) {
            return;
        }
        if (messageObject.messageOwner.action != null && !messageObject.canSetReaction()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - messageObject.reactionsLastCheckTime <= REACTIONS_REFRESH_INTERVAL) {
            return;
        }
        messageObject.reactionsLastCheckTime = now;
        ArrayList<Integer> ids = pendingReactionIds.get(dialogId);
        if (ids == null) {
            ids = new ArrayList<>();
            pendingReactionIds.put(dialogId, ids);
        }
        ids.add(realId);
        if (reactionsRefreshScheduled) {
            return;
        }
        reactionsRefreshScheduled = true;
        AndroidUtilities.runOnUIThread(reactionsRefreshRunnable);
    }

    private void flushReactionsRefresh() {
        reactionsRefreshScheduled = false;
        if (destroyed || !viewportActive) {
            pendingReactionIds.clear();
            return;
        }
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(currentAccount);
        for (int i = 0; i < pendingReactionIds.size(); i++) {
            TLRPC.TL_messages_getMessagesReactions req = new TLRPC.TL_messages_getMessagesReactions();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(pendingReactionIds.keyAt(i));
            req.id.addAll(pendingReactionIds.valueAt(i));
            int reqId = connectionsManager.sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.Updates) {
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    for (int a = 0; a < updates.updates.size(); a++) {
                        TLRPC.Update update = updates.updates.get(a);
                        if (update instanceof TL_update.TL_updateMessageReactions) {
                            ((TL_update.TL_updateMessageReactions) update).updateUnreadState = false;
                        }
                    }
                    MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                }
            });
            connectionsManager.bindRequestToGuid(reqId, reactionsRequestGuid);
        }
        pendingReactionIds.clear();
    }

    private void cancelPendingReactionsRefresh() {
        AndroidUtilities.cancelRunOnUIThread(reactionsRefreshRunnable);
        reactionsRefreshScheduled = false;
        pendingReactionIds.clear();
    }

    private void resetMetadataRefresh() {
        cancelPendingReactionsRefresh();
        ConnectionsManager.getInstance(currentAccount).cancelRequestsForGuid(reactionsRequestGuid);
    }

    public void markAllRead() {
        FeedController.getInstance(currentAccount).markAllRead();
        applyUnreadDivider(false);
        refreshAds();
        host.invalidateVisiblePart();
    }

    public void settleUnreadDivider() {
        if (!canMarkVisibleAsRead() || !host.isListReady()) {
            return;
        }
        int lastVisibleIndex = host.getLastVisibleMessageIndex();
        if (lastVisibleIndex == Integer.MIN_VALUE) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        int end = host.canScrollToNewer() ? Math.min(lastVisibleIndex, messages.size() - 1) : messages.size() - 1;
        if (end >= 0) {
            FeedController feedController = FeedController.getInstance(currentAccount);
            for (int i = 0; i <= end; i++) {
                MessageObject messageObject = messages.get(i);
                if (FeedMessageUtils.isPostRow(messageObject)) {
                    feedController.onPostSeen(messageObject.getDialogId(), messageObject.getRealId());
                }
            }
        }
        applyUnreadDivider(false);
        refreshAds();
        updatePagedownCounter();
    }

    public void onScrollAnimationFinished() {
        if (destroyed || !viewportActive || settleAtNewestScheduled) {
            return;
        }
        settleAtNewestScheduled = true;
        AndroidUtilities.runOnUIThread(settleAtNewestRunnable);
    }

    public void destroy() {
        destroyed = true;
        resetMetadataRefresh();
        if (settleAtNewestScheduled) {
            AndroidUtilities.cancelRunOnUIThread(settleAtNewestRunnable);
            settleAtNewestScheduled = false;
        }
        pendingInitialScrollRestore = null;
    }

    public void saveDrawerScrollPosition() {
        ScrollAnchor anchor = host.captureScrollAnchor();
        if (anchor == null || anchor.row == null) {
            return;
        }
        FeedController.getInstance(currentAccount).saveDrawerScrollPosition(anchor.row.getDialogId(), anchor.row.getRealId(), anchor.offsetTop);
    }

    public void onReadStateRefreshed() {
        ScrollAnchor anchor = host.captureScrollAnchor();
        boolean hadPendingPosition = hasPendingInitialPosition();
        applyUnreadDivider(false);
        refreshAds();
        if (!hadPendingPosition || !hasPendingInitialPosition()) {
            host.restoreScrollAnchor(anchor);
        }
        lastPagedownCount = -1;
        updatePagedownCounter();
        host.invalidateVisiblePart();
    }

    public void onPreserveScrollLoadStarted(int loadIndex) {
        preserveScrollLoadIndex = loadIndex;
    }

    public boolean consumePreserveScrollLoad(int loadIndex) {
        if (preserveScrollLoadIndex != loadIndex) {
            return false;
        }
        preserveScrollLoadIndex = -1;
        return true;
    }

    public void beforePreservedNewerMessagesInserted() {
        scrollPreservedNewerToUnread = host.isListScrollIdle() && !host.isScrollAnimationRunning() && host.getDistanceToNewerPx() <= NEAR_NEWEST_THRESHOLD;
    }

    public boolean afterPreservedNewerMessagesInserted() {
        boolean scroll = scrollPreservedNewerToUnread;
        applyUnreadDivider(scroll, true);
        scrollPreservedNewerToUnread = false;
        return scroll;
    }

    public void applyUnreadDivider(boolean scrollToDivider) {
        applyUnreadDivider(scrollToDivider, false);
    }

    private void applyUnreadDivider(boolean scrollToDivider, boolean granularNotify) {
        if (!host.isListReady()) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        if (messages.isEmpty()) {
            unreadDivider = null;
            readyToMarkAsRead = false;
            return;
        }
        int oldIndex = unreadDivider == null ? -1 : messages.indexOf(unreadDivider);
        MessageObject divider = oldIndex >= 0 ? unreadDivider : null;
        if (oldIndex >= 0) {
            messages.remove(oldIndex);
        }
        unreadDivider = null;
        int firstUnread = FeedController.getInstance(currentAccount).findFirstUnreadIndex(messages);
        if (firstUnread < 0) {
            pendingDividerScroll = false;
            if (oldIndex >= 0) {
                if (scrollToDivider && !granularNotify) {
                    host.notifyAllMessagesChanged();
                } else {
                    host.notifyMessageRemoved(oldIndex);
                    host.invalidateVisiblePart();
                }
            }
            readyToMarkAsRead = true;
            return;
        }
        int newIndex = findDividerInsertIndex(messages, firstUnread);
        if (divider == null) {
            divider = FeedMessageUtils.createUnreadDivider(currentAccount, host.nextStableId());
        }
        messages.add(newIndex, divider);
        unreadDivider = divider;
        if (scrollToDivider) {
            readyToMarkAsRead = false;
            if (!granularNotify) {
                host.notifyAllMessagesChanged();
            } else if (oldIndex < 0) {
                host.notifyMessageInserted(newIndex);
            } else if (oldIndex != newIndex) {
                host.notifyMessageRemoved(oldIndex);
                host.notifyMessageInserted(newIndex);
            }
            pendingDividerScroll = true;
            requestPendingInitialPosition();
            host.invalidateVisiblePart();
            return;
        }
        readyToMarkAsRead = true;
        if (oldIndex < 0) {
            host.notifyMessageInserted(newIndex);
            host.invalidateVisiblePart();
        } else if (oldIndex != newIndex) {
            host.notifyMessageRemoved(oldIndex);
            host.notifyMessageInserted(newIndex);
            host.invalidateVisiblePart();
        }
    }

    public boolean scrollToUnreadDividerIfAbove() {
        if (!host.isListReady()) {
            return false;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        if (unreadDivider == null || !messages.contains(unreadDivider)) {
            applyUnreadDivider(false);
            refreshAds();
            messages = host.getMessages();
        }
        int index = messages.indexOf(unreadDivider);
        if (index < 0) {
            return false;
        }
        int newestVisibleIndex = host.getNewestVisibleMessageIndex();
        if (newestVisibleIndex == Integer.MIN_VALUE || index >= newestVisibleIndex) {
            return false;
        }
        host.scrollToMessageAnimated(index, AndroidUtilities.dp(48));
        host.invalidateVisiblePart();
        return true;
    }

    private static int findDividerInsertIndex(ArrayList<MessageObject> messages, int firstUnread) {
        MessageObject first = messages.get(firstUnread);
        long groupId = first.getGroupId();
        if (groupId == 0) {
            return firstUnread + 1;
        }
        long dialogId = first.getDialogId();
        int index = firstUnread + 1;
        while (index < messages.size()) {
            MessageObject messageObject = messages.get(index);
            if (messageObject == null || messageObject.getGroupId() != groupId || messageObject.getDialogId() != dialogId) {
                break;
            }
            index++;
        }
        return index;
    }

    public void onVisiblePartInvalidated() {
        if (!viewportActive || host.getFragment().isPaused()) {
            return;
        }
        if (pendingInitialScrollRestore != null) {
            host.restoreScrollAnchor(pendingInitialScrollRestore);
            pendingInitialScrollRestore = null;
        }
        maybeScrollToDivider();
        updatePagedownCounter();
    }

    private void maybeScrollToDivider() {
        if (!pendingDividerScroll) {
            return;
        }
        if (!host.isListReady() || unreadDivider == null) {
            pendingDividerScroll = false;
            readyToMarkAsRead = true;
            return;
        }
        int lastVisibleIndex = host.getLastVisibleMessageIndex();
        if (lastVisibleIndex == Integer.MIN_VALUE) {
            return;
        }
        int index = host.getMessages().indexOf(unreadDivider);
        if (index >= 0 && index > lastVisibleIndex) {
            host.scrollToMessage(index, AndroidUtilities.dp(48));
        }
        pendingDividerScroll = false;
        readyToMarkAsRead = true;
    }

    private void updatePagedownCounter() {
        if (!host.isListReady() || host.isScrollAnimationRunning()) {
            return;
        }
        int newestVisibleIndex = host.getNewestVisibleMessageIndex();
        int count = newestVisibleIndex == Integer.MIN_VALUE ? 0 : FeedController.getInstance(currentAccount).countUnreadBelow(host.getMessages(), newestVisibleIndex);
        if (count != lastPagedownCount) {
            lastPagedownCount = count;
            host.setPagedownCount(count);
        }
        if (count > 0) {
            pagedownShownByScroll = false;
            host.setPagedownButtonVisible(true);
        } else if (!host.canScrollToNewer()) {
            pagedownShownByScroll = false;
            host.setPagedownButtonVisible(false);
        }
    }

    public void onScrolled(int dy) {
        if (!viewportActive || !host.isListReady() || host.isScrollAnimationRunning()) {
            return;
        }
        if (!host.canScrollToNewer()) {
            totalScrollDy = 0;
            pagedownShownByScroll = false;
            host.setPagedownButtonVisible(false);
            if (dy > 0 && !settleAtNewestScheduled) {
                settleAtNewestScheduled = true;
                AndroidUtilities.runOnUIThread(settleAtNewestRunnable);
            }
            return;
        }
        if (lastPagedownCount > 0) {
            return;
        }
        boolean pagedownVisible = host.isPagedownButtonVisible();
        if (dy > 0) {
            if (pagedownVisible) {
                return;
            }
            totalScrollDy += dy;
            if (totalScrollDy > PAGEDOWN_SCROLL_THRESHOLD) {
                totalScrollDy = 0;
                pagedownShownByScroll = true;
                host.setPagedownButtonVisible(true);
            }
        } else if (dy < 0 && pagedownShownByScroll && pagedownVisible) {
            totalScrollDy += dy;
            if (totalScrollDy < -PAGEDOWN_SCROLL_THRESHOLD) {
                totalScrollDy = 0;
                host.setPagedownButtonVisible(false);
            }
        }
    }

    public void onMessagesDeleted() {
        if (unreadDivider == null) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        int index = messages.indexOf(unreadDivider);
        for (int i = 0; i < index; i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                return;
            }
        }
        unreadDivider = null;
        if (index < 0 || !host.isListReady()) {
            return;
        }
        messages.remove(index);
        host.notifyMessageRemoved(index);
    }

    public void loadReplyMessages(ArrayList<MessageObject> messages, int mode, int classGuid) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        LongSparseArray<ArrayList<MessageObject>> byDialog = new LongSparseArray<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject == null || messageObject.isDateObject) {
                continue;
            }
            long dialogId = messageObject.getDialogId();
            if (dialogId == 0) {
                continue;
            }
            ArrayList<MessageObject> list = byDialog.get(dialogId);
            if (list == null) {
                list = new ArrayList<>();
                byDialog.put(dialogId, list);
            }
            list.add(messageObject);
        }
        for (int i = 0; i < byDialog.size(); i++) {
            MediaDataController.getInstance(currentAccount).loadReplyMessagesForMessages(byDialog.valueAt(i), byDialog.keyAt(i), mode, 0, null, classGuid, null);
        }
    }

    public void reconcileWithStore() {
        if (!host.isListReady()) {
            return;
        }
        FeedStore store = FeedController.getInstance(currentAccount).getStore();
        ArrayList<MessageObject> messages = host.getMessages();
        int postRows = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                postRows++;
            }
        }
        ArrayList<MessageObject> visibleMessages = store.getVisibleMessages();
        if (postRows == 0) {
            if (!visibleMessages.isEmpty() && host.isFirstLoadComplete()) {
                host.reloadFeed();
            } else if (!store.isEmpty() && !store.isEndReached()) {
                host.requestOlderFeedPage();
            }
            return;
        }
        if (store.isEmpty()) {
            host.reloadFeed();
            return;
        }

        HashSet<MessageObject> visibleSet = new HashSet<>(visibleMessages);
        ArrayList<Integer> idsToDelete = null;
        ArrayList<MessageObject> rowsToHide = null;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (!FeedMessageUtils.isPostRow(messageObject) || visibleSet.contains(messageObject)) {
                continue;
            }
            if (store.getMessage(messageObject.getDialogId(), messageObject.getId()) == messageObject) {
                // still in store, just hidden
                if (rowsToHide == null) {
                    rowsToHide = new ArrayList<>();
                }
                rowsToHide.add(messageObject);
            } else {
                if (idsToDelete == null) {
                    idsToDelete = new ArrayList<>();
                }
                idsToDelete.add(messageObject.getId());
            }
        }
        int removedCount = (idsToDelete == null ? 0 : idsToDelete.size()) + (rowsToHide == null ? 0 : rowsToHide.size());
        if (removedCount == 0 && postRows == visibleMessages.size()) {
            return;
        }

        ScrollAnchor anchor = host.captureScrollAnchor();
        boolean hadPendingPosition = hasPendingInitialPosition();
        if (idsToDelete != null) {
            host.deleteRows(idsToDelete);
        }
        if (rowsToHide != null) {
            for (int i = 0; i < rowsToHide.size(); i++) {
                int index = messages.indexOf(rowsToHide.get(i));
                if (index >= 0) {
                    messages.remove(index);
                    host.dematerializeRow(rowsToHide.get(i));
                    host.notifyMessageRemoved(index);
                }
            }
        }

        HashSet<MessageObject> presentRows = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                presentRows.add(messages.get(i));
            }
        }
        boolean inserted = false;
        int cursor = 0;
        for (int i = 0; i < visibleMessages.size(); i++) {
            MessageObject messageObject = visibleMessages.get(i);
            if (presentRows.contains(messageObject)) {
                while (cursor < messages.size() && messages.get(cursor) != messageObject) {
                    cursor++;
                }
                if (cursor < messages.size()) {
                    cursor++;
                }
            } else {
                host.materializeRow(messageObject);
                int insertIndex = getInsertIndex(messages, messageObject, visibleMessages, i, cursor);
                messages.add(insertIndex, messageObject);
                host.notifyMessageInserted(insertIndex);
                cursor = insertIndex + 1;
                inserted = true;
            }
        }
        boolean changed = removedCount > 0 || inserted;
        if (!normalizeDateHeaders(messages) && !changed) {
            return;
        }
        host.onFeedListChanged();
        applyUnreadDivider(false);
        refreshAds();
        onFeedExclusionsChanged();
        if (!hadPendingPosition || !hasPendingInitialPosition()) {
            host.restoreScrollAnchor(anchor);
        }
        host.invalidateVisiblePart();
        if (store.getVisibleCount() == 0) {
            if (store.isEndReached()) {
                host.showEmptyFeedState();
            } else {
                host.showEmptyFeedProgress();
                host.requestOlderFeedPage();
            }
        }
    }

    private static int getInsertIndex(ArrayList<MessageObject> messages, MessageObject messageObject, ArrayList<MessageObject> visibleMessages, int visibleIndex, int cursor) {
        int index = Math.min(cursor, messages.size());
        if (visibleIndex <= 0 || index >= messages.size()) {
            return index;
        }
        MessageObject prev = visibleMessages.get(visibleIndex - 1);
        MessageObject atIndex = messages.get(index);
        // skip the date header that belongs to the previous row's day
        if (prev != null && atIndex != null && atIndex.isDateObject && prev.dateKeyInt != messageObject.dateKeyInt && atIndex.dateKeyInt == prev.dateKeyInt) {
            return index + 1;
        }
        return index;
    }

    /**
     * Messages are ordered newest first, a date header follows the posts of its day.
     * Removes stale headers and inserts missing ones.
     */
    // TODO(openextera): decompile failed, verify
    private boolean normalizeDateHeaders(ArrayList<MessageObject> messages) {
        boolean changed = false;
        MessageObject dayRow = null;
        int i = 0;
        while (i < messages.size()) {
            MessageObject messageObject = messages.get(i);
            if (messageObject == null || messageObject.type == 6 || messageObject.isSponsored()) {
                i++;
                continue;
            }
            if (messageObject.isDateObject) {
                if (dayRow == null) {
                    messages.remove(i);
                    host.notifyMessageRemoved(i);
                    changed = true;
                    continue;
                }
                if (dayRow.dateKeyInt != messageObject.dateKeyInt) {
                    messages.add(i, createDateHeader(dayRow));
                    host.notifyMessageInserted(i);
                    changed = true;
                }
                i++;
                dayRow = null;
                continue;
            }
            if (dayRow == null || dayRow.dateKeyInt == messageObject.dateKeyInt) {
                dayRow = messageObject;
                i++;
                continue;
            }
            messages.add(i, createDateHeader(dayRow));
            host.notifyMessageInserted(i);
            changed = true;
            i++;
            dayRow = null;
        }
        if (dayRow != null) {
            messages.add(createDateHeader(dayRow));
            host.notifyMessageInserted(messages.size() - 1);
            return true;
        }
        return changed;
    }

    private MessageObject createDateHeader(MessageObject messageObject) {
        return FeedMessageUtils.createDateHeader(currentAccount, messageObject, host.stableIdForDateHeader(messageObject.dateKeyInt));
    }

    public ArrayList<Integer> collectLocalRowIds(long dialogId, ArrayList<Integer> realIds, int maxId) {
        ArrayList<Integer> result = new ArrayList<>();
        HashSet<Integer> realIdSet = realIds != null ? new HashSet<>(realIds) : null;
        ArrayList<MessageObject> messages = host.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (!FeedMessageUtils.isPostRow(messageObject) || messageObject.getDialogId() != dialogId) {
                continue;
            }
            int realId = messageObject.getRealId();
            boolean matches = realIdSet != null ? realIdSet.contains(realId) : realId > 0 && realId <= maxId;
            if (matches) {
                result.add(messageObject.getId());
            }
        }
        return result;
    }

    public static void mergeDeletedIds(ArrayList<Integer> target, ArrayList<Integer> ids) {
        for (int i = 0; i < ids.size(); i++) {
            if (!target.contains(ids.get(i))) {
                target.add(ids.get(i));
            }
        }
    }

    public void hideChannelWithUndo(long dialogId, CharSequence title) {
        FeedConfig feedConfig = FeedConfig.getInstance(currentAccount);
        FeedController feedController = FeedController.getInstance(currentAccount);
        feedConfig.setExcluded(dialogId, true);
        feedController.markConfigApplied();
        feedController.getStore().setHidden(dialogId, true);
        pendingHideDialogId = dialogId;
        reconcileWithStore();
        onFeedExclusionsChanged();
        notifyChannelsChanged();
        BulletinFactory.of(host.getFragment()).createUndoBulletin(
            AndroidUtilities.replaceTags(LocaleController.formatString(R.string.FeedChannelHidden, title)),
            this::undoHideChannel,
            () -> {
                if (pendingHideDialogId == dialogId) {
                    pendingHideDialogId = 0;
                }
            }
        ).show();
    }

    private void undoHideChannel() {
        long dialogId = pendingHideDialogId;
        if (dialogId == 0) {
            return;
        }
        pendingHideDialogId = 0;
        FeedController feedController = FeedController.getInstance(currentAccount);
        FeedConfig.getInstance(currentAccount).setExcluded(dialogId, false);
        feedController.markConfigApplied();
        feedController.getStore().setHidden(dialogId, false);
        reconcileWithStore();
        onFeedExclusionsChanged();
        notifyChannelsChanged();
    }

    public void onFeedExclusionsChanged() {
        lastPagedownCount = -1;
        updatePagedownCounter();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE);
    }
}
