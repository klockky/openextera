package com.exteragram.messenger.export.ui;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;

import com.google.gson.annotations.SerializedName;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExportMapper {

    private final ChatInfo chatInfo;
    private final int currentAccount;
    private final String path;

    public static class Action {

        @SerializedName("action")
        public String actionType;

        @SerializedName("actor")
        public String actor;

        @SerializedName("actor_id")
        public String actor_id;

        @SerializedName("amount")
        public long amount;

        @SerializedName("boost_peer_id")
        public long boost_peer_id;

        @SerializedName("boosts")
        public int boosts;

        @SerializedName("button_id")
        public int button_id;

        @SerializedName("charge_id")
        public String charge_id;

        @SerializedName("currency")
        public String currency;

        @SerializedName("information_text")
        public String customAction;

        @SerializedName("discard_reason")
        public String discard_reason;

        @SerializedName("distance")
        public int distance;

        @SerializedName("duration_seconds")
        public int duration;

        @SerializedName("emoticon")
        public String emotion;

        @SerializedName("from_id")
        public long from_id;

        @SerializedName("game_message_id")
        public int game_message_id;

        @SerializedName("gift_text")
        public List<Entity> giftText;

        @SerializedName("gift_code")
        public String gift_code;

        @SerializedName("gift_id")
        public long gift_id;

        @SerializedName("giveaway_msg_id")
        public int giveaway_msg_id;

        @SerializedName("inviter_id")
        public long inviterId;

        @SerializedName("is_unclaimed")
        public boolean is_unclaimed;

        @SerializedName("media_spoiler")
        public boolean media_spoiler;

        @SerializedName("members")
        public List<Long> members;

        @SerializedName("message_id")
        public int messageId;

        @SerializedName("months")
        public int months;

        @SerializedName("new_icon_emoji_id")
        public int new_icon_emoji_id;

        @SerializedName("peer")
        public long peer;

        @SerializedName("peers")
        public List<String> peers;

        @SerializedName("period")
        public int period;

        @SerializedName("reason_app_id")
        public long reason_app_id;

        @SerializedName("reason_app_name")
        public String reason_app_name;

        @SerializedName("reason_domain")
        public String reason_domain;

        @SerializedName("recurring")
        public String recurring;

        @SerializedName("schedule_date")
        public int schedule_date;

        @SerializedName("score")
        public int score;

        @SerializedName("stars")
        public long stars;

        @SerializedName("stars_boolean")
        public boolean stars_boolean;

        @SerializedName("text")
        public String text;

        @SerializedName("title")
        public String title;

        @SerializedName("to_id")
        public long to_id;

        @SerializedName("transaction_id")
        public String transaction_id;

        @SerializedName("unclaimed")
        public int unclaimed;

        @SerializedName("unclaimed_count")
        public int unclaimed_count;

        @SerializedName("values")
        public List<String> values;

        @SerializedName("via_giveaway")
        public boolean via_giveaway;

        @SerializedName("winners_count")
        public int winners_count;
    }

    public static class ChatInfo {

        @SerializedName("id")
        public long id;

        @SerializedName("msgsCount")
        public int msgsCount;

        @SerializedName("name")
        public String name;

        @SerializedName("type")
        public String type;
    }

    public static class Entity {

        @SerializedName("additional")
        public String additional;

        @SerializedName("text")
        public String text;

        @SerializedName("type")
        public String type;
    }

    public static class JsonMessage {

        @SerializedName("action")
        public Action action;

        @SerializedName("date")
        public int date;

        @SerializedName("from")
        public String from;

        @SerializedName("from_id")
        public String from_id;

        @SerializedName("id")
        public int id;

        @SerializedName("media")
        public Media media;

        @SerializedName("text_entities")
        public List<Entity> text_entities;

        @SerializedName("type")
        public String type;
    }

    public static class Media {

        @SerializedName("contact_information")
        public ContactInformation contact;

        @SerializedName("duration")
        public int duration;

        @SerializedName("file_name")
        public String fileName;

        @SerializedName("file")
        public String filePathRelative;

        @SerializedName("game_description")
        public String gameDescription;

        @SerializedName("game_short_name")
        public String gameShortName;

        @SerializedName("game_title")
        public String gameTitle;

        @SerializedName("giveaway_information")
        public GiveawayInformation giveawayInformation;

        @SerializedName("giveaway_results")
        public GiveawayResults giveawayResults;

        @SerializedName("height")
        public int height;

        @SerializedName("invoice_information")
        public InvoiceInformation invoice;

        @SerializedName("location_information")
        public LocationInformation location;

        @SerializedName("media_type")
        public String mediaType;

        @SerializedName("mimeType")
        public String mimeType;

        @SerializedName("paid_stars_amount")
        public long paidStarsAmount;

        @SerializedName("performer")
        public String performer;

        @SerializedName("photo")
        public String photoPathRelative;

        @SerializedName("poll")
        public Poll poll;

        @SerializedName("serializedSticker")
        public String serializedSticker;

        @SerializedName("size")
        public int size;

        @SerializedName("skipReason")
        public String skipReason;

        @SerializedName("media_spoiler")
        public boolean spoiler;

        @SerializedName("title")
        public String title;

        @SerializedName("ttl")
        public int ttl;

        @SerializedName("address")
        public String venueAddress;

        @SerializedName("place_name")
        public String venueTitle;

        @SerializedName("width")
        public int width;
    }

    public ExportMapper(int currentAccount, String path, ChatInfo chatInfo) {
        this.currentAccount = currentAccount;
        this.path = path;
        this.chatInfo = chatInfo;
    }

    private String getExportRoot() {
        return path.substring(0, path.indexOf("/chats/"));
    }

    private Pair<String, ArrayList<TLRPC.MessageEntity>> getMessageFromEntities(List<Entity> entities) {
        StringBuilder text = new StringBuilder();
        ArrayList<TLRPC.MessageEntity> result = new ArrayList<>();
        if (entities == null) {
            return new Pair<>("", new ArrayList<>());
        }
        int offset = 0;
        for (Entity entity : entities) {
            text.append(entity.text);
            if (entity.type == null) {
                continue;
            }
            TLRPC.MessageEntity messageEntity;
            switch (entity.type) {
                case "spoiler":
                    messageEntity = new TLRPC.TL_messageEntitySpoiler();
                    break;
                case "bank_card":
                    messageEntity = new TLRPC.TL_messageEntityBankCard();
                    break;
                case "italic":
                    messageEntity = new TLRPC.TL_messageEntityItalic();
                    break;
                case "text_link": {
                    TLRPC.TL_messageEntityTextUrl textUrl = new TLRPC.TL_messageEntityTextUrl();
                    textUrl.url = entity.additional;
                    messageEntity = textUrl;
                    break;
                }
                case "underline":
                    messageEntity = new TLRPC.TL_messageEntityUnderline();
                    break;
                case "strikethrough":
                    messageEntity = new TLRPC.TL_messageEntityStrike();
                    break;
                case "bot_command":
                    messageEntity = new TLRPC.TL_messageEntityBotCommand();
                    break;
                case "pre": {
                    TLRPC.TL_messageEntityPre pre = new TLRPC.TL_messageEntityPre();
                    pre.language = entity.additional;
                    messageEntity = pre;
                    break;
                }
                case "bold":
                    messageEntity = new TLRPC.TL_messageEntityBold();
                    break;
                case "code":
                    messageEntity = new TLRPC.TL_messageEntityCode();
                    break;
                case "link":
                    messageEntity = new TLRPC.TL_messageEntityUrl();
                    break;
                case "email":
                    messageEntity = new TLRPC.TL_messageEntityEmail();
                    break;
                case "phone":
                    messageEntity = new TLRPC.TL_messageEntityPhone();
                    break;
                case "cashtag":
                    messageEntity = new TLRPC.TL_messageEntityCashtag();
                    break;
                case "hashtag":
                    messageEntity = new TLRPC.TL_messageEntityHashtag();
                    break;
                case "custom_emoji": {
                    TLRPC.TL_messageEntityCustomEmoji customEmoji = new TLRPC.TL_messageEntityCustomEmoji();
                    customEmoji.document_id = Utilities.parseLong(entity.additional);
                    messageEntity = customEmoji;
                    break;
                }
                case "mention":
                    messageEntity = new TLRPC.TL_messageEntityMention();
                    break;
                case "blockquote": {
                    TLRPC.TL_messageEntityBlockquote blockquote = new TLRPC.TL_messageEntityBlockquote();
                    blockquote.collapsed = "true".equals(entity.additional);
                    messageEntity = blockquote;
                    break;
                }
                case "mention_name": {
                    TLRPC.TL_messageEntityMentionName mentionName = new TLRPC.TL_messageEntityMentionName();
                    mentionName.user_id = Utilities.parseLong(entity.additional);
                    messageEntity = mentionName;
                    break;
                }
                default:
                    messageEntity = new TLRPC.TL_messageEntityUnknown();
                    break;
            }
            messageEntity.offset = offset;
            messageEntity.length = entity.text.length();
            result.add(messageEntity);
            offset += entity.text.length();
        }
        return new Pair<>(text.toString(), result);
    }

    private TLRPC.MessageMedia mapMedia(JsonMessage jsonMessage) {
        Utilities.Callback0Return<TLRPC.MessageMedia> documentMedia = () -> mapDocumentMedia(jsonMessage);
        Media media = jsonMessage.media;
        if (media.mediaType == null) {
            return documentMedia.run();
        }
        switch (media.mediaType) {
            case "sticker":
            case "video_file":
            case "video_message":
            case "voice_message":
            case "animation":
            case "audio_file": {
                if (!media.mediaType.equals("sticker")) {
                    return documentMedia.run();
                }
                byte[] bytes = Base64.decode(media.serializedSticker, Base64.DEFAULT);
                NativeByteBuffer buffer = null;
                try {
                    buffer = new NativeByteBuffer(bytes.length);
                    buffer.buffer.put(bytes);
                    buffer.rewind();
                    return TLRPC.MessageMedia.TLdeserialize(buffer, buffer.readInt32(false), false);
                } catch (Exception e) {
                    FileLog.e("Export: failed to deserialize sticker: ", e);
                    return null;
                } finally {
                    if (buffer != null) {
                        buffer.reuse();
                    }
                }
            }
            case "paidMedia": {
                TLRPC.TL_messageMediaPaidMedia paidMedia = new TLRPC.TL_messageMediaPaidMedia();
                paidMedia.stars_amount = media.paidStarsAmount;
                return paidMedia;
            }
            case "giveawayResults": {
                TLRPC.TL_messageMediaGiveawayResults giveawayResults = new TLRPC.TL_messageMediaGiveawayResults();
                GiveawayResults results = media.giveawayResults;
                giveawayResults.channel_id = Utilities.parseLong(results.channel);
                giveawayResults.winners = results.winners.stream()
                        .map(winner -> Utilities.parseLong(winner))
                        .collect(Collectors.toCollection(ArrayList::new));
                giveawayResults.prize_description = results.additionalPrize;
                giveawayResults.until_date = Utilities.parseInt(results.untilDate);
                giveawayResults.launch_msg_id = Utilities.parseInt(results.launchMessageId);
                giveawayResults.additional_peers_count = Utilities.parseInt(results.additionalPeersCount);
                giveawayResults.winners_count = Utilities.parseInt(results.winnersCount);
                giveawayResults.unclaimed_count = Utilities.parseInt(results.unclaimedCount);
                giveawayResults.months = Utilities.parseInt(results.months);
                giveawayResults.stars = Utilities.parseInt(results.stars);
                giveawayResults.refunded = results.isRefunded;
                giveawayResults.only_new_subscribers = results.onlyNewSubscribers;
                return giveawayResults;
            }
            case "game": {
                TLRPC.TL_messageMediaGame game = new TLRPC.TL_messageMediaGame();
                game.game = new TLRPC.TL_game();
                game.game.title = media.gameTitle;
                game.game.description = media.gameDescription;
                game.game.short_name = media.gameShortName;
                return game;
            }
            case "poll": {
                TLRPC.TL_messageMediaPoll mediaPoll = new TLRPC.TL_messageMediaPoll();
                TLRPC.TL_poll poll = new TLRPC.TL_poll();
                poll.question.text = media.poll.question;
                poll.closed = media.poll.closed;
                for (Answer answer : media.poll.answers) {
                    TLRPC.TL_pollAnswer pollAnswer = new TLRPC.TL_pollAnswer();
                    pollAnswer.text.text = answer.text;
                    poll.answers.add(pollAnswer);
                }
                TLRPC.TL_pollResults pollResults = new TLRPC.TL_pollResults();
                pollResults.total_voters = Utilities.parseInt(media.poll.totalVotes);
                mediaPoll.poll = poll;
                mediaPoll.results = pollResults;
                return mediaPoll;
            }
            case "photo": {
                TLRPC.TL_messageMediaPhoto photo = new TLRPC.TL_messageMediaPhoto();
                photo.spoiler = media.spoiler;
                photo.ttl_seconds = media.ttl;
                if (media.photoPathRelative != null) {
                    photo.attachPath = getExportRoot() + "/" + media.photoPathRelative;
                }
                photo.photo = new TLRPC.TL_photo();
                TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                photoSize.w = media.width;
                photoSize.h = media.height;
                photoSize.type = "y";
                photoSize.location = new ExportFileLocation(path);
                photoSize.size = Utilities.parseInt((media.size / 1024) + "");
                photo.photo.sizes.add(photoSize);
                return photo;
            }
            case "venue": {
                TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
                venue.title = media.venueTitle;
                venue.address = media.venueAddress;
                venue.geo = new TLRPC.TL_geoPoint();
                venue.geo._long = Utilities.parseLong(media.location.longitude);
                venue.geo.lat = Utilities.parseLong(media.location.latitude);
                return venue;
            }
            case "contact": {
                TLRPC.TL_messageMediaContact contact = new TLRPC.TL_messageMediaContact();
                ContactInformation info = media.contact;
                contact.phone_number = info.phoneNumber;
                contact.first_name = info.firstName;
                contact.last_name = info.lastName;
                if (info.vcardRelativePath != null) {
                    contact.vcard = getExportRoot() + "/" + info.vcardRelativePath;
                }
                return contact;
            }
            case "geopoint": {
                TLRPC.TL_messageMediaGeo geo = new TLRPC.TL_messageMediaGeo();
                geo.geo = new TLRPC.TL_geoPoint();
                geo.geo._long = Utilities.parseLong(media.location.longitude);
                geo.geo.lat = Utilities.parseLong(media.location.latitude);
                geo.ttl_seconds = media.ttl;
                return geo;
            }
            case "invoice": {
                TLRPC.TL_messageMediaInvoice invoice = new TLRPC.TL_messageMediaInvoice();
                InvoiceInformation info = media.invoice;
                invoice.title = info.title;
                invoice.description = info.description;
                invoice.total_amount = Utilities.parseLong(info.amount);
                invoice.currency = info.currency;
                invoice.receipt_msg_id = Utilities.parseInt(info.receiptMsgId);
                return invoice;
            }
            case "giveawayStart": {
                TLRPC.TL_messageMediaGiveaway giveaway = new TLRPC.TL_messageMediaGiveaway();
                GiveawayInformation info = media.giveawayInformation;
                giveaway.quantity = Utilities.parseInt(info.quantity);
                giveaway.months = Utilities.parseInt(info.months);
                giveaway.until_date = Utilities.parseInt(info.until_date);
                giveaway.channels = new ArrayList<>(info.channels);
                giveaway.countries_iso2 = new ArrayList<>(info.countries);
                giveaway.prize_description = info.additionalPrize;
                giveaway.stars = Utilities.parseInt(info.stars);
                giveaway.only_new_subscribers = info.onlyNew;
                return giveaway;
            }
            default:
                return new TLRPC.TL_messageMediaUnsupported();
        }
    }

    private TLRPC.TL_messageMediaDocument mapDocumentMedia(JsonMessage jsonMessage) {
        Media media = jsonMessage.media;
        TLRPC.TL_messageMediaDocument mediaDocument = new TLRPC.TL_messageMediaDocument();
        mediaDocument.flags = 1;
        mediaDocument.spoiler = media.spoiler;
        mediaDocument.ttl_seconds = media.ttl;
        TLRPC.TL_document document = new TLRPC.TL_document();
        mediaDocument.document = document;
        document.date = jsonMessage.date;
        document.size = media.size;
        document.mime_type = media.mimeType;
        document.file_name = media.fileName;
        document.file_name_fixed = media.fileName;
        String relativePath = media.filePathRelative;
        String localPath = getExportRoot() + "/" + relativePath;
        document.localPath = localPath;
        if (media.mediaType == null) {
            return mediaDocument;
        }
        TLRPC.DocumentAttribute attribute = null;
        switch (media.mediaType) {
            case "video_file":
            case "video_message": {
                TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                attributeVideo.round_message = media.mediaType.equals("video_message");
                attributeVideo.w = media.width;
                attributeVideo.h = media.height;
                attributeVideo.duration = media.duration;
                if (relativePath != null) {
                    SendMessagesHelper.fillVideoAttribute(localPath, attributeVideo, null);
                    document.thumbs.add(ImageLoader.scaleAndSaveImage(SendMessagesHelper.createVideoThumbnail(localPath, 1), 320, 320, 90, true));
                }
                attribute = attributeVideo;
                break;
            }
            case "voice_message":
            case "audio_file": {
                TLRPC.TL_documentAttributeAudio attributeAudio = new TLRPC.TL_documentAttributeAudio();
                attributeAudio.duration = media.duration;
                attributeAudio.voice = media.mediaType.equals("voice_message");
                if (media.mediaType.equals("audio_file")) {
                    attributeAudio.performer = media.performer;
                    attributeAudio.title = media.title;
                }
                attribute = attributeAudio;
                break;
            }
            case "animation":
                attribute = new TLRPC.TL_documentAttributeAnimated();
                break;
        }
        if (attribute != null) {
            document.attributes.add(attribute);
        }
        return mediaDocument;
    }

    private TLRPC.TL_messageService mapService(JsonMessage jsonMessage) {
        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
        message.id = jsonMessage.id;
        message.date = jsonMessage.date;
        Action action = jsonMessage.action;
        String type = action.actionType;
        long actorId = TextUtils.isEmpty(action.actor_id) ? 0 : Utilities.parseLong(action.actor_id.substring(4));
        if ("create_group".equals(type)) {
            TLRPC.TL_messageActionChatCreate chatCreate = new TLRPC.TL_messageActionChatCreate();
            message.action = chatCreate;
            chatCreate.title = action.title;
            chatCreate.users = new ArrayList<>(action.members);
        } else if ("edit_group_title".equals(type)) {
            TLRPC.TL_messageActionChatEditTitle editTitle = new TLRPC.TL_messageActionChatEditTitle();
            message.action = editTitle;
            editTitle.title = action.title;
        } else if ("edit_group_photo".equals(type)) {
            TLRPC.TL_messageActionChatEditPhoto editPhoto = new TLRPC.TL_messageActionChatEditPhoto();
            message.action = editPhoto;
            editPhoto.photo = new TLRPC.TL_photo();
        } else if ("delete_group_photo".equals(type)) {
            TLRPC.TL_messageActionChatDeletePhoto deletePhoto = new TLRPC.TL_messageActionChatDeletePhoto();
            message.action = deletePhoto;
            deletePhoto.user_id = Utilities.parseLong(action.actor);
        } else if ("invite_members".equals(type)) {
            TLRPC.TL_messageActionChatAddUser addUser = new TLRPC.TL_messageActionChatAddUser();
            message.action = addUser;
            addUser.users = new ArrayList<>(action.members);
        } else if ("remove_members".equals(type)) {
            TLRPC.TL_messageActionChatDeleteUser deleteUser = new TLRPC.TL_messageActionChatDeleteUser();
            message.action = deleteUser;
            deleteUser.user_id = actorId;
        } else if ("join_group_by_link".equals(type)) {
            TLRPC.TL_messageActionChatJoinedByLink joinedByLink = new TLRPC.TL_messageActionChatJoinedByLink();
            message.action = joinedByLink;
            joinedByLink.user_id = actorId;
            joinedByLink.inviter_id = action.inviterId;
        } else if ("create_channel".equals(type)) {
            TLRPC.TL_messageActionChannelCreate channelCreate = new TLRPC.TL_messageActionChannelCreate();
            message.action = channelCreate;
            channelCreate.user_id = actorId;
            channelCreate.title = action.title;
        } else if ("migrate_to_supergroup".equals(type)) {
            message.action = new TLRPC.TL_messageActionChatMigrateTo();
        } else if ("migrate_from_group".equals(type)) {
            TLRPC.TL_messageActionChannelMigrateFrom migrateFrom = new TLRPC.TL_messageActionChannelMigrateFrom();
            message.action = migrateFrom;
            migrateFrom.title = action.title;
        } else if ("pin_message".equals(type)) {
            message.action = new TLRPC.TL_messageActionPinMessage();
        } else if ("clear_history".equals(type)) {
            message.action = new TLRPC.TL_messageActionHistoryClear();
        } else if ("score_in_game".equals(type)) {
            TLRPC.TL_messageActionGameScore gameScore = new TLRPC.TL_messageActionGameScore();
            message.action = gameScore;
            gameScore.score = action.score;
            gameScore.game_id = action.game_message_id;
        } else if ("send_payment".equals(type)) {
            TLRPC.TL_messageActionPaymentSent paymentSent = new TLRPC.TL_messageActionPaymentSent();
            message.action = paymentSent;
            paymentSent.flags = "used".equals(action.recurring) ? 8 : 4;
            paymentSent.total_amount = action.amount;
            paymentSent.currency = action.currency;
            return message;
        } else if ("phone_call".equals(type)) {
            TLRPC.TL_messageActionPhoneCall phoneCall = new TLRPC.TL_messageActionPhoneCall();
            message.action = phoneCall;
            phoneCall.duration = action.duration;
            if ("hangup".equals(action.discard_reason)) {
                phoneCall.reason = new TLRPC.TL_phoneCallDiscardReasonHangup();
            } else if ("busy".equals(action.discard_reason)) {
                phoneCall.reason = new TLRPC.TL_phoneCallDiscardReasonBusy();
            } else if ("missed".equals(action.discard_reason)) {
                phoneCall.reason = new TLRPC.TL_phoneCallDiscardReasonMissed();
            } else if ("disconnect".equals(action.discard_reason)) {
                phoneCall.reason = new TLRPC.TL_phoneCallDiscardReasonDisconnect();
            }
        } else if ("take_screenshot".equals(type)) {
            message.action = new TLRPC.TL_messageActionScreenshotTaken();
        } else if (!TextUtils.isEmpty(action.customAction)) {
            TLRPC.TL_messageActionCustomAction customAction = new TLRPC.TL_messageActionCustomAction();
            message.action = customAction;
            customAction.message = action.customAction;
        } else if ("attach_menu_bot_allowed".equals(type) || "web_app_bot_allowed".equals(type) || "allow_sending_messages".equals(type)) {
            TLRPC.TL_messageActionBotAllowed botAllowed = new TLRPC.TL_messageActionBotAllowed();
            message.action = botAllowed;
            if ("attach_menu_bot_allowed".equals(type)) {
                botAllowed.attach_menu = true;
            } else if ("web_app_bot_allowed".equals(type)) {
                botAllowed.from_request = true;
            } else if ("allow_sending_messages".equals(type)) {
                botAllowed.app = new TLRPC.TL_botApp();
                botAllowed.app.id = action.reason_app_id;
                botAllowed.app.title = action.reason_app_name;
            } else {
                botAllowed.domain = action.reason_domain;
            }
            return message;
        } else if ("send_passport_values".equals(type)) {
            TLRPC.TL_messageActionSecureValuesSent secureValuesSent = new TLRPC.TL_messageActionSecureValuesSent();
            message.action = secureValuesSent;
            if (action.values != null) {
                ArrayList<TLRPC.SecureValueType> types = new ArrayList<>();
                for (String value : action.values) {
                    if (value == null) {
                        continue;
                    }
                    switch (value) {
                        case "bank_statement":
                            types.add(new TLRPC.TL_secureValueTypeBankStatement());
                            break;
                        case "passport_registration":
                            types.add(new TLRPC.TL_secureValueTypePassportRegistration());
                            break;
                        case "rental_agreement":
                            types.add(new TLRPC.TL_secureValueTypeRentalAgreement());
                            break;
                        case "phone_number":
                            types.add(new TLRPC.TL_secureValueTypePhone());
                            break;
                        case "email":
                            types.add(new TLRPC.TL_secureValueTypeEmail());
                            break;
                        case "address_information":
                            types.add(new TLRPC.TL_secureValueTypeAddress());
                            break;
                        case "temporary_registration":
                            types.add(new TLRPC.TL_secureValueTypeTemporaryRegistration());
                            break;
                        case "identity_card":
                            types.add(new TLRPC.TL_secureValueTypeIdentityCard());
                            break;
                        case "utility_bill":
                            types.add(new TLRPC.TL_secureValueTypeUtilityBill());
                            break;
                        case "internal_passport":
                            types.add(new TLRPC.TL_secureValueTypeInternalPassport());
                            break;
                        case "personal_details":
                            types.add(new TLRPC.TL_secureValueTypePersonalDetails());
                            break;
                        case "driver_license":
                            types.add(new TLRPC.TL_secureValueTypeDriverLicense());
                            break;
                        case "passport":
                            types.add(new TLRPC.TL_secureValueTypePassport());
                            break;
                    }
                }
                secureValuesSent.types = types;
            }
        } else if ("joined_telegram".equals(type)) {
            message.action = new TLRPC.TL_messageActionContactSignUp();
        } else if ("proximity_reached".equals(type)) {
            TLRPC.TL_messageActionGeoProximityReached proximityReached = new TLRPC.TL_messageActionGeoProximityReached();
            message.action = proximityReached;
            proximityReached.from_id = MessagesController.getInstance(currentAccount).getPeer(action.from_id);
            proximityReached.to_id = MessagesController.getInstance(currentAccount).getPeer(action.to_id);
            proximityReached.distance = action.distance;
        } else if ("requested_phone_number".equals(type)) {
            message.action = new TLRPC.TL_messageActionPhoneNumberRequest();
        } else if ("group_call".equals(type)) {
            TLRPC.TL_messageActionGroupCall groupCall = new TLRPC.TL_messageActionGroupCall();
            message.action = groupCall;
            groupCall.duration = action.duration;
        } else if ("invite_to_group_call".equals(type)) {
            TLRPC.TL_messageActionInviteToGroupCall inviteToGroupCall = new TLRPC.TL_messageActionInviteToGroupCall();
            message.action = inviteToGroupCall;
            inviteToGroupCall.duration = action.duration;
            ArrayList<Long> users = new ArrayList<>();
            for (String value : action.values) {
                users.add(Utilities.parseLong(value));
            }
            inviteToGroupCall.users = users;
        } else if ("set_messages_ttl".equals(type)) {
            TLRPC.TL_messageActionSetMessagesTTL setMessagesTTL = new TLRPC.TL_messageActionSetMessagesTTL();
            message.action = setMessagesTTL;
            setMessagesTTL.ttl = action.period;
        } else if ("group_call_scheduled".equals(type)) {
            TLRPC.TL_messageActionGroupCallScheduled groupCallScheduled = new TLRPC.TL_messageActionGroupCallScheduled();
            message.action = groupCallScheduled;
            groupCallScheduled.schedule_date = action.schedule_date;
        } else if ("edit_chat_theme".equals(type)) {
            TLRPC.TL_messageActionSetChatTheme setChatTheme = new TLRPC.TL_messageActionSetChatTheme();
            message.action = setChatTheme;
            if (!TextUtils.isEmpty(action.emotion)) {
                ((TLRPC.TL_chatTheme) setChatTheme.theme).emoticon = action.emotion;
            }
        } else if ("join_group_by_request".equals(type)) {
            message.action = new TLRPC.TL_messageActionChatJoinedByRequest();
        } else if ("send_webview_data".equals(type)) {
            TLRPC.TL_messageActionWebViewDataSent webViewDataSent = new TLRPC.TL_messageActionWebViewDataSent();
            message.action = webViewDataSent;
            webViewDataSent.text = action.text;
            return message;
        } else if ("send_premium_gift".equals(type)) {
            TLRPC.TL_messageActionGiftPremium giftPremium = new TLRPC.TL_messageActionGiftPremium();
            message.action = giftPremium;
            giftPremium.amount = action.amount;
            giftPremium.currency = action.currency;
            giftPremium.months = action.months;
        } else if ("topic_created".equals(type)) {
            TLRPC.TL_messageActionTopicCreate topicCreate = new TLRPC.TL_messageActionTopicCreate();
            message.action = topicCreate;
            topicCreate.title = action.title;
        } else if ("topic_edit".equals(type)) {
            TLRPC.TL_messageActionTopicEdit topicEdit = new TLRPC.TL_messageActionTopicEdit();
            message.action = topicEdit;
            topicEdit.title = action.title;
            topicEdit.icon_emoji_id = action.new_icon_emoji_id;
        } else if ("suggest_profile_photo".equals(type)) {
            TLRPC.TL_messageActionSuggestProfilePhoto suggestProfilePhoto = new TLRPC.TL_messageActionSuggestProfilePhoto();
            message.action = suggestProfilePhoto;
            suggestProfilePhoto.title = action.title;
            suggestProfilePhoto.photo = new TLRPC.TL_photo();
        } else if ("requested_peer".equals(type)) {
            TLRPC.TL_messageActionRequestedPeer requestedPeer = new TLRPC.TL_messageActionRequestedPeer();
            message.action = requestedPeer;
            requestedPeer.button_id = action.button_id;
            ArrayList<TLRPC.Peer> peers = new ArrayList<>();
            for (String peer : action.peers) {
                peers.add(MessagesController.getInstance(currentAccount).getPeer(Utilities.parseLong(peer)));
            }
            requestedPeer.peers = peers;
        } else if ("gift_code_prize".equals(type)) {
            TLRPC.TL_messageActionGiftCode giftCode = new TLRPC.TL_messageActionGiftCode();
            message.action = giftCode;
            giftCode.slug = action.gift_code;
            giftCode.boost_peer = MessagesController.getInstance(currentAccount).getPeer(action.boost_peer_id);
            giftCode.unclaimed = action.is_unclaimed;
            giftCode.via_giveaway = action.via_giveaway;
            giftCode.months = action.months;
            return message;
        } else if ("giveaway_launch".equals(type)) {
            message.action = new TLRPC.TL_messageActionGiveawayLaunch();
            return message;
        } else if ("giveaway_results".equals(type)) {
            TLRPC.TL_messageActionGiveawayResults giveawayResults = new TLRPC.TL_messageActionGiveawayResults();
            message.action = giveawayResults;
            giveawayResults.winners_count = action.winners_count;
            giveawayResults.unclaimed_count = action.unclaimed_count;
            giveawayResults.stars = action.stars_boolean;
            return message;
        } else if ("set_same_chat_wallpaper".equals(type) || "set_chat_wallpaper".equals(type)) {
            TLRPC.TL_messageActionSetChatWallPaper setChatWallPaper = new TLRPC.TL_messageActionSetChatWallPaper();
            message.action = setChatWallPaper;
            setChatWallPaper.same = "set_same_chat_wallpaper".equals(type);
        } else if ("boost_apply".equals(type)) {
            TLRPC.TL_messageActionBoostApply boostApply = new TLRPC.TL_messageActionBoostApply();
            message.action = boostApply;
            boostApply.boosts = action.boosts;
        } else if ("refunded_payment".equals(type)) {
            TLRPC.TL_messageActionPaymentRefunded paymentRefunded = new TLRPC.TL_messageActionPaymentRefunded();
            message.action = paymentRefunded;
            paymentRefunded.amount = action.amount;
            paymentRefunded.currency = action.currency;
            paymentRefunded.peer = MessagesController.getInstance(currentAccount).getPeer(action.peer);
            paymentRefunded.charge = new TLRPC.TL_paymentCharge();
            paymentRefunded.charge.id = action.charge_id;
            return message;
        } else if ("send_stars_gift".equals(type)) {
            TLRPC.TL_messageActionGiftStars giftStars = new TLRPC.TL_messageActionGiftStars();
            message.action = giftStars;
            giftStars.stars = action.stars;
            giftStars.amount = action.amount;
            giftStars.currency = action.currency;
        } else if ("stars_prize".equals(type)) {
            TLRPC.TL_messageActionPrizeStars prizeStars = new TLRPC.TL_messageActionPrizeStars();
            message.action = prizeStars;
            prizeStars.amount = action.stars;
            prizeStars.boost_peer = MessagesController.getInstance(currentAccount).getPeer(action.boost_peer_id);
            prizeStars.unclaimed = action.is_unclaimed;
            prizeStars.giveaway_msg_id = action.giveaway_msg_id;
            prizeStars.transaction_id = action.transaction_id;
        } else if ("send_star_gift".equals(type)) {
            TLRPC.TL_messageActionStarGift starGift = new TLRPC.TL_messageActionStarGift();
            message.action = starGift;
            Pair<String, ArrayList<TLRPC.MessageEntity>> giftText = getMessageFromEntities(action.giftText);
            starGift.message = new TLRPC.TL_textWithEntities();
            starGift.message.text = giftText.first;
            starGift.message.entities = giftText.second;
            TL_stars.TL_starGift gift = new TL_stars.TL_starGift();
            starGift.gift = gift;
            gift.id = action.gift_id;
            gift.sticker = new TLRPC.TL_document();
            starGift.convert_stars = action.stars;
        }
        message.from_id = MessagesController.getInstance(UserConfig.selectedAccount).getPeer(actorId);
        message.peer_id = MessagesController.getInstance(UserConfig.selectedAccount).getPeer(actorId);
        return message;
    }

    public ArrayList<MessageObject> mapMessages(JsonMessage[] jsonMessages) {
        ArrayList<MessageObject> result = new ArrayList<>();
        for (int i = jsonMessages.length - 1; i >= 0; i--) {
            JsonMessage jsonMessage = jsonMessages[i];
            if (jsonMessage == null) {
                continue;
            }
            if ("service".equals(jsonMessage.type)) {
                result.add(new MessageObject(currentAccount, mapService(jsonMessage), true, true));
                continue;
            }
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = jsonMessage.id;
            message.realId = jsonMessage.id;
            message.date = jsonMessage.date;
            TLRPC.TL_peerUser fromId = new TLRPC.TL_peerUser();
            if (chatInfo.type != null && chatInfo.type.contains("bot_chat")) {
                fromId.user_id = chatInfo.id;
            } else if (jsonMessage.from_id != null) {
                fromId.user_id = Utilities.parseLong(jsonMessage.from_id.substring(4));
            }
            message.from_id = fromId;
            message.peer_id = MessagesController.getInstance(UserConfig.selectedAccount).getPeer(fromId.user_id);
            message.out = fromId.user_id == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;
            message.dialog_id = fromId.user_id;
            Pair<String, ArrayList<TLRPC.MessageEntity>> text = getMessageFromEntities(jsonMessage.text_entities);
            message.message = text.first;
            message.entities = text.second;
            if (jsonMessage.media != null) {
                message.media = mapMedia(jsonMessage);
                if (jsonMessage.media.skipReason != null) {
                    message.message += "\n\n" + jsonMessage.media.skipReason;
                }
            }
            result.add(new MessageObject(currentAccount, message, true, false));
        }
        return result;
    }

    public static class ContactInformation {

        @SerializedName("first_name")
        public String firstName;

        @SerializedName("last_name")
        public String lastName;

        @SerializedName("phone_number")
        public String phoneNumber;

        @SerializedName("contact_vcard")
        public String vcardRelativePath;

        private ContactInformation() {
        }
    }

    public static class LocationInformation {

        @SerializedName("latitude")
        public String latitude;

        @SerializedName("longitude")
        public String longitude;

        private LocationInformation() {
        }
    }

    public static class InvoiceInformation {

        @SerializedName("amount")
        public String amount;

        @SerializedName("currency")
        public String currency;

        @SerializedName("description")
        public String description;

        @SerializedName("receipt_message_id")
        public String receiptMsgId;

        @SerializedName("title")
        public String title;

        private InvoiceInformation() {
        }
    }

    public static class Poll {

        @SerializedName("answers")
        public List<Answer> answers;

        @SerializedName("closed")
        public boolean closed;

        @SerializedName("question")
        public String question;

        @SerializedName("total_voters")
        public String totalVotes;

        private Poll() {
        }
    }

    public static class Answer {

        @SerializedName("chosen")
        public boolean chosen;

        @SerializedName("text")
        public String text;

        @SerializedName("voters")
        public String votersCount;

        private Answer() {
        }
    }

    public static class GiveawayInformation {

        @SerializedName("additional_prize")
        public String additionalPrize;

        @SerializedName("channels")
        public List<Long> channels;

        @SerializedName("countries")
        public List<String> countries;

        @SerializedName("months")
        public String months;

        @SerializedName("is_only_new_subscribers")
        public boolean onlyNew;

        @SerializedName("quantity")
        public String quantity;

        @SerializedName("stars")
        public String stars;

        @SerializedName("until_date")
        public String until_date;

        private GiveawayInformation() {
        }
    }

    public static class GiveawayResults {

        @SerializedName("additional_peers_count")
        public String additionalPeersCount;

        @SerializedName("additional_prize")
        public String additionalPrize;

        @SerializedName("channel")
        public String channel;

        @SerializedName("is_refunded")
        public boolean isRefunded;

        @SerializedName("launch_message_id")
        public String launchMessageId;

        @SerializedName("months")
        public String months;

        @SerializedName("is_only_new_subscribers")
        public boolean onlyNewSubscribers;

        @SerializedName("stars")
        public String stars;

        @SerializedName("unclaimed_count")
        public String unclaimedCount;

        @SerializedName("until_date")
        public String untilDate;

        @SerializedName("winners")
        public List<String> winners;

        @SerializedName("winners_count")
        public String winnersCount;

        private GiveawayResults() {
        }
    }

    public static class ExportFileLocation extends TLRPC.FileLocation {
        public String path;

        public ExportFileLocation(String path) {
            this.path = path;
        }
    }
}
