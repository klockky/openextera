package com.exteragram.messenger.export.output.json;

import android.util.Base64;
import android.util.Pair;

import com.exteragram.messenger.export.api.ApiWrap;
import com.exteragram.messenger.export.api.DataTypesUtils;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.html.HtmlWriter;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class JsonContext {

    protected OutputFile _file;
    public boolean _currentNestingHadItem = false;
    public ArrayList<Boolean> nesting = new ArrayList<>();

    public JsonContext(OutputFile file) {
        _file = file;
    }

    private static void popNesting(JsonContext context) {
        context.nesting.remove(context.nesting.size() - 1);
    }

    private static String wrapPeerId(Long peerId) {
        if (peerId < 0) {
            return SerializeString("chat" + peerId);
        }
        return SerializeString("user" + peerId);
    }

    private static ApiWrap.Peer peerById(HashMap<Long, ApiWrap.Peer> peers, Long peerId) {
        ApiWrap.Peer peer = peers.get(peerId);
        return peer != null ? peer : new ApiWrap.Peer(new ApiWrap.User());
    }

    private static ApiWrap.User userById(HashMap<Long, ApiWrap.Peer> peers, Long userId) {
        ApiWrap.User user = peerById(peers, userId).user;
        return user != null ? user : new ApiWrap.User();
    }

    @SuppressWarnings("unchecked")
    private static Pair<String, ?>[] toPairsArray(ArrayList<Pair<String, ?>> pairs) {
        return pairs.toArray(new Pair[0]);
    }

    public static String SerializeMessage(JsonContext context, ApiWrap.Message message, HashMap<Long, ApiWrap.Peer> peers, String internalLinksDomain) {
        if (message.media.content instanceof ApiWrap.UnsupportedMedia) {
            return SerializeObject(context,
                    new Pair<>("id", message.id),
                    new Pair<>("type", SerializeString("unsupported")));
        }

        final ArrayList<Pair<String, ?>> values = new ArrayList<>();
        values.add(new Pair<>("id", message.id));
        values.add(new Pair<>("type", SerializeString(message.action != null ? "service" : "message")));
        values.add(new Pair<>("date", SerializeString(String.valueOf(message.date))));

        context.nesting.add(true);
        final Utilities.CallbackVoidReturn<String> serialized = () -> {
            popNesting(context);
            return SerializeObject(context, toPairsArray(values));
        };

        final Utilities.Callback2<String, String> push = (key, value) -> {
            if (!value.isEmpty()) {
                values.add(new Pair<>(key, value));
            }
        };
        if (message.edited != 0) {
            push.run("edited", String.valueOf(message.edited));
        }

        final Utilities.Callback2<String, Object> pushBare = (key, value) -> {
            if (value instanceof Boolean) {
                push.run(key, String.valueOf(value));
            } else if (value instanceof Integer) {
                push.run(key, String.valueOf(value));
            } else if (value instanceof Long) {
                push.run(key, wrapPeerId((Long) value));
            } else if (value instanceof String) {
                String string = (String) value;
                if (!string.isEmpty()) {
                    push.run(key, SerializeString(string));
                }
            }
        };
        final Utilities.CallbackReturn<Long, String> wrapPeerName = peerId -> StringAllowNull(peerById(peers, peerId).name());
        final Utilities.Callback<String> pushFrom = label -> {
            if (label == null) {
                label = "from";
            }
            if (message.fromId != 0) {
                push.run(label, wrapPeerName.run(message.fromId));
                pushBare.run(label + "_id", message.fromId);
            }
        };
        final Utilities.Callback<String> pushReplyToMsgId = label -> {
            if (message.replyToMsgId != 0) {
                pushBare.run(label, message.replyToMsgId);
                if (message.replyToPeerId != 0) {
                    pushBare.run("reply_to_peer_id", message.replyToPeerId);
                }
            }
        };

        if (message.media != null && message.media.content != null) {
            values.add(new Pair<>("media", SerializeMessageMedia(message, context, peers, internalLinksDomain)));
        }
        if (message.action != null) {
            values.add(new Pair<>("action", SerializeMessageAction(context, message.action, message, peers)));
        }

        if (message.action == null) {
            pushFrom.run("from");
            pushBare.run("author", message.signature);
            if (message.forwardedFromId != 0) {
                push.run("forwarded_from", wrapPeerName.run(message.forwardedFromId));
            } else if (message.forwardedFromName != null && !message.forwardedFromName.isEmpty()) {
                push.run("forwarded_from", StringAllowNull(message.forwardedFromName));
            }
            if (message.savedFromChatId != 0) {
                push.run("saved_from", wrapPeerName.run(message.savedFromChatId));
            }
            pushReplyToMsgId.run("reply_to_message_id");
            if (message.viaBotId != 0) {
                final String username = userById(peers, message.viaBotId).username;
                if (username != null && !username.isEmpty()) {
                    pushBare.run("via_bot", username);
                }
            }
        }

        push.run("text_entities", SerializeText(context, message.text, true));

        if (message.inlineButtonRows != null && !message.inlineButtonRows.isEmpty()) {
            final Utilities.CallbackReturn<ArrayList<ApiWrap.HistoryMessageMarkupButton>, String> serializeRow = row -> {
                context.nesting.add(false);
                List<String> buttons = row.stream()
                        .map(button -> serializeInlineButton(context, button))
                        .collect(Collectors.toList());
                popNesting(context);
                return SerializeArray(context, new ArrayList<>(buttons));
            };
            context.nesting.add(false);
            List<String> rows = message.inlineButtonRows.stream()
                    .map(serializeRow::run)
                    .collect(Collectors.toList());
            popNesting(context);
            push.run("inline_bot_buttons", SerializeArray(context, new ArrayList<>(rows)));
        }

        if (!message.reactions.isEmpty()) {
            // TODO(openextera): decompile failed, verify - ApiWrap.Reaction is never instantiated in lite,
            //  so R8 stripped its fields together with the rest of this serializer (count, emoji, document_id, recent).
            final Utilities.CallbackReturn<ApiWrap.Reaction, String> serializeReaction = reaction -> {
                context.nesting.add(true);
                try {
                    ArrayList<Pair<String, ?>> pairs = new ArrayList<>();
                    pairs.add(new Pair<>("type", SerializeString(ApiWrap.Reaction.TypeToString(reaction))));
                    return SerializeObject(context, toPairsArray(pairs));
                } finally {
                    popNesting(context);
                }
            };
            context.nesting.add(false);
            List<String> reactions = message.reactions.stream()
                    .map(serializeReaction::run)
                    .collect(Collectors.toList());
            push.run("reactions", SerializeArray(context, new ArrayList<>(reactions)));
            popNesting(context);
        }

        return serialized.run();
    }

    private static String serializeInlineButton(JsonContext context, ApiWrap.HistoryMessageMarkupButton button) {
        ArrayList<Pair<String, ?>> pairs = new ArrayList<>();
        pairs.add(new Pair<>("type", SerializeString(ApiWrap.HistoryMessageMarkupButton.TypeToString(button))));
        if (!button.text().isEmpty()) {
            pairs.add(new Pair<>("text", SerializeString(button.text())));
        }
        if (button.data() != null && button.data().length != 0) {
            if (button.type() == ApiWrap.HistoryMessageMarkupButton.Type.Callback
                    || button.type() == ApiWrap.HistoryMessageMarkupButton.Type.CallbackWithPassword) {
                pairs.add(new Pair<>("dataBase64", SerializeString(ChatUtils.getInstance().getTextFromCallback(button.data()))));
                pairs.add(new Pair<>("data", SerializeString("")));
            } else {
                pairs.add(new Pair<>("data", SerializeString(ChatUtils.getInstance().getTextFromCallback(button.data()))));
            }
        }
        if (button.forwardText() != null && !button.forwardText().isEmpty()) {
            pairs.add(new Pair<>("forward_text", SerializeString(button.forwardText())));
        }
        if (button.buttonId() != 0) {
            pairs.add(new Pair<>("button_id", DataTypesUtils.NumberToString(button.buttonId())));
        }
        return SerializeObject(context, toPairsArray(pairs));
    }

    private static String SerializeMessageMedia(ApiWrap.Message message, JsonContext context, HashMap<Long, ApiWrap.Peer> peers, String internalLinksDomain) {
        final ArrayList<Pair<String, ?>> values = new ArrayList<>();
        final Utilities.Callback2<String, String> push = (key, value) -> {
            if (!value.isEmpty()) {
                values.add(new Pair<>(key, value));
            }
        };
        final Utilities.Callback2<String, Object> pushBare = (key, value) -> {
            if (value instanceof Boolean) {
                push.run(key, String.valueOf(value));
                return;
            }
            if (value instanceof Integer) {
                push.run(key, String.valueOf(value));
                return;
            }
            if (value instanceof Long) {
                push.run(key, String.valueOf(value));
                return;
            }
            if (value instanceof String) {
                String string = (String) value;
                if (!string.isEmpty()) {
                    push.run(key, SerializeString(string));
                    return;
                }
            }
            if (value instanceof TLRPC.Peer) {
                push.run(key, wrapPeerId(MessageObject.getPeerId((TLRPC.Peer) value)));
            }
        };
        final Utilities.Callback3<ApiWrap.File, String, String> pushPath = (file, key, prefix) -> {
            final String pre = prefix.isEmpty() ? "" : prefix + " ";
            final String skipped;
            switch (file.skipReason) {
                case Unavailable:
                    skipped = pre + "(File unavailable, please try again later)";
                    break;
                case FileSize:
                    skipped = pre + "(File exceeds maximum size. Change data exporting settings to download.)";
                    break;
                case FileType:
                    skipped = pre + "(File not included. Change data exporting settings to download.)";
                    break;
                default:
                    skipped = null;
                    break;
            }
            if (skipped != null) {
                pushBare.run("skipReason", skipped);
                pushBare.run("size", file.size);
            } else {
                pushBare.run(key, file.relativePath);
            }
        };
        final Utilities.Callback<ApiWrap.Image> pushPhoto = image -> {
            pushPath.run(image.file, "photo", "");
            pushBare.run("size", image.file.size);
            if (image.width != 0 && image.height != 0) {
                pushBare.run("width", image.width);
                pushBare.run("height", image.height);
            }
        };

        final Object content = message.media.content;
        if (content instanceof HtmlWriter.Photo) {
            final HtmlWriter.Photo photo = (HtmlWriter.Photo) content;
            pushBare.run("media_type", "photo");
            pushPhoto.run(photo.image);
            if (photo.spoilered) {
                pushBare.run("media_spoiler", true);
            }
            if (message.media.ttl != 0) {
                pushBare.run("ttl", message.media.ttl);
            }
        } else if (content instanceof ApiWrap.Document) {
            final ApiWrap.Document document = (ApiWrap.Document) content;
            pushPath.run(document.file, "file", "");
            pushBare.run("file_name", document.name);
            if (document.isSticker) {
                pushBare.run("media_type", "sticker");
                try {
                    NativeByteBuffer buffer = new NativeByteBuffer(document.sticker.getObjectSize());
                    document.sticker.serializeToStream(buffer);
                    buffer.reuse();
                    buffer.buffer.rewind();
                    byte[] bytes = new byte[buffer.buffer.remaining()];
                    buffer.buffer.get(bytes);
                    push.run("serializedSticker", SerializeString(Base64.encodeToString(bytes, Base64.DEFAULT)));
                } catch (Exception ignore) {
                }
            } else if (document.isVideoMessage) {
                pushBare.run("media_type", "video_message");
            } else if (document.isVoiceMessage) {
                pushBare.run("media_type", "voice_message");
            } else if (document.isAnimated) {
                pushBare.run("media_type", "animation");
            } else if (document.isVideoFile) {
                pushBare.run("media_type", "video_file");
            } else if (document.isAudioFile) {
                pushBare.run("media_type", "audio_file");
                pushBare.run("performer", document.songPerformer);
                pushBare.run("title", document.songTitle);
            }
            push.run("mimeType", SerializeString(document.mime));
            if (document.duration != 0) {
                pushBare.run("duration", document.duration);
            }
            if (document.width != 0 && document.height != 0) {
                pushBare.run("width", document.width);
                pushBare.run("height", document.height);
            }
            if (document.spoilered) {
                pushBare.run("media_spoiler", true);
            }
            if (message.media.ttl != 0) {
                pushBare.run("ttl", message.media.ttl);
            }
        } else if (content instanceof ApiWrap.SharedContact) {
            final ApiWrap.SharedContact contact = (ApiWrap.SharedContact) content;
            push.run("mediaType", SerializeString("contact"));
            push.run("contact_information", SerializeObject(context,
                    new Pair<>("first_name", SerializeString(contact.info.firstName)),
                    new Pair<>("last_name", SerializeString(contact.info.lastName)),
                    new Pair<>("phone_number", SerializeString(contact.info.phoneNumber))));
            final ApiWrap.File vcard = contact.vcard;
            if (vcard.content != null && vcard.content.length != 0) {
                if (vcard.skipReason == ApiWrap.File.SkipReason.None) {
                    pushPath.run(vcard, "contact_vcard", "");
                } else {
                    pushPath.run(vcard, "skipReason", "");
                }
                pushBare.run("size", contact.vcard.size);
            }
        } else if (content instanceof ApiWrap.GeoPoint) {
            final ApiWrap.GeoPoint point = (ApiWrap.GeoPoint) content;
            push.run("mediaType", SerializeString("geopoint"));
            push.run("location_information", point.valid
                    ? SerializeObject(context,
                            new Pair<>("latitude", DataTypesUtils.NumberToString((int) point.latitude)),
                            new Pair<>("longitude", DataTypesUtils.NumberToString((int) point.longitude)))
                    : "null");
            if (message.media.ttl != 0) {
                pushBare.run("ttl", message.media.ttl);
            }
        } else if (content instanceof ApiWrap.Venue) {
            final ApiWrap.Venue venue = (ApiWrap.Venue) content;
            push.run("mediaType", SerializeString("venue"));
            pushBare.run("place_name", venue.title);
            pushBare.run("address", venue.address);
            if (venue.point.valid) {
                push.run("location_information", SerializeObject(context,
                        new Pair<>("latitude", DataTypesUtils.NumberToString((int) venue.point.latitude)),
                        new Pair<>("longitude", DataTypesUtils.NumberToString((int) venue.point.longitude))));
            }
        } else if (content instanceof ApiWrap.Game) {
            final ApiWrap.Game game = (ApiWrap.Game) content;
            push.run("mediaType", SerializeString("game"));
            pushBare.run("game_title", game.title);
            pushBare.run("game_description", game.description);
            if (game.botId != 0 && !game.shortName.isEmpty()) {
                final ApiWrap.User bot = userById(peers, game.botId);
                if (bot.isBot && !bot.username.isEmpty()) {
                    pushBare.run("game_short_name", game.shortName);
                }
            }
        } else if (content instanceof ApiWrap.Invoice) {
            final ApiWrap.Invoice invoice = (ApiWrap.Invoice) content;
            push.run("mediaType", SerializeString("invoice"));
            push.run("invoice_information", SerializeObject(context,
                    new Pair<>("title", SerializeString(invoice.title)),
                    new Pair<>("description", SerializeString(invoice.description)),
                    new Pair<>("amount", DataTypesUtils.NumberToString(invoice.amount)),
                    new Pair<>("currency", SerializeString(invoice.currency)),
                    new Pair<>("receipt_message_id", invoice.receiptMsgId != 0 ? DataTypesUtils.NumberToString(invoice.receiptMsgId) : "")));
        } else if (content instanceof ApiWrap.Poll) {
            final ApiWrap.Poll poll = (ApiWrap.Poll) content;
            push.run("mediaType", SerializeString("poll"));
            context.nesting.add(true);
            final String answers = SerializeArray(context, new ArrayList<>(poll.answers.stream().map(answer -> {
                context.nesting.add(false);
                String result = SerializeObject(context,
                        new Pair<>("text", SerializeString(answer.text())),
                        new Pair<>("voters", DataTypesUtils.NumberToString(answer.votes())),
                        new Pair<>("chosen", answer.my() ? "true" : "false"));
                popNesting(context);
                return result;
            }).collect(Collectors.toList())));
            popNesting(context);
            push.run("poll", SerializeObject(context,
                    new Pair<>("question", SerializeString(poll.question)),
                    new Pair<>("closed", String.valueOf(poll.closed)),
                    new Pair<>("total_voters", DataTypesUtils.NumberToString(poll.totalVotes)),
                    new Pair<>("answers", answers)));
        } else if (content instanceof ApiWrap.GiveawayStart) {
            final ApiWrap.GiveawayStart giveaway = (ApiWrap.GiveawayStart) content;
            push.run("mediaType", SerializeString("giveawayStart"));
            context.nesting.add(false);
            final String channels = SerializeArray(context, new ArrayList<>(giveaway.channels.stream()
                    .map(id -> DataTypesUtils.NumberToString((long) id))
                    .collect(Collectors.toList())));
            popNesting(context);
            context.nesting.add(false);
            final String countries = SerializeArray(context, new ArrayList<>(giveaway.countries.stream()
                    .map(JsonContext::SerializeString)
                    .collect(Collectors.toList())));
            popNesting(context);
            push.run("giveaway_information", SerializeObject(context,
                    new Pair<>("quantity", DataTypesUtils.NumberToString(giveaway.quantity)),
                    new Pair<>("months", DataTypesUtils.NumberToString(giveaway.months)),
                    new Pair<>("until_date", DataTypesUtils.NumberToString(giveaway.untilDate)),
                    new Pair<>("channels", channels),
                    new Pair<>("countries", countries),
                    new Pair<>("additional_prize", SerializeString(giveaway.additionalPrize)),
                    new Pair<>("stars", DataTypesUtils.NumberToString(giveaway.credits)),
                    new Pair<>("is_only_new_subscribers", String.valueOf(!giveaway.all))));
        } else if (content instanceof ApiWrap.GiveawayResults) {
            final ApiWrap.GiveawayResults giveaway = (ApiWrap.GiveawayResults) content;
            push.run("mediaType", SerializeString("giveawayResults"));
            context.nesting.add(false);
            final String winners = SerializeArray(context, new ArrayList<>(giveaway.winners.stream()
                    .map(id -> DataTypesUtils.NumberToString((long) id))
                    .collect(Collectors.toList())));
            popNesting(context);
            push.run("giveaway_results", SerializeObject(context,
                    new Pair<>("channel", DataTypesUtils.NumberToString(giveaway.channel)),
                    new Pair<>("winners", winners),
                    new Pair<>("additional_prize", SerializeString(giveaway.additionalPrize)),
                    new Pair<>("until_date", DataTypesUtils.NumberToString(giveaway.untilDate)),
                    new Pair<>("launch_message_id", DataTypesUtils.NumberToString(giveaway.launchId)),
                    new Pair<>("additional_peers_count", DataTypesUtils.NumberToString(giveaway.additionalPeersCount)),
                    new Pair<>("winners_count", DataTypesUtils.NumberToString(giveaway.winnersCount)),
                    new Pair<>("unclaimed_count", DataTypesUtils.NumberToString(giveaway.unclaimedCount)),
                    new Pair<>("months", DataTypesUtils.NumberToString(giveaway.months)),
                    new Pair<>("stars", DataTypesUtils.NumberToString(giveaway.credits)),
                    new Pair<>("is_refunded", String.valueOf(giveaway.refunded)),
                    new Pair<>("is_only_new_subscribers", String.valueOf(!giveaway.all))));
        } else if (content instanceof ApiWrap.PaidMedia) {
            push.run("mediaType", SerializeString("paidMedia"));
            pushBare.run("paid_stars_amount", ((ApiWrap.PaidMedia) content).stars);
        } else if (content instanceof ApiWrap.UnsupportedMedia) {
            FileLog.e("Export: Unsupported message");
        }

        return SerializeObject(context, toPairsArray(values));
    }

    private static String SerializeMessageAction(JsonContext context, TLRPC.MessageAction action, ApiWrap.Message message, HashMap<Long, ApiWrap.Peer> peers) {
        final ArrayList<Pair<String, ?>> values = new ArrayList<>();
        final Utilities.Callback2<String, String> push = (key, value) -> {
            if (!value.isEmpty()) {
                values.add(new Pair<>(key, value));
            }
        };
        final Utilities.Callback2<String, Object> pushBare = (key, value) -> {
            if (value instanceof Boolean) {
                push.run(key, String.valueOf(value));
                return;
            }
            if (value instanceof Integer) {
                push.run(key, String.valueOf(value));
                return;
            }
            if (value instanceof Long) {
                push.run(key, String.valueOf(value));
                return;
            }
            if (value instanceof String) {
                String string = (String) value;
                if (!string.isEmpty()) {
                    push.run(key, SerializeString(string));
                    return;
                }
            }
            if (value instanceof TLRPC.Peer) {
                push.run(key, wrapPeerId(MessageObject.getPeerId((TLRPC.Peer) value)));
            }
        };
        final Utilities.CallbackReturn<Long, String> wrapPeerName = peerId -> StringAllowNull(peerById(peers, peerId).name());
        final Utilities.Callback<String> pushActor = label -> {
            if (label == null) {
                label = "from";
            }
            if (message.fromId != 0) {
                push.run(label, wrapPeerName.run(message.fromId));
                TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
                peer.user_id = message.fromId;
                pushBare.run(label + "_id", peer);
            }
        };
        final Utilities.Callback<String> pushReplyToMsgId = label -> {
            if (message.replyToMsgId != 0) {
                pushBare.run(label, message.replyToMsgId);
                if (message.replyToPeerId != 0) {
                    pushBare.run("reply_to_peer_id", message.replyToPeerId);
                }
            }
        };
        final Utilities.Callback2<ArrayList<Long>, String> pushUserNames = (userIds, label) -> {
            ArrayList<String> names = new ArrayList<>();
            for (Long userId : userIds) {
                names.add(StringAllowNull(userById(peers, userId).name()));
            }
            push.run(label, SerializeArray(context, names));
        };
        final Utilities.Callback3<ApiWrap.File, String, String> pushPath = (file, key, prefix) -> {
            final String pre = prefix.isEmpty() ? "" : prefix + " ";
            String path = "";
            switch (file.skipReason) {
                case Unavailable:
                    path = pre + "(File unavailable, please try again later)";
                    break;
                case FileSize:
                    path = pre + "(File exceeds maximum size. Change data exporting settings to download.)";
                    break;
                case FileType:
                    path = pre + "(File not included. Change data exporting settings to download.)";
                    break;
                case None:
                    path = file.relativePath;
                    break;
            }
            pushBare.run(key, path);
        };
        final Utilities.Callback<ApiWrap.Image> pushPhoto = image -> {
            pushPath.run(image.file, "photo", "");
            if (image.width != 0 && image.height != 0) {
                pushBare.run("width", image.width);
                pushBare.run("height", image.height);
            }
        };
        final Utilities.Callback<String> pushAction = type -> pushBare.run("action", type);

        if (action instanceof TLRPC.TL_messageActionChatCreate) {
            final TLRPC.TL_messageActionChatCreate data = (TLRPC.TL_messageActionChatCreate) action;
            pushActor.run("actor");
            pushAction.run("create_group");
            pushBare.run("title", data.title);
            pushUserNames.run(data.users, "members");
        } else if (action instanceof TLRPC.TL_messageActionChatEditTitle) {
            pushActor.run("actor");
            pushAction.run("edit_group_title");
            pushBare.run("title", ((TLRPC.TL_messageActionChatEditTitle) action).title);
        } else if (action instanceof TLRPC.TL_messageActionChatEditPhoto) {
            final HtmlWriter.Photo photo = ((ApiWrap.ActionChatEditPhoto) message.parsedAction).photo();
            pushActor.run("actor");
            pushAction.run("edit_group_photo");
            pushPhoto.run(photo.image);
            if (photo.spoilered) {
                pushBare.run("media_spoiler", true);
            }
        } else if (action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
            pushActor.run("actor");
            pushAction.run("delete_group_photo");
        } else if (action instanceof TLRPC.TL_messageActionChatAddUser) {
            pushActor.run("actor");
            pushAction.run("invite_members");
            pushUserNames.run(((TLRPC.TL_messageActionChatAddUser) action).users, "members");
        } else if (action instanceof TLRPC.TL_messageActionChatDeleteUser) {
            pushActor.run("actor");
            pushAction.run("remove_members");
            pushUserNames.run(new ArrayList<>(Collections.singletonList(((TLRPC.TL_messageActionChatDeleteUser) action).user_id)), "members");
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
            pushActor.run("actor");
            pushAction.run("join_group_by_link");
            push.run("inviter_id", "" + ((TLRPC.TL_messageActionChatJoinedByLink) action).inviter_id);
        } else if (action instanceof TLRPC.TL_messageActionChannelCreate) {
            pushActor.run("actor");
            pushAction.run("create_channel");
            pushBare.run("title", ((TLRPC.TL_messageActionChannelCreate) action).title);
        } else if (action instanceof TLRPC.TL_messageActionChatMigrateTo) {
            pushActor.run("actor");
            pushAction.run("migrate_to_supergroup");
        } else if (action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
            pushActor.run("actor");
            pushAction.run("migrate_from_group");
            pushBare.run("title", ((TLRPC.TL_messageActionChannelMigrateFrom) action).title);
        } else if (action instanceof TLRPC.TL_messageActionPinMessage) {
            pushActor.run("actor");
            pushAction.run("pin_message");
            pushReplyToMsgId.run("message_id");
        } else if (action instanceof TLRPC.TL_messageActionHistoryClear) {
            pushActor.run("actor");
            pushAction.run("clear_history");
        } else if (action instanceof TLRPC.TL_messageActionGameScore) {
            pushActor.run("actor");
            pushAction.run("score_in_game");
            pushReplyToMsgId.run("game_message_id");
            pushBare.run("score", ((TLRPC.TL_messageActionGameScore) action).score);
        } else if (action instanceof TLRPC.TL_messageActionPaymentSent) {
            final TLRPC.TL_messageActionPaymentSent data = (TLRPC.TL_messageActionPaymentSent) action;
            pushAction.run("send_payment");
            pushBare.run("amount", data.total_amount);
            pushBare.run("currency", data.currency);
            pushReplyToMsgId.run("invoice_message_id");
            if (data.recurring_used) {
                pushBare.run("recurring", "used");
            } else if (data.recurring_init) {
                pushBare.run("recurring", "init");
            }
        } else if (action instanceof TLRPC.TL_messageActionPhoneCall) {
            final TLRPC.TL_messageActionPhoneCall data = (TLRPC.TL_messageActionPhoneCall) action;
            pushActor.run("actor");
            pushAction.run("phone_call");
            if (data.duration != 0) {
                pushBare.run("duration_seconds", data.duration);
            }
            final String reason;
            if (data.reason instanceof TLRPC.TL_phoneCallDiscardReasonHangup) {
                reason = "hangup";
            } else if (data.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
                reason = "busy";
            } else if (data.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed) {
                reason = "missed";
            } else if (data.reason instanceof TLRPC.TL_phoneCallDiscardReasonDisconnect) {
                reason = "disconnect";
            } else {
                reason = "";
            }
            pushBare.run("discard_reason", reason);
        } else if (action instanceof TLRPC.TL_messageActionScreenshotTaken) {
            pushActor.run("actor");
            pushAction.run("take_screenshot");
        } else if (action instanceof TLRPC.TL_messageActionCustomAction) {
            pushActor.run("actor");
            pushBare.run("information_text", ((TLRPC.TL_messageActionCustomAction) action).message);
        } else if (action instanceof TLRPC.TL_messageActionBotAllowed) {
            final TLRPC.TL_messageActionBotAllowed data = (TLRPC.TL_messageActionBotAllowed) action;
            if (data.attach_menu) {
                pushAction.run("attach_menu_bot_allowed");
            } else if (data.from_request) {
                pushAction.run("web_app_bot_allowed");
            } else if (data.app.id != 0) {
                pushAction.run("allow_sending_messages");
                pushBare.run("reason_app_id", data.app.id);
                pushBare.run("reason_app_name", data.app.title);
            } else {
                pushAction.run("allow_sending_messages");
                pushBare.run("reason_domain", data.domain);
            }
        } else if (action instanceof TLRPC.TL_messageActionSecureValuesSent) {
            pushAction.run("send_passport_values");
            ArrayList<String> list = new ArrayList<>();
            for (TLRPC.SecureValueType type : ((TLRPC.TL_messageActionSecureValuesSent) action).types) {
                final String name;
                if (type instanceof TLRPC.TL_secureValueTypeAddress) {
                    name = "address_information";
                } else if (type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
                    name = "passport_registration";
                } else if (type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
                    name = "identity_card";
                } else if (type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
                    name = "utility_bill";
                } else if (type instanceof TLRPC.TL_secureValueTypeBankStatement) {
                    name = "bank_statement";
                } else if (type instanceof TLRPC.TL_secureValueTypeEmail) {
                    name = "email";
                } else if (type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                    name = "personal_details";
                } else if (type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
                    name = "temporary_registration";
                } else if (type instanceof TLRPC.TL_secureValueTypePassport) {
                    name = "passport";
                } else if (type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
                    name = "rental_agreement";
                } else if (type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                    name = "driver_license";
                } else if (type instanceof TLRPC.TL_secureValueTypePhone) {
                    name = "phone_number";
                } else if (type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                    name = "internal_passport";
                } else {
                    name = "";
                }
                list.add(SerializeString(name));
            }
            push.run("values", SerializeArray(context, list));
        } else if (action instanceof TLRPC.TL_messageActionContactSignUp) {
            pushActor.run("actor");
            pushAction.run("joined_telegram");
        } else if (action instanceof TLRPC.TL_messageActionGeoProximityReached) {
            final TLRPC.TL_messageActionGeoProximityReached data = (TLRPC.TL_messageActionGeoProximityReached) action;
            pushAction.run("proximity_reached");
            if (MessageObject.getPeerId(data.from_id) != 0) {
                push.run("from", wrapPeerName.run(MessageObject.getPeerId(data.from_id)));
                pushBare.run("from_id", MessageObject.getPeerId(data.from_id));
            }
            if (MessageObject.getPeerId(data.to_id) != 0) {
                push.run("to", wrapPeerName.run(MessageObject.getPeerId(data.to_id)));
                pushBare.run("to_id", MessageObject.getPeerId(data.to_id));
            }
            pushBare.run("distance", data.distance);
        } else if (action instanceof TLRPC.TL_messageActionPhoneNumberRequest) {
            pushActor.run("actor");
            pushAction.run("requested_phone_number");
        } else if (action instanceof TLRPC.TL_messageActionGroupCall) {
            pushActor.run("actor");
            pushAction.run("group_call");
            final int duration = ((TLRPC.TL_messageActionGroupCall) action).duration;
            if (duration != 0) {
                pushBare.run("duration", duration);
            }
        } else if (action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
            final TLRPC.TL_messageActionInviteToGroupCall data = (TLRPC.TL_messageActionInviteToGroupCall) action;
            pushActor.run("actor");
            pushAction.run("invite_to_group_call");
            pushUserNames.run(data.users, "members");
            push.run("values", SerializeArray(context, new ArrayList<>(data.users.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList()))));
        } else if (action instanceof TLRPC.TL_messageActionSetMessagesTTL) {
            pushActor.run("actor");
            pushAction.run("set_messages_ttl");
            pushBare.run("period", ((TLRPC.TL_messageActionSetMessagesTTL) action).period);
        } else if (action instanceof TLRPC.TL_messageActionGroupCallScheduled) {
            pushActor.run("actor");
            pushAction.run("group_call_scheduled");
            pushBare.run("schedule_date", ((TLRPC.TL_messageActionGroupCallScheduled) action).schedule_date);
        } else if (action instanceof TLRPC.TL_messageActionSetChatTheme) {
            pushActor.run("actor");
            pushAction.run("edit_chat_theme");
            final TLRPC.ChatTheme theme = ((TLRPC.TL_messageActionSetChatTheme) action).theme;
            if (theme instanceof TLRPC.TL_chatTheme) {
                final String emoticon = ((TLRPC.TL_chatTheme) theme).emoticon;
                if (emoticon != null && !emoticon.isEmpty()) {
                    pushBare.run("emoticon", emoticon);
                }
            }
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
            pushActor.run("actor");
            pushAction.run("join_group_by_request");
        } else if (action instanceof TLRPC.TL_messageActionWebViewDataSent) {
            pushAction.run("send_webview_data");
            pushBare.run("text", ((TLRPC.TL_messageActionWebViewDataSent) action).text);
        } else if (action instanceof TLRPC.TL_messageActionGiftPremium) {
            final TLRPC.TL_messageActionGiftPremium data = (TLRPC.TL_messageActionGiftPremium) action;
            pushActor.run("actor");
            pushAction.run("send_premium_gift");
            if (data.currency != null && !data.currency.isEmpty()) {
                pushBare.run("amount", data.amount);
                pushBare.run("currency", data.currency);
            }
            if (data.months != 0) {
                pushBare.run("months", data.months);
            }
        } else if (action instanceof TLRPC.TL_messageActionTopicCreate) {
            pushActor.run("actor");
            pushAction.run("topic_created");
            pushBare.run("title", ((TLRPC.TL_messageActionTopicCreate) action).title);
        } else if (action instanceof TLRPC.TL_messageActionTopicEdit) {
            final TLRPC.TL_messageActionTopicEdit data = (TLRPC.TL_messageActionTopicEdit) action;
            pushActor.run("actor");
            pushAction.run("topic_edit");
            if (!data.title.isEmpty()) {
                pushBare.run("title", data.title);
            }
            if (data.icon_emoji_id != 0) {
                pushBare.run("new_icon_emoji_id", data.icon_emoji_id);
            }
        } else if (action instanceof TLRPC.TL_messageActionSuggestProfilePhoto) {
            final HtmlWriter.Photo photo = ((ApiWrap.ActionSuggestProfilePhoto) message.parsedAction).photo();
            pushActor.run("actor");
            pushAction.run("suggest_profile_photo");
            pushPhoto.run(photo.image);
            if (photo.spoilered) {
                pushBare.run("media_spoiler", true);
            }
        } else if (action instanceof TLRPC.TL_messageActionRequestedPeer) {
            final TLRPC.TL_messageActionRequestedPeer data = (TLRPC.TL_messageActionRequestedPeer) action;
            pushActor.run("actor");
            pushAction.run("requested_peer");
            pushBare.run("button_id", data.button_id);
            ArrayList<String> ids = new ArrayList<>();
            for (TLRPC.Peer peer : data.peers) {
                ids.add(String.valueOf(MessageObject.getPeerId(peer)));
            }
            pushBare.run("peers", SerializeArray(context, ids));
        } else if (action instanceof TLRPC.TL_messageActionGiftCode) {
            final TLRPC.TL_messageActionGiftCode data = (TLRPC.TL_messageActionGiftCode) action;
            pushAction.run("gift_code_prize");
            pushBare.run("gift_code", data.slug);
            if (MessageObject.getPeerId(data.boost_peer) != 0) {
                pushBare.run("boost_peer_id", MessageObject.getPeerId(data.boost_peer));
            }
            pushBare.run("months", data.months);
            pushBare.run("is_unclaimed", data.unclaimed);
            pushBare.run("via_giveaway", data.via_giveaway);
        } else if (action instanceof TLRPC.TL_messageActionGiveawayLaunch) {
            pushAction.run("giveaway_launch");
        } else if (action instanceof TLRPC.TL_messageActionGiveawayResults) {
            final TLRPC.TL_messageActionGiveawayResults data = (TLRPC.TL_messageActionGiveawayResults) action;
            pushAction.run("giveaway_results");
            pushBare.run("winners", data.winners_count);
            pushBare.run("unclaimed", data.unclaimed_count);
            pushBare.run("stars_boolean", data.stars);
        } else if (action instanceof TLRPC.TL_messageActionSetChatWallPaper) {
            pushActor.run("actor");
            pushAction.run(((TLRPC.TL_messageActionSetChatWallPaper) action).same ? "set_same_chat_wallpaper" : "set_chat_wallpaper");
            pushReplyToMsgId.run("message_id");
        } else if (action instanceof TLRPC.TL_messageActionBoostApply) {
            pushActor.run("actor");
            pushAction.run("boost_apply");
            pushBare.run("boosts", ((TLRPC.TL_messageActionBoostApply) action).boosts);
        } else if (action instanceof TLRPC.TL_messageActionPaymentRefunded) {
            final TLRPC.TL_messageActionPaymentRefunded data = (TLRPC.TL_messageActionPaymentRefunded) action;
            pushAction.run("refunded_payment");
            pushBare.run("amount", data.total_amount);
            pushBare.run("currency", data.currency);
            push.run("peer_name", wrapPeerName.run(MessageObject.getPeerId(data.peer)));
            pushBare.run("peer", data.peer);
            pushBare.run("charge_id", data.charge.id);
        } else if (action instanceof TLRPC.TL_messageActionGiftStars) {
            final TLRPC.TL_messageActionGiftStars data = (TLRPC.TL_messageActionGiftStars) action;
            pushActor.run("actor");
            pushAction.run("send_stars_gift");
            pushBare.run("amount", data.amount);
            pushBare.run("currency", data.currency);
            if (data.stars != 0) {
                pushBare.run("stars", data.stars);
            }
        } else if (action instanceof TLRPC.TL_messageActionPrizeStars) {
            final TLRPC.TL_messageActionPrizeStars data = (TLRPC.TL_messageActionPrizeStars) action;
            pushActor.run("actor");
            pushAction.run("stars_prize");
            pushBare.run("boost_peer_id", MessageObject.getPeerId(data.peer));
            push.run("boost_peer_name", wrapPeerName.run(MessageObject.getPeerId(data.peer)));
            pushBare.run("stars", data.amount);
            pushBare.run("is_unclaimed", data.unclaimed);
            pushBare.run("giveaway_msg_id", data.giveaway_msg_id);
            pushBare.run("transaction_id", data.transaction_id);
        } else if (action instanceof TLRPC.TL_messageActionStarGift) {
            final TLRPC.TL_messageActionStarGift data = (TLRPC.TL_messageActionStarGift) action;
            pushActor.run("actor");
            pushAction.run("send_star_gift");
            values.add(new Pair<>("gift_id", data.gift.id));
            values.add(new Pair<>("stars", data.convert_stars));
            pushBare.run("is_limited", data.gift.limited);
            pushBare.run("is_anonymous", data.name_hidden);
            if ((data.flags & 2) != 0) {
                final TLRPC.TL_textWithEntities text = data.message;
                push.run("gift_text", SerializeText(context, DataTypesUtils.ParseText(text.text, text.entities), true));
            }
        }

        return SerializeObject(context, toPairsArray(values));
    }

    @SafeVarargs
    public static String SerializeObject(JsonContext context, Pair<String, ?>... pairs) {
        final String indent = Indentation(context);
        context.nesting.add(true);
        final String next = "\n" + Indentation(context);

        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Pair<String, ?> pair : pairs) {
            final String key = pair.first;
            final String value = String.valueOf(pair.second);
            if (value.isEmpty()) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                result.append(',');
            }
            result.append(next).append(SerializeString(key)).append(": ").append(value);
        }
        result.append('\n').append(indent).append("}");
        try {
            return result.toString();
        } finally {
            popNesting(context);
        }
    }

    public static String SerializeString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '\n') {
                result.append("\\n");
            } else if (c == '\r') {
                result.append("\\r");
            } else if (c == '\t') {
                result.append("\\t");
            } else if (c == '"') {
                result.append("\\\"");
            } else if (c == '\\') {
                result.append("\\\\");
            } else if (c < ' ') {
                result.append("\\x");
                result.append((c >> 4) + '0');
                final int low = c & 0x0F;
                if (low >= 10) {
                    result.append(low + 'A' - 10);
                } else {
                    result.append(low + '0');
                }
            } else {
                result.append(c);
            }
        }
        result.append('"');
        return result.toString();
    }

    public static String Indentation(JsonContext context) {
        return Indentation(context.nesting.size());
    }

    public static String Indentation(int size) {
        return " ".repeat(size);
    }

    public static String SerializeArray(JsonContext context, ArrayList<String> values) {
        final String indent = Indentation(context.nesting.size());
        final String next = "\n" + Indentation(context.nesting.size() + 1);

        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (first) {
                first = false;
            } else {
                result.append(',');
            }
            result.append(next).append(value);
        }
        result.append('\n').append(indent).append("]");
        return result.toString();
    }

    public static String StringAllowNull(String value) {
        return (value == null || value.isEmpty()) ? "null" : SerializeString(value);
    }

    public static String SerializeText(JsonContext context, ArrayList<ApiWrap.TextPart> data, boolean serializeToObjects) {
        if (data.isEmpty()) {
            return serializeToObjects ? "[]" : SerializeString("");
        }

        context.nesting.add(false);
        ArrayList<String> parts = new ArrayList<>();
        for (ApiWrap.TextPart part : data) {
            if (part.type == ApiWrap.TextPart.Type.Text && !serializeToObjects) {
                parts.add(SerializeString(part.text));
                continue;
            }
            final String typeString;
            switch (part.type) {
                case Unknown:
                    typeString = "unknown";
                    break;
                case Mention:
                    typeString = "mention";
                    break;
                case Hashtag:
                    typeString = "hashtag";
                    break;
                case BotCommand:
                    typeString = "bot_command";
                    break;
                case Url:
                    typeString = "link";
                    break;
                case Email:
                    typeString = "email";
                    break;
                case Bold:
                    typeString = "bold";
                    break;
                case Italic:
                    typeString = "italic";
                    break;
                case Code:
                    typeString = "code";
                    break;
                case Pre:
                    typeString = "pre";
                    break;
                case Text:
                    typeString = "plain";
                    break;
                case TextUrl:
                    typeString = "text_link";
                    break;
                case MentionName:
                    typeString = "mention_name";
                    break;
                case Phone:
                    typeString = "phone";
                    break;
                case Cashtag:
                    typeString = "cashtag";
                    break;
                case Underline:
                    typeString = "underline";
                    break;
                case Strike:
                    typeString = "strikethrough";
                    break;
                case Blockquote:
                    typeString = "blockquote";
                    break;
                case BankCard:
                    typeString = "bank_card";
                    break;
                case Spoiler:
                    typeString = "spoiler";
                    break;
                case CustomEmoji:
                    typeString = "custom_emoji";
                    break;
                default:
                    throw new IllegalStateException("wtf is it? " + part.text);
            }
            final String additionalValue;
            if (part.type == ApiWrap.TextPart.Type.MentionName) {
                additionalValue = part.additional;
            } else if (part.type == ApiWrap.TextPart.Type.Pre
                    || part.type == ApiWrap.TextPart.Type.TextUrl
                    || part.type == ApiWrap.TextPart.Type.CustomEmoji) {
                additionalValue = SerializeString(part.additional);
            } else if (part.type == ApiWrap.TextPart.Type.Blockquote) {
                additionalValue = part.additional.isEmpty() ? "false" : "true";
            } else {
                additionalValue = "";
            }
            parts.add(SerializeObject(context,
                    new Pair<>("type", SerializeString(typeString)),
                    new Pair<>("text", SerializeString(part.text)),
                    new Pair<>("additional", additionalValue)));
        }
        popNesting(context);

        if (!serializeToObjects && data.size() == 1 && data.get(0).type == ApiWrap.TextPart.Type.Text) {
            return parts.get(0);
        }
        return SerializeArray(context, parts);
    }

    public AbstractWriter.Result writeBlock(String block) {
        return _file.writeBlock(block);
    }

    public String SerializeDialog(ApiWrap.DialogInfo dialog, boolean left) {
        return Indentation(this) + SerializeObject(this,
                new Pair<>("name", SerializeString(dialog.name)),
                new Pair<>("id", dialog.peerId),
                new Pair<>("relativePath", SerializeString(dialog.relativePath)),
                new Pair<>("left", left));
    }
}
