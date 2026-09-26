package com.exteragram.messenger.feed;

import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads the feed timeline (posts of all eligible channels merged by date) from the local messages database.
 */
final class FeedTimelineLoader {

    private static final int CHUNK_SIZE = 30;
    private static final int MAX_ROWS_TO_UNREAD = 200;
    private static final int NEWER_PAGE_SIZE = 50;
    private static final int WINDOW_LIMIT = 500;
    private static final int QUERY_BATCH = 64;
    private static final int MAX_ENUMERATION_RETRIES = 3;

    private final int currentAccount;
    private final AtomicInteger channelCacheEpoch = new AtomicInteger();
    private volatile ChannelSet channelSetCache;

    public FeedTimelineLoader(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public static final class Cursor {
        int date;
        long uid;
        int mid;

        public boolean isEmpty() {
            return date == 0;
        }

        public void set(int date, long uid, int mid) {
            this.date = date;
            this.uid = uid;
            this.mid = mid;
        }
    }

    public static final class ChannelSnapshot {
        final long dialogId;
        final int readInboxMax;
        final int unreadCount;
        final int topMessage;
        boolean hasHole;
        int holeEnd;
        int depthMid;
        int depthDate;
        boolean hasCached;
        boolean localStartReached;
        boolean incomplete;

        public ChannelSnapshot(long dialogId, int readInboxMax, int unreadCount, int topMessage) {
            this.dialogId = dialogId;
            this.readInboxMax = readInboxMax;
            this.unreadCount = unreadCount;
            this.topMessage = topMessage;
        }
    }

    public static final class ChannelEnumeration {
        int cacheEpoch;
        int configGeneration;
        boolean failed;
        boolean hasChannels;
        final ArrayList<ChannelSnapshot> included = new ArrayList<>();
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
    }

    public static final class ChannelSet {
        final int sessionGen;
        final int configGen;
        boolean failed;
        boolean hasChannels;
        final ArrayList<long[]> includedRows = new ArrayList<>();
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();

        public ChannelSet(int sessionGen, int configGen) {
            this.sessionGen = sessionGen;
            this.configGen = configGen;
        }
    }

    public static final class OlderPage {
        boolean failed;
        boolean hasIncomplete;
        int lastChunkRowCount;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        /** {dialogId, offsetId, depthDate} */
        final ArrayList<long[]> backfillCandidates = new ArrayList<>();
        final Cursor last = new Cursor();
        final Cursor first = new Cursor();
    }

    public static final class NewerPage {
        boolean failed;
        boolean hasMore;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        final Cursor first = new Cursor();
    }

    public static final class WindowPage {
        boolean failed;
        boolean truncated;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
    }

    public synchronized void invalidateChannelCache() {
        channelCacheEpoch.incrementAndGet();
        channelSetCache = null;
    }

    // TODO(openextera): decompile failed (retry loop), verify
    public ChannelEnumeration enumerateChannels(FeedConfig feedConfig, int sessionGeneration, boolean force) {
        ChannelSet channelSet;
        int epoch;
        int attempt = 0;
        while (true) {
            synchronized (this) {
                channelSet = channelSetCache;
                epoch = channelCacheEpoch.get();
            }
            FeedConfig.Snapshot snapshot = feedConfig.snapshot();
            int configGeneration = snapshot.getGeneration();
            if (force || channelSet == null || channelSet.sessionGen != sessionGeneration || channelSet.configGen != configGeneration) {
                channelSet = buildChannelSet(snapshot.getIncludeArchived(), new HashSet<>(snapshot.getExcludedChannels()), sessionGeneration, configGeneration);
                if (!channelSet.failed) {
                    synchronized (this) {
                        if (epoch == channelCacheEpoch.get()) {
                            channelSetCache = channelSet;
                        }
                    }
                }
            }
            if (channelSet.failed || attempt >= MAX_ENUMERATION_RETRIES) {
                break;
            }
            synchronized (this) {
                if (epoch == channelCacheEpoch.get()) {
                    break;
                }
            }
            // cache was invalidated while building, rebuild
            attempt++;
            force = true;
        }
        ChannelEnumeration enumeration = new ChannelEnumeration();
        enumeration.hasChannels = channelSet.hasChannels;
        enumeration.failed = channelSet.failed;
        enumeration.configGeneration = channelSet.configGen;
        enumeration.cacheEpoch = epoch;
        enumeration.channels.addAll(channelSet.channels);
        for (int i = 0; i < channelSet.includedRows.size(); i++) {
            long[] row = channelSet.includedRows.get(i);
            enumeration.included.add(new ChannelSnapshot(row[0], (int) row[1], (int) row[2], (int) row[3]));
        }
        return enumeration;
    }

    public synchronized int getChannelCacheEpoch() {
        return channelCacheEpoch.get();
    }

    public synchronized boolean isEnumerationCurrent(ChannelEnumeration enumeration) {
        return enumeration != null && enumeration.cacheEpoch == channelCacheEpoch.get();
    }

    private ChannelSet buildChannelSet(boolean includeArchived, HashSet<Long> excluded, int sessionGeneration, int configGeneration) {
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        ChannelSet channelSet = new ChannelSet(sessionGeneration, configGeneration);
        ArrayList<long[]> rows = new ArrayList<>();
        ArrayList<Long> chatIds = new ArrayList<>();
        try {
            SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized("SELECT did, inbox_max, unread_count, last_mid, folder_id FROM dialogs WHERE did < 0");
            while (cursor.next()) {
                long dialogId = cursor.longValue(0);
                if (DialogObject.isChatDialog(dialogId)) {
                    rows.add(new long[]{dialogId, cursor.intValue(1), cursor.intValue(2), cursor.intValue(3), cursor.intValue(4)});
                    chatIds.add(-dialogId);
                }
            }
            cursor.dispose();
            if (rows.isEmpty()) {
                return channelSet;
            }
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            messagesStorage.getChatsInternal(TextUtils.join(",", chatIds), chats);
            LongSparseArray<TLRPC.Chat> chatsById = new LongSparseArray<>();
            for (int i = 0; i < chats.size(); i++) {
                chatsById.put(chats.get(i).id, chats.get(i));
            }
            for (int i = 0; i < rows.size(); i++) {
                long[] row = rows.get(i);
                long dialogId = row[0];
                TLRPC.Chat chat = chatsById.get(-dialogId);
                if (FeedController.isEligibleChannel(chat) && (row[4] != 1 || includeArchived)) {
                    channelSet.hasChannels = true;
                    channelSet.channels.add(chat);
                    if (!excluded.contains(dialogId)) {
                        channelSet.includedRows.add(new long[]{dialogId, row[1], row[2], row[3]});
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            channelSet.failed = true;
        }
        return channelSet;
    }

    public OlderPage loadOlderPage(ArrayList<ChannelSnapshot> channels, Cursor cursor, HashSet<Long> exhausted) {
        OlderPage page = new OlderPage();
        boolean initialLoad = cursor.isEmpty();
        page.last.set(cursor.date, cursor.uid, cursor.mid);
        try {
            ArrayList<Long> dialogIds = new ArrayList<>(channels.size());
            for (ChannelSnapshot channel : channels) {
                dialogIds.add(channel.dialogId);
            }
            String dialogIdsString = TextUtils.join(",", dialogIds);
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);

            HashMap<Long, Integer> holes = new HashMap<>();
            SQLiteCursor holesCursor = messagesStorage.getDatabase().queryFinalized("SELECT uid, max(end) FROM messages_holes WHERE uid IN (" + dialogIdsString + ") GROUP BY uid");
            while (holesCursor.next()) {
                holes.put(holesCursor.longValue(0), holesCursor.intValue(1));
            }
            holesCursor.dispose();
            for (ChannelSnapshot channel : channels) {
                Integer holeEnd = holes.get(channel.dialogId);
                channel.hasHole = holeEnd != null;
                channel.holeEnd = holeEnd != null ? holeEnd : 0;
            }
            loadChannelDepths(messagesStorage, channels);

            // the local timeline is only continuous down to the shallowest incomplete channel
            int minDate = 0;
            for (ChannelSnapshot channel : channels) {
                channel.incomplete = !channel.localStartReached && !exhausted.contains(channel.dialogId);
                if (!channel.incomplete) {
                    continue;
                }
                page.hasIncomplete = true;
                minDate = Math.max(minDate, channel.depthDate);
                long offsetId;
                if (channel.hasCached) {
                    offsetId = channel.depthMid;
                } else {
                    int maxId = Math.max(channel.holeEnd, channel.topMessage);
                    offsetId = maxId > 0 ? maxId + 1 : 0;
                }
                page.backfillCandidates.add(new long[]{channel.dialogId, offsetId, channel.depthDate});
            }
            page.backfillCandidates.sort((a, b) -> Long.compare(b[2], a[2]));
            if (minDate == Integer.MAX_VALUE) {
                return page;
            }

            Cursor unreadBoundary = initialLoad ? findUnreadBoundary(messagesStorage, channels, minDate) : null;
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            int totalRows = 0;
            int rows;
            do {
                rows = loadChunk(messagesStorage, dialogIdsString, minDate, page, usersToLoad, chatsToLoad);
                page.lastChunkRowCount = rows;
                totalRows += rows;
            } while (rows >= CHUNK_SIZE && unreadBoundary != null && totalRows < MAX_ROWS_TO_UNREAD && compareDesc(page.last, unreadBoundary) < 0);
            completeTrailingAlbum(messagesStorage, page, usersToLoad, chatsToLoad);

            for (Long dialogId : dialogIds) {
                long chatId = -dialogId;
                if (!chatsToLoad.contains(chatId)) {
                    chatsToLoad.add(chatId);
                }
            }
            if (!usersToLoad.isEmpty()) {
                messagesStorage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
            page.failed = true;
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    private int loadChunk(MessagesStorage messagesStorage, String dialogIds, int minDate, OlderPage page, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) throws Exception {
        StringBuilder query = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
        query.append(dialogIds).append(") AND mid > 0");
        if (minDate > 0) {
            query.append(" AND date >= ").append(minDate);
        }
        if (!page.last.isEmpty()) {
            appendCursorBound(query, page.last, true, false);
        }
        query.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT ").append(CHUNK_SIZE);
        int rows = 0;
        SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized(query.toString());
        while (cursor.next()) {
            rows++;
            page.last.set(cursor.intValue(2), cursor.longValue(3), cursor.intValue(1));
            if (page.first.isEmpty()) {
                page.first.set(page.last.date, page.last.uid, page.last.mid);
            }
            TLRPC.Message message = readMessage(cursor);
            if (message != null) {
                page.messages.add(message);
                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
            }
        }
        cursor.dispose();
        return rows;
    }

    private Cursor findUnreadBoundary(MessagesStorage messagesStorage, ArrayList<ChannelSnapshot> channels, int minDate) {
        StringBuilder condition = new StringBuilder();
        Cursor boundary = null;
        int batch = 0;
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            if (channel.topMessage > channel.readInboxMax || channel.unreadCount > 0) {
                if (condition.length() > 0) {
                    condition.append(" OR ");
                }
                condition.append("uid = ").append(channel.dialogId).append(" AND mid > ").append(channel.readInboxMax);
                batch++;
            }
            if (batch > 0 && (batch == QUERY_BATCH || i == channels.size() - 1)) {
                Cursor result = queryUnreadBoundary(messagesStorage, condition, minDate);
                if (result != null && (boundary == null || compareDesc(result, boundary) > 0)) {
                    boundary = result;
                }
                condition.setLength(0);
                batch = 0;
            }
        }
        return boundary;
    }

    private Cursor queryUnreadBoundary(MessagesStorage messagesStorage, StringBuilder condition, int minDate) {
        StringBuilder query = new StringBuilder("SELECT date, uid, mid FROM messages_v2 WHERE mid > 0 AND (");
        query.append(condition).append(")");
        if (minDate > 0) {
            query.append(" AND date >= ").append(minDate);
        }
        query.append(" ORDER BY date ASC, uid ASC, mid ASC LIMIT 1");
        try {
            SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized(query.toString());
            try {
                if (!cursor.next()) {
                    return null;
                }
                Cursor result = new Cursor();
                result.set(cursor.intValue(0), cursor.longValue(1), cursor.intValue(2));
                return result;
            } finally {
                cursor.dispose();
            }
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void appendCursorBound(StringBuilder query, Cursor cursor, boolean older, boolean inclusive) {
        String op = older ? "<" : ">";
        query.append(" AND (date ").append(op).append(' ').append(cursor.date)
            .append(" OR date = ").append(cursor.date)
            .append(" AND (uid ").append(op).append(' ').append(cursor.uid)
            .append(" OR uid = ").append(cursor.uid)
            .append(" AND mid ").append(op).append(inclusive ? "= " : " ").append(cursor.mid)
            .append("))");
    }

    private static int compareDesc(Cursor a, Cursor b) {
        if (a.date != b.date) {
            return a.date > b.date ? -1 : 1;
        }
        if (a.uid != b.uid) {
            return a.uid > b.uid ? -1 : 1;
        }
        return -Integer.compare(a.mid, b.mid);
    }

    public NewerPage loadNewerPage(ArrayList<ChannelSnapshot> channels, Cursor cursor) {
        NewerPage page = new NewerPage();
        page.first.set(cursor.date, cursor.uid, cursor.mid);
        try {
            ArrayList<Long> dialogIds = new ArrayList<>(channels.size());
            for (ChannelSnapshot channel : channels) {
                dialogIds.add(channel.dialogId);
            }
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            StringBuilder query = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
            query.append(TextUtils.join(",", dialogIds)).append(") AND mid > 0");
            appendCursorBound(query, cursor, false, false);
            query.append(" ORDER BY date ASC, uid ASC, mid ASC LIMIT ").append(NEWER_PAGE_SIZE);
            SQLiteCursor sqlCursor = messagesStorage.getDatabase().queryFinalized(query.toString());
            int rows = 0;
            while (sqlCursor.next()) {
                rows++;
                page.first.set(sqlCursor.intValue(2), sqlCursor.longValue(3), sqlCursor.intValue(1));
                TLRPC.Message message = readMessage(sqlCursor);
                if (message != null) {
                    page.messages.add(message);
                    MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                }
            }
            sqlCursor.dispose();
            page.hasMore = rows == NEWER_PAGE_SIZE;
            if (!usersToLoad.isEmpty()) {
                messagesStorage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
            page.failed = true;
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    public WindowPage loadChannelWindow(ArrayList<Long> dialogIds, Cursor newest, Cursor oldest) {
        WindowPage page = new WindowPage();
        if (dialogIds.isEmpty() || newest.isEmpty() || oldest.isEmpty()) {
            return page;
        }
        try {
            MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            StringBuilder query = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
            query.append(TextUtils.join(",", dialogIds)).append(") AND mid > 0");
            appendCursorBound(query, newest, true, true);
            appendCursorBound(query, oldest, false, true);
            query.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT ").append(WINDOW_LIMIT + 1);
            SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized(query.toString());
            int rows = 0;
            while (cursor.next()) {
                if (++rows > WINDOW_LIMIT) {
                    page.truncated = true;
                    break;
                }
                TLRPC.Message message = readMessage(cursor);
                if (message != null) {
                    page.messages.add(message);
                    MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                }
            }
            cursor.dispose();
            if (!usersToLoad.isEmpty()) {
                messagesStorage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
            page.failed = true;
            page.messages.clear();
            page.users.clear();
            page.chats.clear();
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    private void completeTrailingAlbum(MessagesStorage messagesStorage, OlderPage page, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) throws Exception {
        if (page.messages.isEmpty()) {
            return;
        }
        TLRPC.Message last = page.messages.get(page.messages.size() - 1);
        if (last.grouped_id == 0) {
            return;
        }
        SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized("SELECT data, mid, date, uid FROM messages_v2 WHERE uid = " + last.dialog_id + " AND mid > 0 AND mid < " + last.id + " ORDER BY date DESC, mid DESC LIMIT 9");
        try {
            while (cursor.next()) {
                TLRPC.Message message = readMessage(cursor);
                if (message == null || message.grouped_id != last.grouped_id) {
                    break;
                }
                page.messages.add(message);
                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
            }
        } finally {
            cursor.dispose();
        }
    }

    private TLRPC.Message readMessage(SQLiteCursor cursor) throws Exception {
        NativeByteBuffer data = cursor.byteBufferValue(0);
        if (data == null) {
            return null;
        }
        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
        if (message == null) {
            data.reuse();
            return null;
        }
        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
        data.reuse();
        if (message instanceof TLRPC.TL_messageEmpty || message.action != null) {
            return null;
        }
        message.id = cursor.intValue(1);
        message.date = cursor.intValue(2);
        message.dialog_id = cursor.longValue(3);
        return message;
    }

    private static void loadChannelDepths(MessagesStorage messagesStorage, ArrayList<ChannelSnapshot> channels) throws Exception {
        LongSparseArray<ChannelSnapshot> byDialogId = new LongSparseArray<>(channels.size());
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            channel.depthMid = 0;
            channel.depthDate = Integer.MAX_VALUE;
            channel.hasCached = false;
            channel.localStartReached = false;
            byDialogId.put(channel.dialogId, channel);
        }
        int index = 0;
        while (index < channels.size()) {
            int end = Math.min(index + QUERY_BATCH, channels.size());
            StringBuilder query = new StringBuilder();
            for (; index < end; index++) {
                if (query.length() > 0) {
                    query.append(" UNION ALL ");
                }
                ChannelSnapshot channel = channels.get(index);
                query.append("SELECT uid, mid, date FROM (SELECT uid, mid, date FROM messages_v2 WHERE uid = ").append(channel.dialogId)
                    .append(" AND mid >= ").append(Math.max(channel.holeEnd, 1))
                    .append(" ORDER BY date ASC, mid ASC LIMIT 1)");
            }
            SQLiteCursor cursor = messagesStorage.getDatabase().queryFinalized(query.toString());
            try {
                while (cursor.next()) {
                    ChannelSnapshot channel = byDialogId.get(cursor.longValue(0));
                    if (channel != null) {
                        channel.depthMid = cursor.intValue(1);
                        channel.depthDate = cursor.intValue(2);
                        channel.hasCached = true;
                    }
                }
            } finally {
                cursor.dispose();
            }
        }
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            channel.localStartReached = !channel.hasHole && channel.hasCached;
        }
    }

    private static void clusterGroupedMessages(ArrayList<TLRPC.Message> messages) {
        if (messages.size() < 3) {
            return;
        }
        HashMap<Long, ArrayList<TLRPC.Message>> groups = new HashMap<>();
        boolean hasSplitGroups = false;
        for (int i = 0; i < messages.size(); i++) {
            long groupedId = messages.get(i).grouped_id;
            if (groupedId == 0) {
                continue;
            }
            ArrayList<TLRPC.Message> group = groups.get(groupedId);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(groupedId, group);
            } else {
                hasSplitGroups = true;
            }
            group.add(messages.get(i));
        }
        if (!hasSplitGroups) {
            return;
        }
        ArrayList<TLRPC.Message> result = new ArrayList<>(messages.size());
        HashSet<Long> added = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            TLRPC.Message message = messages.get(i);
            if (message.grouped_id == 0) {
                result.add(message);
            } else if (added.add(message.grouped_id)) {
                result.addAll(groups.get(message.grouped_id));
            }
        }
        messages.clear();
        messages.addAll(result);
    }
}
