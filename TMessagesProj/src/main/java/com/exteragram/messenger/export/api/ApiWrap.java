package com.exteragram.messenger.export.api;

import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.html.HtmlWriter;

import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ApiWrap {

    private ApiWrap() {
    }

    public record ActionChatEditPhoto(HtmlWriter.Photo photo) {
    }

    public record ActionSuggestProfilePhoto(HtmlWriter.Photo photo) {
    }

    public static class Chat {
        public boolean hasMonoforumAdminRights;
        public boolean isMonoforumAdmin;
        public boolean isMonoforumOfPublicBroadcast;
        public long monoforumLinkId;
        public long bareId = 0;
        public long migratedToChannelId = 0;
        public String title = "";
        public String username = "";
        public int colorIndex = 0;
        public boolean isBroadcast = false;
        public boolean isSupergroup = false;
        public boolean isMonoforum = false;
        public TLRPC.InputPeer input = new TLRPC.TL_inputPeerEmpty();
        public TLRPC.InputPeer monoforumBroadcastInput = new TLRPC.TL_inputPeerEmpty();
    }

    public static class ChatProcess {
        public Runnable done;
        public Utilities.CallbackReturn<DownloadProgress, Boolean> fileProgress;
        public Utilities.CallbackReturn<MessagesSlice, Boolean> handleSlice;
        public DialogInfo info;
        public Utilities.Callback<TLRPC.messages_Messages> requestDone;
        public MessagesSlice slice;
        public Utilities.CallbackReturn<DialogInfo, Boolean> start;
        public int localSplitIndex = 0;
        public int largestIdPlusOne = 1;
        public ParseMediaContext context = new ParseMediaContext();
        public boolean lastSlice = false;
        public int fileIndex = 0;
    }

    public static abstract class ChatsProcess {
        public Utilities.Callback<DialogsInfo> done;
        public Utilities.CallbackReturn<Integer, Boolean> progress;
        public DialogsInfo info = new DialogsInfo();
        public int processedCount = 0;
        public Map<Long, Integer> indexByPeer = new HashMap<>();
    }

    public static class ContactInfo {
        public String firstName;
        public String lastName;
        public String phoneNumber;
        public Long userId = 0L;
        public int date = 0;
        public int colorIndex = 0;
    }

    public static class ContactsList {
        public ArrayList<ContactInfo> list = new ArrayList<>();
        public ArrayList<TopPeer> correspondents = new ArrayList<>();
        public ArrayList<TopPeer> inlineBots = new ArrayList<>();
        public ArrayList<TopPeer> phoneCalls = new ArrayList<>();
    }

    public static class ContactsProcess {
        public Utilities.Callback<ContactsList> done;
        public ContactsList result;
        public int topPeersOffset = 0;
    }

    public static class DialogInfo {
        public String lastName;
        public String name;
        public String relativePath;
        public Type type = Type.Unknown;
        public TLRPC.InputPeer input = new TLRPC.TL_inputPeerEmpty();
        public int topMessageId = 0;
        public int topMessageDate = -1337;
        public long peerId = 0;
        public int colorIndex = 0;
        public TLRPC.InputPeer migratedFromInput = new TLRPC.TL_inputPeerEmpty();
        public long migratedToChannelId = 0;
        public TLRPC.InputPeer monoforumBroadcastInput = new TLRPC.TL_inputPeerEmpty();
        public ArrayList<Integer> splits = new ArrayList<>();
        public boolean onlyMyMessages = false;
        public boolean isLeftChannel = false;
        public boolean isMonoforum = false;
        public ArrayList<Integer> messagesCountPerSplit = new ArrayList<>();

        public enum Type {
            Unknown,
            Self,
            Replies,
            VerifyCodes,
            Personal,
            Bot,
            PrivateGroup,
            PrivateSupergroup,
            PublicSupergroup,
            PrivateChannel,
            PublicChannel
        }
    }

    public static class DialogsInfo {
        public ArrayList<DialogInfo> chats = new ArrayList<>();
        public ArrayList<DialogInfo> left = new ArrayList<>();

        public DialogInfo getItemAt(int index) {
            if (index < 0) {
                return null;
            }
            int chatsCount = chats.size();
            if (index < chatsCount) {
                return chats.get(index);
            }
            int leftIndex = index - chatsCount;
            if (leftIndex < left.size()) {
                return left.get(leftIndex);
            }
            return null;
        }
    }

    public static class DialogsProcess extends ChatsProcess {
        public int splitIndexPlusOne = 0;
        public int offsetDate = 0;
        public int offsetId = 0;
        public TLRPC.InputPeer offsetPeer = new TLRPC.TL_inputPeerEmpty();
    }

    public static class Document {
        public int duration;
        public File file;
        public String mime;
        public String name;
        public String songPerformer;
        public String songTitle;
        public TLRPC.Document sticker;
        public String stickerEmoji;
        public Image thumb;
        public long id = 0;
        public int date = 0;
        public int width = 0;
        public int height = 0;
        public boolean isSticker = false;
        public boolean isAnimated = false;
        public boolean isVideoMessage = false;
        public boolean isVoiceMessage = false;
        public boolean isVideoFile = false;
        public boolean isAudioFile = false;
        public boolean spoilered = false;
    }

    public record DownloadProgress(long randomId, String path, int itemIndex, long ready, long total) {
        public DownloadProgress() {
            this(0L, "", 0, 0L, 0L);
        }
    }

    public static class ExportPersonalInfo {
        public String bio = "";
        public User user;

        public ExportPersonalInfo() {
        }

        public ExportPersonalInfo(ExportPersonalInfo other) {
            this.user = other.user;
            this.bio = other.bio;
        }
    }

    public static class File {
        public byte[] content;
        public FileLocation location;
        public String suggestedPath;
        public long size = 0;
        public int dcId = 0;
        public String relativePath = "";
        public SkipReason skipReason = SkipReason.None;

        public enum SkipReason {
            None,
            Unavailable,
            FileType,
            FileSize,
            DateLimits
        }
    }

    public static class FileLocation {
        public TLRPC.InputFileLocation data;
        public int dcId = 0;
    }

    public record FileOrigin(int split, TLRPC.InputPeer peer, int messageId, int storyId, long customEmojiId) {
        public FileOrigin() {
            this(0, null, 0, 0, 0L);
        }
    }

    public static class FileProcess {
        public Utilities.Callback<String> done;
        public OutputFile file;
        public FileLocation location;
        public FileOrigin origin;
        public Utilities.CallbackReturn<FileProgress, Boolean> progress;
        public String relativePath;
        public long randomId = 0;
        public long offset = 0;
        public long size = 0;
        public long requestId = 0;
        public Deque<Request> requests = new ConcurrentLinkedDeque<>();

        public static class Request {
            public NativeByteBuffer bytes;
            public long offset = 0;
        }

        public FileProcess(String path, OutputFile.Stats stats) {
            this.file = new OutputFile(path, stats);
        }
    }

    public record FileProgress(long ready, long total) {
    }

    public static class Game {
        public String description;
        public String shortName;
        public String title;
        public long id = 0;
        public long botId = 0;
    }

    public static class GeoPoint {
        public double latitude = 0.0d;
        public double longitude = 0.0d;
        public boolean valid = false;
    }

    public static class GiveawayResults {
        public int additionalPeersCount;
        public String additionalPrize;
        public boolean all;
        public long channel;
        public long credits;
        public int launchId;
        public int months;
        public boolean refunded;
        public int unclaimedCount;
        public int untilDate;
        public ArrayList<Long> winners = new ArrayList<>();
        public int winnersCount;

        public GiveawayResults(long channel, int untilDate, int launchId, int additionalPeersCount, int winnersCount, int unclaimedCount, int months, long credits, boolean refunded, boolean all) {
            this.channel = channel;
            this.untilDate = untilDate;
            this.launchId = launchId;
            this.additionalPeersCount = additionalPeersCount;
            this.winnersCount = winnersCount;
            this.unclaimedCount = unclaimedCount;
            this.months = months;
            this.credits = credits;
            this.refunded = refunded;
            this.all = all;
        }
    }

    public static class GiveawayStart {
        public String additionalPrize;
        public boolean all;
        public long credits;
        public int months;
        public int quantity;
        public int untilDate;
        public ArrayList<String> countries = new ArrayList<>();
        public ArrayList<Long> channels = new ArrayList<>();

        public GiveawayStart(int untilDate, long credits, int quantity, int months, boolean all) {
            this.untilDate = untilDate;
            this.credits = credits;
            this.quantity = quantity;
            this.months = months;
            this.all = all;
        }
    }

    public record HistoryMessageMarkupButton(Type type, String text, byte[] data, String forwardText, int buttonId) {

        public enum Type {
            Default,
            Url,
            Callback,
            CallbackWithPassword,
            RequestPhone,
            RequestLocation,
            RequestPoll,
            RequestPeer,
            SwitchInline,
            SwitchInlineSame,
            Game,
            Buy,
            Auth,
            UserProfile,
            WebView,
            SimpleWebView,
            CopyText
        }

        public HistoryMessageMarkupButton(Type type, String text) {
            this(type, text, null, null, 0);
        }

        public HistoryMessageMarkupButton(Type type, String text, byte[] data) {
            this(type, text, data, null, 0);
        }

        public static String TypeToString(HistoryMessageMarkupButton button) {
            switch (button.type) {
                case Default:
                    return "default";
                case Url:
                    return "url";
                case Callback:
                    return "callback";
                case CallbackWithPassword:
                    return "callback_with_password";
                case RequestPhone:
                    return "request_phone";
                case RequestLocation:
                    return "request_location";
                case RequestPoll:
                    return "request_poll";
                case RequestPeer:
                    return "request_peer";
                case SwitchInline:
                    return "switch_inline";
                case SwitchInlineSame:
                    return "switch_inline_same";
                case Game:
                    return "game";
                case Buy:
                    return "buy";
                case Auth:
                    return "auth";
                case UserProfile:
                    return "user_profile";
                case WebView:
                    return "web_view";
                case SimpleWebView:
                    return "simple_web_view";
                case CopyText:
                    return "copy_text";
                default:
                    throw new IncompatibleClassChangeError();
            }
        }
    }

    public static class Image {
        public int width = 0;
        public int height = 0;
        public File file = new File();
    }

    public static class Invoice {
        public String currency;
        public String description;
        public String title;
        public long amount = 0;
        public int receiptMsgId = 0;
    }

    public static class LeftChannelsProcess extends ChatsProcess {
        public int fullCount = 0;
        public int offset = 0;
        public boolean finished = false;
    }

    public static class LoadedFileCache {
        private final int _limit;
        private final HashMap<String, String> _map = new HashMap<>();
        private final Deque<String> _list = new ConcurrentLinkedDeque<>();

        public LoadedFileCache(int limit) {
            this._limit = limit;
        }

        public void save(FileLocation location, String relativePath) {
            if (location == null) {
                return;
            }
            String key = DataTypesUtils.ComputeLocationKey(location);
            _map.put(key, relativePath);
            _list.add(key);
            if (_list.size() > _limit) {
                String first = _list.getFirst();
                _list.removeFirst();
                _map.remove(first);
            }
        }

        public String find(FileLocation location) {
            if (location == null) {
                return null;
            }
            return _map.get(DataTypesUtils.ComputeLocationKey(location));
        }
    }

    public static class MediaData {
        public String title = "";
        public String description = "";
        public String status = "";
        public String classes = "";
        public String thumb = "";
        public String link = "";
    }

    public static class Media {
        public Object content;
        public int ttl = 0;

        public File getFile() {
            if (content instanceof HtmlWriter.Photo) {
                return ((HtmlWriter.Photo) content).image.file;
            }
            if (content instanceof Document) {
                return ((Document) content).file;
            }
            if (content instanceof SharedContact) {
                return ((SharedContact) content).vcard;
            }
            return new File();
        }

        public Image getThumb() {
            if (content instanceof Document) {
                return ((Document) content).thumb;
            }
            return new Image();
        }
    }

    public static class MessageId {
        public String didAndMsgId = "";

        @Override
        public int hashCode() {
            return didAndMsgId.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MessageId) {
                return Objects.equals(((MessageId) obj).didAndMsgId, didAndMsgId);
            }
            return false;
        }
    }

    public static class Message {
        public TLRPC.MessageAction action;
        public ArrayList<ArrayList<HistoryMessageMarkupButton>> inlineButtonRows;
        public Object parsedAction;
        public long viaBotId;
        public int id = 0;
        public int date = 0;
        public int edited = 0;
        public long fromId = 0;
        public long peerId = 0;
        public long selfId = 0;
        public long forwardedFromId = 0;
        public String forwardedFromName = "";
        public int forwardedDate = 0;
        public boolean forwarded = false;
        public boolean showForwardedAsOriginal = false;
        public long savedFromChatId = 0;
        public String signature = "";
        public int replyToMsgId = 0;
        public long replyToPeerId = 0;
        public ArrayList<TextPart> text = new ArrayList<>();
        public ArrayList<Reaction> reactions = new ArrayList<>();
        public File.SkipReason skipReason = File.SkipReason.None;
        public boolean out = false;
        public Media media = new Media();

        public File getFile() {
            if (parsedAction instanceof ActionSuggestProfilePhoto) {
                return ((ActionSuggestProfilePhoto) parsedAction).photo().image.file;
            }
            if (parsedAction instanceof ActionChatEditPhoto) {
                return ((ActionChatEditPhoto) parsedAction).photo().image.file;
            }
            if (media != null) {
                return media.getFile();
            }
            return new File();
        }
    }

    public static class MessagesSlice {
        public ArrayList<Message> list = new ArrayList<>();
        public HashMap<Long, Peer> peers;
    }

    public static class OtherDataProcess {
        public Utilities.Callback<File> done;
        public File file = new File();
    }

    public static class PaidMedia {
        public ArrayList<Media> extended = new ArrayList<>();
        public long stars;
    }

    public static class ParseMediaContext {
        public long selfPeerId = 0;
        public int photos = 0;
        public int audios = 0;
        public int videos = 0;
        public int files = 0;
        public int contacts = 0;
        public long botId = 0;
    }

    public static class Peer {
        public Chat chat;
        public User user;

        public Peer(User user) {
            this.user = user;
        }

        public Peer(Chat chat) {
            this.chat = chat;
        }

        public TLRPC.InputPeer getInput() {
            if (user != null && user.input instanceof TLRPC.TL_inputUser) {
                TLRPC.TL_inputUser inputUser = (TLRPC.TL_inputUser) user.input;
                TLRPC.TL_inputPeerUser inputPeer = new TLRPC.TL_inputPeerUser();
                inputPeer.user_id = inputUser.user_id;
                inputPeer.access_hash = inputUser.access_hash;
                return inputPeer;
            }
            return chat.input;
        }

        public String name() {
            if (user != null) {
                return user.name();
            }
            if (chat != null) {
                return chat.title;
            }
            throw new IllegalStateException("both user and chat are null");
        }

        public int colorIndex() {
            if (user != null) {
                return user.colorIndex;
            }
            if (chat != null) {
                return chat.colorIndex;
            }
            throw new IllegalStateException("both user and chat are null");
        }

        public long id() {
            if (user != null) {
                return user.info.userId;
            }
            if (chat != null) {
                return chat.bareId;
            }
            throw new IllegalStateException("both user and chat are null");
        }
    }

    public static class Poll {
        public String question;
        public long id = 0;
        public int totalVotes = 0;
        public boolean closed = false;
        public ArrayList<Answer> answers = new ArrayList<>();

        public record Answer(String text, byte[] option, int votes, boolean my) {
        }
    }

    // R8 stripped the fields of this class in lite (reactions are never filled in), so they are restored
    // following Telegram Desktop's Export::Data::Reaction.
    public static class Reaction {
        public Type type = Type.Empty;
        public String emoji = "";
        public long documentId = 0;
        public int count = 0;

        public enum Type {
            Empty,
            Emoji,
            CustomEmoji,
            Paid
        }

        public static String TypeToString(Reaction reaction) {
            switch (reaction.type) {
                case Empty:
                    return "empty";
                case Emoji:
                    return "emoji";
                case CustomEmoji:
                    return "custom_emoji";
                case Paid:
                    return "paid";
                default:
                    throw new IncompatibleClassChangeError();
            }
        }
    }

    public static class SessionsList {
        public ArrayList<TLRPC.TL_authorization> list = new ArrayList<>();
        public ArrayList<WebSession> webList = new ArrayList<>();
    }

    public static class SharedContact {
        public ContactInfo info = new ContactInfo();
        public File vcard;
    }

    public static class StoriesProcess {
        public Utilities.CallbackReturn<DownloadProgress, Boolean> fileProgress;
        public Runnable finish;
        public Utilities.CallbackReturn<StoriesSlice, Boolean> handleSlice;
        public StoriesSlice slice;
        public Utilities.CallbackReturn<Integer, Boolean> start;
        public int processed = 0;
        public int offsetId = 0;
        public boolean lastSlice = false;
        public int fileIndex = 0;
    }

    public static class StoriesSlice {
        public ArrayList<Story> list = new ArrayList<>();
        public int lastId = 0;
        public int skipped = 0;
    }

    public static class StoryData {
        public String imageLink;
    }

    public static class Story {
        public ArrayList<TextPart> caption;
        public Media media;
        public int id = 0;
        public int date = 0;
        public int expires = 0;
        public boolean pinned = false;

        public File file() {
            return media.getFile();
        }

        public Image thumb() {
            return media.getThumb();
        }
    }

    public static class Tag {
        public boolean block = true;
        public String name;
    }

    public static class TextPart {
        public String additional;
        public String text;
        public Type type = Type.Text;

        public enum Type {
            Text,
            Unknown,
            Mention,
            Hashtag,
            BotCommand,
            Url,
            Email,
            Bold,
            Italic,
            Code,
            Pre,
            TextUrl,
            MentionName,
            Phone,
            Cashtag,
            Underline,
            Strike,
            Blockquote,
            BankCard,
            Spoiler,
            CustomEmoji
        }

        public static String UnavailableEmoji() {
            return "(unavailable)";
        }
    }

    public static class TopPeer {
        public Peer peer;
        public double rating = 0.0d;
    }

    public static class UnsupportedMedia {
    }

    public static class User {
        public Long id;
        public ContactInfo info;
        public String username;
        public Long bareId = 0L;
        public int colorIndex = 0;
        public boolean isBot = false;
        public boolean isSelf = false;
        public boolean isReplies = false;
        public boolean isVerifyCodes = false;
        public TLRPC.InputUser input = new TLRPC.TL_inputUserEmpty();

        public String name() {
            boolean hasFirst = info.firstName != null && !info.firstName.isEmpty();
            boolean hasLast = info.lastName != null && !info.lastName.isEmpty();
            if (!hasFirst) {
                return hasLast ? info.lastName : "";
            }
            if (!hasLast) {
                return info.firstName;
            }
            return info.firstName + " " + info.lastName;
        }
    }

    public record UserpicsInfo(int count) {
    }

    public static class UserpicsProcess {
        public Utilities.CallbackReturn<DownloadProgress, Boolean> fileProgress;
        public Runnable finish;
        public Utilities.CallbackReturn<ArrayList<HtmlWriter.Photo>, Boolean> handleSlice;
        public ArrayList<HtmlWriter.Photo> slice;
        public Utilities.CallbackReturn<UserpicsInfo, Boolean> start;
        public int processed = 0;
        public long maxId = 0;
        public boolean lastSlice = false;
        public int fileIndex = 0;
    }

    public static class Venue {
        public String address;
        public GeoPoint point;
        public String title;
    }

    public record WebSession(String botUsername, String domain, String browser, String platform, int created, int lastActive, String ip, String region) {
    }
}
