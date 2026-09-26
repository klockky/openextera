package com.exteragram.messenger.feed.ads;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class FeedAdFactory {

    private static final AtomicInteger nextId = new AtomicInteger(-20000000);

    public static MessageObject createAdMessageObject(int currentAccount, FeedAd ad) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = nextId.getAndDecrement();
        message.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        message.message = ad.bodyText != null ? ad.bodyText.toString() : "";
        message.peer_id = new TLRPC.TL_peerChannel();
        message.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        if (ad.entities != null && !ad.entities.isEmpty()) {
            message.entities = ad.entities;
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
        }
        if (ad.media != null) {
            message.media = ad.media;
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
        }

        MessageObject messageObject = new MessageObject(currentAccount, message, new HashMap<>(), new HashMap<>(), true, true);
        messageObject.searchType = 4;
        messageObject.sponsoredId = ad.id.getBytes(StandardCharsets.UTF_8);
        messageObject.sponsoredTitle = ad.title;
        messageObject.sponsoredUrl = ad.url;
        messageObject.sponsoredButtonText = ad.buttonText;
        messageObject.sponsoredInfo = ad.sponsorInfo;
        messageObject.sponsoredAdditionalInfo = ad.additionalInfo;
        messageObject.sponsoredRecommended = ad.recommended;
        messageObject.sponsoredCanReport = false;
        messageObject.sponsoredMedia = ad.media;
        messageObject.sponsoredColor = buildColor(ad.colorId);
        messageObject.setType();
        messageObject.textLayoutBlocks = new ArrayList<>();
        messageObject.generateThumbs(true);
        return messageObject;
    }

    private static TLRPC.PeerColor buildColor(int colorId) {
        if (colorId < 0) {
            return null;
        }
        TLRPC.TL_peerColor color = new TLRPC.TL_peerColor();
        color.flags |= 1;
        color.color = colorId;
        return color;
    }
}
