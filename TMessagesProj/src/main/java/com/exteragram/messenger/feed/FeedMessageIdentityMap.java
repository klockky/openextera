package com.exteragram.messenger.feed;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

final class FeedMessageIdentityMap {

    private static final int FIRST_GENERATED_ID = Integer.MAX_VALUE - 10;

    private final HashMap<MessageCompositeID, Integer> generatedIds = new HashMap<>();
    private final ConcurrentHashMap<Integer, MessageCompositeID> realIdsByGeneratedId = new ConcurrentHashMap<>();
    private final HashMap<MessageCompositeID, MessageObject> messagesByRealId = new HashMap<>();
    private final HashMap<GroupKey, ArrayList<MessageObject>> groupMembers = new HashMap<>();
    private int lastGeneratedId = FIRST_GENERATED_ID;

    public boolean register(MessageObject messageObject) {
        int realId = messageObject.getRealId();
        MessageCompositeID key = new MessageCompositeID(messageObject.getDialogId(), realId);
        Integer generatedId = generatedIds.get(key);
        if (generatedId == null) {
            generatedId = lastGeneratedId--;
            generatedIds.put(key, generatedId);
        }
        realIdsByGeneratedId.put(generatedId, key);
        boolean added;
        if (messagesByRealId.containsKey(key)) {
            added = false;
        } else {
            messagesByRealId.put(key, messageObject);
            addToGroup(messageObject, key.dialog_id);
            added = true;
        }
        TLRPC.Message message = messageObject.messageOwner;
        message.realId = realId;
        message.id = generatedId;
        return added;
    }

    public void replace(MessageObject messageObject) {
        MessageCompositeID key = new MessageCompositeID(messageObject.getDialogId(), messageObject.getRealId());
        generatedIds.put(key, messageObject.getId());
        realIdsByGeneratedId.put(messageObject.getId(), key);
        MessageObject old = messagesByRealId.put(key, messageObject);
        if (old != null) {
            removeFromGroup(old, key.dialog_id);
        }
        addToGroup(messageObject, key.dialog_id);
    }

    public void purge(MessageObject messageObject) {
        MessageCompositeID key = new MessageCompositeID(messageObject.getDialogId(), messageObject.getRealId());
        generatedIds.remove(key);
        messagesByRealId.remove(key);
        realIdsByGeneratedId.remove(messageObject.getId());
        removeFromGroup(messageObject, key.dialog_id);
    }

    public MessageObject getByRealId(long dialogId, int realId) {
        return messagesByRealId.get(new MessageCompositeID(dialogId, realId));
    }

    public MessageObject getByAnyId(long dialogId, int id) {
        MessageObject messageObject = messagesByRealId.get(new MessageCompositeID(dialogId, id));
        if (messageObject != null) {
            return messageObject;
        }
        int realId = resolveRealMessageId(dialogId, id);
        if (realId != id) {
            return messagesByRealId.get(new MessageCompositeID(dialogId, realId));
        }
        return null;
    }

    public int resolveRealMessageId(long dialogId, int id) {
        MessageCompositeID key = realIdsByGeneratedId.get(id);
        return key == null || key.dialog_id != dialogId ? id : key.id;
    }

    public long resolveRealDialogId(int id) {
        MessageCompositeID key = realIdsByGeneratedId.get(id);
        return key != null ? key.dialog_id : 0;
    }

    public boolean isEmpty() {
        return realIdsByGeneratedId.isEmpty();
    }

    public void clear() {
        generatedIds.clear();
        realIdsByGeneratedId.clear();
        messagesByRealId.clear();
        groupMembers.clear();
        lastGeneratedId = FIRST_GENERATED_ID;
    }

    private void addToGroup(MessageObject messageObject, long dialogId) {
        if (!messageObject.hasValidGroupId()) {
            messageObject.isPrimaryGroupMessage = false;
            return;
        }
        GroupKey key = new GroupKey(dialogId, messageObject.messageOwner.grouped_id);
        ArrayList<MessageObject> members = groupMembers.get(key);
        if (members == null) {
            members = new ArrayList<>();
            groupMembers.put(key, members);
        }
        if (!members.contains(messageObject)) {
            members.add(messageObject);
        }
        electPrimary(members);
    }

    private void removeFromGroup(MessageObject messageObject, long dialogId) {
        if (!messageObject.hasValidGroupId()) {
            return;
        }
        GroupKey key = new GroupKey(dialogId, messageObject.messageOwner.grouped_id);
        ArrayList<MessageObject> members = groupMembers.get(key);
        if (members == null) {
            return;
        }
        members.remove(messageObject);
        messageObject.isPrimaryGroupMessage = false;
        if (members.isEmpty()) {
            groupMembers.remove(key);
        } else {
            electPrimary(members);
        }
    }

    private static void electPrimary(ArrayList<MessageObject> members) {
        MessageObject primary = null;
        for (int i = 0; i < members.size(); i++) {
            MessageObject messageObject = members.get(i);
            if (primary == null || messageObject.getRealId() > primary.getRealId()) {
                primary = messageObject;
            }
        }
        for (int i = 0; i < members.size(); i++) {
            members.get(i).isPrimaryGroupMessage = members.get(i) == primary;
        }
    }

    public static final class GroupKey {
        final long dialog_id;
        final long groupedId;

        public GroupKey(long dialogId, long groupedId) {
            this.dialog_id = dialogId;
            this.groupedId = groupedId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            GroupKey other = (GroupKey) obj;
            return dialog_id == other.dialog_id && groupedId == other.groupedId;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(dialog_id) * 31 + Long.hashCode(groupedId);
        }
    }

    public static final class MessageCompositeID {
        final long dialog_id;
        final int id;

        public MessageCompositeID(long dialogId, int id) {
            this.dialog_id = dialogId;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MessageCompositeID other = (MessageCompositeID) obj;
            return dialog_id == other.dialog_id && id == other.id;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(dialog_id) * 31 + id;
        }
    }
}
