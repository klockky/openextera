package com.exteragram.messenger.ai.ui;

import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

public abstract class OnDeviceModelDialogs {

    public static void showDownloadDialog(BaseFragment fragment) {
        if (fragment == null || fragment.getContext() == null || OnDeviceAvailability.isDownloading()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(LocaleController.getString(R.string.AIOnDeviceDownloadTitle));
        builder.setSubtitle(AndroidUtilities.replaceTags(LocaleController.getString(R.string.AIOnDeviceDownloadInfo)));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.AIOnDeviceDownloadButton), (dialog, which) -> startDownload(fragment));
        fragment.showDialog(builder.create());
    }

    private static void startDownload(BaseFragment fragment) {
        int modelKey = OnDeviceAvailability.getModelKey();
        OnDeviceAvailability.download(modelKey, createListener(modelKey));
        OnDeviceModelNotifications.showProgress(modelKey, -1);
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_download, LocaleController.getString(R.string.AIOnDeviceDownloadStarted)).show();
    }

    private static OnDeviceAvailability.DownloadListener createListener(int modelKey) {
        return new OnDeviceAvailability.DownloadListener() {
            @Override
            public void onStarted(long totalBytes) {
                OnDeviceModelNotifications.showProgress(modelKey, -1);
            }

            @Override
            public void onProgress(float progress, long downloadedBytes) {
                OnDeviceModelNotifications.showProgress(modelKey, progress < 0 ? -1 : (int) (progress * 100));
            }

            @Override
            public void onCompleted() {
                OnDeviceModelNotifications.showCompleted(modelKey);
            }

            @Override
            public void onFailed(Exception e) {
                FileLog.e("AI_ON_DEVICE_DOWNLOAD_FAILED", e);
                OnDeviceModelNotifications.cancel(modelKey);
            }
        };
    }
}
