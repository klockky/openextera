package com.exteragram.messenger.utils.chats;

import android.graphics.Point;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.feed.FeedMessageUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

public abstract class WidePosts {

    private static final int SEARCH_TYPE_FEED = 4;
    private static final int GROUP_WIDTH = 1000;

    private static final Map<MessageObject.GroupedMessagePosition, GroupPositionState> GROUP_POSITION_STATES = new WeakHashMap<>();
    private static final Map<MessageObject, CommentsPostContext> COMMENTS_POST_CONTEXTS = new WeakHashMap<>();

    public static void registerCommentsPostContext(MessageObject messageObject, TLRPC.Chat channel, MessageObject originalMessage) {
        if (messageObject == null || messageObject.messageOwner == null || !ChatObject.isChannelAndNotMegaGroup(channel)) {
            return;
        }
        TLRPC.Message message = messageObject.messageOwner;
        TLRPC.MessageFwdHeader fwdFrom = message.fwd_from;
        long fromChannelId = message.from_id != null ? message.from_id.channel_id : 0;
        long peerChannelId = message.peer_id != null ? message.peer_id.channel_id : 0;
        long savedFromChannelId = fwdFrom != null && fwdFrom.saved_from_peer != null ? fwdFrom.saved_from_peer.channel_id : 0;
        boolean fromChannelInDiscussion = fromChannelId == channel.id && peerChannelId != 0 && peerChannelId != channel.id;
        boolean isChannelPostCopy = message.reply_to == null && fwdFrom != null
                && (message.isThreadMessage || savedFromChannelId == channel.id || fwdFrom.channel_post != 0 || message.replies != null);
        if (!fromChannelInDiscussion || !isChannelPostCopy) {
            return;
        }
        String postAuthor = null;
        if (originalMessage != null && originalMessage.messageOwner != null && originalMessage.getDialogId() == -channel.id) {
            postAuthor = originalMessage.messageOwner.post_author;
        }
        CommentsPostContext context = COMMENTS_POST_CONTEXTS.get(messageObject);
        if (context != null && context.channelId == channel.id) {
            context.updatePostAuthor(postAuthor);
        } else {
            COMMENTS_POST_CONTEXTS.put(messageObject, new CommentsPostContext(channel.id, postAuthor));
        }
    }

    public static boolean isCommentsChannelPost(MessageObject messageObject) {
        return getCommentsChannelId(messageObject) != 0;
    }

    public static void copyCommentsPostContext(MessageObject from, MessageObject to) {
        if (from == null || to == null) {
            return;
        }
        CommentsPostContext context = COMMENTS_POST_CONTEXTS.get(from);
        if (context != null) {
            COMMENTS_POST_CONTEXTS.put(to, context);
        }
    }

    public static String getCommentsPostAuthor(MessageObject messageObject) {
        CommentsPostContext context = getCommentsPostContext(messageObject);
        return context != null ? context.postAuthor : null;
    }

    public static boolean isEnabledFor(boolean feed, boolean channelPost) {
        if (!channelPost) {
            return false;
        }
        return feed ? ExteraConfig.getWidePostsInFeed() : ExteraConfig.getWidePostsInChannels();
    }

    public static boolean isEnabledFor(MessageObject messageObject) {
        if (messageObject == null || !(ExteraConfig.getWidePostsInFeed() || ExteraConfig.getWidePostsInChannels())) {
            return false;
        }
        return isEnabledFor(messageObject.searchType == SEARCH_TYPE_FEED, isChannelPostOrSponsored(messageObject));
    }

    public static int getBubbleRight(int width) {
        return Math.max(AndroidUtilities.dp(120), width - AndroidUtilities.dp(9));
    }

    private static boolean shouldExpand(MessageObject messageObject) {
        return isEnabledFor(messageObject) && !messageObject.shouldDrawWithoutBackground();
    }

    public static boolean shouldDrawBackground(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        if (messageObject.shouldDrawWithoutBackground()) {
            return messageObject.isRoundVideo() && messageObject.isVoiceTranscriptionOpen();
        }
        return true;
    }

