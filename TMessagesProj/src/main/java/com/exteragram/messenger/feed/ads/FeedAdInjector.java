package com.exteragram.messenger.feed.ads;

import com.exteragram.messenger.feed.FeedChatIntegration;
import com.exteragram.messenger.feed.FeedMessageUtils;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FeedAdInjector {

    private final int currentAccount;
    private final FeedChatIntegration.Host host;
    private final HashMap<MessageObject, MessageObject> adByAnchor = new HashMap<>();
    private final HashMap<MessageObject, Integer> slotOrdinalByAnchor = new HashMap<>();

    public FeedAdInjector(int currentAccount, FeedChatIntegration.Host host) {
        this.currentAccount = currentAccount;
        this.host = host;
    }

    public void clear() {
        adByAnchor.clear();
        slotOrdinalByAnchor.clear();
    }

    public void refresh(MessageObject pivot) {
        if (!host.isListReady()) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        FeedAdController adController = FeedAdController.getInstance(currentAccount);
        boolean enabled = adController.isEnabled();
        boolean changed = false;

        for (MessageObject anchor : new ArrayList<>(adByAnchor.keySet())) {
            if (!enabled || !messages.contains(anchor)) {
                changed |= removeAd(messages, adByAnchor.remove(anchor));
                slotOrdinalByAnchor.remove(anchor);
            }
        }

        if (enabled) {
            changed |= revalidateKeptAds(messages, adController);

            for (Map.Entry<MessageObject, MessageObject> entry : adByAnchor.entrySet()) {
                MessageObject anchor = entry.getKey();
                MessageObject ad = entry.getValue();
                int anchorIndex = messages.indexOf(anchor);
                if (anchorIndex < 0) {
                    continue;
                }
                int adIndex = messages.indexOf(ad);
                int targetIndex = anchorIndex + 1;
                if (adIndex == targetIndex) {
                    continue;
                }
                if (adIndex >= 0) {
                    messages.remove(adIndex);
                    host.notifyMessageRemoved(adIndex);
                    targetIndex = messages.indexOf(anchor) + 1;
                }
                messages.add(targetIndex, ad);
                host.notifyMessageInserted(targetIndex);
                changed = true;
            }

            for (AnchorSlot slot : computeNewAnchors(messages, pivot, adController)) {
                FeedAd ad = adController.nextAd();
                if (ad == null) {
                    break;
                }
                int anchorIndex = messages.indexOf(slot.anchor);
                if (anchorIndex < 0) {
                    continue;
                }
                MessageObject adMessage = FeedAdFactory.createAdMessageObject(currentAccount, ad);
                adMessage.stableId = host.nextStableId();
                adByAnchor.put(slot.anchor, adMessage);
                slotOrdinalByAnchor.put(slot.anchor, slot.ordinal);
                messages.add(anchorIndex + 1, adMessage);
                host.notifyMessageInserted(anchorIndex + 1);
                changed = true;
            }
        }

        if (changed) {
            host.onFeedListChanged();
            host.invalidateVisiblePart();
        }
    }

    private boolean revalidateKeptAds(ArrayList<MessageObject> messages, FeedAdController adController) {
        if (adByAnchor.isEmpty()) {
            return false;
        }
        boolean changed = false;

        // keep ads after the last message of an album
        for (MessageObject anchor : new ArrayList<>(adByAnchor.keySet())) {
            int index = messages.indexOf(anchor);
            if (index < 0) {
                continue;
            }
            int lastIndex = lastGroupMemberIndex(messages, index);
            if (lastIndex == index) {
                continue;
            }
            MessageObject ad = adByAnchor.remove(anchor);
            Integer ordinal = slotOrdinalByAnchor.remove(anchor);
            MessageObject newAnchor = messages.get(lastIndex);
            if (adByAnchor.containsKey(newAnchor)) {
                changed |= removeAd(messages, ad);
            } else {
                adByAnchor.put(newAnchor, ad);
                if (ordinal != null) {
                    slotOrdinalByAnchor.put(newAnchor, ordinal);
                }
            }
        }

        HashMap<MessageObject, Integer> postOrdinals = new HashMap<>();
        int ordinal = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                postOrdinals.put(messages.get(i), ordinal++);
            }
        }
        int baseEvery = adController.getBaseEvery();
        int minTrailing = adController.getMinTrailing();
        TreeMap<Integer, MessageObject> anchorsByOrdinal = new TreeMap<>();
        for (MessageObject anchor : adByAnchor.keySet()) {
            Integer anchorOrdinal = postOrdinals.get(anchor);
            if (anchorOrdinal != null) {
                anchorsByOrdinal.put(anchorOrdinal, anchor);
            }
        }
        int prevOrdinal = Integer.MIN_VALUE;
        for (Map.Entry<Integer, MessageObject> entry : anchorsByOrdinal.entrySet()) {
            int anchorOrdinal = entry.getKey();
            if (anchorOrdinal < minTrailing || prevOrdinal != Integer.MIN_VALUE && anchorOrdinal - prevOrdinal < baseEvery) {
                MessageObject anchor = entry.getValue();
                changed |= removeAd(messages, adByAnchor.remove(anchor));
                slotOrdinalByAnchor.remove(anchor);
            } else {
                prevOrdinal = anchorOrdinal;
            }
        }
        return changed;
    }

    private boolean removeAd(ArrayList<MessageObject> messages, MessageObject ad) {
        if (ad == null) {
            return false;
        }
        int index = messages.indexOf(ad);
        if (index < 0) {
            return false;
        }
        messages.remove(index);
        host.notifyMessageRemoved(index);
        return true;
    }

    private ArrayList<AnchorSlot> computeNewAnchors(ArrayList<MessageObject> messages, MessageObject pivot, FeedAdController adController) {
        ArrayList<Integer> postIndices = new ArrayList<>();
        HashMap<MessageObject, Integer> postOrdinals = new HashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                postOrdinals.put(messages.get(i), postIndices.size());
                postIndices.add(i);
            }
        }
        ArrayList<AnchorSlot> result = new ArrayList<>();
        int postCount = postIndices.size();
        if (postCount == 0) {
            return result;
        }
        int firstAfter = adController.getFirstAfter();
        int every = adController.getEffectiveEvery();
        int minTrailing = adController.getMinTrailing();

        int pivotIndex = pivot != null ? messages.indexOf(pivot) : -1;
        int pivotOrdinal = 0;
        if (pivotIndex >= 0) {
            for (Integer index : postIndices) {
                if (index >= pivotIndex) {
                    break;
                }
                pivotOrdinal++;
            }
        }

        TreeSet<Integer> slots = new TreeSet<>();
        for (int slot = pivotOrdinal - firstAfter; slot >= 0; slot -= every) {
            slots.add(slot);
        }
        for (int slot = pivotOrdinal + firstAfter; slot <= postCount - 1; slot += every) {
            slots.add(slot);
        }

        ArrayList<Integer> takenSlots = new ArrayList<>();
        for (MessageObject anchor : adByAnchor.keySet()) {
            if (postOrdinals.containsKey(anchor)) {
                Integer slot = slotOrdinalByAnchor.get(anchor);
                if (slot == null) {
                    slot = postOrdinals.get(anchor);
                }
                takenSlots.add(slot);
            }
        }

        for (Integer slot : slots) {
            if (slot < minTrailing) {
                continue;
            }
            MessageObject anchor = messages.get(lastGroupMemberIndex(messages, postIndices.get(slot)));
            if (postOrdinals.get(anchor) == null || adByAnchor.containsKey(anchor)) {
                continue;
            }
            boolean tooClose = false;
            for (int i = 0; i < takenSlots.size(); i++) {
                if (Math.abs(slot - takenSlots.get(i)) < every) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                result.add(new AnchorSlot(anchor, slot));
                takenSlots.add(slot);
            }
        }
        return result;
    }

    private static int lastGroupMemberIndex(ArrayList<MessageObject> messages, int index) {
        long groupId = messages.get(index).getGroupId();
        if (groupId == 0) {
            return index;
        }
        int last = index;
        for (int i = index + 1; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (!FeedMessageUtils.isPostRow(messageObject) || messageObject.getGroupId() != groupId) {
                break;
            }
            last = i;
        }
        return last;
    }

    private static final class AnchorSlot {
        final MessageObject anchor;
        final int ordinal;

        AnchorSlot(MessageObject anchor, int ordinal) {
            this.anchor = anchor;
            this.ordinal = ordinal;
        }
    }
}
