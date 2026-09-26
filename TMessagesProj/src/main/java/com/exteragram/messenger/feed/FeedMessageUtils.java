package com.exteragram.messenger.feed;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.Calendar;

public abstract class FeedMessageUtils {

    public static boolean isAllowedDoubleTapAction(int action) {
        return action == 1 || action == 2 || action == 3 || action == 4 || action == 6 || action == 9;
    }

    public static boolean isAllowedFeedOption(int option) {
        switch (option) {
            case 2:
            case 3:
            case 4:
            case 6:
            case 7:
            case 8:
            case 10:
            case 16:
            case 22:
            case 25:
            case 28:
            case 29:
            case 36:
            case 115:
            case 200:
            case 203:
            case 206:
                return true;
            default:
                return false;
        }
    }

    public static boolean isPostRow(MessageObject messageObject) {
        return messageObject != null && !messageObject.isDateObject && messageObject.type != 6 && !messageObject.isSponsored();
    }

    public static TLRPC.Chat resolveDisplayChannel(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.searchType != 4 || messageObject.preview || messageObject.isSponsored() || messageObject.type == MessageObject.TYPE_JOINED_CHANNEL) {
            return null;
        }
        TLRPC.Message message = messageObject.messageOwner;
        if (!message.post || message.action != null || message.peer_id == null || message.peer_id.channel_id == 0) {
            return null;
        }
        TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(message.peer_id.channel_id);
        return ChatObject.isChannelAndNotMegaGroup(chat) ? chat : null;
    }

    public static boolean hasProfileSignature(MessageObject messageObject) {
        TLRPC.Chat chat = resolveDisplayChannel(messageObject);
        return chat != null && chat.signature_profiles;
    }

    public static boolean shouldMergePosts(MessageObject message, TLRPC.Chat chat, MessageObject prevMessage, TLRPC.Chat prevChat) {
        return chat != null && prevChat != null && chat.id == prevChat.id
            && message.contentType == prevMessage.contentType
            && !(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup)
            && message.isOutOwner() == prevMessage.isOutOwner()
            && Math.abs(message.messageOwner.date - prevMessage.messageOwner.date) <= 5 * 60
            && prevMessage.messageOwner.paid_message_stars <= 0
            && DialogObject.getPeerDialogId(message.messageOwner.guestchat_via_from) == DialogObject.getPeerDialogId(prevMessage.messageOwner.guestchat_via_from);
    }

    public static MessageObject createUnreadDivider(int currentAccount, int stableId) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.message = "";
        message.id = 0;
        MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
        messageObject.type = 6;
        messageObject.contentType = 2;
        messageObject.stableId = stableId;
        return messageObject;
    }

    public static MessageObject createDateHeader(int currentAccount, MessageObject source, int stableId) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.message = LocaleController.formatDateChat(source.messageOwner.date);
        message.id = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis((long) source.messageOwner.date * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        message.date = (int) (calendar.getTimeInMillis() / 1000);
        MessageObject dateObject = new MessageObject(currentAccount, message, false, false);
        dateObject.type = MessageObject.TYPE_DATE;
        dateObject.contentType = 1;
        dateObject.isDateObject = true;
        dateObject.stableId = stableId;
        return dateObject;
    }

    public static TLRPC.InputPeer getInputPeerForMessageRequest(MessagesController messagesController, long dialogId, boolean isFeed, MessageObject messageObject) {
        if (isFeed && messageObject != null) {
            dialogId = messageObject.getDialogId();
        }
        return messagesController.getInputPeer(dialogId);
    }

    public static boolean matchesPlaybackNotification(int currentAccount, MessageObject messageObject, int messageId) {
        if (messageObject == null) {
            return false;
        }
        if (messageObject.getId() == messageId) {
            return true;
        }
        FeedController feedController = FeedController.peekInstance(currentAccount);
        if (feedController == null) {
            return false;
        }
        long realDialogId = feedController.resolveRealDialogId(messageId);
        return realDialogId != 0 && realDialogId == messageObject.getDialogId() && feedController.resolveRealMessageId(realDialogId, messageId) == messageObject.getFeedRealId();
    }

    public static int getPlaybackScrollMessageId(boolean isFeed, long dialogId, MessageObject messageObject) {
        if (messageObject != null && messageObject.searchType == 4 && !isFeed && messageObject.getDialogId() == dialogId) {
            return messageObject.getRealId();
        }
        return messageObject != null ? messageObject.getId() : 0;
    }

    public static MessageObject getForwardingMessageObject(int currentAccount, boolean isFeed, MessageObject messageObject) {
        if (!isFeed || messageObject == null || messageObject.getId() == messageObject.getRealId()) {
            return messageObject;
        }
        TLRPC.TL_message message = copyMessage(messageObject.messageOwner);
        message.id = messageObject.getRealId();
        message.realId = 0;
        message.dialog_id = messageObject.getDialogId();
        MessageObject copy = new MessageObject(currentAccount, message, messageObject.replyMessageObject, null, null, null, null, false, true, 0, false, false, false);
        copy.isPrimaryGroupMessage = messageObject.isPrimaryGroupMessage;
        copy.localGroupId = messageObject.localGroupId;
        copy.copyStableParams(messageObject);
        return copy;
    }

    public static MessageObject createReplacement(int currentAccount, long dialogId, MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        FeedController feedController = FeedController.getInstance(currentAccount);
        MessageObject existing = feedController.getMessage(dialogId, messageObject.getRealId());
        if (existing == null) {
            return null;
        }
        TLRPC.TL_message message = copyMessage(messageObject.messageOwner);
        message.id = existing.getId();
        message.realId = existing.getRealId();
        message.dialog_id = existing.getDialogId();
        MessageObject replacement = new MessageObject(currentAccount, message, existing.replyMessageObject, null, null, null, null, true, true, 0, false, false, false, 4);
        replacement.isPrimaryGroupMessage = existing.isPrimaryGroupMessage;
        replacement.localGroupId = existing.localGroupId;
        replacement.copyStableParams(existing);
        feedController.replaceMessage(existing, replacement);
        return replacement;
    }

    public static ArrayList<MessageObject> createReplacements(int currentAccount, long dialogId, ArrayList<MessageObject> messages) {
        ArrayList<MessageObject> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject replacement = createReplacement(currentAccount, dialogId, messages.get(i));
            if (replacement != null) {
                result.add(replacement);
            }
        }
        return result;
    }

    public static boolean isAllowedSwipeAction(int action) {
        return action != 1 && isAllowedDoubleTapAction(action);
    }

    public static void filterAllowedOptions(ArrayList<CharSequence> items, ArrayList<Integer> options, ArrayList<Integer> icons) {
        for (int i = options.size() - 1; i >= 0; i--) {
            if (!isAllowedFeedOption(options.get(i))) {
                icons.remove(i);
                items.remove(i);
                options.remove(i);
            }
        }
    }

    public static void copyFeedPostLink(ChatActivity chatActivity, MessageObject messageObject) {
        if (chatActivity == null || messageObject == null) {
            return;
        }
        TLRPC.Chat chat = chatActivity.getMessagesController().getChat(-messageObject.getDialogId());
        if (!ChatObject.isChannel(chat)) {
            return;
        }
        TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
        req.id = messageObject.getRealId();
        req.channel = MessagesController.getInputChannel(chat);
        chatActivity.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_exportedMessageLink) {
                String link = ((TLRPC.TL_exportedMessageLink) response).link;
                if (AndroidUtilities.addToClipboard(link) && BulletinFactory.canShowBulletin(chatActivity)) {
                    BulletinFactory.of(chatActivity).createCopyLinkBulletin(link.contains("/c/")).show();
                }
            }
        }));
    }

    public static void copyTranslationState(MessageObject from, MessageObject to) {
        if (from == null || to == null || from == to || from.messageOwner == null || to.messageOwner == null) {
            return;
        }
        TLRPC.Message src = from.messageOwner;
        TLRPC.Message dst = to.messageOwner;
        dst.translatedText = src.translatedText;
        dst.translatedToLanguage = src.translatedToLanguage;
        dst.translatedVoiceTranscription = src.translatedVoiceTranscription;
        dst.translatedPoll = src.translatedPoll;
        dst.summaryText = src.summaryText;
        dst.summarizedOpen = src.summarizedOpen;
        dst.translatedSummaryText = src.translatedSummaryText;
        dst.translatedSummaryLanguage = src.translatedSummaryLanguage;
    }

    private static TLRPC.TL_message copyMessage(TLRPC.Message message) {
        TLRPC.TL_message copy = new TLRPC.TL_message();
        copy.id = message.id;
        copy.from_id = message.from_id;
        copy.from_boosts_applied = message.from_boosts_applied;
        copy.peer_id = message.peer_id;
        copy.saved_peer_id = message.saved_peer_id;
        copy.date = message.date;
        copy.expire_date = message.expire_date;
        copy.action = message.action;
        copy.message = message.message;
        copy.media = message.media;
        copy.flags = message.flags;
        copy.flags2 = message.flags2;
        copy.mentioned = message.mentioned;
        copy.media_unread = message.media_unread;
        copy.out = message.out;
        copy.unread = message.unread;
        copy.entities = message.entities;
        copy.via_bot_name = message.via_bot_name;
        copy.reply_markup = message.reply_markup;
        copy.views = message.views;
        copy.forwards = message.forwards;
        copy.replies = message.replies;
        copy.edit_date = message.edit_date;
        copy.silent = message.silent;
        copy.post = message.post;
        copy.from_scheduled = message.from_scheduled;
        copy.legacy = message.legacy;
        copy.edit_hide = message.edit_hide;
        copy.pinned = message.pinned;
        copy.fwd_from = message.fwd_from;
        copy.via_bot_id = message.via_bot_id;
        copy.via_business_bot_id = message.via_business_bot_id;
        copy.reply_to = message.reply_to;
        copy.post_author = message.post_author;
        copy.grouped_id = message.grouped_id;
        copy.reactions = message.reactions;
        copy.restriction_reason = message.restriction_reason;
        copy.ttl_period = message.ttl_period;
        copy.quick_reply_shortcut_id = message.quick_reply_shortcut_id;
        copy.effect = message.effect;
        copy.noforwards = message.noforwards;
        copy.invert_media = message.invert_media;
        copy.offline = message.offline;
        copy.factcheck = message.factcheck;
        copy.send_state = message.send_state;
        copy.fwd_msg_id = message.fwd_msg_id;
        copy.params = message.params;
        copy.random_id = message.random_id;
        copy.local_id = message.local_id;
        copy.attachPath = message.attachPath;
        copy.dialog_id = message.dialog_id;
        copy.ttl = message.ttl;
        copy.destroyTime = message.destroyTime;
        copy.destroyTimeMillis = message.destroyTimeMillis;
        copy.layer = message.layer;
        copy.seq_in = message.seq_in;
        copy.seq_out = message.seq_out;
        copy.with_my_score = message.with_my_score;
        copy.replyMessage = message.replyMessage;
        copy.reqId = message.reqId;
        copy.realId = message.realId;
        copy.stickerVerified = message.stickerVerified;
        copy.isThreadMessage = message.isThreadMessage;
        copy.voiceTranscription = message.voiceTranscription;
        copy.voiceTranscriptionOpen = message.voiceTranscriptionOpen;
        copy.voiceTranscriptionRated = message.voiceTranscriptionRated;
        copy.voiceTranscriptionFinal = message.voiceTranscriptionFinal;
        copy.voiceTranscriptionForce = message.voiceTranscriptionForce;
        copy.voiceTranscriptionId = message.voiceTranscriptionId;
        copy.premiumEffectWasPlayed = message.premiumEffectWasPlayed;
        copy.originalLanguage = message.originalLanguage;
        copy.translatedToLanguage = message.translatedToLanguage;
        copy.translatedText = message.translatedText;
        copy.replyStory = message.replyStory;
        copy.quick_reply_shortcut = message.quick_reply_shortcut;
        return copy;
    }
}