    public static boolean isTextMessage(MessageObject messageObject) {
        int type = messageObject.type;
        return type == MessageObject.TYPE_TEXT || type == MessageObject.TYPE_ARTICLE || type == MessageObject.TYPE_STORY_MENTION;
    }

    public static boolean isExpandableMedia(MessageObject messageObject) {
        int type = messageObject.type;
        return type == MessageObject.TYPE_PHOTO || type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW || type == MessageObject.TYPE_VIDEO || type == MessageObject.TYPE_GIF;
    }

    public static int getImageHeight(int photoWidth, int photoHeight, int minHeight, int width) {
        return Math.max(AndroidUtilities.dp(120), Math.min(Math.round(width * ((float) photoHeight / photoWidth)), Math.max(minHeight, Math.min(AndroidUtilities.getPhotoSize(), width + AndroidUtilities.dp(100)))));
    }

    public static boolean isProfileResolved(MessageObject messageObject) {
        if (messageObject == null || !shouldEmbedProfileAvatar(messageObject)) {
            return true;
        }
        return isPeerResolved(messageObject.currentAccount, getEmbeddedProfileDialogId(messageObject))
                && isPeerResolved(messageObject.currentAccount, getFeedSignatureProfileDialogId(messageObject));
    }

    private static boolean isPeerResolved(int account, long dialogId) {
        if (dialogId > 0) {
            return MessagesController.getInstance(account).getUser(dialogId) != null;
        }
        return dialogId >= 0 || MessagesController.getInstance(account).getChat(-dialogId) != null;
    }

    public static boolean shouldEmbedProfileAvatar(MessageObject messageObject) {
        return shouldEmbedFeedChannelProfileAvatar(messageObject) || shouldEmbedCommentsChannelProfileAvatar(messageObject) || shouldEmbedAuthorProfileAvatar(messageObject);
    }

    public static boolean shouldSuppressExternalAvatar(MessageObject messageObject) {
        return isEnabledFor(messageObject) && messageObject.searchType == SEARCH_TYPE_FEED && messageObject.isSponsored();
    }

    private static boolean shouldEmbedFeedChannelProfileAvatar(MessageObject messageObject) {
        return isEmbeddedProfileAvatarMessage(messageObject) && FeedMessageUtils.resolveDisplayChannel(messageObject) != null;
    }

    public static boolean shouldEmbedAuthorProfileAvatar(MessageObject messageObject) {
        if (!isEmbeddedProfileAvatarMessage(messageObject) || messageObject.searchType == SEARCH_TYPE_FEED) {
            return false;
        }
        TLRPC.Chat channel = getBroadcastChannel(messageObject);
        return channel != null && (channel.signature_profiles || messageObject.currentEvent != null) && ChatObject.isChannelAndNotMegaGroup(channel);
    }

    private static boolean shouldEmbedCommentsChannelProfileAvatar(MessageObject messageObject) {
        boolean embedded = isEmbeddedProfileAvatarMessage(messageObject);
        long channelId = getCommentsChannelId(messageObject);
        CommentsPostContext context = getCommentsPostContext(messageObject);
        TLRPC.Chat channel = messageObject != null && channelId != 0 ? MessagesController.getInstance(messageObject.currentAccount).getChat(channelId) : null;
        if (!embedded || channelId == 0) {
            return false;
        }
        return context != null || ChatObject.isChannelAndNotMegaGroup(channel);
    }

    public static long getEmbeddedProfileDialogId(MessageObject messageObject) {
        if (shouldEmbedFeedChannelProfileAvatar(messageObject)) {
            TLRPC.Chat channel = FeedMessageUtils.resolveDisplayChannel(messageObject);
            return channel != null ? -channel.id : 0;
        }
        if (shouldEmbedCommentsChannelProfileAvatar(messageObject)) {
            long channelId = getCommentsChannelId(messageObject);
            return channelId != 0 ? -channelId : 0;
        }
        if (!shouldEmbedAuthorProfileAvatar(messageObject) || messageObject.messageOwner.from_id == null) {
            return 0;
        }
        return DialogObject.getPeerDialogId(messageObject.messageOwner.from_id);
    }

