package com.exteragram.messenger.feed.ads;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Set;

public final class FeedAd {

    public String id;
    public String title;
    public CharSequence bodyText;
    public ArrayList<TLRPC.MessageEntity> entities;
    public TLRPC.MessageMedia media;
    public String url;
    public String buttonText;
    public String sponsorInfo;
    public String additionalInfo;
    public boolean recommended;
    public Set<String> locales;
    public int weight = 1;
    public int colorId = -1;
    public int premium = 0;
    public int badge = 0;

    int bodyMessageId;
    int mediaMessageId;

    public boolean isDisplayable() {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return bodyText != null || title != null || media != null;
    }
}
