package com.exteragram.messenger.feed;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

public final class FeedStore {

    private static final int LOADING_ROWS = 3;

    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final FeedMessageIdentityMap identityMap = new FeedMessageIdentityMap();
    private final HashSet<Long> hiddenDialogIds = new HashSet<>();
    private final FeedTimelineLoader.Cursor oldestCursor = new FeedTimelineLoader.Cursor();
    private final FeedTimelineLoader.Cursor newestCursor = new FeedTimelineLoader.Cursor();
    private boolean endReached;
    private int count;

    public ArrayList<MessageObject> getMessages() {
        return messages;
    }

    public ArrayList<MessageObject> getVisibleMessages() {
        if (hiddenDialogIds.isEmpty()) {
            return new ArrayList<>(messages);
        }
        ArrayList<MessageObject> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject != null && !hiddenDialogIds.contains(messageObject.getDialogId())) {
                result.add(messageObject);
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public int getVisibleCount() {
        if (hiddenDialogIds.isEmpty()) {
            return messages.size();
        }
        int visible = 0;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject != null && !hiddenDialogIds.contains(messageObject.getDialogId())) {
                visible++;
            }
        }
        return visible;
    }

    public boolean hasMessagesForDialog(long dialogId) {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject != null && messageObject.getDialogId() == dialogId) {
                return true;
            }
        }
        return false;
    }

    public HashSet<Long> getLoadedDialogIds() {
        HashSet<Long> result = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject != null) {
                result.add(messageObject.getDialogId());
            }
        }
        return result;
    }

    public HashSet<Long> getHiddenSnapshot() {
        return new HashSet<>(hiddenDialogIds);
    }

    public boolean setHidden(long dialogId, boolean hidden) {
        boolean changed = hidden ? hiddenDialogIds.add(dialogId) : hiddenDialogIds.remove(dialogId);
        if (changed) {
            updateCount();
        }
        return changed;
    }

    public boolean applyIncludedDialogs(HashSet<Long> included) {
        HashSet<Long> loaded = getLoadedDialogIds();
        boolean changed = false;
        for (Long dialogId : loaded) {
            if (!included.contains(dialogId)) {
                changed |= hiddenDialogIds.add(dialogId);
            }
        }
        Iterator<Long> iterator = hiddenDialogIds.iterator();
        while (iterator.hasNext()) {
            Long dialogId = iterator.next();
            if (included.contains(dialogId) || !loaded.contains(dialogId)) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            updateCount();
        }
        return changed;
    }

    public FeedTimelineLoader.Cursor getOldestCursor() {
        return oldestCursor;
    }

    public FeedTimelineLoader.Cursor getNewestCursor() {
        return newestCursor;
    }

    public boolean isEndReached() {
        return endReached;
    }

    public void setEndReached(boolean endReached) {
        this.endReached = endReached;
        updateCount();
    }

    public int getCount() {
        return count;
    }

    private void updateCount() {
        int visible = getVisibleCount();
        count = visible == 0 ? 0 : visible + (endReached ? 0 : LOADING_ROWS);
    }

    public void clear() {
        messages.clear();
        identityMap.clear();
        hiddenDialogIds.clear();
        endReached = false;
        count = 0;
        oldestCursor.set(0, 0, 0);
        newestCursor.set(0, 0, 0);
    }

    public ArrayList<MessageObject> appendMessages(ArrayList<MessageObject> newMessages, boolean prepend) {
        ArrayList<MessageObject> added = new ArrayList<>(newMessages.size());
        for (MessageObject messageObject : newMessages) {
            if (identityMap.register(messageObject)) {
                added.add(messageObject);
            }
        }
        if (prepend) {
            ArrayList<MessageObject> reversed = new ArrayList<>(added);
            Collections.reverse(reversed);
            messages.addAll(0, reversed);
        } else {
            messages.addAll(added);
        }
        updateCount();
        return added;
    }

    public ArrayList<MessageObject> mergeRows(ArrayList<MessageObject> newMessages) {
        ArrayList<MessageObject> added = new ArrayList<>(newMessages.size());
        for (MessageObject messageObject : newMessages) {
            if (identityMap.register(messageObject)) {
                added.add(messageObject);
            }
        }
        int insertIndex = 0;
        int i = 0;
        while (i < added.size()) {
            MessageObject first = added.get(i);
            int groupEnd = i + 1;
            long groupId = first.getGroupId();
            while (groupId != 0 && groupEnd < added.size() && added.get(groupEnd).getGroupId() == groupId && added.get(groupEnd).getDialogId() == first.getDialogId()) {
                groupEnd++;
            }
            insertIndex = findMergeIndex(first, insertIndex);
            while (i < groupEnd) {
                messages.add(insertIndex++, added.get(i++));
            }
        }
        updateCount();
        return added;
    }

    private int findMergeIndex(MessageObject messageObject, int from) {
        int index = from;
        while (index < messages.size()) {
            MessageObject existing = messages.get(index);
            if (existing != null && compareTimeline(existing.messageOwner.date, existing.getDialogId(), existing.getRealId(), messageObject.messageOwner.date, messageObject.getDialogId(), messageObject.getRealId()) < 0) {
                break;
            }
            index++;
        }
        // don't split an album
        while (index > 0 && index < messages.size()) {
            MessageObject prev = messages.get(index - 1);
            MessageObject next = messages.get(index);
            if (prev == null || next == null || prev.getGroupId() == 0 || prev.getGroupId() != next.getGroupId() || prev.getDialogId() != next.getDialogId()) {
                break;
            }
            index++;
        }
        return index;
    }

    public void replaceMessage(MessageObject oldMessage, MessageObject newMessage) {
        if (oldMessage == null || newMessage == null) {
            return;
        }
        int index = messages.indexOf(oldMessage);
        if (index >= 0) {
            messages.set(index, newMessage);
        }
        identityMap.replace(newMessage);
    }

    public ArrayList<Integer> deleteMessages(long dialogId, ArrayList<Integer> realIds, boolean[] changed) {
        ArrayList<Integer> removedIds = new ArrayList<>();
        if (realIds == null) {
            return removedIds;
        }
        HashSet<Integer> realIdSet = new HashSet<>(realIds);
        HashSet<Integer> seen = new HashSet<>();
        boolean removed = false;
        for (int i = 0; i < realIds.size(); i++) {
            MessageObject messageObject = identityMap.getByRealId(dialogId, realIds.get(i));
            if (messageObject != null) {
                messages.remove(messageObject);
                purgeRow(messageObject, removedIds, seen);
                removed = true;
            }
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject messageObject = messages.get(i);
            if (messageObject != null && messageObject.getDialogId() == dialogId && realIdSet.contains(messageObject.getRealId())) {
                messages.remove(i);
                purgeRow(messageObject, removedIds, seen);
                removed = true;
            }
        }
        if (removed) {
            if (!hasMessagesForDialog(dialogId)) {
                hiddenDialogIds.remove(dialogId);
            }
            onRowsRemoved();
        }
        changed[0] = removed;
        return removedIds;
    }

    public ArrayList<Integer> deleteHistory(long dialogId, int maxId, boolean[] changed) {
        ArrayList<Integer> removedIds = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();
        boolean removed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject messageObject = messages.get(i);
            if (messageObject != null && messageObject.getDialogId() == dialogId && messageObject.getRealId() > 0 && messageObject.getRealId() <= maxId) {
                messages.remove(i);
                purgeRow(messageObject, removedIds, seen);
                removed = true;
            }
        }
        if (removed) {
            if (!hasMessagesForDialog(dialogId)) {
                hiddenDialogIds.remove(dialogId);
            }
            onRowsRemoved();
        }
        changed[0] = removed;
        return removedIds;
    }

    public boolean trim(int maxCount) {
        if (maxCount <= 0 || messages.size() <= maxCount) {
            return false;
        }
        MessageObject last = messages.get(maxCount - 1);
        int lastDate = last.messageOwner.date;
        long lastDialogId = last.getDialogId();
        int lastRealId = last.getRealId();
        boolean removed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject messageObject = messages.get(i);
            if (messageObject != null && compareTimeline(messageObject.messageOwner.date, messageObject.getDialogId(), messageObject.getRealId(), lastDate, lastDialogId, lastRealId) < 0) {
                messages.remove(i);
                identityMap.purge(messageObject);
                removed = true;
            }
        }
        if (!removed) {
            return false;
        }
        if (messages.isEmpty()) {
            oldestCursor.set(0, 0, 0);
        } else {
            int oldestDate = 0;
            long oldestDialogId = 0;
            int oldestRealId = 0;
            for (int i = 0; i < messages.size(); i++) {
                MessageObject messageObject = messages.get(i);
                if (messageObject != null && (oldestDate == 0 || compareTimeline(messageObject.messageOwner.date, messageObject.getDialogId(), messageObject.getRealId(), oldestDate, oldestDialogId, oldestRealId) < 0)) {
                    oldestDate = messageObject.messageOwner.date;
                    oldestDialogId = messageObject.getDialogId();
                    oldestRealId = messageObject.getRealId();
                }
            }
            oldestCursor.set(oldestDate, oldestDialogId, oldestRealId);
        }
        endReached = false;
        updateCount();
        return true;
    }

    private void onRowsRemoved() {
        if (!rebuildPagingCursorsFromLoadedRows()) {
            endReached = false;
        }
        updateCount();
    }

    private boolean rebuildPagingCursorsFromLoadedRows() {
        boolean hadOldestCursor = !oldestCursor.isEmpty();
        int newestDate = 0;
        long newestDialogId = 0;
        int newestRealId = 0;
        int oldestDate = 0;
        long oldestDialogId = 0;
        int oldestRealId = 0;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (!isPagingRow(messageObject)) {
                continue;
            }
            int date = messageObject.messageOwner.date;
            long dialogId = messageObject.getDialogId();
            int realId = messageObject.getRealId();
            if (newestDate == 0 || compareTimeline(date, dialogId, realId, newestDate, newestDialogId, newestRealId) > 0) {
                newestDate = date;
                newestDialogId = dialogId;
                newestRealId = realId;
            }
            if (oldestDate == 0 || compareTimeline(date, dialogId, realId, oldestDate, oldestDialogId, oldestRealId) < 0) {
                oldestDate = date;
                oldestDialogId = dialogId;
                oldestRealId = realId;
            }
        }
        if (newestDate == 0) {
            oldestCursor.set(0, 0, 0);
            newestCursor.set(0, 0, 0);
            return false;
        }
        if (hadOldestCursor && compareTimeline(oldestDate, oldestDialogId, oldestRealId, oldestCursor.date, oldestCursor.uid, oldestCursor.mid) > 0) {
            endReached = false;
        }
        newestCursor.set(newestDate, newestDialogId, newestRealId);
        oldestCursor.set(oldestDate, oldestDialogId, oldestRealId);
        return true;
    }

    private static boolean isPagingRow(MessageObject messageObject) {
        return messageObject != null && !messageObject.isDateObject && messageObject.messageOwner != null && messageObject.getRealId() > 0;
    }

    public static int compareTimeline(int date1, long dialogId1, int id1, int date2, long dialogId2, int id2) {
        if (date1 != date2) {
            return Integer.compare(date1, date2);
        }
        if (dialogId1 != dialogId2) {
            return Long.compare(dialogId1, dialogId2);
        }
        return Integer.compare(id1, id2);
    }

    private void purgeRow(MessageObject messageObject, ArrayList<Integer> removedIds, HashSet<Integer> seen) {
        identityMap.purge(messageObject);
        if (seen.add(messageObject.getId())) {
            removedIds.add(messageObject.getId());
        }
    }

    public boolean hasNoSyntheticIds() {
        return identityMap.isEmpty();
    }

    public MessageObject getMessage(long dialogId, int id) {
        return identityMap.getByAnyId(dialogId, id);
    }

    public int resolveRealMessageId(long dialogId, int id) {
        return identityMap.resolveRealMessageId(dialogId, id);
    }

    public long resolveRealDialogId(int id) {
        return identityMap.resolveRealDialogId(id);
    }
}