    public static long getFeedSignatureProfileDialogId(MessageObject messageObject) {
        if (FeedMessageUtils.hasProfileSignature(messageObject) && messageObject.messageOwner.from_id != null) {
            long dialogId = DialogObject.getPeerDialogId(messageObject.messageOwner.from_id);
            if (dialogId != DialogObject.getPeerDialogId(messageObject.messageOwner.peer_id)) {
                return dialogId;
            }
        }
        return 0;
    }

    public static boolean shouldDrawFeedAuthorSignature(MessageObject messageObject) {
        return FeedMessageUtils.hasProfileSignature(messageObject);
    }

    public static String getFeedPostAuthor(MessageObject messageObject) {
        if (FeedMessageUtils.hasProfileSignature(messageObject)) {
            return messageObject.messageOwner.post_author;
        }
        return null;
    }

    public static boolean hasSameEmbeddedProfileHeader(MessageObject a, MessageObject b) {
        return shouldEmbedFeedChannelProfileAvatar(a) == shouldEmbedFeedChannelProfileAvatar(b) && getEmbeddedProfileDialogId(a) == getEmbeddedProfileDialogId(b);
    }

    private static boolean isEmbeddedProfileAvatarMessage(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (!shouldExpand(messageObject) && !(isEnabledFor(messageObject) && isEmbeddedProfileAvatarMessageWithoutBackground(messageObject))) {
            return false;
        }
        if (messageObject.isOutOwner() || messageObject.isSponsored() || messageObject.isExpiredStory() || !messageObject.needDrawAvatar()) {
            return false;
        }
        TLRPC.MessageFwdHeader fwdFrom = messageObject.messageOwner.fwd_from;
        if (fwdFrom != null && fwdFrom.psa_type != null && !fwdFrom.psa_type.isEmpty()) {
            return false;
        }
        return getChannelId(messageObject) != 0;
    }

    public static boolean isEmbeddedProfileAvatarMessageWithoutBackground(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        int type = messageObject.type;
        return type == MessageObject.TYPE_ROUND_VIDEO || type == MessageObject.TYPE_STICKER || type == MessageObject.TYPE_ANIMATED_STICKER || type == MessageObject.TYPE_EMOJIS;
    }

    public static boolean shouldHideAuxiliaryActions(MessageObject messageObject) {
        return isEnabledFor(messageObject) && !messageObject.isSponsored();
    }

    public static boolean isWideGroupedMedia(MessageObject.GroupedMessages group) {
        return isGroupedLayout(group) && !group.isDocuments;
    }

    public static int getScaledGroupWidth(MessageObject.GroupedMessages group) {
        if (group == null || group.posArray == null || group.posArray.isEmpty()) {
            return 0;
        }
        GroupPositionState state = GROUP_POSITION_STATES.get(group.posArray.get(0));
        return state != null ? state.scaledForWidth : 0;
    }

    public static int getDefaultGroupWidth() {
        Point size = AndroidUtilities.displaySize;
        boolean landscape = size.x > size.y;
        if (AndroidUtilities.isInMultiwindow || !AndroidUtilities.isTablet()) {
            return size.x;
        }
        if (AndroidUtilities.isSmallTablet() && !landscape) {
            return size.x;
        }
        return size.x - Math.max(size.x * 35 / 100, AndroidUtilities.dp(320));
    }

