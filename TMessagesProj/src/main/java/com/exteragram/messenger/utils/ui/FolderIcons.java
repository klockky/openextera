package com.exteragram.messenger.utils.ui;

import androidx.core.util.Pair;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.TabIconsMode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;

import java.util.LinkedHashMap;

public abstract class FolderIcons {

    public static LinkedHashMap<String, Integer> folderIcons = new LinkedHashMap<>();

    static {
        folderIcons.put("🐱", R.drawable.filter_cat);
        folderIcons.put("📕", R.drawable.filter_book);
        folderIcons.put("💰", R.drawable.filter_money);
        folderIcons.put("🎮", R.drawable.filter_game);
        folderIcons.put("💡", R.drawable.filter_light);
        folderIcons.put("👌", R.drawable.filter_like);
        folderIcons.put("🎵", R.drawable.filter_note);
        folderIcons.put("🎨", R.drawable.filter_palette);
        folderIcons.put("✈", R.drawable.filter_travel);
        folderIcons.put("⚽", R.drawable.filter_sport);
        folderIcons.put("⭐", R.drawable.filter_favorite);
        folderIcons.put("🎓", R.drawable.filter_study);
        folderIcons.put("🛫", R.drawable.filter_airplane);
        folderIcons.put("👤", R.drawable.filter_private);
        folderIcons.put("👥", R.drawable.filter_group);
        folderIcons.put("💬", R.drawable.filter_all);
        folderIcons.put("✅", R.drawable.filter_unread);
        folderIcons.put("🤖", R.drawable.filter_bots);
        folderIcons.put("👑", R.drawable.filter_crown);
        folderIcons.put("🌹", R.drawable.filter_flower);
        folderIcons.put("🏠", R.drawable.filter_home);
        folderIcons.put("❤", R.drawable.filter_love);
        folderIcons.put("🎭", R.drawable.filter_mask);
        folderIcons.put("🍸", R.drawable.filter_party);
        folderIcons.put("📈", R.drawable.filter_trade);
        folderIcons.put("💼", R.drawable.filter_work);
        folderIcons.put("🔔", R.drawable.filter_unmuted);
        folderIcons.put("📢", R.drawable.filter_channels);
        folderIcons.put("📁", R.drawable.filter_custom);
        folderIcons.put("📋", R.drawable.filter_setup);
    }

    // Mirrors FilterCreateActivity.fillFilterName(), additionally picking a matching emoticon
    public static Pair<String, String> getEmoticonFromFlags(int newFilterFlags) {
        int flags = newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
        String newName = "";
        String newEmoticon = "";
        if ((flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) {
            if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                newName = LocaleController.getString(R.string.FilterNameUnread);
                newEmoticon = "✅";
            } else if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
                newName = LocaleController.getString(R.string.FilterNameNonMuted);
                newEmoticon = "🔔";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
            if (flags == 0 || (flags & ~MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) == 0) {
                newName = LocaleController.getString(R.string.FilterContacts);
                newEmoticon = "👤";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
            if (flags == 0) {
                newName = LocaleController.getString(R.string.FilterNonContacts);
                newEmoticon = "👤";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_GROUPS;
            if (flags == 0) {
                newName = LocaleController.getString(R.string.FilterGroups);
                newEmoticon = "👥";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_BOTS;
            if (flags == 0) {
                newName = LocaleController.getString(R.string.FilterBots);
                newEmoticon = "🤖";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
            if (flags == 0) {
                newName = LocaleController.getString(R.string.FilterChannels);
                newEmoticon = "📢";
            }
        }
        return Pair.create(newName, newEmoticon);
    }

    public static int getIconWidth() {
        return AndroidUtilities.dp(24);
    }

    public static int getPadding() {
        if (ExteraConfig.getTabIcons() == TabIconsMode.ICONS_AND_TITLES) {
            return AndroidUtilities.dp(4);
        }
        return 0;
    }

    public static int getTotalIconWidth() {
        if (ExteraConfig.getTabIcons() != TabIconsMode.TITLES_ONLY) {
            return getIconWidth() + getPadding();
        }
        return 0;
    }

    public static int getTabIcon(String emoticon) {
        if (emoticon != null) {
            Integer icon = folderIcons.get(emoticon);
            if (icon != null) {
                return icon;
            }
        }
        return R.drawable.filter_custom;
    }

    public static int getPaddingTab() {
        return AndroidUtilities.dp(24);
    }
}
