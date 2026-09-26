package com.exteragram.messenger.export.output.json;

import android.util.Log;
import android.util.Pair;

import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.api.ApiWrap;
import com.exteragram.messenger.export.api.DataTypesUtils;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.FileManager;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.html.HtmlWriter;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class JsonWriter extends AbstractWriter {

    private volatile JsonContext _chat;
    private volatile JsonContext _chatSummary;
    private volatile JsonContext _contacts;
    private ApiWrap.DialogInfo _dialog;
    private HtmlWriter.DialogsMode _dialogsMode = HtmlWriter.DialogsMode.None;
    private int _messagesCount;
    private volatile JsonContext _otherData;
    private volatile JsonContext _sessions;
    private ExportSettings _settings;
    private OutputFile.Stats _stats;
    private volatile JsonContext _stories;
    private JsonContext _summary;

    private static String messagesFile(int index) {
        return "messages" + index + ".json";
    }

    private static String StringAllowNull(String data) {
        return (data == null || data.isEmpty()) ? "null" : JsonContext.SerializeString(data);
    }

    @Override
    public String mainFilePath() {
        return _settings.path + "/result.json";
    }

    @Override
    public Result start(ExportSettings settings, OutputFile.Stats stats) {
        _settings = settings;
        _stats = stats;
        _summary = new JsonContext(fileWithRelativePath("result.json"));
        _contacts = new JsonContext(fileWithRelativePath("contacts.json"));
        _stories = new JsonContext(fileWithRelativePath("stories.json"));
        if (_settings.onlySinglePeer()) {
            return Result.Success();
        }
        return _summary.writeBlock(pushNesting(true, _summary)
                + prepareObjectItemStart("about", _summary)
                + JsonContext.SerializeString("Here is the data you requested. Remember: Telegram is ad free, it doesn't use your data for ad targeting and doesn't sell it to others. Telegram only keeps the information it needs to function as a secure and feature-rich cloud service.\\n\\nCheck out Settings > Privacy & Security on Telegram's mobile apps for the relevant settings."));
    }

    @Override
    public Result writePersonal(ApiWrap.ExportPersonalInfo data) {
        final ApiWrap.ContactInfo info = data.user.info;
        return _summary.writeBlock(prepareObjectItemStart("personal_information", _summary)
                + JsonContext.SerializeObject(_summary,
                        new Pair<>("user_id", data.user.bareId),
                        new Pair<>("first_name", JsonContext.SerializeString(info.firstName)),
                        new Pair<>("last_name", JsonContext.SerializeString(info.lastName)),
                        new Pair<>("phone_number", JsonContext.SerializeString(info.phoneNumber)),
                        new Pair<>("username", !data.user.username.isEmpty() ? JsonContext.SerializeString(data.user.username) : ""),
                        new Pair<>("bio", !data.bio.isEmpty() ? JsonContext.SerializeString(data.bio) : "")));
    }

    @Override
    public Result writeDialogsStart(ApiWrap.DialogsInfo data) {
        StringBuilder block = new StringBuilder(prepareArrayItemStart(_summary));
        _summary._currentNestingHadItem = false;
        block.append(prepareObjectItemStart("chats", _summary));
        block.append(pushNesting(false, _summary));
        boolean first = true;
        for (ApiWrap.DialogInfo dialog : data.chats) {
            if (first) {
                first = false;
            } else {
                block.append(",\n");
            }
            block.append(_summary.SerializeDialog(dialog, false));
        }
        for (ApiWrap.DialogInfo dialog : data.left) {
            if (first) {
                first = false;
            } else {
                block.append(",\n");
            }
            block.append(_summary.SerializeDialog(dialog, true));
        }
        block.append(popNesting(_summary));
        return _summary.writeBlock(block.toString());
    }

    @Override
    public Result writeDialogStart(ApiWrap.DialogInfo data) {
        _chatSummary = new JsonContext(fileWithRelativePath(data.relativePath + "info.json"));
        _chat = new JsonContext(fileWithRelativePath(data.relativePath + messagesFile(0)));
        _summary.writeBlock(prepareObjectItemStart("msgsCount", _summary) + data.messagesCountPerSplit.get(0));
        _messagesCount = 0;
        _dialog = data;

        if (!_settings.onlySinglePeer()) {
            Result result = validateDialogsMode(data.isLeftChannel);
            if (!result.isSuccess()) {
                return result;
            }
        }

        String type;
        switch (data.type) {
            case Self:
                type = "saved_messages";
                break;
            case Replies:
                type = "replies";
                break;
            case VerifyCodes:
                type = "verification_codes";
                break;
            case Personal:
                type = "personal_chat";
                break;
            case Bot:
                type = "bot_chat";
                break;
            case PrivateGroup:
                type = "private_group";
                break;
            case PrivateSupergroup:
                type = "private_supergroup";
                break;
            case PublicSupergroup:
                type = "public_supergroup";
                break;
            case PrivateChannel:
                type = "private_channel";
                break;
            case PublicChannel:
                type = "public_channel";
                break;
            case Unknown:
            default:
                type = "";
                break;
        }

        StringBuilder block = new StringBuilder(_settings.onlySinglePeer() ? "" : prepareArrayItemStart(_chatSummary));
        block.append(pushNesting(true, _chatSummary));
        if (data.type != ApiWrap.DialogInfo.Type.Self && data.type != ApiWrap.DialogInfo.Type.Replies && data.type != ApiWrap.DialogInfo.Type.VerifyCodes) {
            block.append(prepareObjectItemStart("name", _chatSummary)).append(StringAllowNull(data.name));
        }
        block.append(prepareObjectItemStart("type", _chatSummary)).append(StringAllowNull(type));
        block.append(prepareObjectItemStart("id", _chatSummary)).append(data.peerId);
        return _chatSummary.writeBlock(block.toString());
    }

    @Override
    public Result writeDialogSlice(ApiWrap.MessagesSlice data) {
        final int oldIndex = _messagesCount > 0 ? (_messagesCount - 1) / 100 : 0;
        final int newIndex = _messagesCount / 100;
        Log.d("exteraGram", "switching to next chat file! old index: " + oldIndex + ", new index: " + newIndex);
        switchToNextChatFile(newIndex);

        _chat.writeBlock(pushNesting(false, _chat));
        for (int i = 0; i < data.list.size(); i++) {
            ApiWrap.Message message = data.list.get(i);
            if (DataTypesUtils.SkipMessageByDate(message, _settings)) {
                continue;
            }
            _chat.writeBlock(JsonContext.SerializeMessage(_chat, message, data.peers, "https://t.me/"));
            if (i != data.list.size() - 1) {
                _chat._currentNestingHadItem = true;
                _chat.writeBlock(prepareArrayItemStart(_chat));
            }
            _messagesCount++;
        }
        _chat.writeBlock(popNesting(_chat));
        return Result.Success();
    }

    @Override
    public Result writeDialogEnd() {
        _chatSummary.writeBlock(prepareObjectItemStart("msgsCount", _chatSummary) + _messagesCount);
        return _chatSummary.writeBlock(popNesting(_chatSummary));
    }

    @Override
    public Result writeDialogsEnd() {
        return Result.Success();
    }

    @Override
    public Result writeSessionsList(ApiWrap.SessionsList data) {
        _sessions = new JsonContext(fileWithRelativePath("sessions.json"));
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
        return _summary.writeBlock(prepareObjectItemStart("profile_pictures", _summary) + pushNesting(false, _summary));
    }

    @Override
    public Result writeUserpicsSlice(ArrayList<HtmlWriter.Photo> data) {
        StringBuilder block = new StringBuilder();
        for (HtmlWriter.Photo userpic : data) {
            final ApiWrap.File file = userpic.image.file;
            final String path;
            switch (file.skipReason) {
                case Unavailable:
                    path = "(Photo unavailable, please try again later)";
                    break;
                case FileSize:
                    path = "(Photo exceeds maximum size. Change data exporting settings to download.)";
                    break;
                case FileType:
                    path = "(Photo not included. Change data exporting settings to download.)";
                    break;
                case None:
                    path = file.relativePath;
                    break;
                case DateLimits:
                    throw new IllegalStateException("Skip reason while writing photo path.");
                default:
                    throw new IncompatibleClassChangeError();
            }
            block.append(prepareArrayItemStart(_summary));
            block.append(JsonContext.SerializeObject(_summary,
                    new Pair<>("date_unixtime", userpic.date != 0 ? JsonContext.SerializeString(String.valueOf(userpic.date)) : ""),
                    new Pair<>("photo", JsonContext.SerializeString(path))));
        }
        return _summary.writeBlock(block.toString());
    }

    @Override
    public Result writeUserpicsEnd() {
        return _summary.writeBlock(popNesting(_summary));
    }

    @Override
    public Result writeStoriesStart(int count) {
        return _stories.writeBlock(prepareObjectItemStart("stories", _stories) + pushNesting(false, _stories));
    }

    @Override
    public Result writeStoriesSlice(ApiWrap.StoriesSlice data) {
        if (data.list.isEmpty()) {
            return Result.Success();
        }
        StringBuilder block = new StringBuilder();
        for (ApiWrap.Story story : data.list) {
            final ApiWrap.File file = story.file();
            final String path;
            switch (file.skipReason) {
                case Unavailable:
                    path = "(Photo unavailable, please try again later)";
                    break;
                case FileSize:
                    path = "(Photo exceeds maximum size. Change data exporting settings to download.)";
                    break;
                case FileType:
                    path = "(Photo not included. Change data exporting settings to download.)";
                    break;
                case None:
                    path = file.relativePath;
                    break;
                case DateLimits:
                    throw new IllegalStateException("date limited skip reason while writing story path");
                default:
                    throw new IncompatibleClassChangeError();
            }
            block.append(prepareArrayItemStart(_stories));
            block.append(JsonContext.SerializeObject(_stories,
                    new Pair<>("date_unixtime", story.date != 0 ? JsonContext.SerializeString(String.valueOf(story.date)) : ""),
                    new Pair<>("expires_unixtime", story.expires != 0 ? JsonContext.SerializeString(String.valueOf(story.expires)) : ""),
                    new Pair<>("pinned", story.pinned ? "true" : "false"),
                    new Pair<>("media", JsonContext.SerializeString(path))));
        }
        return _stories.writeBlock(block.toString());
    }

    @Override
    public Result writeStoriesEnd() {
        return _stories.writeBlock(popNesting(_stories));
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
        OutputFile file = fileWithRelativePath(data.relativePath);
        String content = FileManager.readFileContent(file._file);
        if (content == null || content.isEmpty()) {
            return Result.Success();
        }
        _otherData = new JsonContext(file);
        prepareObjectItemStart("other_data", _otherData);
        return Result.Success();
    }

    private Result writeSessions(ApiWrap.SessionsList data) {
        StringBuilder block = new StringBuilder(prepareObjectItemStart("sessions", _sessions));
        block.append(pushNesting(true, _sessions));
        block.append(prepareObjectItemStart("about", _sessions));
        block.append(JsonContext.SerializeString("_environment.aboutSessions"));
        block.append(prepareObjectItemStart("list", _sessions));
        block.append(pushNesting(false, _sessions));
        for (TLRPC.TL_authorization session : data.list) {
            block.append(prepareArrayItemStart(_sessions));
            block.append(JsonContext.SerializeObject(_sessions,
                    new Pair<>("last_active", JsonContext.SerializeString(String.valueOf(session.date_active))),
                    new Pair<>("last_ip", JsonContext.SerializeString(session.ip)),
                    new Pair<>("last_country", JsonContext.SerializeString(session.country)),
                    new Pair<>("last_region", JsonContext.SerializeString(session.region)),
                    new Pair<>("application_name", StringAllowNull(session.app_name)),
                    new Pair<>("application_version", session.app_version.isEmpty() ? "" : JsonContext.SerializeString(session.app_version)),
                    new Pair<>("device_model", JsonContext.SerializeString(session.device_model)),
                    new Pair<>("platform", JsonContext.SerializeString(session.platform)),
                    new Pair<>("system_version", JsonContext.SerializeString(session.system_version)),
                    new Pair<>("created", JsonContext.SerializeString(String.valueOf(session.date_created)))));
        }
        block.append(popNesting(_sessions));
        return _sessions.writeBlock(block + popNesting(_sessions));
    }

    private Result writeWebSessions(ApiWrap.SessionsList data) {
        StringBuilder block = new StringBuilder(prepareObjectItemStart("web_sessions", _sessions));
        block.append(pushNesting(true, _sessions));
        block.append(prepareObjectItemStart("about", _sessions));
        block.append(JsonContext.SerializeString("_environment.aboutWebSessions"));
        block.append(prepareObjectItemStart("list", _sessions));
        block.append(pushNesting(false, _sessions));
        for (ApiWrap.WebSession session : data.webList) {
            block.append(prepareArrayItemStart(_sessions));
            block.append(JsonContext.SerializeObject(_sessions,
                    new Pair<>("last_active_unixtime", JsonContext.SerializeString(String.valueOf(session.lastActive()))),
                    new Pair<>("last_ip", JsonContext.SerializeString(session.ip())),
                    new Pair<>("last_region", JsonContext.SerializeString(session.region())),
                    new Pair<>("bot_username", StringAllowNull(session.botUsername())),
                    new Pair<>("domain_name", StringAllowNull(session.domain())),
                    new Pair<>("browser", JsonContext.SerializeString(session.browser())),
                    new Pair<>("platform", JsonContext.SerializeString(session.platform())),
                    new Pair<>("created_unixtime", JsonContext.SerializeString(String.valueOf(session.created())))));
        }
        block.append(popNesting(_sessions));
        return _sessions.writeBlock(block + popNesting(_sessions));
    }

    private Result writeSavedContacts(ApiWrap.ContactsList data) {
        StringBuilder block = new StringBuilder(prepareObjectItemStart("contacts", _contacts));
        block.append(pushNesting(true, _contacts));
        block.append(prepareObjectItemStart("about", _contacts));
        block.append(JsonContext.SerializeString("_environment.aboutContacts"));
        block.append(prepareObjectItemStart("list", _contacts));
        block.append(pushNesting(false, _contacts));
        for (Integer index : DataTypesUtils.SortedContactsIndices(data)) {
            final ApiWrap.ContactInfo contact = data.list.get(index);
            block.append(prepareArrayItemStart(_contacts));
            if (contact.firstName.isEmpty() && contact.lastName.isEmpty() && contact.phoneNumber.isEmpty()) {
                block.append(JsonContext.SerializeObject(_contacts,
                        new Pair<>("date_unixtime", JsonContext.SerializeString(String.valueOf(contact.date)))));
            } else {
                block.append(JsonContext.SerializeObject(_contacts,
                        new Pair<>("user_id", contact.userId != 0 ? String.valueOf(contact.userId) : ""),
                        new Pair<>("first_name", JsonContext.SerializeString(contact.firstName)),
                        new Pair<>("last_name", JsonContext.SerializeString(contact.lastName)),
                        new Pair<>("phone_number", JsonContext.SerializeString(PhoneFormat.getInstance().format(contact.phoneNumber))),
                        new Pair<>("date_unixtime", JsonContext.SerializeString(String.valueOf(contact.date)))));
            }
        }
        block.append(popNesting(_contacts));
        return _contacts.writeBlock(block + popNesting(_contacts));
    }

    private Result writeFrequentContacts(ApiWrap.ContactsList data) {
        final StringBuilder block = new StringBuilder(prepareObjectItemStart("frequent_contacts", _contacts));
        block.append(pushNesting(true, _contacts));
        block.append(prepareObjectItemStart("about", _contacts));
        block.append(JsonContext.SerializeString("_environment.aboutFrequent"));
        block.append(prepareObjectItemStart("list", _contacts));
        block.append(pushNesting(false, _contacts));

        final Utilities.Callback2<ArrayList<ApiWrap.TopPeer>, String> writeList = (peers, category) -> {
            for (ApiWrap.TopPeer top : peers) {
                final ApiWrap.Chat chat = top.peer.chat;
                final String type;
                if (chat == null) {
                    type = "user";
                } else if (chat.username.isEmpty()) {
                    if (chat.isBroadcast) {
                        type = "private_channel";
                    } else if (chat.isSupergroup) {
                        type = "private_supergroup";
                    } else {
                        type = "private_group";
                    }
                } else if (chat.isBroadcast) {
                    type = "public_channel";
                } else {
                    type = "public_supergroup";
                }
                block.append(prepareArrayItemStart(_contacts));
                block.append(JsonContext.SerializeObject(_contacts,
                        new Pair<>("id", String.valueOf(top.peer.id())),
                        new Pair<>("category", JsonContext.SerializeString(category)),
                        new Pair<>("type", JsonContext.SerializeString(type)),
                        new Pair<>("name", StringAllowNull(top.peer.name())),
                        new Pair<>("rating", String.valueOf(top.rating))));
            }
        };
        writeList.run(data.correspondents, "people");
        writeList.run(data.inlineBots, "inline_bots");
        writeList.run(data.phoneCalls, "calls");

        block.append(popNesting(_contacts));
        return _contacts.writeBlock(block + popNesting(_contacts));
    }

    private Result switchToNextChatFile(int index) {
        _chat = new JsonContext(fileWithRelativePath(_dialog.relativePath + messagesFile(index)));
        return Result.Success();
    }

    @Override
    public Result finish() {
        if (_settings.onlySinglePeer()) {
            return Result.Success();
        }
        return _summary.writeBlock(popNesting(_summary));
    }

    private OutputFile fileWithRelativePath(String path) {
        return new OutputFile(_settings.path + "/" + path, _stats);
    }

    private String pushNesting(Boolean object, JsonContext context) {
        context.nesting.add(object);
        context._currentNestingHadItem = false;
        return object ? "{" : "[";
    }

    private String prepareObjectItemStart(String key, JsonContext context) {
        try {
            return (context._currentNestingHadItem ? ",\n" : "\n")
                    + JsonContext.Indentation(context)
                    + JsonContext.SerializeString(key)
                    + ": ";
        } finally {
            context._currentNestingHadItem = true;
        }
    }

    private Result writeChatsEnd() {
        return Result.Success();
    }

    private String popNesting(JsonContext context) {
        final Boolean type = context.nesting.get(context.nesting.size() - 1);
        context.nesting.remove(context.nesting.size() - 1);
        context._currentNestingHadItem = true;
        return "\n" + JsonContext.Indentation(context) + (type ? '}' : ']');
    }

    public Result validateDialogsMode(boolean isLeftChannel) {
        final HtmlWriter.DialogsMode mode = isLeftChannel ? HtmlWriter.DialogsMode.Left : HtmlWriter.DialogsMode.Chats;
        if (_dialogsMode == mode) {
            return Result.Success();
        } else if (_dialogsMode != HtmlWriter.DialogsMode.None) {
            Result result = writeChatsEnd();
            if (!result.isSuccess()) {
                return result;
            }
        }
        _dialogsMode = mode;
        return writeChatsStart(
                isLeftChannel ? "left_chats" : "chats",
                isLeftChannel
                        ? "Below are the supergroups and channels from this export that you've left or where you were banned.\\n\\nNote that when you leave a channel or supergroup you've created, you have the option to either delete it, or simply leave (in case you want to rejoin later, or keep the community alive despite not being a member)."
                        : "This page lists all chats from this export.");
    }

    private Result writeChatsStart(String listName, String about) {
        return Result.Success();
    }

    private String prepareArrayItemStart(JsonContext context) {
        try {
            return (context._currentNestingHadItem ? ",\n" : "\n") + JsonContext.Indentation(context);
        } finally {
            context._currentNestingHadItem = true;
        }
    }
}