    public static void updateGroupedLayout(MessageObject.GroupedMessages group, int width) {
        if (!isWideGroupedMedia(group) || width <= 0) {
            restoreGroupedLayout(group);
            return;
        }
        MessageObject primary = getPrimaryGroupMessage(group);
        if (!shouldExpand(primary)) {
            restoreGroupedLayout(group);
            return;
        }
        boolean removeAvatarOffset = shouldEmbedProfileAvatar(primary) || shouldSuppressExternalAvatar(primary);
        for (MessageObject.GroupedMessagePosition position : group.posArray) {
            if (position == null) {
                continue;
            }
            GroupPositionState state = GROUP_POSITION_STATES.get(position);
            if (state == null) {
                state = new GroupPositionState(position);
                GROUP_POSITION_STATES.put(position, state);
            }
            state.restore(position);
            if (removeAvatarOffset) {
                removeGroupAvatarOffset(position);
            }
        }
        int baseWidth = getBaseGroupVisualWidth(group);
        if (baseWidth <= 0) {
            restoreGroupedLayout(group);
            return;
        }
        int targetWidth = Utilities.clamp(Math.round(getBubbleRight(width) * (float) GROUP_WIDTH / width), GROUP_WIDTH, 1);
        float scale = (float) targetWidth / baseWidth;
        for (MessageObject.GroupedMessagePosition position : group.posArray) {
            if (position == null || GROUP_POSITION_STATES.get(position) == null) {
                continue;
            }
            boolean hadLeftOffset = position.leftSpanOffset != 0;
            position.pw = Math.max(1, Math.round(position.pw * scale));
            position.leftSpanOffset = hadLeftOffset ? Math.max(0, targetWidth - position.pw) : 0;
        }
        normalizeGroupRows(group, targetWidth, removeAvatarOffset);

        int remainder = GROUP_WIDTH - targetWidth;
        boolean changed = false;
        for (MessageObject.GroupedMessagePosition position : group.posArray) {
            if (position == null) {
                continue;
            }
            GroupPositionState state = GROUP_POSITION_STATES.get(position);
            if (state == null) {
                continue;
            }
            // TODO(openextera): decompile failed (lost "spanSize = 1000" branch), verify full-width row handling
            if (!removeAvatarOffset && state.spanSize == GROUP_WIDTH) {
                position.spanSize = GROUP_WIDTH;
            } else {
                position.spanSize = position.pw;
                if ((position.flags & MessageObject.POSITION_FLAG_RIGHT) != 0) {
                    position.spanSize += remainder;
                }
            }
            state.scaledForWidth = width;
            changed |= state.apply(position);
        }
        if (changed) {
            group.cachedWidthForCaption = -1;
        }
    }

