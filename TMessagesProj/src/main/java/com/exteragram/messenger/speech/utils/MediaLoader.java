package com.exteragram.messenger.speech.utils;

import android.os.Build;
import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class MediaLoader implements NotificationCenter.NotificationCenterDelegate {

    private final AccountInstance currentAccount;
    private final ArrayList<MessageObject> messageObjects;
    private final MessagesStorage.IntCallback onFinishRunnable;
    private final HashMap<String, MessageObject> loadingMessageObjects = new HashMap<>();
    private CountDownLatch waitingForFile;
    private int copiedFiles;

    private MediaLoader(AccountInstance account, ArrayList<MessageObject> messages, MessagesStorage.IntCallback onFinish) {
        currentAccount = account;
        messageObjects = messages;
        onFinishRunnable = onFinish;
        account.getNotificationCenter().addObserver(this, NotificationCenter.fileLoaded);
        account.getNotificationCenter().addObserver(this, NotificationCenter.fileLoadFailed);
    }

    public static void loadFiles(AccountInstance account, ArrayList<MessageObject> messages, MessagesStorage.IntCallback onFinish) {
        new MediaLoader(account, messages, onFinish).start();
    }

    public void start() {
        new Thread(() -> {
            try {
                for (int i = 0; i < messageObjects.size(); i++) {
                    MessageObject message = messageObjects.get(i);
                    File file = new File(resolvePath(message));
                    if (!file.exists()) {
                        waitingForFile = new CountDownLatch(1);
                        addMessageToLoad(message);
                        waitingForFile.await();
                    }
                    if (file.exists()) {
                        copiedFiles++;
                    }
                }
                checkIfFinished();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }).start();
    }

    private String resolvePath(MessageObject message) {
        String path = message.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path) && !new File(path).exists()) {
            path = null;
        }
        if (!TextUtils.isEmpty(path)) {
            return path;
        }
        path = null;
        if (Build.VERSION.SDK_INT >= 29) {
            TLRPC.Document document = message.getDocument();
            String fileName = FileLoader.getDocumentFileName(document);
            if (!TextUtils.isEmpty(fileName) && !(message.messageOwner instanceof TLRPC.TL_message_secret) && FileLoader.canSaveAsFile(message)) {
                File directory = FileLoader.getDirectory(FileLoader.MEDIA_DIR_FILES);
                if (directory != null) {
                    path = new File(directory, fileName).getAbsolutePath();
                }
            }
        }
        if (path == null) {
            path = FileLoader.getInstance(currentAccount.getCurrentAccount()).getPathToMessage(message.messageOwner).toString();
        }
        return path;
    }

    private void checkIfFinished() {
        if (!loadingMessageObjects.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (onFinishRunnable != null) {
                    AndroidUtilities.runOnUIThread(() -> onFinishRunnable.run(copiedFiles));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            currentAccount.getNotificationCenter().removeObserver(this, NotificationCenter.fileLoaded);
            currentAccount.getNotificationCenter().removeObserver(this, NotificationCenter.fileLoadFailed);
        });
    }

    private void addMessageToLoad(MessageObject message) {
        AndroidUtilities.runOnUIThread(() -> {
            TLRPC.Document document = message.getDocument();
            if (document == null) {
                return;
            }
            loadingMessageObjects.put(FileLoader.getAttachFileName(document), message);
            currentAccount.getFileLoader().loadFile(document, message, FileLoader.PRIORITY_LOW, message.shouldEncryptPhotoOrVideo() ? 2 : 0);
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed) {
            if (loadingMessageObjects.remove((String) args[0]) != null) {
                waitingForFile.countDown();
            }
        }
    }
}
