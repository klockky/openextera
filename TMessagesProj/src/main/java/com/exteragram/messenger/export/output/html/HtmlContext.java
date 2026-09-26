package com.exteragram.messenger.export.output.html;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.exteragram.messenger.export.api.ApiWrap;
import com.exteragram.messenger.export.api.DataTypesUtils;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.FileManager;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.google.zxing.Dimension;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class HtmlContext {

    private final String _base;
    private final String _composedStart;
    private final OutputFile _file;
    private final ArrayList<ApiWrap.Tag> _tags = new ArrayList<>();
    private boolean _closed = false;

    public HtmlContext(String path, String basePath, OutputFile.Stats stats) {
        _file = new OutputFile(path, stats);
        final String relative = path.substring(basePath.length() + 1);
        final int depth = relative.length() - relative.replace("/", "").length();
        _base = "../".repeat(depth);
        _composedStart = composeStart();
    }

    public static String pathWithRelativePath(String path) {
        return FileManager.defaultSavePath + "/" + path;
    }

    public static String SerializeString(String value) {
        StringBuilder result = new StringBuilder();
        final int size = value.length();
        final char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (c == '\n') {
                result.append("<br>");
            } else if (c == '"') {
                result.append("&quot;");
            } else if (c == '&') {
                result.append("&amp;");
            } else if (c == '\'') {
                result.append("&apos;");
            } else if (c == '<') {
                result.append("&lt;");
            } else if (c == '>') {
                result.append("&gt;");
            } else if (c < ' ') {
                result.append("&#x");
                result.append((c >> 4) + '0');
                final int low = c & 0x0F;
                if (low >= 10) {
                    result.append(low + 'A' - 10);
                } else {
                    result.append(low + '0');
                }
                result.append(';');
            } else if (c == 0xE2 && i + 2 < size && chars[i + 1] == 0x80) {
                if (chars[i + 2] == 0xA8) { // Line separator.
                    result.append("<br>");
                } else if (chars[i + 2] == 0xA9) { // Paragraph separator.
                    result.append("<br>");
                } else {
                    result.append(c);
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static Pair<String, Dimension> WriteImageThumb(String basePath, String largePath, Function<Dimension, Dimension> convertSize, String format, int quality, String postfix) {
        if (postfix == null) {
            postfix = "_thumb";
        }
        if (largePath.isEmpty()) {
            return new Pair<>(null, null);
        }
        final String path = basePath + "/" + largePath;
        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap image = BitmapFactory.decodeFile(path, options);
        if (new File(path).length() == 0 || options.outWidth >= 10000 || options.outHeight >= 10000) {
            Log.e("exteraGram", "width or height are more than 10000, path: " + path);
            return new Pair<>(null, null);
        }
        final Dimension finalSize = convertSize.apply(new Dimension(options.outWidth, options.outHeight));
        if (finalSize == null) {
            return new Pair<>(null, null);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(image, finalSize.getWidth(), finalSize.getHeight(), true);
        if (quality == -1) {
            quality = 100;
        }

        final int dot = largePath.indexOf('.', largePath.lastIndexOf('/') + 1);
        final String thumb;
        if (dot >= 0) {
            thumb = largePath.substring(0, dot) + postfix + largePath.substring(dot);
        } else {
            thumb = largePath + postfix;
        }
        final String result = OutputFile.PrepareRelativePath(basePath, thumb);
        try {
            File file = new File(basePath + "/" + result);
            file.createNewFile();
            FileOutputStream stream = new FileOutputStream(file);
            scaled.compress("PNG".equals(format) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, quality, stream);
            stream.flush();
            stream.close();
            image.recycle();
            scaled.recycle();
            return new Pair<>(result, finalSize);
        } catch (Exception e) {
            FileLog.e(e);
            throw new IllegalStateException(e);
        }
    }

    public static Function<Dimension, Dimension> CalculateThumbSize(int maxWidth, int maxHeight, int minWidth, int minHeight, boolean allowStretch) {
        return largeSize -> {
            final int multiplier = allowStretch ? 2 : 1;
            final int width = largeSize.getWidth() * multiplier;
            final int height = largeSize.getHeight() * multiplier;
            final Dimension size;
            if (width > maxWidth || height > maxHeight) {
                final double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
                size = new Dimension((int) (width * scale), (int) (height * scale));
            } else {
                size = new Dimension(largeSize.getWidth(), largeSize.getHeight());
            }
            final Dimension result = new Dimension(
                    size.getWidth() % 2 == 0 ? size.getWidth() : size.getWidth() - 1,
                    size.getHeight() % 2 == 0 ? size.getHeight() : size.getHeight() - 1
            );
            return (result.getWidth() < minWidth || result.getHeight() < minHeight) ? new Dimension(0, 0) : result;
        };
    }

    private static String countryToEmoji(String countryCode) {
        final int offset = 0x1F1E6 - 'A';
        return new String(Character.toChars(Character.codePointAt(countryCode, 0) + offset))
                + new String(Character.toChars(Character.codePointAt(countryCode, 1) + offset));
    }

    public String relativePath(String path) {
        return _base + path;
    }

    public String pushDiv(String className) {
        return pushDiv(className, "");
    }

    public String pushDiv(String className, String style) {
        if (style.isEmpty()) {
            return pushTag("div", new Pair<>("class", className));
        }
        return pushTag("div", new Pair<>("class", className), new Pair<>("style", style));
    }

    public String composeStart() {
        StringBuilder result = new StringBuilder("<!DOCTYPE html>" + pushTag("html"));
        result.append(pushTag("head"));
        result.append(pushTag("meta", new Pair<>("charset", "utf-8"), new Pair<>("empty", "")));
        result.append(pushTag("title", new Pair<>("inline", "")));
        result.append("Exported Data");
        result.append(popTag());
        result.append(pushTag("meta", new Pair<>("name", "viewport"), new Pair<>("content", "width=device-width, initial-scale=1.0"), new Pair<>("empty", "")));
        result.append(pushTag("link", new Pair<>("href", relativePath("css/style.css")), new Pair<>("rel", "stylesheet"), new Pair<>("empty", "")));
        result.append(pushTag("script", new Pair<>("src", relativePath("js/script.js")), new Pair<>("type", "text/javascript")));
        result.append(popTag());
        result.append(popTag());
        result.append(pushTag("body", new Pair<>("onload", "CheckLocation();")));
        result.append(pushDiv("page_wrap", ""));
        return result.toString();
    }

    @SafeVarargs
    public final String pushTag(String name, Pair<String, String>... attributes) {
        ApiWrap.Tag tag = new ApiWrap.Tag();
        tag.name = name;
        StringBuilder attributesString = new StringBuilder();
        boolean empty = false;
        for (Pair<String, String> attribute : attributes) {
            final String key = attribute.first;
            final String value = attribute.second;
            if (key.equals("inline")) {
                tag.block = false;
            } else if (key.equals("empty")) {
                empty = true;
            } else {
                attributesString.append(' ').append(key).append("=\"").append(SerializeString(value)).append("\"");
            }
        }
        final String result = (tag.block ? "\n" + indent() : "")
                + "<" + tag.name + attributesString + (empty ? "/" : "") + ">"
                + (tag.block ? "\n" : "");
        if (!empty) {
            _tags.add(tag);
        }
        return result;
    }

    public String pushListEntry(HtmlWriter.UserpicData userpic, String name, String details, String info, String link) {
        return pushGenericListEntry(link, userpic, name, null, Collections.singletonList(details), info);
    }

    public String pushSessionListEntry(int apiId, String name, String subname, ArrayList<String> details, String link) {
        HtmlWriter.UserpicData userpic = new HtmlWriter.UserpicData();
        userpic.colorIndex = DataTypesUtils.ApplicationColorIndex(apiId);
        userpic.pixelSize = 48;
        userpic.firstName = name;
        return pushGenericListEntry("", userpic, name, subname, details, link);
    }

    public String pushStoriesListEntry(ApiWrap.StoryData story, String name, ArrayList<String> details, String info, ArrayList<ApiWrap.TextPart> caption, String internalLinksDomain, String link) {
        StringBuilder result = new StringBuilder(pushDiv("entry clearfix"));
        if (!link.isEmpty()) {
            result.append(pushTag("a", new Pair<>("class", "pull_left userpic_wrap"), new Pair<>("href", relativePath(link) + "#allow_back")));
        } else {
            result.append(pushDiv("pull_left userpic_wrap"));
        }
        if (!story.imageLink.isEmpty()) {
            result.append(pushTag("img",
                    new Pair<>("class", "story"),
                    new Pair<>("style", "width: 45px; height: 80px"),
                    new Pair<>("src", relativePath(story.imageLink)),
                    new Pair<>("empty", "")));
        }
        result.append(popTag());
        result.append(pushDiv("body"));
        if (!info.isEmpty()) {
            result.append(pushDiv("pull_right info details"));
            result.append(SerializeString(info));
            result.append(popTag());
        }
        if (!name.isEmpty()) {
            if (!link.isEmpty()) {
                result.append(pushTag("a", new Pair<>("class", "block_link expanded"), new Pair<>("href", relativePath(link) + "#allow_back")));
            }
            result.append(pushDiv("name bold"));
            result.append(SerializeString(name));
            result.append(popTag());
            if (!link.isEmpty()) {
                result.append(popTag());
            }
        }
        final String text = caption.isEmpty() ? "" : DataTypesUtils.FormatText(caption, internalLinksDomain, _base);
        if (!text.isEmpty()) {
            result.append(pushDiv("text"));
            result.append(text);
            result.append(popTag());
        }
        for (String detail : details) {
            result.append(pushDiv("details_entry details"));
            result.append(SerializeString(detail));
            result.append(popTag());
        }
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    public String pushGenericListEntry(String link, HtmlWriter.UserpicData userpic, String name, String subname, List<String> details, String info) {
        StringBuilder result = new StringBuilder(link.isEmpty()
                ? pushDiv("entry clearfix")
                : pushTag("a", new Pair<>("class", "entry block_link clearfix"), new Pair<>("href", relativePath(link) + "#allow_back")));
        result.append(pushDiv("pull_left userpic_wrap"));
        result.append(pushUserpic(userpic));
        result.append(popTag());
        result.append(pushDiv("body"));
        if (!info.isEmpty()) {
            result.append(pushDiv("pull_right info details"));
            result.append(SerializeString(info));
            result.append(popTag());
        }
        if (!name.isEmpty()) {
            result.append(pushDiv("name bold"));
            result.append(SerializeString(name));
            result.append(popTag());
        }
        if (subname != null && !subname.isEmpty()) {
            result.append(pushDiv("subname bold"));
            result.append(SerializeString(subname));
            result.append(popTag());
        }
        for (String detail : details) {
            result.append(pushDiv("details_entry details"));
            result.append(SerializeString(detail));
            result.append(popTag());
        }
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    public String indent() {
        return " ".repeat(_tags.size());
    }

    public String popTag() {
        final ApiWrap.Tag tag = _tags.remove(_tags.size() - 1);
        return (tag.block ? "\n" + indent() : "")
                + "</" + tag.name + ">"
                + (tag.block ? "\n" : "");
    }

    public boolean isTagsEmpty() {
        return _tags.isEmpty();
    }

    public String pushUserpic(HtmlWriter.UserpicData userpic) {
        final String size = userpic.pixelSize + "px";
        StringBuilder result = new StringBuilder();
        if (!userpic.largeLink.isEmpty()) {
            result.append(pushTag("a", new Pair<>("class", "userpic_link"), new Pair<>("href", pathWithRelativePath(userpic.largeLink))));
        }
        final String sizeStyle = "width: " + size + "; height: " + size;
        if (userpic.imageLink != null && !userpic.imageLink.isEmpty()) {
            result.append(pushTag("img",
                    new Pair<>("class", "userpic"),
                    new Pair<>("style", sizeStyle),
                    new Pair<>("src", pathWithRelativePath(userpic.imageLink)),
                    new Pair<>("empty", "")));
        } else {
            result.append(pushTag("div",
                    new Pair<>("class", "userpic userpic" + (userpic.colorIndex + 1)),
                    new Pair<>("style", sizeStyle)));
            if (userpic.tooltip.isEmpty()) {
                result.append(pushDiv("initials", "line-height: " + size));
            } else {
                result.append(pushTag("div",
                        new Pair<>("class", "initials"),
                        new Pair<>("style", "line-height: " + size),
                        new Pair<>("title", userpic.tooltip)));
            }
            final String firstName = userpic.firstName.trim();
            result.append(firstName.isEmpty() ? "" : SerializeString(firstName.substring(0, 1)));
            final String lastName = userpic.lastName.trim();
            result.append(lastName.isEmpty() ? "" : SerializeString(lastName.substring(0, 1)));
            result.append(popTag());
            result.append(popTag());
        }
        if (!userpic.largeLink.isEmpty()) {
            result.append(popTag());
        }
        return result.toString();
    }

    public AbstractWriter.Result writeBlock(ArrayList<String> blocks) {
        for (String block : blocks) {
            if (!writeBlock(block).isSuccess()) {
                throw new IllegalStateException("writeBlock : " + block);
            }
        }
        return AbstractWriter.Result.Success();
    }

    public AbstractWriter.Result writeBlock(String block) {
        if (_closed) {
            throw new IllegalStateException("file is closed!");
        }
        final AbstractWriter.Result result;
        if (block.isEmpty()) {
            result = _file.writeBlock(block);
        } else if (_file.empty()) {
            result = _file.writeBlock(_composedStart + block);
        } else {
            result = _file.writeBlock(block);
        }
        if (!result.isSuccess()) {
            _closed = true;
        }
        return result;
    }

    public AbstractWriter.Result close() {
        if (_closed || _file.empty()) {
            return AbstractWriter.Result.Success();
        }
        _closed = true;
        StringBuilder block = new StringBuilder();
        while (!isTagsEmpty()) {
            block.append(popTag());
        }
        return _file.writeBlock(block.toString());
    }

    public String pushHeader(String header, String path) {
        StringBuilder result = new StringBuilder(pushDiv("page_header", ""));
        if (path.isEmpty()) {
            result.append(pushDiv("content", ""));
        } else {
            result.append(pushTag("a",
                    new Pair<>("class", "content block_link"),
                    new Pair<>("href", relativePath(path)),
                    new Pair<>("onclick", "return GoBack(this)")));
        }
        result.append(pushDiv("text bold", ""));
        result.append(SerializeString(header));
        result.append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    public String pushSection(String header, String type, int count, String link) {
        return pushTag("a", new Pair<>("class", "section block_link " + type), new Pair<>("href", link + "#allow_back"))
                + pushDiv("counter details", "")
                + count
                + popTag()
                + pushDiv("label bold", "")
                + SerializeString(header)
                + popTag()
                + popTag();
    }

    public String pushAbout(String text, boolean withDivider) {
        return pushDiv(withDivider ? "page_about details with_divider" : "page_about details", "")
                + HtmlWriter.MakeLinks(SerializeString(text))
                + popTag();
    }

    public String pushServiceMessage(int messageId, ApiWrap.DialogInfo dialog, String basePath, String serialized, HtmlWriter.Photo photo) {
        StringBuilder result = new StringBuilder(pushTag("div",
                new Pair<>("class", "message service"),
                new Pair<>("id", "message" + messageId)));
        result.append(pushDiv("body details"));
        result.append(serialized);
        result.append(popTag());
        if (photo != null) {
            HtmlWriter.UserpicData userpic = new HtmlWriter.UserpicData();
            userpic.colorIndex = dialog.colorIndex;
            userpic.firstName = dialog.name;
            userpic.lastName = dialog.lastName;
            userpic.pixelSize = 60;
            final String path = photo.image.file.relativePath;
            userpic.largeLink = path;
            userpic.imageLink = HtmlWriter.WriteUserpicThumb(basePath, path, userpic);
            result.append(pushDiv("userpic_wrap"));
            result.append(pushUserpic(userpic));
            result.append(popTag());
        }
        result.append(popTag());
        return result.toString();
    }

    public Pair<HtmlWriter.MessageInfo, String> pushMessage(ApiWrap.Message message, HtmlWriter.MessageInfo previous, ApiWrap.DialogInfo dialog, String basePath, HashMap<Long, ApiWrap.Peer> peers, HtmlWriter writer, String internalLinksDomain, Utilities.Callback2Return<Integer, String, String> wrapMessageLink) {
        if (message == null || dialog == null) {
            return null;
        }

        HtmlWriter.MessageInfo info = new HtmlWriter.MessageInfo();
        info.id = message.id;
        info.fromId = message.fromId;
        info.viaBotId = message.viaBotId;
        info.date = message.date;
        info.forwardedFromId = message.forwardedFromId;
        info.forwardedFromName = message.forwardedFromName;
        info.forwardedDate = message.forwardedDate;
        info.forwarded = message.forwarded;
        info.showForwardedAsOriginal = message.showForwardedAsOriginal;

        if (message.media.content instanceof ApiWrap.UnsupportedMedia) {
            return new Pair<>(info, pushServiceMessage(message.id, dialog, basePath, LocaleController.getString(R.string.UnsupportedMedia2), null));
        }

        final boolean isChannel = dialog.type == ApiWrap.DialogInfo.Type.PrivateChannel || dialog.type == ApiWrap.DialogInfo.Type.PublicChannel;
        final String serviceText = writer.getTextFromAction(message, HtmlWriter.wrapPeerName(message.fromId), isChannel);
        if (!serviceText.isEmpty()) {
            HtmlWriter.Photo photo = null;
            if (message.parsedAction instanceof ApiWrap.ActionSuggestProfilePhoto) {
                photo = ((ApiWrap.ActionSuggestProfilePhoto) message.parsedAction).photo();
            } else if (message.parsedAction instanceof ApiWrap.ActionChatEditPhoto) {
                photo = ((ApiWrap.ActionChatEditPhoto) message.parsedAction).photo();
            }
            return new Pair<>(info, pushServiceMessage(message.id, dialog, basePath, serviceText, photo));
        }

        info.type = HtmlWriter.MessageInfo.Type.Default;

        final boolean wrap = DataTypesUtils.messageNeedsWrap(message, previous);
        final long fromPeerId = message.fromId;
        final boolean showForwardedInfo = message.forwarded && !message.showForwardedAsOriginal;

        HtmlWriter.UserpicData forwardedUserpic = new HtmlWriter.UserpicData();
        if (message.forwarded) {
            forwardedUserpic.colorIndex = message.forwardedFromId != 0
                    ? DataTypesUtils.PeerColorIndex(message.forwardedFromId)
                    : DataTypesUtils.PeerColorIndex(message.id);
            forwardedUserpic.pixelSize = 42;
            if (message.forwardedFromId != 0) {
                DataTypesUtils.FillUserpicNames(forwardedUserpic, peers.get(message.forwardedFromId));
            } else {
                DataTypesUtils.FillUserpicNames(forwardedUserpic, message.forwardedFromName);
            }
        }

        HtmlWriter.UserpicData userpic = new HtmlWriter.UserpicData();
        if (message.showForwardedAsOriginal) {
            userpic = forwardedUserpic;
        } else {
            userpic.colorIndex = DataTypesUtils.PeerColorIndex(fromPeerId);
            userpic.pixelSize = 42;
            DataTypesUtils.FillUserpicNames(userpic, peers.get(fromPeerId));
        }

        StringBuilder block = new StringBuilder(pushTag("div",
                new Pair<>("class", wrap ? "message default clearfix" : "message default clearfix joined"),
                new Pair<>("id", "message" + message.id)));
        if (wrap) {
            block.append(pushDiv("pull_left userpic_wrap"));
            block.append(pushUserpic(userpic));
            block.append(popTag());
        }
        block.append(pushDiv("body"));
        block.append(pushTag("div",
                new Pair<>("class", "pull_right date details"),
                new Pair<>("title", LocaleController.getInstance().getExportFullDateFormatter().format((long) message.date * 1000))));
        block.append(LocaleController.getInstance().getFormatterDay().format((long) message.date * 1000));
        block.append(popTag());
        if (wrap) {
            block.append(pushDiv("from_name"));
            block.append(SerializeString(DataTypesUtils.ComposeName(userpic, "Deleted Account")));
            block.append(popTag());
        }
        if (showForwardedInfo) {
            final boolean forwardedWrap = DataTypesUtils.forwardedNeedsWrap(message, previous);
            if (forwardedWrap) {
                block.append(pushDiv("pull_left forwarded userpic_wrap"));
                block.append(pushUserpic(forwardedUserpic));
                block.append(popTag());
            }
            block.append(pushDiv("forwarded body"));
            if (forwardedWrap) {
                block.append(pushDiv("from_name"));
                block.append(SerializeString(DataTypesUtils.ComposeName(forwardedUserpic, "Deleted Account")));
                block.append(pushTag("span",
                        new Pair<>("class", "date details"),
                        new Pair<>("title", LocaleController.formatDate(message.forwardedDate)),
                        new Pair<>("inline", "")));
                block.append(" ").append(LocaleController.formatDate(message.forwardedDate));
                block.append(popTag());
                block.append(popTag());
            }
        }
        if (message.replyToMsgId != 0) {
            block.append(pushDiv("reply_to details"));
            if (message.replyToPeerId != 0) {
                block.append("In reply to a message in another chat");
            } else {
                block.append("In reply to ");
                block.append(wrapMessageLink.run(message.replyToMsgId, "this message"));
            }
            block.append(popTag());
        }

        block.append(pushMedia(message, basePath, peers, internalLinksDomain, wrapMessageLink));

        final String text = DataTypesUtils.FormatText(message.text, internalLinksDomain, _base);
        if (!text.isEmpty()) {
            block.append(pushDiv("text"));
            block.append(text);
            block.append(popTag());
        }

        if (message.inlineButtonRows != null && !message.inlineButtonRows.isEmpty()) {
            block.append(pushTag("table", new Pair<>("class", "bot_button_table")));
            block.append(pushTag("tbody"));
            for (ArrayList<ApiWrap.HistoryMessageMarkupButton> row : message.inlineButtonRows) {
                block.append(pushTag("tr"));
                block.append(pushTag("td", new Pair<>("class", "bot_button_row")));
                for (ApiWrap.HistoryMessageMarkupButton button : row) {
                    String descriptor = "";
                    if (button.data() != null && button.data().length != 0) {
                        descriptor = "Data: " + ChatUtils.getInstance().getTextFromCallback(button.data()) + " | ";
                    }
                    if (button.forwardText() != null && !button.forwardText().isEmpty()) {
                        descriptor += "Forward text: " + button.forwardText() + " | ";
                    }
                    descriptor += "Type: " + ApiWrap.HistoryMessageMarkupButton.TypeToString(button);

                    final boolean isUrl = button.type() == ApiWrap.HistoryMessageMarkupButton.Type.Url;
                    final String link = isUrl ? ChatUtils.getInstance().getTextFromCallback(button.data()) : "";
                    final String onClick = !isUrl ? "return ShowTextCopied('" + descriptor + "');" : "";

                    block.append(pushTag("div", new Pair<>("class", "bot_button")));
                    block.append(pushTag("a",
                            link.isEmpty() ? new Pair<>("", "") : new Pair<>("href", link),
                            onClick.isEmpty() ? new Pair<>("", "") : new Pair<>("onclick", onClick)));
                    block.append(pushTag("div"));
                    block.append(button.text());
                    block.append(popTag());
                    block.append(popTag());
                    block.append(popTag());

                    if (button != row.get(row.size() - 1)) {
                        block.append(pushTag("div", new Pair<>("class", "bot_button_column_separator")));
                        block.append(popTag());
                    }
                }
                block.append(popTag());
                block.append(popTag());
            }
            block.append(popTag());
            block.append(popTag());
        }

        if (!message.signature.isEmpty()) {
            block.append(pushDiv("signature details"));
            block.append(SerializeString(message.signature));
            block.append(popTag());
        }
        if (showForwardedInfo) {
            block.append(popTag());
        }

        if (!message.reactions.isEmpty()) {
            block.append(pushDiv("reactions"));
            for (ApiWrap.Reaction reaction : message.reactions) {
                // TODO(openextera): decompile failed, verify - ApiWrap.Reaction is never instantiated in lite,
                //  so R8 dropped the whole reaction rendering (fields, userpics, counters) from this loop.
            }
            block.append(popTag());
        }

        block.append(popTag());
        block.append(popTag());

        return new Pair<>(info, block.toString());
    }

    public String pushMedia(ApiWrap.Message message, String basePath, HashMap<Long, ApiWrap.Peer> peers, String internalLinksDomain, Utilities.Callback2Return<Integer, String, String> wrapMessageLink) {
        final ApiWrap.MediaData data = prepareMediaData(message, basePath, peers, internalLinksDomain);
        if (data.classes == null || !data.classes.isEmpty()) {
            return pushGenericMedia(data);
        }
        final Object content = message.media.content;
        if (content instanceof ApiWrap.Document) {
            final ApiWrap.Document document = (ApiWrap.Document) content;
            if (document.isSticker) {
                return pushStickerMedia(document, basePath);
            } else if (document.isAnimated) {
                return pushAnimatedMedia(document, basePath);
            } else if (document.isVideoFile) {
                return pushVideoFileMedia(document, basePath);
            }
            throw new RuntimeException("Non generic document in pushMedia.");
        } else if (content instanceof HtmlWriter.Photo) {
            return pushPhotoMedia((HtmlWriter.Photo) content, basePath);
        } else if (content instanceof ApiWrap.Poll) {
            return pushPoll((ApiWrap.Poll) content);
        } else if (content instanceof ApiWrap.GiveawayStart) {
            return pushGiveaway(peers, (ApiWrap.GiveawayStart) content);
        } else if (content instanceof ApiWrap.GiveawayResults) {
            return pushGiveaway(peers, (ApiWrap.GiveawayResults) content, wrapMessageLink);
        }
        return "";
    }

    private String pushStickerMedia(ApiWrap.Document data, String basePath) {
        final Pair<String, Dimension> thumb = WriteImageThumb(basePath, data.file.relativePath, CalculateThumbSize(384, 384, 80, 80, false), "PNG", -1, "");
        final String image = thumb.first;
        final Dimension size = thumb.second;
        if (image == null || image.isEmpty()) {
            ApiWrap.MediaData generic = new ApiWrap.MediaData();
            generic.title = "Sticker";
            generic.status = data.stickerEmoji;
            if (data.file.relativePath.isEmpty()) {
                if (!generic.status.isEmpty()) {
                    generic.status += ", ";
                }
                generic.status += AndroidUtilities.formatFileSize(data.file.size);
            } else {
                generic.link = data.file.relativePath;
            }
            generic.description = DataTypesUtils.NoFileDescription(data.file.skipReason);
            generic.classes = "media_photo";
            return pushGenericMedia(generic);
        }
        return pushDiv("media_wrap clearfix")
                + pushTag("a",
                        new Pair<>("class", "sticker_wrap clearfix pull_left"),
                        new Pair<>("href", data.file.relativePath))
                + pushTag("img",
                        new Pair<>("class", "sticker"),
                        new Pair<>("style", "width: " + DataTypesUtils.NumberToString(size.getWidth() / 2) + "px; height: " + DataTypesUtils.NumberToString(size.getHeight() / 2) + "px"),
                        new Pair<>("src", image),
                        new Pair<>("empty", ""))
                + popTag()
                + popTag();
    }

    private String pushAnimatedMedia(ApiWrap.Document data, String basePath) {
        final Dimension size = new Dimension(data.width, data.height);
        final Function<Dimension, Dimension> convert = CalculateThumbSize(520, 520, 80, 80, true);
        if (data.thumb.file.relativePath.isEmpty()
                || data.file.relativePath.isEmpty()
                || convert.apply(size).getWidth() == 0
                || convert.apply(size).getHeight() == 0) {
            ApiWrap.MediaData generic = new ApiWrap.MediaData();
            generic.title = "Animation";
            generic.status = AndroidUtilities.formatFileSize(data.file.size);
            generic.link = data.file.relativePath;
            generic.description = DataTypesUtils.NoFileDescription(data.file.skipReason);
            generic.classes = "media_video";
            return pushGenericMedia(generic);
        }
        return pushDiv("media_wrap clearfix")
                + pushTag("a",
                        new Pair<>("class", "animated_wrap clearfix pull_left"),
                        new Pair<>("href", relativePath(data.file.relativePath)))
                + pushDiv("video_play_bg")
                + pushDiv("gif_play")
                + "GIF"
                + popTag()
                + popTag()
                + pushTag("img",
                        new Pair<>("class", "animated"),
                        new Pair<>("style", "width: " + DataTypesUtils.NumberToString(convert.apply(size).getWidth() / 2) + "px; height: " + DataTypesUtils.NumberToString(convert.apply(size).getHeight() / 2) + "px"),
                        new Pair<>("src", data.thumb.file.relativePath),
                        new Pair<>("empty", ""))
                + popTag()
                + popTag();
    }

    private ApiWrap.MediaData prepareMediaData(ApiWrap.Message message, String basePath, HashMap<Long, ApiWrap.Peer> peers, String internalLinksDomain) {
        ApiWrap.MediaData result = new ApiWrap.MediaData();

        if (message.action instanceof TLRPC.TL_messageActionPhoneCall) {
            final TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) message.action;
            result.classes = "media_call";
            final ApiWrap.Peer peer = peers.get(message.out ? message.peerId : message.selfId);
            result.title = peer != null ? peer.name() : "";
            final boolean missed = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed;
            final boolean busy = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy;
            if (message.out) {
                result.status = missed ? "Cancelled" : "Outgoing";
            } else if (missed) {
                result.status = "Missed";
            } else if (busy) {
                result.status = "Declined";
            } else {
                result.status = "Incoming";
            }
            if (call.duration > 0) {
                result.classes += " success";
                result.status += " (" + LocaleController.formatCallDuration(call.duration) + " seconds)";
            }
            return result;
        }

        final ApiWrap.Media media = message.media;
        final Object content = media.content;
        if (content instanceof HtmlWriter.Photo) {
            final HtmlWriter.Photo photo = (HtmlWriter.Photo) content;
            if (media.ttl != 0) {
                result.title = "Self-destructing photo";
                result.status = photo.id == 0 ? "Please view it on your mobile" : "Expired";
                result.classes = "media_photo";
            }
        } else if (content instanceof ApiWrap.Document) {
            final ApiWrap.Document document = (ApiWrap.Document) content;
            if (media.ttl != 0) {
                result.title = "Self-destructing video";
                result.status = document.id != 0 ? "Please view it on your mobile" : "Expired";
                result.classes = "media_video";
                return result;
            }
            final boolean noFile = document.file.relativePath.isEmpty();
            result.link = document.file.relativePath;
            result.description = DataTypesUtils.NoFileDescription(message.skipReason);
            if (document.isSticker) {
                return result;
            } else if (document.isVideoMessage) {
                result.title = "Video message";
                result.status = LocaleController.formatDuration(document.duration);
                if (noFile) {
                    result.status += ", " + AndroidUtilities.formatFileSize(document.file.size);
                }
                result.thumb = document.thumb.file.relativePath;
                result.classes = "media_video";
            } else if (document.isVoiceMessage) {
                result.title = "Voice message";
                result.status = LocaleController.formatDuration(document.duration);
                if (noFile) {
                    result.status += ", " + AndroidUtilities.formatFileSize(document.file.size);
                }
                result.classes = "media_voice_message";
            } else if (document.isAnimated || document.isVideoFile) {
                return result;
            } else if (document.isAudioFile) {
                final boolean hasTitle = document.songPerformer != null && !document.songPerformer.isEmpty()
                        && document.songTitle != null && !document.songTitle.isEmpty();
                result.title = hasTitle ? document.songPerformer + " – " + document.songTitle : "Audio file";
                result.status = AndroidUtilities.formatLongDuration(document.duration);
                if (noFile) {
                    result.status += ", " + AndroidUtilities.formatFileSize(document.file.size);
                }
                result.classes = "media_audio_file";
            } else {
                result.title = document.name == null || document.name.isEmpty() ? "File" : document.name;
                result.status = AndroidUtilities.formatFileSize(document.file.size);
                result.classes = "media_file";
            }
        } else if (content instanceof ApiWrap.SharedContact) {
            final ApiWrap.SharedContact contact = (ApiWrap.SharedContact) content;
            result.title = contact.info.firstName + " " + contact.info.lastName;
            result.classes = "media_contact";
            result.status = PhoneFormat.getInstance().format(contact.info.phoneNumber);
            if (contact.vcard != null && contact.vcard.content != null && contact.vcard.content.length > 0) {
                result.status += " - vCard";
                result.link = contact.vcard.relativePath;
            }
        } else if (content instanceof ApiWrap.GeoPoint) {
            final ApiWrap.GeoPoint point = (ApiWrap.GeoPoint) content;
            if (media.ttl != 0) {
                result.classes = "media_live_location";
                result.title = "Live location";
                result.status = "";
            } else {
                result.classes = "media_location";
                result.title = "Location";
            }
            final String latitude = String.valueOf(point.latitude);
            final String longitude = String.valueOf(point.longitude);
            final String coords = latitude + ',' + longitude;
            result.status = latitude + ", " + longitude;
            result.link = "https://maps.google.com/maps?q=" + coords + "&ll=" + coords + "&z=16";
        } else if (content instanceof ApiWrap.Venue) {
            final ApiWrap.Venue venue = (ApiWrap.Venue) content;
            result.classes = "media_venue";
            result.title = venue.title;
            result.description = venue.address;
            if (venue.point != null && venue.point.valid) {
                final String coords = String.valueOf(venue.point.latitude) + ',' + venue.point.longitude;
                result.link = "https://maps.google.com/maps?q=" + coords + "&ll=" + coords + "&z=16";
            }
        } else if (content instanceof ApiWrap.Game) {
            final ApiWrap.Game game = (ApiWrap.Game) content;
            result.classes = "media_game";
            result.title = game.title;
            result.description = game.description;
            if (game.botId != 0 && game.shortName != null && !game.shortName.isEmpty()) {
                final ApiWrap.Peer bot = peers.get(game.botId);
                if (bot != null && bot.user != null && bot.user.isBot && bot.user.username != null && !bot.user.username.isEmpty()) {
                    final String link = internalLinksDomain + bot.user.username + "?game=" + game.shortName;
                    result.link = link;
                    result.status = link;
                }
            }
        } else if (content instanceof ApiWrap.Invoice) {
            final ApiWrap.Invoice invoice = (ApiWrap.Invoice) content;
            result.classes = "media_invoice";
            result.title = invoice.title;
            result.description = invoice.description;
            result.status = LocaleController.getInstance().formatCurrencyString(invoice.amount, invoice.currency);
        } else if (content instanceof ApiWrap.PaidMedia) {
            result.classes = "media_invoice";
            result.status = LocaleController.getInstance().formatCurrencyString(((ApiWrap.PaidMedia) content).stars, "XTR");
        }
        return result;
    }

    private String pushGenericMedia(ApiWrap.MediaData data) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        if (data.link.isEmpty()) {
            result.append(pushDiv("media clearfix pull_left " + data.classes));
        } else {
            final String lower = data.link.toLowerCase();
            final String href = lower.startsWith("http://") || lower.startsWith("https://") ? data.link : relativePath(data.link);
            result.append(pushTag("a",
                    new Pair<>("class", "media clearfix pull_left block_link " + data.classes),
                    new Pair<>("href", href)));
        }
        if (data.thumb.isEmpty()) {
            result.append(pushDiv("fill pull_left"));
            result.append(popTag());
        } else {
            result.append(pushTag("img",
                    new Pair<>("class", "thumb pull_left"),
                    new Pair<>("src", relativePath(data.thumb)),
                    new Pair<>("empty", "")));
        }
        result.append(pushDiv("body"));
        if (!data.title.isEmpty()) {
            result.append(pushDiv("title bold"));
            result.append(SerializeString(data.title));
            result.append(popTag());
        }
        if (!data.description.isEmpty()) {
            result.append(pushDiv("description"));
            result.append(SerializeString(data.description));
            result.append(popTag());
        }
        if (!data.status.isEmpty()) {
            result.append(pushDiv("status details"));
            result.append(SerializeString(data.status));
            result.append(popTag());
        }
        result.append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    private String pushVideoFileMedia(ApiWrap.Document data, String basePath) {
        final Dimension size = CalculateThumbSize(520, 520, 80, 80, true).apply(new Dimension(data.width, data.height));
        if (data.thumb.file.relativePath.isEmpty()
                || data.file.relativePath.isEmpty()
                || size.getWidth() == 0
                || size.getHeight() == 0) {
            ApiWrap.MediaData generic = new ApiWrap.MediaData();
            generic.title = "Video file";
            generic.status = AndroidUtilities.formatLongDuration(data.duration);
            if (data.file.relativePath.isEmpty()) {
                generic.status += ", " + AndroidUtilities.formatFileSize(data.file.size);
            } else {
                generic.link = data.file.relativePath;
            }
            generic.description = DataTypesUtils.NoFileDescription(data.file.skipReason);
            generic.classes = "media_video";
            return pushGenericMedia(generic);
        }
        return pushDiv("media_wrap clearfix")
                + pushTag("a",
                        new Pair<>("class", "video_file_wrap clearfix pull_left"),
                        new Pair<>("href", data.file.relativePath))
                + pushDiv("video_play_bg")
                + pushDiv("video_play")
                + popTag()
                + popTag()
                + pushDiv("video_duration")
                + AndroidUtilities.formatLongDuration(data.duration)
                + popTag()
                + pushTag("img",
                        new Pair<>("class", "video_file"),
                        new Pair<>("style", "width: " + DataTypesUtils.NumberToString(size.getWidth() / 2) + "px; height: " + DataTypesUtils.NumberToString(size.getHeight() / 2) + "px"),
                        new Pair<>("src", relativePath(data.thumb.file.relativePath)),
                        new Pair<>("empty", ""))
                + popTag()
                + popTag();
    }

    private String pushPhotoMedia(HtmlWriter.Photo data, String basePath) {
        ApiWrap.MediaData generic = new ApiWrap.MediaData();
        generic.title = "Photo";
        generic.status = data.image.width + "×" + data.image.height;
        if (data.image.file.relativePath.isEmpty()) {
            generic.status += ", " + AndroidUtilities.formatFileSize(data.image.file.size);
        } else {
            generic.link = data.image.file.relativePath;
        }
        generic.description = DataTypesUtils.NoFileDescription(data.image.file.skipReason);
        generic.classes = "media_photo";
        return pushGenericMedia(generic);
    }

    private String pushPoll(ApiWrap.Poll data) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        result.append(pushDiv("media_poll"));
        result.append(pushDiv("question bold"));
        result.append(SerializeString(data.question));
        result.append(popTag());
        result.append(pushDiv("details"));
        if (data.closed) {
            result.append(SerializeString("Final results"));
        } else {
            result.append(SerializeString("Anonymous poll"));
        }
        result.append(popTag());

        final Utilities.CallbackReturn<Integer, String> votes = count -> {
            if (count > 1) {
                return DataTypesUtils.NumberToString(count) + " votes";
            } else if (count > 0) {
                return DataTypesUtils.NumberToString(count) + " vote";
            }
            return "No votes";
        };
        final Utilities.CallbackReturn<ApiWrap.Poll.Answer, String> details = answer -> {
            if (answer.votes() == 0) {
                return "";
            } else if (!answer.my()) {
                return " <span class=\"details\">" + votes.run(answer.votes()) + "</span>";
            }
            return " <span class=\"details\">" + votes.run(answer.votes()) + ", chosen vote</span>";
        };
        for (ApiWrap.Poll.Answer answer : data.answers) {
            result.append(pushDiv("answer"));
            result.append("- ");
            result.append(SerializeString(answer.text()));
            result.append(details.run(answer));
            result.append(popTag());
        }
        result.append(pushDiv("total details\t"));
        result.append(votes.run(data.totalVotes));
        result.append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    private String pushGiveaway(HashMap<Long, ApiWrap.Peer> peers, ApiWrap.GiveawayStart data) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        result.append(pushDiv("media_giveaway"));

        result.append(pushDiv("section_title bold"));
        result.append(SerializeString(data.quantity > 1 ? "Giveaway Prizes" : "Giveaway Prize"));
        result.append(popTag());
        result.append(pushDiv("section_body"));
        result.append("<b>");
        result.append(DataTypesUtils.NumberToString(data.quantity));
        result.append("</b> ");
        result.append(SerializeString(data.additionalPrize));
        result.append(popTag());

        result.append(pushDiv("section_title bold"));
        result.append(SerializeString("with"));
        result.append(popTag());
        result.append(pushDiv("section_body"));
        if (data.credits > 0) {
            result.append("<b>");
            result.append(DataTypesUtils.NumberToString((int) data.credits));
            result.append(SerializeString(data.credits == 1 ? " Star" : " Stars"));
            result.append("/<b>");
            result.append(SerializeString("will be distributed "));
            if (data.quantity == 1) {
                result.append(SerializeString("to "));
                result.append(SerializeString("<b>"));
                result.append(DataTypesUtils.NumberToString(data.quantity));
                result.append(SerializeString("</b> "));
                result.append(SerializeString("winner."));
            } else {
                result.append(SerializeString("among "));
                result.append("<b>");
                result.append(DataTypesUtils.NumberToString(data.quantity));
                result.append(SerializeString("</b> "));
                result.append(SerializeString("winners."));
            }
        } else {
            result.append("<b>");
            result.append(DataTypesUtils.NumberToString(data.quantity));
            result.append("</b> ");
            if (data.quantity > 1) {
                result.append(SerializeString("Telegram Premium Subscriptions"));
            } else {
                result.append(SerializeString("Telegram Premium Subscription"));
            }
            result.append(" for <b>");
            result.append(DataTypesUtils.NumberToString(data.months));
            result.append("</b> ");
            result.append(data.months > 1 ? "months." : "month.");
        }
        result.append(popTag());

        result.append(pushDiv("section_title bold"));
        result.append(SerializeString("Participants"));
        result.append(popTag());
        result.append(pushDiv("section_body"));
        ArrayList<String> channels = new ArrayList<>();
        boolean anyGroup = false;
        boolean anyChannel = false;
        for (Long channelId : data.channels) {
            final ApiWrap.Peer peer = peers.get(channelId);
            if (peer != null && peer.chat != null) {
                if (peer.chat.isBroadcast) {
                    anyChannel = true;
                } else if (peer.chat.isSupergroup) {
                    anyGroup = true;
                }
            }
            channels.add("<b>" + HtmlWriter.wrapPeerName(channelId) + "</b>");
        }
        final int count = channels.size();
        String participants = "";
        if (data.all && !anyGroup && anyChannel && count == 1) {
            participants = "All subscribers of the channel:";
        }
        if (data.all && !anyGroup && anyChannel && count > 1) {
            participants = "All subscribers of the channels:";
        }
        if (data.all && anyGroup && !anyChannel && count == 1) {
            participants = "All members of the group:";
        }
        if (data.all && anyGroup && !anyChannel && count > 1) {
            participants = "All members of the groups:";
        }
        if (data.all && anyGroup && anyChannel && count == 1) {
            participants = "All members of the group:";
        }
        if (data.all && anyGroup && anyChannel && count > 1) {
            participants = "All members of the groups and channels:";
        }
        if (!data.all && !anyGroup && anyChannel && count == 1) {
            participants = "All users who joined the channel below after this date:";
        }
        if (!data.all && !anyGroup && anyChannel && count > 1) {
            participants = "All users who joined the channels below after this date:";
        }
        if (!data.all && anyGroup && !anyChannel && count == 1) {
            participants = "All users who joined the group below after this date:";
        }
        if (!data.all && anyGroup && !anyChannel && count > 1) {
            participants = "All users who joined the groups below after this date:";
        }
        if (!data.all && anyGroup && anyChannel && count == 1) {
            participants = "All users who joined the group below after this date:";
        }
        if (!data.all && anyGroup && anyChannel && count > 1) {
            participants = "All users who joined the groups and channels below after this date:";
        }
        result.append(SerializeString(participants));
        result.append(TextUtils.join(", ", channels));
        result.append(popTag());

        ArrayList<String> countries = new ArrayList<>();
        HashMap<String, String> codesByName = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            codesByName.put(new Locale("", code).getDisplayCountry(), code);
        }
        for (String country : data.countries) {
            final String code = codesByName.get(country);
            countries.add(countryToEmoji(code) + "\t " + code);
        }
        if (!countries.isEmpty()) {
            final int size = countries.size();
            String joined = countries.get(0);
            for (int i = 1; i != size; i++) {
                joined = String.format(i + 1 == size ? "%1s and %2s" : "%1s, %2s", joined, countries.get(i));
            }
            result.append(pushDiv("section_body"));
            result.append(SerializeString(String.format("from %s", joined)));
            result.append(popTag());
        }

        result.append(pushDiv("section_title bold"));
        result.append(SerializeString("Winners Selection Date"));
        result.append(popTag());
        result.append(pushDiv("section_body"));
        result.append(LocaleController.formatDateTime(data.untilDate, false));
        result.append(popTag());

        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    private String pushGiveaway(HashMap<Long, ApiWrap.Peer> peers, ApiWrap.GiveawayResults data, Utilities.Callback2Return<Integer, String, String> wrapMessageLink) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        result.append(pushDiv("media_giveaway"));

        result.append(pushDiv("section_title bold"));
        result.append(SerializeString(data.winnersCount > 1 ? "Winners Selected!" : "Winner Selected!"));
        result.append(popTag());
        result.append(pushDiv("section_body"));
        result.append("<b>");
        result.append(DataTypesUtils.NumberToString(data.winnersCount));
        result.append("</b> ");
        result.append(SerializeString(data.winnersCount > 1 ? "winners" : "winner"));
        result.append(" of the ");
        result.append(wrapMessageLink.run(data.launchId, "Giveaway"));
        result.append(" was randomly selected by Telegram.");
        result.append(popTag());

        result.append(pushDiv("section_title bold"));
        result.append(SerializeString(data.winnersCount > 1 ? "Winners" : "Winner"));
        result.append(popTag());
        result.append(pushDiv("section_body"));
        ArrayList<String> winners = new ArrayList<>();
        for (Long winnerId : data.winners) {
            winners.add("<b>" + HtmlWriter.wrapPeerName(winnerId) + "</b>");
        }
        final String more = data.winnersCount > data.winners.size()
                ? SerializeString(" and ") + DataTypesUtils.NumberToString(data.winnersCount - data.winners.size()) + SerializeString(" more!")
                : "";
        result.append(String.join(", ", winners));
        result.append(more);
        result.append(popTag());

        result.append(pushDiv("section_body"));
        final boolean singleStar = data.credits == 1;
        String footer = "";
        if (data.credits != 0 && data.winnersCount == 1) {
            footer = SerializeString("The winner received ")
                    + "<b>" + DataTypesUtils.NumberToString((int) data.credits) + "</b>"
                    + SerializeString(singleStar ? " Star." : " Stars.");
        } else if (data.credits != 0 && data.winnersCount > 1) {
            footer = SerializeString("All winners received ")
                    + "<b>" + DataTypesUtils.NumberToString((int) data.credits) + "</b>"
                    + SerializeString(singleStar ? " Star in total." : " Stars in total.");
        } else if (data.unclaimedCount != 0) {
            footer = SerializeString("Some winners couldn't be selected.");
        } else if (data.winnersCount == 1) {
            footer = SerializeString("The winner received their gift link in a private message.");
        } else if (data.winnersCount > 1) {
            footer = SerializeString("All winners received gift links in private messages.");
        }
        result.append(footer);
        result.append(popTag());

        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }
}