    private static boolean isChannelPostOrSponsored(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || messageObject.preview || messageObject.type == MessageObject.TYPE_JOINED_CHANNEL) {
            return false;
        }
        if (messageObject.isSponsored()) {
            return true;
        }
        long channelId = getCommentsChannelId(messageObject);
        if (channelId == 0 && messageObject.messageOwner.peer_id == null) {
            return false;
        }
        if (channelId == 0) {
            channelId = getChannelId(messageObject);
        }
        if (channelId == 0) {
            return false;
        }
        TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(channelId);
        if (chat != null) {
            return ChatObject.isChannelAndNotMegaGroup(chat);
        }
        return true;
    }

    private static long getChannelId(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return 0;
        }
        long commentsChannelId = getCommentsChannelId(messageObject);
        if (commentsChannelId != 0) {
            return commentsChannelId;
        }
        TLRPC.Peer peer = messageObject.messageOwner.peer_id;
        long channelId = peer != null ? peer.channel_id : 0;
        if (channelId != 0) {
            return channelId;
        }
        long dialogId = messageObject.getDialogId();
        return dialogId < 0 ? -dialogId : 0;
    }

    private static long getCommentsChannelId(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return 0;
        }
        CommentsPostContext context = getCommentsPostContext(messageObject);
        return context != null ? context.channelId : 0;
    }

    private static CommentsPostContext getCommentsPostContext(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        return COMMENTS_POST_CONTEXTS.get(messageObject);
    }

    private static TLRPC.Chat getBroadcastChannel(MessageObject messageObject) {
        long channelId = getChannelId(messageObject);
        if (channelId != 0) {
            return MessagesController.getInstance(messageObject.currentAccount).getChat(channelId);
        }
        return null;
    }

    private static boolean isGroupedLayout(MessageObject.GroupedMessages group) {
        if (group == null) {
            return false;
        }
        return group.posArray != null && group.posArray.size() > 1 || group.messages != null && group.messages.size() > 1;
    }

    private static MessageObject getPrimaryGroupMessage(MessageObject.GroupedMessages group) {
        if (group == null) {
            return null;
        }
        int flags = group.reversed
                ? MessageObject.POSITION_FLAG_BOTTOM | MessageObject.POSITION_FLAG_RIGHT
                : MessageObject.POSITION_FLAG_TOP | MessageObject.POSITION_FLAG_LEFT;
        if (group.messages != null) {
            for (MessageObject messageObject : group.messages) {
                MessageObject.GroupedMessagePosition position = group.getPosition(messageObject);
                if (position != null && (position.flags & flags) == flags) {
                    return messageObject;
                }
            }
        }
        if (group.messages == null || group.messages.isEmpty()) {
            return null;
        }
        return group.messages.get(0);
    }

    private static int getMaxRow(MessageObject.GroupedMessages group) {
        int maxRow = 0;
        for (MessageObject.GroupedMessagePosition position : group.posArray) {
            if (position != null) {
                maxRow = Math.max(maxRow, position.maxY);
            }
        }
        return maxRow;
    }

    private static int getBaseGroupVisualWidth(MessageObject.GroupedMessages group) {
        int maxRow = getMaxRow(group);
        int maxWidth = 0;
        for (int row = 0; row <= maxRow; row++) {
            int rowWidth = 0;
            for (MessageObject.GroupedMessagePosition position : group.posArray) {
                if (position != null && position.minY <= row && position.maxY >= row) {
                    rowWidth += position.pw;
                }
            }
            maxWidth = Math.max(maxWidth, rowWidth);
        }
        return Math.min(GROUP_WIDTH, maxWidth);
    }

    private static void removeGroupAvatarOffset(MessageObject.GroupedMessagePosition position) {
        int offset = MessageObject.GroupedMessages.GROUPED_AVATAR_OFFSET;
        if (position.edge) {
            position.pw = Math.max(1, position.pw - offset);
            if (position.spanSize != GROUP_WIDTH) {
                position.spanSize = Math.max(1, position.spanSize - offset);
            }
        } else if ((position.flags & MessageObject.POSITION_FLAG_RIGHT) != 0) {
            if (position.spanSize != GROUP_WIDTH) {
                position.spanSize += offset;
            } else if (position.leftSpanOffset != 0) {
                position.leftSpanOffset = Math.max(0, position.leftSpanOffset - offset);
            }
        }
    }

    private static void normalizeGroupRows(MessageObject.GroupedMessages group, int targetWidth, boolean removeAvatarOffset) {
        int maxRow = getMaxRow(group);
        for (int row = 0; row <= maxRow; row++) {
            MessageObject.GroupedMessagePosition last = null;
            int rowWidth = 0;
            for (MessageObject.GroupedMessagePosition position : group.posArray) {
                if (position == null || position.minY > row || position.maxY < row) {
                    continue;
                }
                rowWidth += position.pw;
                boolean right = (position.flags & MessageObject.POSITION_FLAG_RIGHT) != 0;
                boolean lastRight = last != null && (last.flags & MessageObject.POSITION_FLAG_RIGHT) != 0;
                if (last == null || right && !lastRight || right == lastRight && position.maxX > last.maxX) {
                    last = position;
                }
            }
            if (last == null) {
                continue;
            }
            last.pw = Math.max(1, last.pw + targetWidth - rowWidth);
            GroupPositionState state = GROUP_POSITION_STATES.get(last);
            if (removeAvatarOffset) {
                if (last.leftSpanOffset > 0) {
                    last.leftSpanOffset = Math.max(0, targetWidth - last.pw);
                }
            } else if (state != null && state.leftSpanOffset > 0) {
                last.leftSpanOffset = Math.max(0, targetWidth - last.pw);
            }
        }
    }

    private static void restoreGroupedLayout(MessageObject.GroupedMessages group) {
        if (group == null || group.posArray == null || group.posArray.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (MessageObject.GroupedMessagePosition position : group.posArray) {
            if (position == null) {
                continue;
            }
            GroupPositionState state = GROUP_POSITION_STATES.remove(position);
            if (state != null) {
                state.restore(position);
                changed = true;
            }
        }
        if (changed) {
            group.cachedWidthForCaption = -1;
        }
    }

    public static final class CommentsPostAuthorLoader {
        private final int currentAccount;
        private final TLRPC.Chat channel;
        private final ArrayList<MessageObject> messages = new ArrayList<>();
        private MessageObject originalMessage;
        private ChatActivity chatActivity;

        public CommentsPostAuthorLoader(int currentAccount, TLRPC.Chat channel) {
            this.currentAccount = currentAccount;
            this.channel = channel;
        }

        public MessageObject getOriginalMessage() {
            return originalMessage;
        }

        public int load(int messageId, BooleanSupplier cancelled) {
            TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
            req.channel = MessagesController.getInputChannel(channel);
            req.id.add(messageId);
            return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                        if (cancelled != null && cancelled.getAsBoolean()) {
                            return;
                        }
                        if (response instanceof TLRPC.messages_Messages) {
                            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                            for (int i = 0; i < res.messages.size(); i++) {
                                TLRPC.Message message = res.messages.get(i);
                                if (message != null && message.id == messageId) {
                                    originalMessage = new MessageObject(currentAccount, message, true, true);
                                    break;
                                }
                            }
                        }
                        apply();
                    })
            ));
        }

        public void setMessages(ArrayList<MessageObject> messages, ChatActivity chatActivity) {
            this.messages.clear();
            if (messages != null) {
                this.messages.addAll(messages);
            }
            this.chatActivity = chatActivity;
            apply();
        }

        private void apply() {
            if (messages.isEmpty()) {
                return;
            }
            for (int i = 0; i < messages.size(); i++) {
                MessageObject messageObject = messages.get(i);
                registerCommentsPostContext(messageObject, channel, originalMessage);
                messageObject.forceUpdate = true;
            }
            if (originalMessage != null && chatActivity != null) {
                chatActivity.updateWidePostsCommentsAuthor(messages);
            }
        }
    }

    public static final class CommentsPostContext {
        final long channelId;
        String postAuthor;

        public CommentsPostContext(long channelId, String postAuthor) {
            this.channelId = channelId;
            this.postAuthor = postAuthor;
        }

        public void updatePostAuthor(String postAuthor) {
            if (postAuthor != null) {
                this.postAuthor = postAuthor;
            }
        }
    }

    public static final class GroupPositionState {
        final int width;
        final int spanSize;
        final int leftSpanOffset;
        private int appliedWidth;
        private int appliedSpanSize;
        private int appliedLeftSpanOffset;
        private int scaledForWidth;

        public GroupPositionState(MessageObject.GroupedMessagePosition position) {
            width = appliedWidth = position.pw;
            spanSize = appliedSpanSize = position.spanSize;
            leftSpanOffset = appliedLeftSpanOffset = position.leftSpanOffset;
        }

        public boolean apply(MessageObject.GroupedMessagePosition position) {
            boolean changed = appliedWidth != position.pw || appliedSpanSize != position.spanSize || appliedLeftSpanOffset != position.leftSpanOffset;
            appliedWidth = position.pw;
            appliedSpanSize = position.spanSize;
            appliedLeftSpanOffset = position.leftSpanOffset;
            return changed;
        }

        public void restore(MessageObject.GroupedMessagePosition position) {
            position.pw = width;
            position.spanSize = spanSize;
            position.leftSpanOffset = leftSpanOffset;
        }
    }
}
