package com.exteragram.messenger.export.output.html;

import android.util.Pair;

import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.api.ApiWrap;
import com.exteragram.messenger.export.api.DataTypesUtils;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.FileManager;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.google.zxing.Dimension;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class HtmlWriter extends AbstractWriter {

    private static final int kMessagesInFile = 1000;

    private HtmlContext _chat;
    private boolean _chatFileEmpty;
    private HtmlContext _chats;
    private int _dateMessageId;
    private ApiWrap.ExportPersonalInfo _delayedPersonalInfo;
    private ApiWrap.DialogInfo _dialog;
    private DialogsMode _dialogsMode;
    private String _dialogsRelativePath;
    private MessageInfo _lastMessageInfo;
    private int _messagesCount;
    private ExportSettings _settings;
    private OutputFile.Stats _stats;
    private HtmlContext _stories;
    private HtmlContext _summary;
    private HtmlContext _userpics;
    private int selectedAcc;
    private int _selfColorIndex = 0;
    private boolean _haveSections = false;
    private boolean _summaryNeedDivider = false;
    private int _userpicsCount = 0;
    private int _storiesCount = 0;
    private final ArrayList<Integer> _lastMessageIdsPerFile = new ArrayList<>();
    private final ArrayList<SavedSection> _savedSections = new ArrayList<>();

    public enum DialogsMode {
        None,
        Chats,
        Left
    }

    public static class MessageInfo {
        public int date;
        public long forwardedFromId;
        public String forwardedFromName;
        public long fromId;
        public int id;
        public long viaBotId;
        public int forwardedDate = 0;
        public boolean forwarded = false;
        public boolean showForwardedAsOriginal = false;
        public Type type = Type.Service;

        public enum Type {
            Service,
            Default
        }
    }

    public static class Photo {
        public long id = 0;
        public int date = 0;
        public boolean spoilered = false;
        public ApiWrap.Image image = new ApiWrap.Image();
    }

    public static class SavedSection {
        String label;
        String path;
        String type;
        int priority = 0;
        int count = 0;
    }

    public static class UserpicData {
        public int colorIndex = 0;
        public int pixelSize = 0;
        public String imageLink = "";
        public String largeLink = "";
        public String firstName = "";
        public String lastName = "";
        public String tooltip = "";
    }

    public static String MakeLinks(String value) {
        final String domain = "https://telegram.org/";
        StringBuilder result = new StringBuilder();
        int offset = 0;
        while (true) {
            final int start = value.indexOf(domain, offset);
            if (start < 0) {
                break;
            }
            int end = start + domain.length();
            while (end != value.length()) {
                final char c = value.charAt(end);
                if ((c >= 'a' && c <= 'z')
                        || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9')
                        || c == '-'
                        || c == '_'
                        || c == '/') {
                    end++;
                } else {
                    break;
                }
            }
            if (start > offset) {
                final String link = value.substring(start, end - start);
                result.append(value.substring(offset, start - offset));
                result.append("<a href=\"").append(link).append("\">").append(link).append("</a>");
                offset = end;
            }
        }
        if (result.length() == 0) {
            return value;
        }
        if (offset < value.length()) {
            result.append(value.substring(offset));
        }
        return result.toString();
    }

    private static String wrapUserNames(ArrayList<Long> userIds) {
        StringBuilder result = new StringBuilder();
        for (Long userId : userIds) {
            result.append(wrapUserName(userId)).append(" ");
        }
        return result.toString();
    }

    private static String wrapUserName(Long userId) {
        final String name = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId).first_name;
        return name.isEmpty() ? "Deleted Account" : name;
    }

    public static String wrapPeerName(TLRPC.Peer peer) {
        final String name = ChatUtils.getInstance().getName(DialogObject.getPeerDialogId(peer));
        return name.isEmpty() ? "Deleted" : name;
    }

    public static String wrapPeerName(long peerId) {
        final String name = ChatUtils.getInstance().getName(peerId);
        return name.isEmpty() ? "Deleted" : name;
    }

    private static String userpicsFilePath() {
        return "lists/profile_pictures.html";
    }

    public static String WriteUserpicThumb(String basePath, String largePath, UserpicData userpic) {
        return HtmlContext.WriteImageThumb(basePath, largePath, size -> {
            final int side = userpic.pixelSize * 2;
            return new Dimension(side, side);
        }, null, 0, "_thumb").first;
    }

    private static String storiesFilePath() {
        return "lists/stories.html";
    }

    private static String messagesFile(int index) {
        return "messages" + (index > 0 ? String.valueOf(index + 1) : "") + ".html";
    }

    @Override
    public String mainFilePath() {
        return HtmlContext.pathWithRelativePath(_settings.onlySinglePeer() ? messagesFile(0) : "export_results.html");
    }

    @Override
    public Result start(ExportSettings settings, OutputFile.Stats stats) {
        _settings = settings;
        final File folder = FileManager.defaultSavePath;
        new File(folder, "css").mkdirs();
        new File(folder, "js").mkdirs();
        new File(folder, "images").mkdirs();
        FileManager.copyAssets();

        if (_settings.onlySinglePeer()) {
            return Result.Success();
        }
        _summary = fileWithRelativePath("export_results.html");
        return _summary.writeBlock(_summary.pushHeader("Exported Data", "") + _summary.pushDiv("page_body"));
    }

    @Override
    public Result writePersonal(ApiWrap.ExportPersonalInfo data) {
        _selfColorIndex = data.user.info.colorIndex;
        if ((_settings.types & 0x02) != 0) { // userpics
            _delayedPersonalInfo = new ApiWrap.ExportPersonalInfo(data);
            return Result.Success();
        }
        return writePreparedPersonal(data, "");
    }

    private Result writePreparedPersonal(ApiWrap.ExportPersonalInfo data, String userpicPath) {
        final ApiWrap.ContactInfo info = data.user.info;
        final UserpicData userpic = new UserpicData();
        userpic.colorIndex = _selfColorIndex;
        userpic.pixelSize = 90;
        userpic.largeLink = userpicPath.isEmpty() ? "" : userpicsFilePath();
        userpic.imageLink = HtmlContext.WriteImageThumb(_settings.path, userpicPath, size -> {
            final int side = userpic.pixelSize * 2;
            return new Dimension(side, side);
        }, null, -1, "_info").first;
        userpic.firstName = info.firstName;
        userpic.lastName = info.lastName;

        StringBuilder block = new StringBuilder(_summary.pushDiv("personal_info clearfix"));
        block.append(_summary.pushDiv("pull_right userpic_wrap"));
        block.append(_summary.pushUserpic(userpic));
        block.append(_summary.popTag());
        pushRows("names", List.of(
                new Pair<>("First name", info.firstName),
                new Pair<>("Last name", info.lastName)
        ), block);
        final String phone = info.phoneNumber.isEmpty() ? "" : PhoneFormat.getInstance().format("+" + info.phoneNumber);
        final String username = data.user.username.isEmpty() ? data.user.username : "@" + data.user.username;
        pushRows("info", List.of(
                new Pair<>("Phone number", phone),
                new Pair<>("Username", username)
        ), block);
        pushRows("bio", List.of(new Pair<>("Bio", data.bio)), block);
        block.append(_summary.popTag());

        _summaryNeedDivider = true;
        return _summary.writeBlock(block.toString());
    }

    private Result writeSessions(ApiWrap.SessionsList data) {
        if (data.list.isEmpty()) {
            return Result.Success();
        }

        final String filename = "lists/sessions.html";
        final HtmlContext file = fileWithRelativePath(filename);
        StringBuilder list = new StringBuilder(file.pushHeader("Sessions", "export_results.html"));
        list.append(file.pushDiv("page_body list_page"));
        list.append(file.pushAbout("We store session info to display your connected devices in Settings > Privacy & Security > Active Sessions.", false));
        list.append(file.pushDiv("entry_list"));
        for (TLRPC.TL_authorization session : data.list) {
            final String name = (session.app_name.isEmpty() ? "Unknown" : session.app_name) + ' ' + session.app_version;
            final String subname = session.device_model + ", " + session.platform + ' ' + session.system_version;
            final String location = session.ip + " - " + session.region
                    + (session.region.isEmpty() || session.country.isEmpty() ? "" : ", ")
                    + session.country;
            list.append(file.pushSessionListEntry(
                    session.api_id,
                    name,
                    subname,
                    new ArrayList<>(Arrays.asList(
                            location,
                            "Last active: " + LocaleController.formatDate(session.date_active),
                            "Created: " + LocaleController.formatDate(session.date_created))),
                    ""));
        }
        Result result = file.writeBlock(list.toString());
        if (!result.isSuccess()) {
            return result;
        }
        result = file.close();
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(6, "Sessions", "sessions", data.list.size(), filename);
        return Result.Success();
    }

    private Result writeWebSessions(ApiWrap.SessionsList data) {
        if (data.webList.isEmpty()) {
            return Result.Success();
        }

        final String filename = "lists/web_sessions.html";
        final HtmlContext file = fileWithRelativePath(filename);
        StringBuilder list = new StringBuilder(file.pushHeader("Web sessions", "export_results.html"));
        list.append(file.pushDiv("page_body list_page"));
        list.append(file.pushAbout("We store this to display the websites where you logged in using authentication via Telegram. This information is shown in Settings > Privacy & Security > Active Sessions.", false));
        list.append(file.pushDiv("entry_list"));
        for (ApiWrap.WebSession session : data.webList) {
            final int colorIndex = (int) (DataTypesUtils.StringBarePeerId(session.domain()) + 4096);
            final String name = session.domain().isEmpty() ? "Unknown" : session.domain();
            final String subname = session.platform() + ", " + session.browser();
            final ArrayList<String> details = new ArrayList<>(Arrays.asList(
                    session.ip() + " - " + session.region(),
                    "Last active: " + LocaleController.formatDate(session.lastActive()),
                    "Created: " + LocaleController.formatDate(session.created())));
            final String info = session.botUsername().isEmpty() ? "" : "@" + session.botUsername();
            list.append(file.pushSessionListEntry(colorIndex, name, subname, details, info));
        }
        Result result = file.writeBlock(list.toString());
        if (!result.isSuccess()) {
            return result;
        }
        result = file.close();
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(7, "Web sessions", "web", data.webList.size(), filename);
        return Result.Success();
    }

    private void pushRows(String name, List<Pair<String, String>> values, StringBuilder block) {
        block.append(_summary.pushDiv("rows " + name));
        for (Pair<String, String> row : values) {
            final String key = row.first;
            final String value = row.second;
            if (value.isEmpty()) {
                continue;
            }
            block.append(_summary.pushDiv("row"));
            block.append(_summary.pushDiv("label details"));
            block.append(HtmlContext.SerializeString(key));
            block.append(_summary.popTag());
            block.append(_summary.pushDiv("value bold"));
            block.append(HtmlContext.SerializeString(value));
            block.append(_summary.popTag());
            block.append(_summary.popTag());
        }
        block.append(_summary.popTag());
    }

    private Result switchToNextChatFile(int index) {
        final String nextPath = messagesFile(index);
        Result result = _chat.writeBlock(_chat.pushTag("a",
                new Pair<>("class", "pagination block_link"),
                new Pair<>("href", nextPath))
                + "Next messages"
                + _chat.popTag());
        if (!result.isSuccess()) {
            return result;
        }
        result = _chat.close();
        if (!result.isSuccess()) {
            return result;
        }
        _chat = fileWithRelativePath(_dialog.relativePath + nextPath);
        _chatFileEmpty = true;
        return Result.Success();
    }

    private Result writeDialogOpening(int index) {
        final String name = _dialog.name.isEmpty() && _dialog.lastName.isEmpty()
                ? "Deleted Account"
                : _dialog.name + ' ' + _dialog.lastName;
        StringBuilder block = new StringBuilder(_chat.pushHeader(name, _settings.onlySinglePeer() ? "" : _dialogsRelativePath));
        block.append(_chat.pushDiv("page_body chat_page"));
        block.append(_chat.pushDiv("history"));
        if (index > 0) {
            block.append(_chat.pushTag("a",
                    new Pair<>("class", "pagination block_link"),
                    new Pair<>("href", messagesFile(index - 1))));
            block.append("Previous messages");
            block.append(_chat.popTag());
        }
        return _chat.writeBlock(block.toString());
    }

    @Override
    public Result writeDialogsStart(ApiWrap.DialogsInfo data) {
        if (_chats != null) {
            throw new IllegalStateException("chats already initialized!");
        }
        if (data.chats.isEmpty() && data.left.isEmpty()) {
            return Result.Success();
        } else if (_settings.onlySinglePeer()) {
            return Result.Success();
        }

        final String filename = "lists/chats.html";
        _dialogsRelativePath = filename;
        _chats = fileWithRelativePath(filename);
        // The header and page body are pushed onto the tag stack but never written (matches exteraGram).
        _chats.pushHeader("Chats", "export_results.html");
        _chats.pushDiv("page_body list_page");

        SavedSection section = new SavedSection();
        section.priority = 0;
        section.label = "Chats";
        section.type = "chats";
        section.count = data.chats.size() + data.left.size();
        section.path = filename;
        _savedSections.add(section);
        return writeSections();
    }

    public void pushSection(int priority, String label, String type, int count, String path) {
        SavedSection section = new SavedSection();
        section.priority = priority;
        section.label = label;
        section.type = type;
        section.count = count;
        section.path = path;
        _savedSections.add(section);
    }

    private Result writeSections() {
        if (_savedSections.isEmpty()) {
            return Result.Success();
        } else if (!_haveSections) {
            final Result result = _summary.writeBlock(_summary.pushDiv(_summaryNeedDivider ? "sections with_divider" : "sections", ""));
            if (!result.isSuccess()) {
                return Result.Success();
            }
            _haveSections = true;
            _summaryNeedDivider = false;
        }

        Collections.sort(_savedSections, Comparator.comparingInt(section -> section.priority));
        ArrayList<String> block = new ArrayList<>();
        for (SavedSection section : _savedSections) {
            block.add(_summary.pushSection(section.label, section.type, section.count, _summary.relativePath(section.path)));
        }
        return _summary.writeBlock(block);
    }

    public String getTextFromAction(ApiWrap.Message message, String serviceFrom, boolean isChannel) {
        // Reconstructed from bytecode, jadx failed to decompile this method.
        final LocaleController localeController = LocaleController.getInstance();
        if (message == null || message.action == null) {
            return "";
        }
        final TLRPC.MessageAction action = message.action;

        if (action instanceof TLRPC.TL_messageActionChatCreate) {
            final TLRPC.TL_messageActionChatCreate data = (TLRPC.TL_messageActionChatCreate) action;
            return serviceFrom + " created group &laquo;" + data.title + "&raquo;"
                    + (data.users.isEmpty() ? "" : " with members " + wrapUserNames(data.users));
        } else if (action instanceof TLRPC.TL_messageActionChatEditTitle) {
            final TLRPC.TL_messageActionChatEditTitle data = (TLRPC.TL_messageActionChatEditTitle) action;
            return isChannel
                    ? "Channel title changed to &laquo;" + data.title + "&raquo;"
                    : serviceFrom + " changed group title to &laquo;" + data.title + "&raquo;";
        } else if (action instanceof TLRPC.TL_messageActionChatEditPhoto) {
            return isChannel ? "Channel photo changed" : serviceFrom + " changed group photo";
        } else if (action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
            return isChannel ? "Channel photo removed" : serviceFrom + " removed group photo";
        } else if (action instanceof TLRPC.TL_messageActionChatAddUser) {
            return serviceFrom + " invited " + wrapUserNames(((TLRPC.TL_messageActionChatAddUser) action).users);
        } else if (action instanceof TLRPC.TL_messageActionChatDeleteUser) {
            return serviceFrom + " removed " + wrapUserName(((TLRPC.TL_messageActionChatDeleteUser) action).user_id);
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
            return serviceFrom + " joined group by link from " + wrapUserName(((TLRPC.TL_messageActionChatJoinedByLink) action).inviter_id);
        } else if (action instanceof TLRPC.TL_messageActionChannelCreate) {
            return "Channel &laquo;" + ((TLRPC.TL_messageActionChannelCreate) action).title + "&raquo; created";
        } else if (action instanceof TLRPC.TL_messageActionChatMigrateTo) {
            return serviceFrom + " converted this group to a supergroup";
        } else if (action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
            return serviceFrom + " converted a basic group to this supergroup &laquo;" + ((TLRPC.TL_messageActionChannelMigrateFrom) action).title + "&raquo;";
        } else if (action instanceof TLRPC.TL_messageActionPinMessage) {
            return serviceFrom + " pinned " + wrapMessageLink(message.id, "this message");
        } else if (action instanceof TLRPC.TL_messageActionHistoryClear) {
            return "History cleared";
        } else if (action instanceof TLRPC.TL_messageActionGameScore) {
            return serviceFrom + " scored " + ((TLRPC.TL_messageActionGameScore) action).score + " in " + wrapMessageLink(message.id, "this game");
        } else if (action instanceof TLRPC.TL_messageActionPaymentSent) {
            final TLRPC.TL_messageActionPaymentSent data = (TLRPC.TL_messageActionPaymentSent) action;
            final String amount = localeController.formatCurrencyString(data.amount, data.currency);
            if (data.recurring_used) {
                return "You were charged " + amount + " via recurring payment";
            }
            String result = "You have successfully transferred " + amount + " for " + wrapMessageLink(message.id, "this invoice");
            if (data.recurring_init) {
                result += " and allowed future recurring payments";
            }
            return result;
        } else if (action instanceof TLRPC.TL_messageActionPhoneCall) {
            return "";
        } else if (action instanceof TLRPC.TL_messageActionScreenshotTaken) {
            return serviceFrom + " took a screenshot";
        } else if (action instanceof TLRPC.TL_messageActionCustomAction) {
            return ((TLRPC.TL_messageActionCustomAction) action).message;
        } else if (action instanceof TLRPC.TL_messageActionBotAllowed) {
            final TLRPC.TL_messageActionBotAllowed data = (TLRPC.TL_messageActionBotAllowed) action;
            if (data.attach_menu) {
                return "You allowed this bot to message you when you added it in the attachment menu.";
            } else if (data.from_request) {
                return "You allowed this bot to message you in his web-app.";
            } else if (data.app != null) {
                return "You allowed this bot to message you when you opened " + data.app;
            }
            return "You allowed this bot to message you when you logged in on " + data.domain;
        } else if (action instanceof TLRPC.TL_messageActionSecureValuesSent) {
            ArrayList<String> documents = new ArrayList<>();
            for (TLRPC.SecureValueType type : ((TLRPC.TL_messageActionSecureValuesSent) action).types) {
                final String name;
                if (type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
                    name = "Personal details";
                } else if (type instanceof TLRPC.TL_secureValueTypePassport) {
                    name = "Passport";
                } else if (type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
                    name = "Driver license";
                } else if (type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
                    name = "Identity card";
                } else if (type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
                    name = "Internal passport";
                } else if (type instanceof TLRPC.TL_secureValueTypeAddress) {
                    name = "Address information";
                } else if (type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
                    name = "Utility bill";
                } else if (type instanceof TLRPC.TL_secureValueTypeBankStatement) {
                    name = "Bank statement";
                } else if (type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
                    name = "Rental agreement";
                } else if (type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
                    name = "Passport registration";
                } else if (type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
                    name = "Temporary registration";
                } else if (type instanceof TLRPC.TL_secureValueTypePhone) {
                    name = "Phone number";
                } else if (type instanceof TLRPC.TL_secureValueTypeEmail) {
                    name = "Email";
                } else {
                    name = "";
                }
                documents.add(name);
            }
            return "You have sent the following documents: " + SerializeList(documents);
        } else if (action instanceof TLRPC.TL_messageActionContactSignUp) {
            return serviceFrom + " joined Telegram";
        } else if (action instanceof TLRPC.TL_messageActionGeoProximityReached) {
            final TLRPC.TL_messageActionGeoProximityReached data = (TLRPC.TL_messageActionGeoProximityReached) action;
            final String fromName = wrapPeerName(data.from_id);
            final String toName = wrapPeerName(data.to_id);
            final String distance;
            if (data.distance >= 1000) {
                distance = (data.distance / 10 * 10 / 1000) + " km";
            } else if (data.distance == 1) {
                distance = "1 meter";
            } else {
                distance = data.distance + " meters";
            }
            final long selfId = UserConfig.getInstance(selectedAcc).getClientUserId();
            if (DialogObject.getPeerDialogId(data.from_id) == selfId) {
                return "You are now within " + distance + " from " + toName;
            } else if (DialogObject.getPeerDialogId(data.to_id) == selfId) {
                return fromName + " is now within " + distance + " from you";
            }
            return fromName + " is now within " + distance + " from " + toName;
        } else if (action instanceof TLRPC.TL_messageActionPhoneNumberRequest) {
            return serviceFrom + " requested your phone number";
        } else if (action instanceof TLRPC.TL_messageActionGroupCall) {
            final TLRPC.TL_messageActionGroupCall data = (TLRPC.TL_messageActionGroupCall) action;
            final String duration = data.duration != 0 ? " (" + data.duration + " seconds)" : "";
            return isChannel ? "Voice chat" + duration : serviceFrom + " started voice chat" + duration;
        } else if (action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
            return serviceFrom + " invited " + wrapUserNames(((TLRPC.TL_messageActionInviteToGroupCall) action).users) + " to the voice chat";
        } else if (action instanceof TLRPC.TL_messageActionSetMessagesTTL) {
            final int period = ((TLRPC.TL_messageActionSetMessagesTTL) action).period;
            String periodText = "";
            if (period == 7 * 86400) {
                periodText = "7 days";
            } else if (period == 86400) {
                periodText = "24 hours";
            }
            if (isChannel) {
                return period != 0 ? "New messages will auto-delete in " + periodText : "New messages will not auto-delete";
            }
            return period != 0
                    ? serviceFrom + " has set messages to auto-delete in " + periodText
                    : serviceFrom + " has set messages not to auto-delete";
        } else if (action instanceof TLRPC.TL_messageActionGroupCallScheduled) {
            final String date = LocaleController.formatDate(((TLRPC.TL_messageActionGroupCallScheduled) action).schedule_date);
            return isChannel ? "Voice chat scheduled for " + date : serviceFrom + " scheduled a voice chat for " + date;
        } else if (action instanceof TLRPC.TL_messageActionSetChatTheme) {
            final TLRPC.ChatTheme theme = ((TLRPC.TL_messageActionSetChatTheme) action).theme;
            if (theme instanceof TLRPC.TL_chatTheme) {
                final String emoticon = ((TLRPC.TL_chatTheme) theme).emoticon;
                if (emoticon.isEmpty()) {
                    return isChannel ? "Channel theme was disabled" : serviceFrom + " disabled chat theme";
                }
                return isChannel ? "Channel theme was changed to " + emoticon : serviceFrom + " changed chat theme to " + emoticon;
            }
            return "Theme was changed";
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
            return serviceFrom + " joined group by request";
        } else if (action instanceof TLRPC.TL_messageActionWebViewDataSent) {
            return "You have just successfully transferred action from the &laquo;" + ((TLRPC.TL_messageActionWebViewDataSent) action).text + "&raquo; button to the bot";
        } else if (action instanceof TLRPC.TL_messageActionGiftPremium) {
            final TLRPC.TL_messageActionGiftPremium data = (TLRPC.TL_messageActionGiftPremium) action;
            if (data.months != 0 || localeController.formatCurrencyString(data.amount, data.currency).isEmpty()) {
                return serviceFrom + " sent you a gift.";
            }
            return serviceFrom + " sent you a gift for " + data.amount + ": Telegram Premium for " + data.months + " months.";
        } else if (action instanceof TLRPC.TL_messageActionTopicCreate) {
            return serviceFrom + " created topic &laquo;" + ((TLRPC.TL_messageActionTopicCreate) action).title + "&raquo;";
        } else if (action instanceof TLRPC.TL_messageActionTopicEdit) {
            final TLRPC.TL_messageActionTopicEdit data = (TLRPC.TL_messageActionTopicEdit) action;
            String changes = "";
            if (!data.title.isEmpty()) {
                changes = "title to &laquo;" + data.title + "&raquo;" + ",";
            }
            if (data.icon_emoji_id != 0) {
                changes = changes + "icon to &laquo;" + data.icon_emoji_id + "&raquo;" + ",";
            }
            return serviceFrom + " changed topic " + changes;
        } else if (action instanceof TLRPC.TL_messageActionSuggestProfilePhoto) {
            return serviceFrom + " suggests to use this photo";
        } else if (action instanceof TLRPC.TL_messageActionRequestedPeer) {
            return "requested: " + DialogObject.getPeerDialogId(((TLRPC.TL_messageActionRequestedPeer) action).peer);
        } else if (action instanceof TLRPC.TL_messageActionSetChatWallPaper) {
            final TLRPC.TL_messageActionSetChatWallPaper data = (TLRPC.TL_messageActionSetChatWallPaper) action;
            return serviceFrom + (data.same
                    ? " set " + wrapMessageLink(message.id, "the same background") + " for this chat"
                    : " set a new background for this chat");
        } else if (action instanceof TLRPC.TL_messageActionGiftCode) {
            final TLRPC.TL_messageActionGiftCode data = (TLRPC.TL_messageActionGiftCode) action;
            final String months = data.months > 1 ? " months" : "month";
            if (data.unclaimed) {
                return "This is an unclaimed Telegram Premium for " + data.months + months + " prize in a giveaway organized by a channel.";
            } else if (data.via_giveaway) {
                return "You won a Telegram Premium for " + data.months + months + " prize in a giveaway organized by a channel.";
            }
            return "You've received a Telegram Premium for " + data.months + months + " gift from a channel.";
        } else if (action instanceof TLRPC.TL_messageActionGiveawayLaunch) {
            return serviceFrom + " just started a giveaway of Telegram Premium subscriptions to its followers.";
        } else if (action instanceof TLRPC.TL_messageActionGiveawayResults) {
            final TLRPC.TL_messageActionGiveawayResults data = (TLRPC.TL_messageActionGiveawayResults) action;
            if (data.winners_count != 0) {
                return "No winners of the giveaway could be selected.";
            } else if (data.stars && data.unclaimed_count != 0) {
                return "Some winners of the giveaway were randomly selected by Telegram and received their prize.";
            } else if (!data.stars && data.unclaimed_count != 0) {
                return "Some winners of the giveaway were randomly selected by Telegram and received private messages with giftcodes.";
            } else if (data.stars) {
                return data.winners_count + " of the giveaway was randomly selected by Telegram and received their prize.";
            }
            return data.winners_count + " of the giveaway was randomly selected by Telegram and received private messages with giftcodes.";
        } else if (action instanceof TLRPC.TL_messageActionBoostApply) {
            final TLRPC.TL_messageActionBoostApply data = (TLRPC.TL_messageActionBoostApply) action;
            return serviceFrom + " boosted the group " + data.boosts + (data.boosts > 1 ? " times" : " time");
        } else if (action instanceof TLRPC.TL_messageActionPaymentRefunded) {
            final TLRPC.TL_messageActionPaymentRefunded data = (TLRPC.TL_messageActionPaymentRefunded) action;
            final String amount = localeController.formatCurrencyString(data.amount, data.currency);
            return wrapPeerName(data.peer) + " refunded back " + amount;
        } else if (action instanceof TLRPC.TL_messageActionGiftStars) {
            final TLRPC.TL_messageActionGiftStars data = (TLRPC.TL_messageActionGiftStars) action;
            final String cost = localeController.formatCurrencyString(data.amount, data.currency);
            if (data.stars != 0 || cost.isEmpty()) {
                return serviceFrom + " sent you a gift.";
            }
            return serviceFrom + " sent you a gift for " + cost + ": " + data.stars + " Telegram Stars.";
        } else if (action instanceof TLRPC.TL_messageActionPrizeStars) {
            final TLRPC.TL_messageActionPrizeStars data = (TLRPC.TL_messageActionPrizeStars) action;
            return "You won a prize in a giveaway organized by " + wrapPeerName(data.peer) + ".\n Your prize is " + data.amount + " Telegram Stars.";
        } else if (action instanceof TLRPC.TL_messageActionStarGift) {
            final TLRPC.TL_messageActionStarGift data = (TLRPC.TL_messageActionStarGift) action;
            return serviceFrom + " sent you a gift of " + data.gift.stars + " Telegram Stars.";
        }
        return "";
    }

    @Override
    public Result writeDialogStart(ApiWrap.DialogInfo data) {
        _chat = fileWithRelativePath(data.relativePath + messagesFile(0));
        _chatFileEmpty = true;
        _messagesCount = 0;
        _dateMessageId = 0;
        _lastMessageInfo = null;
        _lastMessageIdsPerFile.clear();
        _dialog = data;
        return Result.Success();
    }

    @Override
    public Result writeDialogSlice(ApiWrap.MessagesSlice data) {
        int oldIndex = _messagesCount > 0 ? (_messagesCount - 1) / kMessagesInFile : 0;
        MessageInfo previous = _lastMessageInfo;
        MessageInfo saved = new MessageInfo();
        StringBuilder block = new StringBuilder();
        for (ApiWrap.Message message : data.list) {
            if (DataTypesUtils.SkipMessageByDate(message, _settings)) {
                continue;
            }
            final int newIndex = _messagesCount / kMessagesInFile;
            if (oldIndex != newIndex) {
                Result result = _chat.writeBlock(block.toString());
                if (!result.isSuccess()) {
                    return result;
                }
                result = switchToNextChatFile(newIndex);
                if (!result.isSuccess()) {
                    return result;
                }
                _lastMessageIdsPerFile.add(saved != null ? saved.id : _lastMessageInfo.id);
                block = new StringBuilder();
                previous = null;
                _lastMessageInfo = null;
                oldIndex = newIndex;
            }
            if (_chatFileEmpty) {
                Result result = writeDialogOpening(oldIndex);
                if (!result.isSuccess()) {
                    return result;
                }
                _chatFileEmpty = false;
            }
            final int date = message.date;
            if (DataTypesUtils.DisplayDate(date, previous != null ? previous.date : 0)) {
                block.append(_chat.pushServiceMessage(--_dateMessageId, _dialog, _settings.path, LocaleController.formatDate(date), null));
            }
            final Pair<MessageInfo, String> pushed = _chat.pushMessage(message, previous, _dialog, _settings.path, data.peers, this, "https://t.me/", this::wrapMessageLink);
            saved = pushed.first;
            block.append(pushed.second);
            ++_messagesCount;
            previous = saved;
        }
        if (saved != null) {
            _lastMessageInfo = saved;
        }
        return block.toString().isEmpty() ? Result.Success() : _chat.writeBlock(block.toString());
    }

    @Override
    public Result writeDialogEnd() {
        Result result = writeEmptySinglePeer();
        if (!result.isSuccess()) {
            return result;
        }
        result = _chat.close();
        if (!result.isSuccess()) {
            return result;
        }
        if (_settings.onlySinglePeer()) {
            return Result.Success();
        }

        final String name;
        switch (_dialog.type) {
            case Self:
                name = "Saved messages";
                break;
            case Replies:
                name = "Replies";
                break;
            case VerifyCodes:
                name = "Verification Codes";
                break;
            default:
                name = _dialog.name;
                break;
        }
        final String lastName = _dialog.type == ApiWrap.DialogInfo.Type.Personal || _dialog.type == ApiWrap.DialogInfo.Type.Bot
                ? _dialog.lastName
                : "";

        UserpicData userpic = new UserpicData();
        userpic.colorIndex = _dialog.type == ApiWrap.DialogInfo.Type.Self
                || _dialog.type == ApiWrap.DialogInfo.Type.Replies
                || _dialog.type == ApiWrap.DialogInfo.Type.VerifyCodes
                ? 3
                : DataTypesUtils.PeerColorIndex(_dialog.peerId);
        userpic.pixelSize = 48;
        userpic.firstName = name;
        userpic.lastName = lastName;

        result = validateDialogsMode(_dialog.isLeftChannel);
        if (!result.isSuccess()) {
            return result;
        }

        return _chats.writeBlock(_chats.pushListEntry(
                userpic,
                DataTypesUtils.ComposeName(userpic, DataTypesUtils.DeletedString(_dialog.type)),
                DataTypesUtils.CountString(_messagesCount, _dialog.onlyMyMessages),
                DataTypesUtils.TypeString(_dialog.type),
                _messagesCount > 0 ? _dialog.relativePath + "messages.html" : ""));
    }

    @Override
    public Result writeDialogsEnd() {
        if (_chats != null) {
            return _chats.close();
        }
        return Result.Success();
    }

    @Override
    public Result writeSessionsList(ApiWrap.SessionsList data) {
        Result result = writeSessions(data);
        if (!result.isSuccess()) {
            return result;
        }
        result = writeWebSessions(data);
        if (!result.isSuccess()) {
            return result;
        }
        return Result.Success();
    }

    @Override
    public Result writeUserpicsStart(ApiWrap.UserpicsInfo data) {
        _userpicsCount = data.count();
        if (_userpicsCount == 0) {
            return Result.Success();
        }
        _userpics = fileWithRelativePath(userpicsFilePath());
        final Result result = _userpics.writeBlock(_userpics.pushHeader("Profile pictures", "export_results.html")
                + _userpics.pushDiv("page_body list_page")
                + _userpics.pushDiv("entry_list"));
        if (!result.isSuccess()) {
            return result;
        }
        if (_delayedPersonalInfo == null) {
            pushSection(4, "Profile pictures", "photos", _userpicsCount, userpicsFilePath());
        }
        return Result.Success();
    }

    private Result writeEmptySinglePeer() {
        if (!_settings.onlySinglePeer() || _messagesCount != 0) {
            return Result.Success();
        }
        final Result result = writeDialogOpening(0);
        if (!result.isSuccess()) {
            return result;
        }
        return _chat.writeBlock(_chat.pushServiceMessage(--_dateMessageId, _dialog, _settings.path, "No exported messages", null));
    }

    @Override
    public Result writeUserpicsSlice(ArrayList<Photo> data) {
        final Result result = writeDelayedPersonal(data.get(0).image.file.relativePath);
        if (!result.isSuccess()) {
            return result;
        }

        StringBuilder block = new StringBuilder();
        for (Photo userpic : data) {
            UserpicData userpicData = new UserpicData();
            userpicData.colorIndex = _selfColorIndex;
            userpicData.pixelSize = 48;

            final ApiWrap.File file = userpic.image.file;
            final String status;
            switch (file.skipReason) {
                case Unavailable:
                    status = "(Photo unavailable, please try again later)";
                    break;
                case FileSize:
                    status = "(Photo exceeds maximum size. Change data exporting settings to download.)";
                    break;
                case FileType:
                    status = "(Photo not included. Change data exporting settings to download.)";
                    break;
                case None:
                    status = AndroidUtilities.formatFileSize(file.size);
                    break;
                default:
                    status = null;
                    break;
            }
            final String path = userpic.image.file.relativePath;
            userpicData.imageLink = WriteUserpicThumb(_settings.path, path, userpicData);
            userpicData.firstName = path;
            block.append(_userpics.pushListEntry(
                    userpicData,
                    path.isEmpty() ? "Photo unavailable" : path,
                    status,
                    userpic.date > 0 ? LocaleController.formatDate(userpic.date) : "",
                    path));
        }
        return _userpics.writeBlock(block.toString());
    }

    public Result writeDelayedPersonal(String userpicPath) {
        if (_delayedPersonalInfo == null) {
            return Result.Success();
        }
        final Result result = writePreparedPersonal(_delayedPersonalInfo, userpicPath);
        if (!result.isSuccess()) {
            return result;
        }
        if (_userpicsCount != 0) {
            pushSection(4, "Profile pictures", "photos", _userpicsCount, userpicsFilePath());
        }
        return Result.Success();
    }

    @Override
    public Result writeUserpicsEnd() {
        final Result result = writeDelayedPersonal("");
        if (!result.isSuccess()) {
            return result;
        }
        if (_userpics != null) {
            return _userpics.close();
        }
        return Result.Success();
    }

    @Override
    public Result writeStoriesStart(int count) {
        _storiesCount = count;
        if (count == 0) {
            return Result.Success();
        }
        _stories = fileWithRelativePath(storiesFilePath());
        final Result result = _stories.writeBlock(_stories.pushHeader("Stories archive", "export_results.html")
                + _stories.pushDiv("page_body list_page")
                + _stories.pushDiv("entry_list"));
        if (!result.isSuccess()) {
            return result;
        }
        return Result.Success();
    }

    @Override
    public Result writeStoriesSlice(ApiWrap.StoriesSlice data) {
        _storiesCount -= data.skipped;
        if (data.list == null || data.list.isEmpty()) {
            return Result.Success();
        }
        StringBuilder block = new StringBuilder();
        for (ApiWrap.Story story : data.list) {
            ApiWrap.StoryData storyData = new ApiWrap.StoryData();
            final ApiWrap.File file = story.file();
            ArrayList<String> details = new ArrayList<>();
            if (story.pinned) {
                details.add("Saved to Profile");
            }
            if (story.expires > 0) {
                details.add("Expiring: ");
                details.add(LocaleController.formatDate(story.expires));
            }
            if (file.skipReason == ApiWrap.File.SkipReason.DateLimits) {
                throw new IllegalStateException("Skip reason while writing story path.");
            }
            final String path = story.file().relativePath;
            final String thumbPath = story.thumb().file.relativePath.isEmpty()
                    ? story.file().relativePath
                    : story.thumb().file.relativePath;
            storyData.imageLink = HtmlContext.WriteImageThumb(_settings.path, thumbPath, size -> new Dimension(90, 160), null, 0, null).first;
            block.append(_stories.pushStoriesListEntry(
                    storyData,
                    path.isEmpty() ? "Story unavailable" : path,
                    details,
                    story.date > 0 ? LocaleController.formatDate(story.date) : "",
                    story.caption,
                    "_environment.internalLinksDomain",
                    path));
        }
        return _stories.writeBlock(block.toString());
    }

    @Override
    public Result writeStoriesEnd() {
        pushSection(5, "Stories archive", "stories", _storiesCount, storiesFilePath());
        if (_stories != null) {
            return _stories.close();
        }
        return Result.Success();
    }

    @Override
    public Result writeContactsList(ApiWrap.ContactsList data) {
        Result result = writeSavedContacts(data);
        if (!result.isSuccess()) {
            return result;
        }
        result = writeFrequentContacts(data);
        if (!result.isSuccess()) {
            return result;
        }
        return Result.Success();
    }

    @Override
    public Result writeOtherData(ApiWrap.File data) {
        pushSection(8, "Other data", "other", 1, data.relativePath);
        return Result.Success();
    }

    private Result writeSavedContacts(ApiWrap.ContactsList data) {
        if (data.list == null || data.list.isEmpty()) {
            return Result.Success();
        }

        final String filename = "lists/contacts.html";
        final HtmlContext file = fileWithRelativePath(filename);
        StringBuilder block = new StringBuilder(file.pushHeader("Contacts", "export_results.html"));
        block.append(file.pushDiv("page_body list_page"));
        block.append(file.pushAbout("_environment.aboutContacts", false));
        block.append(file.pushDiv("entry_list"));
        for (Integer index : DataTypesUtils.SortedContactsIndices(data)) {
            final ApiWrap.ContactInfo contact = data.list.get(index);
            UserpicData userpic = new UserpicData();
            userpic.colorIndex = contact.colorIndex;
            userpic.pixelSize = 48;
            userpic.firstName = contact.firstName;
            userpic.lastName = contact.lastName;
            if (contact.userId != 0) {
                userpic.tooltip = "ID: " + contact.userId;
            }
            block.append(file.pushListEntry(
                    userpic,
                    DataTypesUtils.ComposeName(userpic, "Deleted Account"),
                    PhoneFormat.getInstance().format(contact.phoneNumber),
                    LocaleController.formatDate(contact.date),
                    ""));
        }
        Result result = file.writeBlock(block.toString());
        if (!result.isSuccess()) {
            return result;
        }
        result = file.close();
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(2, "Contacts", "contacts", data.list.size(), filename);
        return Result.Success();
    }

    private Result writeFrequentContacts(ApiWrap.ContactsList data) {
        final int size = data.correspondents.size() + data.inlineBots.size() + data.phoneCalls.size();
        if (size == 0) {
            return Result.Success();
        }

        final String filename = "lists/frequent.html";
        final HtmlContext file = fileWithRelativePath(filename);
        final StringBuilder block = new StringBuilder(file.pushHeader("Frequent contacts", "export_results.html"));
        block.append(file.pushDiv("page_body list_page"));
        block.append(file.pushAbout("_environment.aboutFrequent", false));
        block.append(file.pushDiv("entry_list"));
        final Utilities.Callback2<ArrayList<ApiWrap.TopPeer>, String> writeList = (peers, category) -> {
            for (ApiWrap.TopPeer top : peers) {
                final ApiWrap.Peer peer = top.peer;
                final String firstName;
                if (peer.chat != null) {
                    firstName = peer.name();
                } else if (peer.user.isSelf) {
                    firstName = "Saved messages";
                } else {
                    firstName = peer.user.info.firstName;
                }
                final String lastName = peer.user != null && !peer.user.isSelf ? peer.user.info.lastName : "";

                UserpicData userpic = new UserpicData();
                userpic.colorIndex = DataTypesUtils.PeerColorIndex(peer.id());
                userpic.pixelSize = 48;
                userpic.firstName = firstName;
                userpic.lastName = lastName;
                block.append(file.pushListEntry(
                        userpic,
                        DataTypesUtils.ComposeName(userpic, "Deleted Account"),
                        "Rating: " + top.rating,
                        category,
                        ""));
            }
        };
        writeList.run(data.correspondents, "people");
        writeList.run(data.inlineBots, "inline bots");
        writeList.run(data.phoneCalls, "calls");
        Result result = file.writeBlock(block.toString());
        if (!result.isSuccess()) {
            return result;
        }
        result = file.close();
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(3, "Frequent contacts", "frequent", size, filename);
        return Result.Success();
    }

    @Override
    public Result finish() {
        if (_settings.onlySinglePeer()) {
            return Result.Success();
        }

        Result result = writeSections();
        if (!result.isSuccess()) {
            return result;
        }

        StringBuilder block = new StringBuilder();
        if (_haveSections) {
            block.append(_summary.popTag());
            _summaryNeedDivider = true;
            _haveSections = false;
        }
        block.append(_summary.pushAbout("about telegram bla bla lorum ipsum", _summaryNeedDivider));
        result = _summary.writeBlock(block.toString());
        if (!result.isSuccess()) {
            return result;
        }
        return _summary.close();
    }

    private Result validateDialogsMode(boolean isLeftChannel) {
        final DialogsMode mode = isLeftChannel ? DialogsMode.Left : DialogsMode.Chats;
        if (_dialogsMode == mode) {
            return Result.Success();
        } else if (_dialogsMode != DialogsMode.None) {
            final Result result = _chats.writeBlock(_chats.popTag());
            if (!result.isSuccess()) {
                return result;
            }
        }
        _dialogsMode = mode;
        return _chats.writeBlock(_chats.pushAbout(isLeftChannel ? "left chats" : "just a chat", false) + _chats.pushDiv("entry_list"));
    }

    private String SerializeList(ArrayList<String> values) {
        final int size = values.size();
        if (size == 1) {
            return values.get(0);
        } else if (size > 1) {
            StringBuilder result = new StringBuilder(values.get(0));
            for (int i = 1; i != size - 1; i++) {
                result.append(", ").append(values.get(i));
            }
            return result + " and " + values.get(size - 1);
        }
        return "";
    }

    private String wrapMessageLink(int messageId, String text) {
        final Optional<Integer> file = _lastMessageIdsPerFile.stream()
                .filter(lastId -> messageId <= lastId)
                .findFirst();
        if (!file.isPresent()) {
            return "<a href=\"#go_to_message" + messageId + "\" onclick=\"return GoToMessage(" + messageId + ")\">" + text + "</a>";
        }
        return "<a href=\"" + messagesFile(file.get() - _lastMessageIdsPerFile.get(0)) + "#go_to_message" + messageId + "\">" + text + "</a>";
    }

    private HtmlContext fileWithRelativePath(String path) {
        return new HtmlContext(HtmlContext.pathWithRelativePath(path), _settings.path, _stats);
    }
}
