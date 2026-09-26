package com.exteragram.messenger.export.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.collection.LongSparseArray;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.components.MessageDetailsPopupWrapper;
import com.exteragram.messenger.export.output.FileManager;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.exteragram.messenger.utils.system.VibratorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.tlutils.TLKeyboardHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.AspectRatioFrameLayout;
import org.telegram.ui.AvatarPreviewer;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatLoadingCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ChatUnreadCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.ChatScrimPopupContainerLayout;
import org.telegram.ui.Components.EmbedBottomSheet;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhonebookShareAlert;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.ContactAddActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LanguageSelectActivity;
import org.telegram.ui.LocationActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ThemePreviewActivity;
import org.telegram.ui.recyclerview.ChatListItemAnimator;
import org.telegram.ui.recyclerview.LinearSmoothScrollerCustom;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatViewer extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int OPTION_COPY = 3;
    private static final int OPTION_SAVE_TO_GALLERY = 4;
    private static final int OPTION_APPLY_FILE = 5;
    private static final int OPTION_SHARE = 6;
    private static final int OPTION_SAVE_TO_GALLERY2 = 7;
    private static final int OPTION_SAVE_STICKER = 9;
    private static final int OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC = 10;
    private static final int OPTION_SAVE_TO_GIFS = 11;
    private static final int OPTION_ADD_CONTACT = 15;
    private static final int OPTION_COPY_PHONE = 16;
    private static final int OPTION_CALL = 17;
    private static final int OPTION_JUMP_TO_DATE = 18;
    private static final int OPTION_DETAILS = 204;

    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private ChatAvatarContainer avatarContainer;
    private ChatActivityAdapter chatAdapter;
    private final ExportMapper.ChatInfo chatInfo;
    private LinearLayoutManager chatLayoutManager;
    private ChatListItemAnimator chatListItemAnimator;
    private RecyclerListView chatListView;
    private boolean checkTextureViewPosition;
    private SizeNotifierFrameLayout contentView;
    protected TLRPC.Chat currentChat;
    private boolean currentFloatingDateOnScreen;
    private boolean currentFloatingTopIsNotMessage;
    private TextView emptyView;
    private FrameLayout emptyViewContainer;
    private boolean endReached;
    private AnimatorSet floatingDateAnimation;
    private ChatActionCell floatingDateView;
    private boolean loading;
    private long minEventId;
    private final String path;
    private RadialProgressView progressBar;
    private FrameLayout progressView;
    private View progressView2;
    private FrameLayout roundVideoContainer;
    private ActionBarPopupWindow scrimPopupWindow;
    private boolean scrollingFloatingDate;
    private boolean searchWas;
    private MessageObject selectedObject;
    public ChatMessageSharedResources sharedResources;
    private UndoView undoView;
    private TextureView videoTextureView;
    private final ArrayList<ChatMessageCell> chatMessageCellsCache = new ArrayList<>();
    private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker(new int[]{
            NotificationCenter.chatInfoDidLoad,
            NotificationCenter.dialogsNeedReload,
            NotificationCenter.closeChats,
            NotificationCenter.messagesDidLoad,
            NotificationCenter.botKeyboardDidLoad
    });
    private final LongSparseArray<MessageObject> messagesDict = new LongSparseArray<>();
    private final HashMap<String, ArrayList<MessageObject>> messagesByDays = new HashMap<>();
    protected ArrayList<MessageObject> messages = new ArrayList<>();
    private int scrollToPositionOnRecreate = -1;
    private int scrollToOffsetOnRecreate = 0;
    private boolean paused = true;
    private boolean wasPaused = false;
    private final String searchQuery = "";
    private final ChatActivity.ThemeDelegate theme = null;
    private AtomicInteger lastLoadedMsgFileId = null;

    private final PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            int count = chatListView.getChildCount();
            for (int a = 0; a < count; a++) {
                ImageReceiver imageReceiver = null;
                View view = chatListView.getChildAt(a);
                if (view instanceof ChatMessageCell) {
                    if (messageObject != null) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject message = cell.getMessageObject();
                        if (message != null && message.getId() == messageObject.getId()) {
                            imageReceiver = cell.getPhotoImage();
                        }
                    }
                } else if (view instanceof ChatActionCell) {
                    ChatActionCell cell = (ChatActionCell) view;
                    MessageObject message = cell.getMessageObject();
                    if (message != null) {
                        if (messageObject != null) {
                            if (message.getId() == messageObject.getId()) {
                                imageReceiver = cell.getPhotoImage();
                            }
                        } else if (fileLocation != null && message.photoThumbs != null) {
                            for (int b = 0; b < message.photoThumbs.size(); b++) {
                                TLRPC.PhotoSize photoSize = message.photoThumbs.get(b);
                                if (photoSize.location.volume_id == fileLocation.volume_id && photoSize.location.local_id == fileLocation.local_id) {
                                    imageReceiver = cell.getPhotoImage();
                                    break;
                                }
                            }
                        }
                    }
                }

                if (imageReceiver != null) {
                    int[] coords = new int[2];
                    view.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1];
                    object.parentView = chatListView;
                    object.imageReceiver = imageReceiver;
                    object.thumb = imageReceiver.getBitmapSafe();
                    object.radius = imageReceiver.getRoundRadius();
                    object.isEvent = true;
                    return object;
                }
            }
            return null;
        }
    };

    public ChatViewer(String path) {
        this.path = path;
        this.chatInfo = ExteraConfig.getGSON().fromJson(FileManager.readFileContent(new File(path + "/info.json")), ExportMapper.ChatInfo.class);
    }

    private long getDialogId() {
        return chatInfo.id;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        loadMessages(true);
        return true;
    }

    private void fillMessagesCount() {
        Utilities.globalQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
            if (avatarContainer != null) {
                avatarContainer.setSubtitle("msgs count: " + chatInfo.msgsCount);
            }
        }));
    }

    @Override
    public int getThemedColor(int key) {
        return Theme.getColor(key);
    }

    @Override
    public int getNavigationBarColor() {
        return getThemedColor(Theme.key_chat_messagePanelBackground);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        notificationsLocker.unlock();
    }

    private void updateEmptyPlaceholder() {
        if (emptyView == null) {
            return;
        }
        emptyView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(5), AndroidUtilities.dp(8), AndroidUtilities.dp(5));
        emptyView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.NoResult)));
    }

    private ArrayList<MessageObject> loadDeleted(int maxId) {
        if (lastLoadedMsgFileId != null && lastLoadedMsgFileId.get() == 0) {
            return new ArrayList<>();
        }
        int fileId;
        if (lastLoadedMsgFileId != null) {
            fileId = lastLoadedMsgFileId.decrementAndGet();
        } else {
            int maxFileId = Integer.MIN_VALUE;
            File[] files = new File(path).listFiles((dir, name) -> name.contains("messages") && name.contains("json"));
            for (File file : files) {
                Matcher matcher = Pattern.compile("(\\d+)").matcher(file.getName());
                if (matcher.find()) {
                    maxFileId = Math.max(maxFileId, Integer.parseInt(matcher.group()));
                }
            }
            lastLoadedMsgFileId = new AtomicInteger(maxFileId);
            fileId = maxFileId;
        }
        File messagesFile = new File(path + "/messages" + fileId + ".json");
        if (messagesFile.length() == 0) {
            return new ArrayList<>();
        }
        Log.d("exteraGram", "msg file size: " + messagesFile.length());
        ExportMapper.JsonMessage[] jsonMessages = ExteraConfig.getGSON().fromJson(FileManager.readFileContent(messagesFile), ExportMapper.JsonMessage[].class);
        ArrayList<MessageObject> mappedMessages = new ExportMapper(currentAccount, path, chatInfo).mapMessages(jsonMessages);

        final MessagesController messagesController = getMessagesController();
        MessagesStorage messagesStorage = getMessagesStorage();
        ArrayList<Long> usersToLoad = new ArrayList<>();
        ArrayList<Long> chatsToLoad = new ArrayList<>();
        for (MessageObject messageObject : mappedMessages) {
            if (!TextUtils.isEmpty(messageObject.messageOwner.message)) {
                MessagesStorage.addUsersAndChatsFromMessage(messageObject.messageOwner, usersToLoad, chatsToLoad, null);
            }
        }
        QuadroResult entities = getEntities(messagesStorage, usersToLoad, chatsToLoad);
        Pair<LongSparseArray<TLRPC.User>, LongSparseArray<TLRPC.Chat>> dicts = entities.getDicts();
        final ArrayList<TLRPC.User> users = entities.getUsers();
        final ArrayList<TLRPC.Chat> chats = entities.getChats();
        AndroidUtilities.runOnUIThread(() -> {
            if (!users.isEmpty()) {
                messagesController.putUsers(users, true);
            }
            if (!chats.isEmpty()) {
                messagesController.putChats(chats, true);
            }
        });

        ArrayList<MessageObject> result = new ArrayList<>();
        for (int a = 0; a < mappedMessages.size(); a++) {
            result.add(new MessageObject(currentAccount, mappedMessages.get(a).messageOwner, dicts.first, dicts.second, false, false));
        }
        return result;
    }

    private String getDateKey(MessageObject messageObject) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(((long) messageObject.messageOwner.date) * 1000);
        int dateDay = calendar.get(Calendar.DAY_OF_YEAR);
        return String.format("%d_%02d_%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), dateDay);
    }

    private void loadMessages(boolean reset) {
        if (loading) {
            return;
        }
        if (reset) {
            minEventId = Long.MAX_VALUE;
            if (progressView != null) {
                AndroidUtilities.updateViewVisibilityAnimated(progressView, true, 0.3f, true);
                emptyViewContainer.setVisibility(View.INVISIBLE);
                chatListView.setEmptyView(null);
            }
            messagesDict.clear();
            messages.clear();
            messagesByDays.clear();
            fillMessagesCount();
        }
        loading = true;
        updateEmptyPlaceholder();
        final ArrayList<MessageObject> loadedMessages = loadDeleted(reset || messages.isEmpty() ? Integer.MAX_VALUE : (int) minEventId);
        AndroidUtilities.runOnUIThread(() -> {
            chatListItemAnimator.setShouldAnimateEnterFromBottom(false);
            int oldRowsCount = messages.size();
            boolean added = false;
            for (MessageObject messageObject : loadedMessages) {
                if (messagesDict.indexOfKey(messageObject.messageOwner.id) >= 0) {
                    continue;
                }
                minEventId = Math.min(minEventId, messageObject.messageOwner.id);
                if (messageObject.contentType >= 0) {
                    messagesDict.put(messageObject.messageOwner.id, messageObject);
                    String dateKey = getDateKey(messageObject);
                    if (messagesByDays.get(dateKey) == null) {
                        messagesByDays.put(dateKey, new ArrayList<>());
                        TLRPC.TL_message dateMessage = new TLRPC.TL_message();
                        dateMessage.message = LocaleController.formatDateChat(messageObject.messageOwner.date);
                        dateMessage.id = 0;
                        dateMessage.date = messageObject.messageOwner.date;
                        MessageObject dateObject = new MessageObject(currentAccount, dateMessage, false, false);
                        dateObject.type = MessageObject.TYPE_DATE;
                        dateObject.contentType = 1;
                        dateObject.isDateObject = true;
                        messages.add(messageObject);
                        messages.add(dateObject);
                    } else if (messages.isEmpty()) {
                        messages.add(messageObject);
                    } else if (messages.get(messages.size() - 1).isDateObject) {
                        MessageObject dateObject = messages.remove(messages.size() - 1);
                        messages.add(messageObject);
                        messages.add(dateObject);
                    } else {
                        messages.add(messageObject);
                    }
                }
                added = true;
            }
            int newRowsCount = messages.size() - oldRowsCount;
            loading = false;
            if (!added) {
                endReached = true;
            }
            AndroidUtilities.updateViewVisibilityAnimated(progressView, false, 0.3f, true);
            chatListView.setEmptyView(emptyViewContainer);
            if (newRowsCount != 0) {
                boolean end = false;
                if (endReached) {
                    end = true;
                    chatAdapter.notifyItemRangeChanged(0, 2);
                }
                int firstVisPos = chatLayoutManager.findLastVisibleItemPosition();
                View firstVisView = chatLayoutManager.findViewByPosition(firstVisPos);
                int top = (firstVisView == null ? 0 : firstVisView.getTop()) - chatListView.getPaddingTop();
                if (newRowsCount - (end ? 1 : 0) > 0) {
                    int insertStart = 1 + (end ? 0 : 1);
                    chatAdapter.notifyItemChanged(insertStart);
                    chatAdapter.notifyItemRangeInserted(insertStart, newRowsCount - (end ? 1 : 0));
                }
                if (firstVisPos != -1) {
                    chatLayoutManager.scrollToPositionWithOffset(firstVisPos + newRowsCount - (end ? 1 : 0), top);
                }
            } else if (endReached) {
                chatAdapter.notifyItemRemoved(0);
            }
        });
        if (reset && chatAdapter != null) {
            chatAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (chatListView != null) {
                chatListView.invalidateViews();
            }
        } else if (id == NotificationCenter.messagePlayingDidStart) {
            MessageObject messageObject = (MessageObject) args[0];
            if (messageObject.isRoundVideo()) {
                MediaController.getInstance().setTextureView(createTextureView(true), aspectRatioFrameLayout, roundVideoContainer, true);
                updateTextureViewPosition();
            }
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject1 = cell.getMessageObject();
                        if (messageObject1 != null) {
                            if (messageObject1.isVoice() || messageObject1.isMusic()) {
                                cell.updateButtonState(false, true, false);
                            } else if (messageObject1.isRoundVideo()) {
                                cell.checkVideoPlayback(false, null);
                                if (!MediaController.getInstance().isPlayingMessage(messageObject1)) {
                                    if (messageObject1.audioProgress != 0) {
                                        messageObject1.resetPlayingProgress();
                                        cell.invalidate();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null) {
                            if (messageObject.isVoice() || messageObject.isMusic()) {
                                cell.updateButtonState(false, true, false);
                            } else if (messageObject.isRoundVideo()) {
                                if (!MediaController.getInstance().isPlayingMessage(messageObject)) {
                                    cell.checkVideoPlayback(true, null);
                                }
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (chatListView != null) {
                int count = chatListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = chatListView.getChildAt(a);
                    if (view instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) view;
                        MessageObject playing = cell.getMessageObject();
                        if (playing != null && playing.getId() == mid) {
                            MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                            if (player != null) {
                                playing.audioProgress = player.audioProgress;
                                playing.audioProgressSec = player.audioProgressSec;
                                playing.audioPlayerDuration = player.audioPlayerDuration;
                                cell.updatePlayingMessageProgress();
                            }
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (fragmentView != null) {
                contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
                progressView2.invalidate();
                if (emptyView != null) {
                    emptyView.invalidate();
                }
                chatListView.invalidateViews();
            }
        }
    }

    @Override
    public View createView(Context context) {
        sharedResources = new ChatMessageSharedResources(context);
        if (chatMessageCellsCache.isEmpty()) {
            for (int a = 0; a < 8; a++) {
                chatMessageCellsCache.add(new ChatMessageCell(context, currentAccount));
            }
        }

        searchWas = false;
        hasOwnBackground = true;

        Theme.createChatResources(context, false);

        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(!AndroidUtilities.isTablet());
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        avatarContainer = new ChatAvatarContainer(context, null, false, theme) {
            @Override
            protected void openSearch() {
            }

            @Override
            protected boolean canSearch() {
                return TextUtils.isEmpty(searchQuery);
            }
        };
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
        avatarContainer.setEnabled(false);
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 56, 0, 40, 0));

        TLObject dialog = getDialogInAnyWay(chatInfo.id, UserConfig.selectedAccount, true);
        if (dialog instanceof TLRPC.User) {
            avatarContainer.setTitle(chatInfo.name);
            fillMessagesCount();
            avatarContainer.setUserAvatar((TLRPC.User) dialog);
        } else if (dialog instanceof TLRPC.Chat) {
            avatarContainer.setTitle(chatInfo.name);
            fillMessagesCount();
            avatarContainer.setChatAvatar((TLRPC.Chat) dialog);
        }

        fragmentView = new SizeNotifierFrameLayout(context) {

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && messageObject.isRoundVideo() && messageObject.eventId != 0 && messageObject.getDialogId() == getDialogId()) {
                    MediaController.getInstance().setTextureView(createTextureView(false), aspectRatioFrameLayout, roundVideoContainer, true);
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == actionBar && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() : 0);
                }
                return result;
            }

            @Override
            protected boolean isActionBarVisible() {
                return actionBar.getVisibility() == VISIBLE;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                if (actionBar.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (child == chatListView || child == progressView) {
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), heightSize), MeasureSpec.EXACTLY);
                        child.measure(contentWidthSpec, contentHeightSpec);
                    } else if (child == emptyViewContainer) {
                        int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                        int contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                        child.measure(contentWidthSpec, contentHeightSpec);
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            if (child != actionBar && actionBar.getVisibility() == VISIBLE) {
                                childTop += actionBar.getMeasuredHeight();
                            }
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = (b - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = (b - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (child == emptyViewContainer) {
                        childTop -= AndroidUtilities.dp(24) - (actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() / 2 : 0);
                    } else if (child == actionBar) {
                        childTop -= getPaddingTop();
                    } else if (child == backgroundView) {
                        childTop = 0;
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                updateMessagesVisiblePart();
                notifyHeightChanged();
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (AvatarPreviewer.hasVisibleInstance()) {
                    AvatarPreviewer.getInstance().onTouchEvent(ev);
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected Theme.ResourcesProvider getResourceProvider() {
                return theme;
            }

            @Override
            protected Drawable getNewDrawable() {
                Drawable drawable = Theme.getCachedWallpaper();
                return drawable != null ? drawable : super.getNewDrawable();
            }
        };

        contentView = (SizeNotifierFrameLayout) fragmentView;

        contentView.setOccupyStatusBar(!AndroidUtilities.isTablet());
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());

        emptyViewContainer = new FrameLayout(context);
        emptyViewContainer.setVisibility(View.INVISIBLE);
        contentView.addView(emptyViewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        emptyViewContainer.setOnTouchListener((v, event) -> true);

        emptyView = new TextView(context);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        emptyView.setBackground(Theme.createServiceDrawable(AndroidUtilities.dp(6), emptyView, contentView));
        emptyView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        emptyViewContainer.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 0, 16, 0));

        chatListView = new RecyclerListView(context, theme) {

            @Override
            public void setTranslationY(float translationY) {
                if (translationY != getTranslationY()) {
                    super.setTranslationY(translationY);
                    updateMessagesVisiblePart();
                }
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell chatMessageCell = (ChatMessageCell) child;
                    ImageReceiver imageReceiver = chatMessageCell.getAvatarImage();
                    if (imageReceiver != null) {
                        if (chatMessageCell.getMessageObject().deleted) {
                            imageReceiver.setVisible(false, false);
                            return result;
                        }

                        int top = (int) child.getY();
                        if (chatMessageCell.drawPinnedBottom()) {
                            RecyclerView.ViewHolder holder = chatListView.getChildViewHolder(child);
                            int p = holder.getAdapterPosition();
                            if (p >= 0) {
                                int nextPosition = p + 1;
                                holder = chatListView.findViewHolderForAdapterPosition(nextPosition);
                                if (holder != null) {
                                    imageReceiver.setVisible(false, false);
                                    return result;
                                }
                            }
                        }
                        float tx = chatMessageCell.getSlidingOffsetX() + chatMessageCell.getCheckBoxTranslation();

                        int y = (int) child.getY() + chatMessageCell.getLayoutHeight();
                        int maxY = chatListView.getMeasuredHeight() - chatListView.getPaddingBottom();
                        if (y > maxY) {
                            y = maxY;
                        }

                        if (chatMessageCell.drawPinnedTop()) {
                            RecyclerView.ViewHolder holder = chatListView.getChildViewHolder(child);
                            int p = holder.getAdapterPosition();
                            if (p >= 0) {
                                int tries = 0;
                                while (tries < 20) {
                                    tries++;
                                    int prevPosition = p - 1;
                                    holder = chatListView.findViewHolderForAdapterPosition(prevPosition);
                                    if (holder == null) {
                                        break;
                                    }
                                    top = holder.itemView.getTop();
                                    if (!(holder.itemView instanceof ChatMessageCell)) {
                                        break;
                                    }
                                    chatMessageCell = (ChatMessageCell) holder.itemView;
                                    if (!chatMessageCell.drawPinnedTop()) {
                                        break;
                                    }
                                    p = prevPosition;
                                }
                            }
                        }
                        if (y - AndroidUtilities.dp(48) < top) {
                            y = top + AndroidUtilities.dp(48);
                        }
                        if (!chatMessageCell.drawPinnedBottom()) {
                            int cellBottom = (int) (chatMessageCell.getY() + chatMessageCell.getMeasuredHeight());
                            if (y > cellBottom) {
                                y = cellBottom;
                            }
                        }
                        canvas.save();
                        if (tx != 0) {
                            canvas.translate(tx, 0);
                        }
                        if (chatMessageCell.getCurrentMessagesGroup() != null && chatMessageCell.getCurrentMessagesGroup().transitionParams.backgroundChangeBounds) {
                            y -= chatMessageCell.getTranslationY();
                        }
                        imageReceiver.setImageY(y - AndroidUtilities.dp(44));
                        if (chatMessageCell.shouldDrawAlphaLayer()) {
                            imageReceiver.setAlpha(chatMessageCell.getAlpha());
                            canvas.scale(
                                    chatMessageCell.getScaleX(), chatMessageCell.getScaleY(),
                                    chatMessageCell.getX() + chatMessageCell.getPivotX(), chatMessageCell.getY() + (chatMessageCell.getHeight() >> 1)
                            );
                        } else {
                            imageReceiver.setAlpha(1f);
                        }
                        imageReceiver.setVisible(true, false);
                        imageReceiver.draw(canvas);
                        canvas.restore();
                    }
                }
                return result;
            }
        };
        chatListView.setOnItemClickListener((RecyclerListView.OnItemClickListenerExtended) (view, position, x, y) -> createMenu(view, x, y));
        chatListView.setTag(1);
        chatListView.setVerticalScrollBarEnabled(true);
        chatListView.setAdapter(chatAdapter = new ChatActivityAdapter(context));
        chatListView.setClipToPadding(false);
        chatListView.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(3));
        chatListView.setItemAnimator(chatListItemAnimator = new ChatListItemAnimator(null, chatListView, theme) {

            int scrollAnimationIndex = -1;
            Runnable finishRunnable;

            @Override
            public void onAnimationStart() {
                if (scrollAnimationIndex == -1) {
                    scrollAnimationIndex = getNotificationCenter().setAnimationInProgress(scrollAnimationIndex, null, false);
                }
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                    finishRunnable = null;
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("admin logs chatItemAnimator disable notifications");
                }
                updateMessagesVisiblePart();
            }

            @Override
            protected void onAllAnimationsDone() {
                super.onAllAnimationsDone();
                if (finishRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(finishRunnable);
                }
                AndroidUtilities.runOnUIThread(finishRunnable = () -> {
                    if (scrollAnimationIndex != -1) {
                        getNotificationCenter().onAnimationFinish(scrollAnimationIndex);
                        scrollAnimationIndex = -1;
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("admin logs chatItemAnimator enable notifications");
                    }
                    updateMessagesVisiblePart();
                });
            }
        });
        chatListItemAnimator.setReversePositions(true);
        chatListView.setLayoutAnimation(null);
        chatLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return true;
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(recyclerView.getContext(), LinearSmoothScrollerCustom.POSITION_MIDDLE);
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        };
        chatLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        chatLayoutManager.setStackFromEnd(true);
        chatListView.setLayoutManager(chatLayoutManager);
        contentView.addView(chatListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        chatListView.setOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scrollingFloatingDate = true;
                    checkTextureViewPosition = true;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    scrollingFloatingDate = false;
                    checkTextureViewPosition = false;
                    hideFloatingDateView(true);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                chatListView.invalidate();
                if (dy != 0 && scrollingFloatingDate && !currentFloatingTopIsNotMessage) {
                    if (floatingDateView.getTag() == null) {
                        if (floatingDateAnimation != null) {
                            floatingDateAnimation.cancel();
                        }
                        floatingDateView.setTag(1);
                        floatingDateAnimation = new AnimatorSet();
                        floatingDateAnimation.setDuration(150);
                        floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, "alpha", 1.0f));
                        floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animation.equals(floatingDateAnimation)) {
                                    floatingDateAnimation = null;
                                }
                            }
                        });
                        floatingDateAnimation.start();
                    }
                }
                checkScrollForLoad(true);
                updateMessagesVisiblePart();
            }
        });
        if (scrollToPositionOnRecreate != -1) {
            chatLayoutManager.scrollToPositionWithOffset(scrollToPositionOnRecreate, scrollToOffsetOnRecreate);
            scrollToPositionOnRecreate = -1;
        }

        progressView = new FrameLayout(context);
        progressView.setVisibility(View.INVISIBLE);
        contentView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        progressView2 = new View(context);
        progressView2.setBackground(Theme.createServiceDrawable(AndroidUtilities.dp(18), progressView2, contentView));
        progressView.addView(progressView2, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        progressBar = new RadialProgressView(context, theme);
        progressBar.setSize(AndroidUtilities.dp(28));
        progressBar.setProgressColor(getThemedColor(Theme.key_chat_serviceText));
        progressView.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));

        floatingDateView = new ChatActionCell(context, false, theme);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        contentView.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        contentView.addView(actionBar);

        chatAdapter.updateRows();
        if (loading && messages.isEmpty()) {
            AndroidUtilities.updateViewVisibilityAnimated(progressView, true, 0.3f, true);
            chatListView.setEmptyView(null);
        } else {
            AndroidUtilities.updateViewVisibilityAnimated(progressView, false, 0.3f, true);
            chatListView.setEmptyView(emptyViewContainer);
        }
        chatListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA_SCALE);

        undoView = new UndoView(context, null, false, theme);
        contentView.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        updateEmptyPlaceholder();

        return fragmentView;
    }

    private void closeMenu() {
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
        }
    }

    private boolean createMenu(View v) {
        return createMenu(v, 0, 0);
    }

    private boolean createMenu(View v, float x, float y) {
        MessageObject message = null;
        if (v instanceof ChatMessageCell) {
            message = ((ChatMessageCell) v).getMessageObject();
        } else if (v instanceof ChatActionCell) {
            message = ((ChatActionCell) v).getMessageObject();
        }
        if (message == null || message.type == MessageObject.TYPE_DATE) {
            return false;
        }
        final int type = getMessageType(message);
        selectedObject = message;
        if (getParentActivity() == null) {
            return false;
        }

        ArrayList<CharSequence> items = new ArrayList<>();
        final ArrayList<Integer> options = new ArrayList<>();
        final ArrayList<Integer> icons = new ArrayList<>();

        if (selectedObject.type == MessageObject.TYPE_TEXT || selectedObject.caption != null) {
            items.add(LocaleController.getString(R.string.Copy));
            icons.add(R.drawable.msg_copy);
            options.add(OPTION_COPY);
        }
        if (type == 3) {
            if (selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && MessageObject.isNewGifDocument(selectedObject.messageOwner.media.webpage.document)) {
                items.add(LocaleController.getString(R.string.SaveToGIFs));
                icons.add(R.drawable.msg_gif);
                options.add(OPTION_SAVE_TO_GIFS);
            }
        } else if (type == 4) {
            if (selectedObject.isVideo()) {
                items.add(LocaleController.getString(R.string.SaveToGallery));
                icons.add(R.drawable.msg_gallery);
                options.add(OPTION_SAVE_TO_GALLERY);
                items.add(LocaleController.getString(R.string.ShareFile));
                icons.add(R.drawable.msg_share);
                options.add(OPTION_SHARE);
            } else if (selectedObject.isMusic()) {
                items.add(LocaleController.getString(R.string.SaveToMusic));
                icons.add(R.drawable.msg_download);
                options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
                items.add(LocaleController.getString(R.string.ShareFile));
                icons.add(R.drawable.msg_share);
                options.add(OPTION_SHARE);
            } else if (selectedObject.getDocument() != null) {
                if (MessageObject.isNewGifDocument(selectedObject.getDocument())) {
                    items.add(LocaleController.getString(R.string.SaveToGIFs));
                    icons.add(R.drawable.msg_gif);
                    options.add(OPTION_SAVE_TO_GIFS);
                }
                items.add(LocaleController.getString(R.string.SaveToDownloads));
                icons.add(R.drawable.msg_download);
                options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
                items.add(LocaleController.getString(R.string.ShareFile));
                icons.add(R.drawable.msg_share);
                options.add(OPTION_SHARE);
            } else {
                items.add(LocaleController.getString(R.string.SaveToGallery));
                icons.add(R.drawable.msg_gallery);
                options.add(OPTION_SAVE_TO_GALLERY);
            }
        } else if (type == 5) {
            items.add(LocaleController.getString(R.string.ApplyLocalizationFile));
            icons.add(R.drawable.msg_language);
            options.add(OPTION_APPLY_FILE);
            items.add(LocaleController.getString(R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
            items.add(LocaleController.getString(R.string.ShareFile));
            icons.add(R.drawable.msg_share);
            options.add(OPTION_SHARE);
        } else if (type == 10) {
            items.add(LocaleController.getString(R.string.ApplyThemeFile));
            icons.add(R.drawable.msg_theme);
            options.add(OPTION_APPLY_FILE);
            items.add(LocaleController.getString(R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
            items.add(LocaleController.getString(R.string.ShareFile));
            icons.add(R.drawable.msg_share);
            options.add(OPTION_SHARE);
        } else if (type == 6) {
            items.add(LocaleController.getString(R.string.SaveToGallery));
            icons.add(R.drawable.msg_gallery);
            options.add(OPTION_SAVE_TO_GALLERY2);
            items.add(LocaleController.getString(R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            options.add(OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC);
            items.add(LocaleController.getString(R.string.ShareFile));
            icons.add(R.drawable.msg_share);
            options.add(OPTION_SHARE);
        } else if (type == 7) {
            if (selectedObject.isMask()) {
                items.add(LocaleController.getString(R.string.AddToMasks));
            } else {
                items.add(LocaleController.getString(R.string.AddToStickers));
            }
            icons.add(R.drawable.msg_sticker);
            options.add(OPTION_SAVE_STICKER);
        } else if (type == 8) {
            long uid = selectedObject.messageOwner.media.user_id;
            TLRPC.User user = null;
            if (uid != 0) {
                user = MessagesController.getInstance(currentAccount).getUser(uid);
            }
            if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId() && ContactsController.getInstance(currentAccount).contactsDict.get(user.id) == null) {
                items.add(LocaleController.getString(R.string.AddContactTitle));
                icons.add(R.drawable.msg_addcontact);
                options.add(OPTION_ADD_CONTACT);
            }
            if (!TextUtils.isEmpty(selectedObject.messageOwner.media.phone_number)) {
                items.add(LocaleController.getString(R.string.Copy));
                icons.add(R.drawable.msg_copy);
                options.add(OPTION_COPY_PHONE);
                items.add(LocaleController.getString(R.string.Call));
                icons.add(R.drawable.msg_calls);
                options.add(OPTION_CALL);
            }
        }

        items.add(LocaleController.getString(R.string.Details));
        options.add(OPTION_DETAILS);
        icons.add(R.drawable.msg_info);

        showMenu(v, x, y, items, options, icons);
        return true;
    }

    private void showMenu(View v, float x, float y, ArrayList<CharSequence> items, ArrayList<Integer> options, ArrayList<Integer> icons) {
        if (options.isEmpty()) {
            return;
        }

        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, getResourceProvider(), ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        popupLayout.setMinimumWidth(AndroidUtilities.dp(200));
        Rect backgroundPaddings = new Rect();
        Drawable shadowDrawable = getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.getPadding(backgroundPaddings);
        popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        for (int a = 0, N = items.size(); a < N; ++a) {
            if (options.get(a) == null) {
                popupLayout.addView(new ActionBarPopupWindow.GapView(getContext(), getResourceProvider()), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
                continue;
            }
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), a == 0, a == N - 1, getResourceProvider());
            cell.setMinimumWidth(AndroidUtilities.dp(200));
            cell.setTextAndIcon(items.get(a), icons.get(a));
            final Integer option = options.get(a);
            popupLayout.addView(cell);
            final int i = a;
            cell.setOnClickListener(v1 -> {
                if (selectedObject == null || i >= options.size()) {
                    return;
                }
                processSelectedOption(option, options, popupLayout, cell);
            });
            if (option == OPTION_DETAILS) {
                int swipeBackIndex = popupLayout.addViewToSwipeBack(new MessageDetailsPopupWrapper(this, popupLayout.getSwipeBack(), selectedObject, getResourceProvider()) {
                    @Override
                    public void copy(String text) {
                        if (AndroidUtilities.addToClipboard(text)) {
                            BulletinFactory.of(ChatViewer.this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                        }
                    }
                }.swipeBack);
                cell.setRightIcon(R.drawable.msg_arrowright);
                cell.setOnClickListener(v1 -> {
                    if (selectedObject == null || getParentActivity() == null) {
                        return;
                    }
                    popupLayout.getSwipeBack().openForeground(swipeBackIndex);
                });
            }
        }

        ChatScrimPopupContainerLayout scrimPopupContainerLayout = new ChatScrimPopupContainerLayout(contentView.getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                    closeMenu();
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                boolean b = super.dispatchTouchEvent(ev);
                if (ev.getAction() == MotionEvent.ACTION_DOWN && !b) {
                    closeMenu();
                }
                return b;
            }
        };
        scrimPopupContainerLayout.addView(popupLayout, LayoutHelper.createLinearRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 0, 0, 0));
        scrimPopupContainerLayout.setPopupWindowLayout(popupLayout);

        scrimPopupWindow = new ActionBarPopupWindow(scrimPopupContainerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (scrimPopupWindow != this) {
                    return;
                }
                Bulletin.hideVisible();
                scrimPopupWindow = null;
            }
        };
        scrimPopupWindow.setPauseNotifications(true);
        scrimPopupWindow.setDismissAnimationDuration(220);
        scrimPopupWindow.setOutsideTouchable(true);
        scrimPopupWindow.setClippingEnabled(true);
        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow.setFocusable(true);
        scrimPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
        popupLayout.setFitItems(true);

        int popupX = v.getLeft() + (int) x - scrimPopupContainerLayout.getMeasuredWidth() + backgroundPaddings.left - AndroidUtilities.dp(28);
        if (popupX < AndroidUtilities.dp(6)) {
            popupX = AndroidUtilities.dp(6);
        } else if (popupX > chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - scrimPopupContainerLayout.getMeasuredWidth()) {
            popupX = chatListView.getMeasuredWidth() - AndroidUtilities.dp(6) - scrimPopupContainerLayout.getMeasuredWidth();
        }
        if (AndroidUtilities.isTablet()) {
            int[] location = new int[2];
            fragmentView.getLocationInWindow(location);
            popupX += location[0];
        }
        int totalHeight = contentView.getHeight();
        int height = scrimPopupContainerLayout.getMeasuredHeight() + AndroidUtilities.dp(48);
        int keyboardHeight = contentView.measureKeyboardHeight();
        if (keyboardHeight > AndroidUtilities.dp(20)) {
            totalHeight += keyboardHeight;
        }
        int popupY;
        if (height < totalHeight) {
            popupY = (int) (chatListView.getY() + v.getTop() + y);
            if (height - backgroundPaddings.top - backgroundPaddings.bottom > AndroidUtilities.dp(240)) {
                popupY += AndroidUtilities.dp(240) - height;
            }
            if (popupY < chatListView.getY() + AndroidUtilities.dp(24)) {
                popupY = (int) (chatListView.getY() + AndroidUtilities.dp(24));
            } else if (popupY > totalHeight - height - AndroidUtilities.dp(8)) {
                popupY = totalHeight - height - AndroidUtilities.dp(8);
            }
        } else {
            popupY = inBubbleMode ? 0 : AndroidUtilities.statusBarHeight;
        }
        scrimPopupContainerLayout.setMaxHeight(totalHeight - popupY);
        scrimPopupWindow.showAtLocation(chatListView, Gravity.LEFT | Gravity.TOP, popupX, popupY);
        scrimPopupWindow.dimBehind();
    }

    private TextureView createTextureView(boolean add) {
        if (parentLayout == null) {
            return null;
        }
        if (roundVideoContainer == null) {
            roundVideoContainer = new FrameLayout(getParentActivity()) {
                @Override
                public void setTranslationY(float translationY) {
                    super.setTranslationY(translationY);
                    contentView.invalidate();
                }
            };
            roundVideoContainer.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize);
                }
            });
            roundVideoContainer.setClipToOutline(true);
            roundVideoContainer.setWillNotDraw(false);
            roundVideoContainer.setVisibility(View.INVISIBLE);
            aspectRatioFrameLayout = new AspectRatioFrameLayout(getParentActivity());
            aspectRatioFrameLayout.setBackgroundColor(0);
            if (add) {
                roundVideoContainer.addView(aspectRatioFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
            videoTextureView = new TextureView(getParentActivity());
            videoTextureView.setOpaque(false);
            aspectRatioFrameLayout.addView(videoTextureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        if (roundVideoContainer.getParent() == null) {
            contentView.addView(roundVideoContainer, 1, new FrameLayout.LayoutParams(AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize));
        }
        roundVideoContainer.setVisibility(View.INVISIBLE);
        aspectRatioFrameLayout.setDrawingReady(false);
        return videoTextureView;
    }

    private String getSelectedObjectPath() {
        String path = selectedObject.messageOwner.attachPath;
        if (path != null && path.length() > 0) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (path == null || path.length() == 0) {
            path = getFileLoader().getPathToMessage(selectedObject.messageOwner).toString();
        }
        return path;
    }

    private boolean checkStoragePermission() {
        if ((Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && getParentActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            selectedObject = null;
            return false;
        }
        return true;
    }

    private void processSelectedOption(int option, ArrayList<Integer> options, ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout, ActionBarMenuSubItem subItem) {
        MessageObject messageObject = selectedObject;
        if (messageObject == null) {
            return;
        }
        switch (option) {
            case OPTION_COPY: {
                AndroidUtilities.addToClipboard(ChatUtils.getInstance().getMessageText(selectedObject, null));
                BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.MessageCopied)).show();
                break;
            }
            case OPTION_SAVE_TO_GALLERY: {
                String path = getSelectedObjectPath();
                if (selectedObject.type == MessageObject.TYPE_VIDEO || selectedObject.type == MessageObject.TYPE_PHOTO) {
                    if (!checkStoragePermission()) {
                        return;
                    }
                    MediaController.saveFile(path, getParentActivity(), selectedObject.type == MessageObject.TYPE_VIDEO ? 1 : 0, null, null);
                }
                break;
            }
            case OPTION_APPLY_FILE: {
                File locFile = null;
                if (selectedObject.messageOwner.attachPath != null && selectedObject.messageOwner.attachPath.length() != 0) {
                    File f = new File(selectedObject.messageOwner.attachPath);
                    if (f.exists()) {
                        locFile = f;
                    }
                }
                if (locFile == null) {
                    File f = getFileLoader().getPathToMessage(selectedObject.messageOwner);
                    if (f.exists()) {
                        locFile = f;
                    }
                }
                if (locFile != null) {
                    if (locFile.getName().toLowerCase().endsWith("attheme")) {
                        saveScrollPositionForRecreate();
                        Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, selectedObject.getDocumentName(), null, true);
                        if (themeInfo != null) {
                            presentFragment(new ThemePreviewActivity(themeInfo));
                        } else {
                            scrollToPositionOnRecreate = -1;
                            if (getParentActivity() == null) {
                                selectedObject = null;
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString(R.string.AppName));
                            builder.setMessage(LocaleController.getString(R.string.IncorrectTheme));
                            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                            showDialog(builder.create());
                        }
                    } else {
                        if (LocaleController.getInstance().applyLanguageFile(locFile, currentAccount)) {
                            presentFragment(new LanguageSelectActivity());
                        } else {
                            if (getParentActivity() == null) {
                                selectedObject = null;
                                return;
                            }
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString(R.string.AppName));
                            builder.setMessage(LocaleController.getString(R.string.IncorrectLocalization));
                            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
                            showDialog(builder.create());
                        }
                    }
                }
                break;
            }
            case OPTION_SHARE: {
                String path = getSelectedObjectPath();
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(selectedObject.getDocument().mime_type);
                try {
                    intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", new File(path)));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignore) {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(path)));
                }
                try {
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.ShareFile)), 500);
                } catch (Exception ignore) {

                }
                break;
            }
            case OPTION_SAVE_TO_GALLERY2: {
                String path = getSelectedObjectPath();
                if (!checkStoragePermission()) {
                    return;
                }
                MediaController.saveFile(path, getParentActivity(), 0, null, null);
                break;
            }
            case OPTION_SAVE_STICKER: {
                showDialog(new StickersAlert(getParentActivity(), this, selectedObject.getInputStickerSet(), null, null, false));
                break;
            }
            case OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC: {
                if (!checkStoragePermission()) {
                    return;
                }
                String fileName = FileLoader.getDocumentFileName(selectedObject.getDocument());
                if (TextUtils.isEmpty(fileName)) {
                    fileName = selectedObject.getFileName();
                }
                String path = getSelectedObjectPath();
                MediaController.saveFile(path, getParentActivity(), selectedObject.isMusic() ? 3 : 2, fileName, selectedObject.getDocument() != null ? selectedObject.getDocument().mime_type : "");
                break;
            }
            case OPTION_SAVE_TO_GIFS: {
                MessagesController.getInstance(currentAccount).saveGif(selectedObject, messageObject.getDocument());
                break;
            }
            case OPTION_ADD_CONTACT: {
                Bundle args = new Bundle();
                args.putLong("user_id", selectedObject.messageOwner.media.user_id);
                args.putString("phone", selectedObject.messageOwner.media.phone_number);
                args.putBoolean("addContact", true);
                presentFragment(new ContactAddActivity(args));
                break;
            }
            case OPTION_COPY_PHONE: {
                AndroidUtilities.addToClipboard(messageObject.messageOwner.media.phone_number);
                BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.PhoneCopied)).show();
                break;
            }
            case OPTION_CALL: {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + selectedObject.messageOwner.media.phone_number));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getParentActivity().startActivityForResult(intent, 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                break;
            }
            case OPTION_JUMP_TO_DATE: {
                final int date = messageObject.messageOwner.date;
                if (parentLayout != null && parentLayout.getFragmentStack().size() > 1) {
                    BaseFragment previousFragment = parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 2);
                    if (previousFragment instanceof ChatActivity) {
                        final ChatActivity chatActivity = (ChatActivity) previousFragment;
                        AndroidUtilities.runOnUIThread(() -> {
                            finishFragment();
                            chatActivity.jumpToDate(date);
                        }, 300);
                    }
                }
                finishPreviewFragment();
                break;
            }
        }
        if (option != OPTION_DETAILS) {
            closeMenu();
            selectedObject = null;
        }
    }

    private void saveScrollPositionForRecreate() {
        if (chatLayoutManager != null) {
            int lastPosition = chatLayoutManager.findLastVisibleItemPosition();
            if (lastPosition < chatLayoutManager.getItemCount() - 1) {
                scrollToPositionOnRecreate = chatLayoutManager.findFirstVisibleItemPosition();
                RecyclerListView.Holder holder = (RecyclerListView.Holder) chatListView.findViewHolderForAdapterPosition(scrollToPositionOnRecreate);
                if (holder != null) {
                    scrollToOffsetOnRecreate = holder.itemView.getTop();
                } else {
                    scrollToPositionOnRecreate = -1;
                }
            } else {
                scrollToPositionOnRecreate = -1;
            }
        }
    }

    private int getMessageType(MessageObject messageObject) {
        if (messageObject == null) {
            return -1;
        }
        if (messageObject.type == 6) {
            return -1;
        } else if (messageObject.type == MessageObject.TYPE_DATE || messageObject.type == MessageObject.TYPE_ACTION_PHOTO || messageObject.type == MessageObject.TYPE_PHONE_CALL) {
            if (messageObject.getId() == 0) {
                return -1;
            }
            return 1;
        } else {
            if (messageObject.isVoice()) {
                return 2;
            } else if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
                TLRPC.InputStickerSet inputStickerSet = messageObject.getInputStickerSet();
                if (inputStickerSet instanceof TLRPC.TL_inputStickerSetID) {
                    if (!MediaDataController.getInstance(currentAccount).isStickerPackInstalled(inputStickerSet.id)) {
                        return 7;
                    }
                } else if (inputStickerSet instanceof TLRPC.TL_inputStickerSetShortName) {
                    if (!MediaDataController.getInstance(currentAccount).isStickerPackInstalled(inputStickerSet.short_name)) {
                        return 7;
                    }
                }
            } else if ((!messageObject.isRoundVideo() || messageObject.isRoundVideo() && BuildVars.DEBUG_VERSION) && (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto || messageObject.getDocument() != null || messageObject.isMusic() || messageObject.isVideo())) {
                boolean canSave = false;
                if (messageObject.messageOwner.attachPath != null && messageObject.messageOwner.attachPath.length() != 0) {
                    File f = new File(messageObject.messageOwner.attachPath);
                    if (f.exists()) {
                        canSave = true;
                    }
                }
                if (!canSave) {
                    File f = getFileLoader().getPathToMessage(messageObject.messageOwner);
                    if (f.exists()) {
                        canSave = true;
                    }
                }
                if (canSave) {
                    if (messageObject.getDocument() != null) {
                        String mime = messageObject.getDocument().mime_type;
                        if (mime != null) {
                            if (messageObject.getDocumentName().toLowerCase().endsWith("attheme")) {
                                return 10;
                            } else if (mime.endsWith("/xml")) {
                                return 5;
                            } else if (mime.endsWith("/png") || mime.endsWith("/jpg") || mime.endsWith("/jpeg")) {
                                return 6;
                            }
                        }
                    }
                    return 4;
                }
            } else if (messageObject.type == MessageObject.TYPE_CONTACT) {
                return 8;
            } else if (messageObject.isMediaEmpty()) {
                return 3;
            }
            return 2;
        }
    }

    @Override
    public void onRemoveFromParent() {
        MediaController.getInstance().setTextureView(videoTextureView, null, null, false);
    }

    private void hideFloatingDateView(boolean animated) {
        if (floatingDateView.getTag() != null && !currentFloatingDateOnScreen && (!scrollingFloatingDate || currentFloatingTopIsNotMessage)) {
            floatingDateView.setTag(null);
            if (animated) {
                floatingDateAnimation = new AnimatorSet();
                floatingDateAnimation.setDuration(150);
                floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, "alpha", 0.0f));
                floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(floatingDateAnimation)) {
                            floatingDateAnimation = null;
                        }
                    }
                });
                floatingDateAnimation.setStartDelay(500);
                floatingDateAnimation.start();
            } else {
                if (floatingDateAnimation != null) {
                    floatingDateAnimation.cancel();
                    floatingDateAnimation = null;
                }
                floatingDateView.setAlpha(0.0f);
            }
        }
    }

    private void checkScrollForLoad(boolean scroll) {
        if (chatLayoutManager == null || paused) {
            return;
        }
        int firstVisibleItem = chatLayoutManager.findFirstVisibleItemPosition();
        int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(chatLayoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
        if (visibleItemCount > 0) {
            int checkLoadCount = scroll ? 25 : 5;
            if (firstVisibleItem <= checkLoadCount && !loading && !endReached) {
                loadMessages(false);
            }
        }
    }

    private void updateTextureViewPosition() {
        boolean foundTextureViewMessage = false;
        int count = chatListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                MessageObject messageObject = messageCell.getMessageObject();
                if (roundVideoContainer != null && messageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    roundVideoContainer.setTranslationX(imageReceiver.getImageX());
                    roundVideoContainer.setTranslationY(fragmentView.getPaddingTop() + messageCell.getTop() + imageReceiver.getImageY());
                    fragmentView.invalidate();
                    roundVideoContainer.invalidate();
                    foundTextureViewMessage = true;
                    break;
                }
            }
        }
        if (roundVideoContainer != null) {
            MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
            if (!foundTextureViewMessage) {
                roundVideoContainer.setTranslationY(-AndroidUtilities.roundMessageSize - 100);
                fragmentView.invalidate();
                if (messageObject != null && messageObject.isRoundVideo()) {
                    if (checkTextureViewPosition || PipRoundVideoView.getInstance() != null) {
                        MediaController.getInstance().setCurrentVideoVisible(false);
                    }
                }
            } else {
                MediaController.getInstance().setCurrentVideoVisible(true);
            }
        }
    }

    private void updateMessagesVisiblePart() {
        if (chatListView == null) {
            return;
        }
        int count = chatListView.getChildCount();
        int height = chatListView.getMeasuredHeight();
        int minPositionHolder = Integer.MAX_VALUE;
        int minPositionDateHolder = Integer.MAX_VALUE;
        View minDateChild = null;
        View minChild = null;
        View minMessageChild = null;
        boolean foundTextureViewMessage = false;
        for (int a = 0; a < count; a++) {
            View view = chatListView.getChildAt(a);
            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                int top = messageCell.getTop();
                int viewTop = top >= 0 ? 0 : -top;
                int viewBottom = messageCell.getMeasuredHeight();
                if (viewBottom > height) {
                    viewBottom = viewTop + height;
                }
                messageCell.setVisiblePart(viewTop, viewBottom - viewTop, contentView.getHeightWithKeyboard() - chatListView.getTop(), 0, view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getMeasuredWidth(), contentView.getBackgroundSizeY(), 0, 0, 0);
                messageCell.invalidate();

                MessageObject messageObject = messageCell.getMessageObject();
                if (roundVideoContainer != null && messageObject.isRoundVideo() && MediaController.getInstance().isPlayingMessage(messageObject)) {
                    ImageReceiver imageReceiver = messageCell.getPhotoImage();
                    roundVideoContainer.setTranslationX(imageReceiver.getImageX());
                    roundVideoContainer.setTranslationY(fragmentView.getPaddingTop() + top + imageReceiver.getImageY());
                    fragmentView.invalidate();
                    roundVideoContainer.invalidate();
                    foundTextureViewMessage = true;
                }
            } else if (view instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) view;
                cell.setVisiblePart(view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
                if (cell.hasGradientService()) {
                    cell.invalidate();
                }
            }
            if (view.getBottom() <= chatListView.getPaddingTop()) {
                continue;
            }
            int position = view.getBottom();
            if (position < minPositionHolder) {
                minPositionHolder = position;
                if (view instanceof ChatMessageCell || view instanceof ChatActionCell) {
                    minMessageChild = view;
                }
                minChild = view;
            }
            if (chatListItemAnimator == null || (!chatListItemAnimator.willRemoved(view) && !chatListItemAnimator.willAddedFromAlpha(view))) {
                if (view instanceof ChatActionCell && ((ChatActionCell) view).getMessageObject().isDateObject) {
                    if (view.getAlpha() != 1.0f) {
                        view.setAlpha(1.0f);
                    }
                    if (position < minPositionDateHolder) {
                        minPositionDateHolder = position;
                        minDateChild = view;
                    }
                }
            }
        }
        if (roundVideoContainer != null) {
            if (!foundTextureViewMessage) {
                roundVideoContainer.setTranslationY(-AndroidUtilities.roundMessageSize - 100);
                fragmentView.invalidate();
                MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
                if (messageObject != null && messageObject.isRoundVideo() && checkTextureViewPosition) {
                    MediaController.getInstance().setCurrentVideoVisible(false);
                }
            } else {
                MediaController.getInstance().setCurrentVideoVisible(true);
            }
        }
        if (minMessageChild != null) {
            MessageObject messageObject;
            if (minMessageChild instanceof ChatMessageCell) {
                messageObject = ((ChatMessageCell) minMessageChild).getMessageObject();
            } else {
                messageObject = ((ChatActionCell) minMessageChild).getMessageObject();
            }
            floatingDateView.setCustomDate(messageObject.messageOwner.date, false, true);
        }
        currentFloatingDateOnScreen = false;
        currentFloatingTopIsNotMessage = !(minChild instanceof ChatMessageCell || minChild instanceof ChatActionCell);
        if (minDateChild != null) {
            if (minDateChild.getTop() > chatListView.getPaddingTop() || currentFloatingTopIsNotMessage) {
                if (minDateChild.getAlpha() != 1.0f) {
                    minDateChild.setAlpha(1.0f);
                }
                hideFloatingDateView(!currentFloatingTopIsNotMessage);
            } else {
                if (minDateChild.getAlpha() != 0.0f) {
                    minDateChild.setAlpha(0.0f);
                }
                if (floatingDateAnimation != null) {
                    floatingDateAnimation.cancel();
                    floatingDateAnimation = null;
                }
                if (floatingDateView.getTag() == null) {
                    floatingDateView.setTag(1);
                }
                if (floatingDateView.getAlpha() != 1.0f) {
                    floatingDateView.setAlpha(1.0f);
                }
                currentFloatingDateOnScreen = true;
            }
            int offset = minDateChild.getBottom() - chatListView.getPaddingTop();
            if (offset > floatingDateView.getMeasuredHeight() && offset < floatingDateView.getMeasuredHeight() * 2) {
                floatingDateView.setTranslationY(-floatingDateView.getMeasuredHeight() * 2 + offset);
            } else {
                floatingDateView.setTranslationY(0);
            }
        } else {
            hideFloatingDateView(true);
            floatingDateView.setTranslationY(0);
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        if (isOpen) {
            notificationsLocker.lock();
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            notificationsLocker.unlock();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (contentView != null) {
            contentView.onResume();
        }
        paused = false;
        checkScrollForLoad(false);
        if (wasPaused) {
            wasPaused = false;
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (contentView != null) {
            contentView.onPause();
        }
        if (undoView != null) {
            undoView.hide(true, 0);
        }
        paused = true;
        wasPaused = true;
        if (AvatarPreviewer.hasVisibleInstance()) {
            AvatarPreviewer.getInstance().close();
        }
    }

    @Override
    public void onBecomeFullyHidden() {
        if (undoView != null) {
            undoView.hide(true, 0);
        }
    }

    public void openVCard(TLRPC.User user, String vcard, String first_name, String last_name) {
        try {
            File f = AndroidUtilities.getSharingDirectory();
            f.mkdirs();
            f = new File(f, "vcard.vcf");
            BufferedWriter writer = new BufferedWriter(new FileWriter(f));
            writer.write(vcard);
            writer.close();
            showDialog(new PhonebookShareAlert(this, null, user, null, f, first_name, last_name));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (visibleDialog instanceof DatePickerDialog) {
            visibleDialog.dismiss();
        }
    }

    private void alertUserOpenError(MessageObject message) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        if (message.type == MessageObject.TYPE_VIDEO) {
            builder.setMessage(LocaleController.getString(R.string.NoPlayerInstalled));
        } else {
            builder.setMessage(LocaleController.formatString(R.string.NoHandleAppInstalled, message.getDocument().mime_type));
        }
        showDialog(builder.create());
    }

    public void showOpenUrlAlert(final String url, boolean ask) {
        if (Browser.isInternalUrl(url, null) || !ask) {
            Browser.openUrl(getParentActivity(), url, true);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString(R.string.OpenUrlTitle));
            builder.setMessage(LocaleController.formatString(R.string.OpenUrlAlert2, url));
            builder.setPositiveButton(LocaleController.getString(R.string.Open), (dialogInterface, i) -> Browser.openUrl(getParentActivity(), url, true));
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(builder.create());
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateMessageAnimatedInternal(MessageObject message, boolean updateReactions) {
        if (chatAdapter == null || fragmentView == null) {
            return;
        }
        MessageObject existing = messagesDict.get(message.getId());
        if (updateReactions) {
            message.forceUpdate = true;
            message.reactionsChanged = true;
        }
        int index = messages.indexOf(existing);
        if (index >= 0) {
            chatAdapter.notifyItemChanged(messages.size() - (index - chatAdapter.messagesStartRow) - 1);
        }
    }

    public class ChatActivityAdapter extends RecyclerView.Adapter {

        private final Context mContext;
        private int rowCount;
        private int loadingUpRow;
        private int messagesStartRow;
        private int messagesEndRow;

        public ChatActivityAdapter(Context context) {
            mContext = context;
        }

        public void updateRows() {
            rowCount = 0;
            if (!messages.isEmpty()) {
                if (!endReached) {
                    loadingUpRow = rowCount++;
                } else {
                    loadingUpRow = -1;
                }
                messagesStartRow = rowCount;
                rowCount += messages.size();
                messagesEndRow = rowCount;
            } else {
                loadingUpRow = -1;
                messagesStartRow = -1;
                messagesEndRow = -1;
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public long getItemId(int i) {
            return RecyclerListView.NO_ID;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                if (!chatMessageCellsCache.isEmpty()) {
                    view = chatMessageCellsCache.get(0);
                    chatMessageCellsCache.remove(0);
                } else {
                    view = new ChatMessageCell(mContext, currentAccount);
                }
                ChatMessageCell chatMessageCell = (ChatMessageCell) view;
                chatMessageCell.setDelegate(createMessageCellDelegate());
                chatMessageCell.setAllowAssistant(true);
            } else if (viewType == 1) {
                ChatActionCell actionCell = new ChatActionCell(mContext, false, theme) {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(info);
                        info.setVisibleToUser(true);
                    }
                };
                actionCell.setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                    @Override
                    public void didPressReplyMessage(ChatActionCell cell, int id) {

                    }

                    @Override
                    public void didClickImage(ChatActionCell cell) {
                        MessageObject message = cell.getMessageObject();
                        PhotoViewer.getInstance().setParentActivity(ChatViewer.this);
                        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 640);
                        if (photoSize != null) {
                            ImageLocation imageLocation = ImageLocation.getForPhoto(photoSize, message.messageOwner.action.photo);
                            PhotoViewer.getInstance().openPhoto(photoSize.location, imageLocation, provider);
                        } else {
                            PhotoViewer.getInstance().openPhoto(message, (ChatActivity) null, 0, 0, 0, provider);
                        }
                    }

                    @Override
                    public boolean didLongPress(ChatActionCell cell, float x, float y) {
                        return createMenu(cell);
                    }

                    @Override
                    public void needOpenUserProfile(long uid) {
                        if (uid < 0) {
                            Bundle args = new Bundle();
                            args.putLong("chat_id", -uid);
                            if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, ChatViewer.this)) {
                                presentFragment(new ChatActivity(args), true);
                            }
                        } else if (uid != UserConfig.getInstance(currentAccount).getClientUserId()) {
                            ProfileActivity fragment = new ProfileActivity(new Bundle());
                            fragment.setPlayProfileAnimation(0);
                            presentFragment(fragment);
                        }
                    }

                    @Override
                    public BaseFragment getBaseFragment() {
                        return ChatViewer.this;
                    }

                    @Override
                    public long getDialogId() {
                        return ChatViewer.this.getDialogId();
                    }
                });
                view = actionCell;
            } else if (viewType == 2) {
                view = new ChatUnreadCell(mContext, theme);
            } else {
                view = new ChatLoadingCell(mContext, contentView, theme);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        private ChatMessageCell.ChatMessageCellDelegate createMessageCellDelegate() {
            return new ChatMessageCell.ChatMessageCellDelegate() {

                @Override
                public boolean canPerformActions() {
                    return true;
                }

                @Override
                public boolean canPerformReply() {
                    return false;
                }

                @Override
                public void didPressCancelSendButton(ChatMessageCell cell) {

                }

                @Override
                public boolean doNotShowLoadingReply(MessageObject msg) {
                    return true;
                }

                @Override
                public void forceUpdate(ChatMessageCell cell, boolean anchorScroll) {
                    if (cell == null) {
                        return;
                    }
                    MessageObject messageObject = cell.getPrimaryMessageObject();
                    if (messageObject == null) {
                        return;
                    }
                    messageObject.forceUpdate = true;
                    updateMessageAnimatedInternal(messageObject, false);
                }

                @Override
                public void didPressCodeCopy(ChatMessageCell cell, MessageObject.TextLayoutBlock block) {
                    if (block == null || block.textLayout == null || block.textLayout.getText() == null) {
                        return;
                    }
                    String code = block.textLayout.getText().toString();
                    SpannableString text = new SpannableString(code);
                    text.setSpan(new CodeHighlighting.Span(false, 0, null, block.language, code), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    AndroidUtilities.addToClipboard(text);
                    BulletinFactory.of(ChatViewer.this).createCopyBulletin(LocaleController.getString(R.string.CodeCopied)).show();
                }

                @Override
                public void didPressSideButton(ChatMessageCell cell) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    showDialog(ShareAlert.createShareAlert(mContext, cell.getMessageObject(), null, ChatObject.isChannel(currentChat) && !currentChat.megagroup, null, false));
                }

                @Override
                public boolean needPlayMessage(ChatMessageCell cell, MessageObject messageObject, boolean muted) {
                    if (messageObject.isVoice() || messageObject.isRoundVideo()) {
                        boolean result = MediaController.getInstance().playMessage(messageObject, muted);
                        MediaController.getInstance().setVoiceMessagesPlaylist(null, false);
                        return result;
                    } else if (messageObject.isMusic()) {
                        return MediaController.getInstance().setPlaylist(messages, messageObject, 0);
                    }
                    return false;
                }

                @Override
                public void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY, boolean asForward) {
                    if (chat != null && chat != currentChat) {
                        Bundle args = new Bundle();
                        args.putLong("chat_id", chat.id);
                        if (postId != 0) {
                            args.putInt("message_id", postId);
                        }
                        if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args, ChatViewer.this)) {
                            presentFragment(new ChatActivity(args), true);
                        }
                    }
                }

                @Override
                public void didPressOther(ChatMessageCell cell, float otherX, float otherY) {
                    createMenu(cell);
                }

                @Override
                public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY, boolean asForward) {
                    if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                        openProfile(user);
                    }
                }

                @Override
                public boolean didLongPressUserAvatar(ChatMessageCell cell, TLRPC.User user, float touchX, float touchY) {
                    if (user != null && user.id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                        TLRPC.User cachedUser = getMessagesController().getUser(user.id);
                        if (cachedUser != null) {
                            user = cachedUser;
                        }
                        final TLRPC.User finalUser = user;
                        final AvatarPreviewer.MenuItem[] menuItems = {AvatarPreviewer.MenuItem.OPEN_PROFILE, AvatarPreviewer.MenuItem.SEND_MESSAGE};
                        final TLRPC.UserFull userFull = getMessagesController().getUserFull(user.id);
                        final AvatarPreviewer.Data data;
                        if (userFull != null) {
                            data = AvatarPreviewer.Data.of(user, userFull, menuItems);
                        } else {
                            data = AvatarPreviewer.Data.of(user, classGuid, menuItems);
                        }
                        if (AvatarPreviewer.canPreview(data)) {
                            AvatarPreviewer.getInstance().show((ViewGroup) fragmentView, getResourceProvider(), data, item -> {
                                switch (item) {
                                    case SEND_MESSAGE:
                                        openDialog(cell, finalUser);
                                        break;
                                    case OPEN_PROFILE:
                                        openProfile(finalUser);
                                        break;
                                }
                            });
                            return true;
                        }
                    }
                    return false;
                }

                private void openProfile(TLRPC.User user) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", user.id);
                    ProfileActivity fragment = new ProfileActivity(args);
                    fragment.setPlayProfileAnimation(0);
                    presentFragment(fragment);
                }

                private void openDialog(ChatMessageCell cell, TLRPC.User user) {
                    if (user != null) {
                        Bundle args = new Bundle();
                        args.putLong("user_id", user.id);
                        if (getMessagesController().checkCanOpenChat(args, ChatViewer.this)) {
                            presentFragment(new ChatActivity(args));
                        }
                    }
                }

                @Override
                public void didLongPress(ChatMessageCell cell, float x, float y) {
                    createMenu(cell);
                }

                @Override
                public void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
                    if (url == null) {
                        return;
                    }
                    MessageObject messageObject = cell.getMessageObject();
                    if (url instanceof URLSpanMono) {
                        ((URLSpanMono) url).copyToClipboard();
                        if (AndroidUtilities.shouldShowClipboardToast()) {
                            Toast.makeText(getParentActivity(), LocaleController.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
                        }
                    } else if (url instanceof URLSpanUserMention) {
                        long peerId = Utilities.parseLong(((URLSpanUserMention) url).getURL());
                        if (peerId > 0) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                            if (user != null) {
                                MessagesController.getInstance(currentAccount).openChatOrProfileWith(user, null, ChatViewer.this, 0, false);
                            }
                        } else {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                            if (chat != null) {
                                MessagesController.getInstance(currentAccount).openChatOrProfileWith(null, chat, ChatViewer.this, 0, false);
                            }
                        }
                    } else if (url instanceof URLSpanNoUnderline) {
                        String str = ((URLSpanNoUnderline) url).getURL();
                        if (str.startsWith("@")) {
                            MessagesController.getInstance(currentAccount).openByUserName(str.substring(1), ChatViewer.this, 0);
                        } else if (str.startsWith("#")) {
                            DialogsActivity fragment = new DialogsActivity(null);
                            fragment.setSearchString(str);
                            presentFragment(fragment);
                        }
                    } else {
                        final String urlFinal = ((URLSpan) url).getURL();
                        if (longPress) {
                            BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                            builder.setTitle(urlFinal);
                            builder.setItems(new CharSequence[]{LocaleController.getString(R.string.Open), LocaleController.getString(R.string.Copy)}, (dialog, which) -> {
                                if (which == 0) {
                                    Browser.openUrl(getParentActivity(), urlFinal, true);
                                } else if (which == 1) {
                                    String link = urlFinal;
                                    if (link.startsWith("mailto:")) {
                                        link = link.substring(7);
                                    } else if (link.startsWith("tel:")) {
                                        link = link.substring(4);
                                    }
                                    AndroidUtilities.addToClipboard(link);
                                }
                            });
                            showDialog(builder.create());
                        } else {
                            if (url instanceof URLSpanReplacement) {
                                showOpenUrlAlert(((URLSpanReplacement) url).getURL(), true);
                            } else {
                                if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                                    String lowerUrl = urlFinal.toLowerCase();
                                    String lowerUrl2 = messageObject.messageOwner.media.webpage.url.toLowerCase();
                                    if ((Browser.isTelegraphUrl(lowerUrl, false) || lowerUrl.contains("t.me/iv")) && (lowerUrl.contains(lowerUrl2) || lowerUrl2.contains(lowerUrl))) {
                                        ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChatViewer.this);
                                        ArticleViewer.getInstance().open(messageObject);
                                        return;
                                    }
                                }
                                Browser.openUrl(getParentActivity(), urlFinal, true);
                            }
                        }
                    }
                    if (longPress) {
                        cell.resetPressedLink(-1);
                    }
                }

                @Override
                public void needOpenWebView(MessageObject message, String url, String title, String description, String originalUrl, int w, int h) {
                    EmbedBottomSheet.show(ChatViewer.this, message, provider, title, description, originalUrl, url, w, h, false);
                }

                @Override
                public void didPressImage(ChatMessageCell cell, float x, float y, boolean fullPreview) {
                    MessageObject message = cell.getMessageObject();
                    if (message.getInputStickerSet() != null) {
                        showDialog(new StickersAlert(getParentActivity(), ChatViewer.this, message.getInputStickerSet(), null, null, false));
                    } else if (message.isVideo() || message.type == MessageObject.TYPE_PHOTO || message.type == MessageObject.TYPE_TEXT && !message.isWebpageDocument() || message.isGif()) {
                        PhotoViewer.getInstance().setParentActivity(ChatViewer.this);
                        PhotoViewer.getInstance().openPhoto(message, (ChatActivity) null, 0, 0, 0, provider);
                    } else if (message.type == MessageObject.TYPE_VIDEO) {
                        try {
                            File f = null;
                            if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                f = new File(message.messageOwner.attachPath);
                            }
                            if (f == null || !f.exists()) {
                                f = getFileLoader().getPathToMessage(message.messageOwner);
                            }
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            intent.setDataAndType(FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", f), "video/mp4");
                            getParentActivity().startActivityForResult(intent, 500);
                        } catch (Exception e) {
                            alertUserOpenError(message);
                        }
                    } else if (message.type == MessageObject.TYPE_GEO) {
                        if (!AndroidUtilities.isMapsInstalled(ChatViewer.this)) {
                            return;
                        }
                        LocationActivity fragment = new LocationActivity(0);
                        fragment.setMessageObject(message);
                        presentFragment(fragment);
                    } else if (message.type == MessageObject.TYPE_FILE || message.type == MessageObject.TYPE_TEXT) {
                        if (message.getDocumentName().toLowerCase().endsWith("attheme")) {
                            File locFile = null;
                            if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                File f = new File(message.messageOwner.attachPath);
                                if (f.exists()) {
                                    locFile = f;
                                }
                            }
                            if (locFile == null) {
                                File f = getFileLoader().getPathToMessage(message.messageOwner);
                                if (f.exists()) {
                                    locFile = f;
                                }
                            }
                            saveScrollPositionForRecreate();
                            Theme.ThemeInfo themeInfo = Theme.applyThemeFile(locFile, message.getDocumentName(), null, true);
                            if (themeInfo != null) {
                                presentFragment(new ThemePreviewActivity(themeInfo));
                                return;
                            } else {
                                scrollToPositionOnRecreate = -1;
                            }
                        }
                        try {
                            AndroidUtilities.openForView(message, getParentActivity(), null, false);
                        } catch (Exception e) {
                            alertUserOpenError(message);
                        }
                    }
                }

                @Override
                public void didPressInstantButton(ChatMessageCell cell, int type) {
                    MessageObject messageObject = cell.getMessageObject();
                    if (type == 0) {
                        if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.cached_page != null) {
                            ArticleViewer.getInstance().setParentActivity(getParentActivity(), ChatViewer.this);
                            ArticleViewer.getInstance().open(messageObject);
                        }
                    } else if (type == 5) {
                        TLRPC.MessageMedia media = messageObject.messageOwner.media;
                        openVCard(getMessagesController().getUser(media.user_id), media.vcard, media.first_name, media.last_name);
                    } else {
                        if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.webpage != null) {
                            Browser.openUrl(getParentActivity(), messageObject.messageOwner.media.webpage.url);
                        }
                    }
                }

                @Override
                public boolean didPressAnimatedEmoji(ChatMessageCell cell, AnimatedEmojiSpan span) {
                    if (getMessagesController().premiumFeaturesBlocked() || span == null || span.standard) {
                        return false;
                    }
                    long documentId = span.getDocumentId();
                    TLRPC.Document document = span.document == null ? AnimatedEmojiDrawable.findDocument(currentAccount, documentId) : span.document;
                    if (document == null) {
                        return false;
                    }
                    TLRPC.InputStickerSet inputStickerSet = MessageObject.getInputStickerSet(document);
                    if (inputStickerSet == null) {
                        return false;
                    }
                    ArrayList<TLRPC.InputStickerSet> inputSets = new ArrayList<>(1);
                    inputSets.add(inputStickerSet);
                    EmojiPacksAlert alert = new EmojiPacksAlert(ChatViewer.this, getParentActivity(), theme, inputSets);
                    alert.setPreviewEmoji(document);
                    alert.setCalcMandatoryInsets(contentView.getKeyboardHeight() > AndroidUtilities.dp(20));
                    showDialog(alert);
                    return true;
                }

                @Override
                public void didLongPressBotButton(ChatMessageCell cell, TL_keyboard.KeyboardButtonProto button) {
                    final TL_keyboard.TL_inlineButtonTypeCopy copyType = TLKeyboardHelper.getType(button, TL_keyboard.TL_inlineButtonTypeCopy.class);
                    final TL_keyboard.TL_inlineButtonTypeCallback callbackType = TLKeyboardHelper.getType(button, TL_keyboard.TL_inlineButtonTypeCallback.class);
                    final TL_keyboard.TL_inlineButtonTypeSwitchInline switchInlineType = TLKeyboardHelper.getType(button, TL_keyboard.TL_inlineButtonTypeSwitchInline.class);
                    final TL_keyboard.TL_inlineButtonTypeUserProfile userProfileType = TLKeyboardHelper.getType(button, TL_keyboard.TL_inlineButtonTypeUserProfile.class);
                    if (copyType == null && callbackType == null && switchInlineType == null && userProfileType == null
                            && !TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeUrl.class)
                            && !TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeGame.class)
                            && !TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeBuy.class)
                            && !TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeUrlAuth.class)
                            && !TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeWebView.class)) {
                        return;
                    }
                    if (copyType != null) {
                        didLongPressCopyButton(copyType.copy_text);
                        return;
                    }
                    final String text = button.getText();
                    final String buttonUrl = button.getUrl();
                    BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), false, theme);
                    builder.setTitle(text);
                    builder.setItems(new CharSequence[]{
                            LocaleController.getString(R.string.CopyTitle),
                            callbackType != null && callbackType.data != null ? LocaleController.getString(R.string.CopyCallback) : null,
                            !TextUtils.isEmpty(buttonUrl) ? LocaleController.getString(R.string.CopyLink) : null,
                            switchInlineType != null && switchInlineType.query != null ? LocaleController.getString(R.string.CopyInlineQuery) : null,
                            userProfileType != null && userProfileType.user_id != 0 ? LocaleController.getString(R.string.CopyID) : null
                    }, (dialog, which) -> {
                        if (which == 0) {
                            AndroidUtilities.addToClipboard(text);
                        } else if (which == 1) {
                            AndroidUtilities.addToClipboard(ChatUtils.getInstance().getTextFromCallback(callbackType.data));
                        } else if (which == 2) {
                            AndroidUtilities.addToClipboard(buttonUrl);
                        } else if (which == 3) {
                            AndroidUtilities.addToClipboard(switchInlineType.query);
                        } else if (which == 4) {
                            AndroidUtilities.addToClipboard(String.valueOf(userProfileType.user_id));
                        }
                        if (undoView != null) {
                            undoView.showWithAction(0, UndoView.ACTION_TEXT_COPIED, (Runnable) null);
                        }
                    });
                    showDialog(builder.create());
                    try {
                        cell.performHapticFeedback(VibratorUtils.getType(HapticFeedbackConstants.LONG_PRESS), HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                    } catch (Exception ignore) {
                    }
                }

                public void didLongPressCopyButton(String copyText) {
                    BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity(), false, theme);
                    builder.setTitle(copyText);
                    builder.setTitleMultipleLines(true);
                    builder.setItems(new CharSequence[]{LocaleController.getString(R.string.Copy)}, (dialog, which) -> {
                        AndroidUtilities.addToClipboard(copyText);
                        BulletinFactory.of(ChatViewer.this).createCopyBulletin(LocaleController.formatString(R.string.ExactTextCopied, copyText)).show();
                    });
                    showDialog(builder.create());
                }
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position == loadingUpRow) {
                ChatLoadingCell loadingCell = (ChatLoadingCell) holder.itemView;
                loadingCell.setProgressVisible(false);
            } else if (position >= messagesStartRow && position < messagesEndRow) {
                MessageObject message = messages.get(messages.size() - (position - messagesStartRow) - 1);
                View view = holder.itemView;

                if (view instanceof ChatMessageCell) {
                    final ChatMessageCell messageCell = (ChatMessageCell) view;
                    messageCell.isChat = false;
                    int nextType = getItemViewType(position + 1);
                    int prevType = getItemViewType(position - 1);
                    boolean pinnedBottom;
                    boolean pinnedTop;
                    if (!(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && nextType == holder.getItemViewType()) {
                        MessageObject nextMessage = messages.get(messages.size() - (position + 1 - messagesStartRow) - 1);
                        pinnedBottom = nextMessage.isOutOwner() == message.isOutOwner() && (nextMessage.getFromChatId() == message.getFromChatId()) && Math.abs(nextMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                    } else {
                        pinnedBottom = false;
                    }
                    if (prevType == holder.getItemViewType()) {
                        MessageObject prevMessage = messages.get(messages.size() - (position - messagesStartRow));
                        pinnedTop = !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && (prevMessage.getFromChatId() == message.getFromChatId()) && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                    } else {
                        pinnedTop = false;
                    }
                    messageCell.setMessageObject(message, null, pinnedBottom, pinnedTop, false);
                    messageCell.setHighlighted(false);
                    messageCell.setHighlightedText(null);
                } else if (view instanceof ChatActionCell) {
                    ChatActionCell actionCell = (ChatActionCell) view;
                    actionCell.setMessageObject(message);
                    actionCell.setAlpha(1.0f);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= messagesStartRow && position < messagesEndRow) {
                return messages.get(messages.size() - (position - messagesStartRow) - 1).contentType;
            }
            return 4;
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            final View view = holder.itemView;
            if (view instanceof ChatMessageCell || view instanceof ChatActionCell) {
                view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        view.getViewTreeObserver().removeOnPreDrawListener(this);

                        int height = chatListView.getMeasuredHeight();
                        int top = view.getTop();
                        int viewTop = top >= 0 ? 0 : -top;
                        int viewBottom = view.getMeasuredHeight();
                        if (viewBottom > height) {
                            viewBottom = viewTop + height;
                        }
                        if (holder.itemView instanceof ChatMessageCell) {
                            ((ChatMessageCell) view).setVisiblePart(viewTop, viewBottom - viewTop, contentView.getHeightWithKeyboard() - chatListView.getTop(), 0, view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getMeasuredWidth(), contentView.getBackgroundSizeY(), 0, 0, 0);
                        } else if (holder.itemView instanceof ChatActionCell) {
                            if (actionBar != null && contentView != null) {
                                ((ChatActionCell) view).setVisiblePart(view.getY() + actionBar.getMeasuredHeight() - contentView.getBackgroundTranslationY(), contentView.getBackgroundSizeY());
                            }
                        }
                        updateMessagesVisiblePart();
                        return true;
                    }
                });
            }
            if (holder.itemView instanceof ChatMessageCell) {
                final ChatMessageCell messageCell = (ChatMessageCell) holder.itemView;
                messageCell.setBackgroundDrawable(null);
                messageCell.setCheckPressed(true, false);
                messageCell.setHighlighted(false);
            }
        }

        @Override
        public void notifyDataSetChanged() {
            updateRows();
            try {
                super.notifyDataSetChanged();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemChanged(int position) {
            updateRows();
            try {
                super.notifyItemChanged(position);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            updateRows();
            try {
                super.notifyItemRangeChanged(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemInserted(int position) {
            updateRows();
            try {
                super.notifyItemInserted(position);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemMoved(int fromPosition, int toPosition) {
            updateRows();
            try {
                super.notifyItemMoved(fromPosition, toPosition);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            updateRows();
            try {
                super.notifyItemRangeInserted(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRemoved(int position) {
            updateRows();
            try {
                super.notifyItemRemoved(position);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            updateRows();
            try {
                super.notifyItemRangeRemoved(positionStart, itemCount);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static QuadroResult getEntities(MessagesStorage messagesStorage, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) {
        ArrayList<TLRPC.User> users = new ArrayList<>();
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        try {
            if (!usersToLoad.isEmpty()) {
                messagesStorage.getUsersInternal(usersToLoad, users);
            }
        } catch (Exception ignore) {
        }
        try {
            if (!chatsToLoad.isEmpty()) {
                messagesStorage.getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
            }
        } catch (Exception ignore) {
        }
        return new QuadroResult(users, chats);
    }

    public static class QuadroResult {

        private final ArrayList<TLRPC.User> users;
        private final ArrayList<TLRPC.Chat> chats;
        private LongSparseArray<TLRPC.User> usersDict;
        private LongSparseArray<TLRPC.Chat> chatsDict;

        public QuadroResult(ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats) {
            this.users = users;
            this.chats = chats;
        }

        public Pair<LongSparseArray<TLRPC.User>, LongSparseArray<TLRPC.Chat>> getDicts() {
            if (usersDict == null && chatsDict == null) {
                usersDict = new LongSparseArray<>();
                chatsDict = new LongSparseArray<>();
                for (TLRPC.User user : users) {
                    usersDict.put(user.id, user);
                }
                for (TLRPC.Chat chat : chats) {
                    chatsDict.put(chat.id, chat);
                }
            }
            return new Pair<>(usersDict, chatsDict);
        }

        public ArrayList<TLRPC.User> getUsers() {
            return users;
        }

        public ArrayList<TLRPC.Chat> getChats() {
            return chats;
        }
    }

    public static TLObject getDialogInAnyWay(long dialogId, Integer accountNum, boolean createUnknown) {
        TLObject dialog = getDialogFromAccountNumber(accountNum, dialogId);
        if (dialog != null) {
            return dialog;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a != accountNum && UserConfig.isValidAccount(a)) {
                dialog = getDialogFromAccountNumber(a, dialogId);
                if (dialog != null) {
                    return dialog;
                }
            }
        }
        if (!createUnknown) {
            return null;
        }
        TLRPC.TL_chat chat = new TLRPC.TL_chat();
        chat.id = dialogId;
        chat.title = "Unknown (ID: " + dialogId + ")";
        return chat;
    }

    private static TLObject getDialogFromAccountNumber(int accountNum, long dialogId) {
        TLObject userOrChat = MessagesController.getInstance(accountNum).getUserOrChat(dialogId);
        if (userOrChat != null) {
            return userOrChat;
        }
        TLRPC.User user = MessagesStorage.getInstance(accountNum).getUserSync(dialogId);
        if (user != null) {
            return user;
        }
        TLRPC.Chat chat = MessagesStorage.getInstance(accountNum).getChatSync(dialogId);
        return chat != null ? chat : MessagesStorage.getInstance(accountNum).getChatSync(Math.abs(dialogId));
    }
}
