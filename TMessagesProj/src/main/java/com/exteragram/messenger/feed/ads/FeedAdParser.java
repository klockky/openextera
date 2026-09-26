package com.exteragram.messenger.feed.ads;

import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

abstract class FeedAdParser {

    public static final int MATCH_ANY = 0;
    public static final int MATCH_HAS = 1;
    public static final int MATCH_NONE = 2;

    public static ArrayList<FeedAd> parse(TLRPC.messages_Messages res) {
        ArrayList<FeedAd> ads = new ArrayList<>();
        if (res == null || res.messages == null) {
            return ads;
        }
        HashSet<String> ids = new HashSet<>();
        for (TLRPC.Message message : res.messages) {
            if (message instanceof TLRPC.TL_message && message.message != null && message.message.startsWith("feed_ad")) {
                FeedAd ad = parseManifest(message.message);
                if (ad != null && ids.add(ad.id)) {
                    ads.add(ad);
                }
            }
        }
        if (ads.isEmpty()) {
            return ads;
        }
        for (TLRPC.Message message : res.messages) {
            if (!(message instanceof TLRPC.TL_message)) {
                continue;
            }
            for (int i = 0; i < ads.size(); i++) {
                FeedAd ad = ads.get(i);
                if (ad.bodyMessageId != 0 && message.id == ad.bodyMessageId) {
                    ad.bodyText = message.message;
                    ad.entities = message.entities == null || message.entities.isEmpty() ? null : new ArrayList<>(message.entities);
                }
                if (ad.mediaMessageId != 0 && message.id == ad.mediaMessageId && message.media != null) {
                    ad.media = message.media;
                }
            }
        }
        ArrayList<FeedAd> result = new ArrayList<>(ads.size());
        for (int i = 0; i < ads.size(); i++) {
            if (ads.get(i).isDisplayable()) {
                result.add(ads.get(i));
            }
        }
        return result;
    }

    private static FeedAd parseManifest(String text) {
        try {
            FeedAd ad = new FeedAd();
            for (String line : text.split("\n")) {
                int index = line.indexOf('=');
                if (index < 0) {
                    continue;
                }
                String key = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                if (value.isEmpty()) {
                    continue;
                }
                switch (key) {
                    case "id":
                        ad.id = value;
                        break;
                    case "title":
                        ad.title = value;
                        break;
                    case "body":
                        ad.bodyMessageId = parseInt(value, 0);
                        break;
                    case "media":
                        ad.mediaMessageId = parseInt(value, 0);
                        break;
                    case "url":
                        ad.url = value;
                        break;
                    case "button":
                        ad.buttonText = value;
                        break;
                    case "sponsor_info":
                        ad.sponsorInfo = value;
                        break;
                    case "additional_info":
                        ad.additionalInfo = value;
                        break;
                    case "recommended":
                        ad.recommended = Boolean.parseBoolean(value);
                        break;
                    case "color":
                        ad.colorId = parseInt(value, -1);
                        break;
                    case "weight":
                        ad.weight = Math.max(1, parseInt(value, 1));
                        break;
                    case "locale":
                        ad.locales = parseLocales(value);
                        break;
                    case "premium":
                        ad.premium = parseMatch(value);
                        break;
                    case "badge":
                        ad.badge = parseMatch(value);
                        break;
                }
            }
            if (ad.id == null || ad.id.isEmpty()) {
                return null;
            }
            return ad;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Set<String> parseLocales(String value) {
        HashSet<String> locales = new HashSet<>();
        for (String part : value.split(",")) {
            String locale = part.trim().toLowerCase();
            if (!locale.isEmpty()) {
                locales.add(locale);
            }
        }
        return locales.isEmpty() ? null : locales;
    }

    private static int parseMatch(String value) {
        if (TextUtils.equals(value, "true") || TextUtils.equals(value, "has")) {
            return MATCH_HAS;
        }
        if (TextUtils.equals(value, "false") || TextUtils.equals(value, "none")) {
            return MATCH_NONE;
        }
        return MATCH_ANY;
    }

    private static int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
