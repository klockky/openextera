package com.exteragram.messenger.feed;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

final class FeedBackfillCoordinator {

    private static final int MAX_PARALLEL_LOADS = 4;
    private static final int PAGE_SIZE = 20;
    private static final long ROUND_TIMEOUT = 10000;

    private final int currentAccount;
    private final Runnable onRoundFinished;
    private final int guid = ConnectionsManager.generateClassGuid();
    private final HashSet<Long> pending = new HashSet<>();
    private final HashSet<Long> exhausted = new HashSet<>();
    private int loadIndex;
    private int roundId;
    private boolean running;

    public FeedBackfillCoordinator(int currentAccount, Runnable onRoundFinished) {
        this.currentAccount = currentAccount;
        this.onRoundFinished = onRoundFinished;
    }

    public HashSet<Long> getExhaustedSnapshot() {
        return new HashSet<>(exhausted);
    }

    public void clearExhausted() {
        exhausted.clear();
    }

    public void cancel() {
        running = false;
        roundId++;
        pending.clear();
        ConnectionsManager.getInstance(currentAccount).cancelRequestsForGuid(guid);
    }

    /**
     * @param requests list of {dialogId, maxId} pairs
     */
    public void startRound(ArrayList<long[]> requests) {
        running = true;
        final int round = ++roundId;
        pending.clear();
        int count = Math.min(MAX_PARALLEL_LOADS, requests.size());
        for (int i = 0; i < count; i++) {
            pending.add(requests.get(i)[0]);
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        for (int i = 0; i < count; i++) {
            long dialogId = requests.get(i)[0];
            int maxId = (int) requests.get(i)[1];
            messagesController.loadMessages(dialogId, 0, false, PAGE_SIZE, maxId, 0, false, 0, guid, 0, 0, 0, 0, 0, loadIndex++, false);
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (round == roundId && running) {
                exhausted.addAll(pending);
                finishRound();
            }
        }, ROUND_TIMEOUT);
    }

    public void onMessagesDidLoad(Object... args) {
        if ((Integer) args[10] != guid) {
            return;
        }
        long dialogId = (Long) args[0];
        if (((ArrayList<?>) args[2]).size() < PAGE_SIZE) {
            exhausted.add(dialogId);
        }
        onResult(dialogId);
    }

    public void onLoadingMessagesFailed(Object... args) {
        if ((Integer) args[0] != guid) {
            return;
        }
        long dialogId = 0;
        if (args[1] instanceof TLRPC.TL_messages_getHistory) {
            TLRPC.InputPeer peer = ((TLRPC.TL_messages_getHistory) args[1]).peer;
            if (peer != null) {
                dialogId = -(peer.channel_id != 0 ? peer.channel_id : peer.chat_id);
            }
        }
        if (dialogId != 0) {
            exhausted.add(dialogId);
        }
        onResult(dialogId);
    }

    private void onResult(long dialogId) {
        if (running && pending.remove(dialogId) && pending.isEmpty()) {
            finishRound();
        }
    }

    private void finishRound() {
        running = false;
        roundId++;
        pending.clear();
        onRoundFinished.run();
    }
}
