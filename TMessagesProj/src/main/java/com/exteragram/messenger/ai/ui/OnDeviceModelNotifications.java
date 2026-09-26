package com.exteragram.messenger.ai.ui;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.utils.AppUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

public abstract class OnDeviceModelNotifications {

    private static final String CHANNEL_ID = "ai_on_device_model";
    private static final int NOTIFICATION_ID_BASE = 8732834;

    public static void showProgress(int modelKey, int percent) {
        NotificationCompat.Builder builder = createBuilder(modelKey)
                .setContentTitle(LocaleController.getString(R.string.DownloadingModel))
                .setSilent(true)
                .setOngoing(false)
                .setTimeoutAfter(5 * 60 * 1000L);
        if (percent >= 0) {
            builder.setContentText(percent + "%");
            builder.setProgress(100, percent, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        notify(modelKey, builder);
    }

    public static void showCompleted(int modelKey) {
        notify(modelKey, createBuilder(modelKey)
                .setContentTitle(LocaleController.getString(R.string.AIOnDeviceNotificationReady))
                .setContentText(LocaleController.getString(R.string.AIOnDeviceNotificationReadyInfo))
                .setAutoCancel(true));
    }

    public static void cancel(int modelKey) {
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_ID_BASE + modelKey);
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_NOTIFICATION", e);
        }
    }

    private static NotificationCompat.Builder createBuilder(int modelKey) {
        Context context = ApplicationLoader.applicationContext;
        Intent intent = new Intent(context, LaunchActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(IconManager.getNotificationIcon())
                .setColor(AppUtils.getNotificationColor())
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setSubText(getModelLabel(modelKey))
                .setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static String getModelLabel(int modelKey) {
        boolean preview = (modelKey & OnDeviceAvailability.MODEL_KEY_PREVIEW) != 0;
        boolean fast = (modelKey & OnDeviceAvailability.MODEL_KEY_FAST) != 0;
        if (preview && fast) {
            return LocaleController.getString(R.string.AIOnDevicePreviewModel) + ", " + LocaleController.getString(R.string.AIOnDeviceFastModel);
        } else if (preview) {
            return LocaleController.getString(R.string.AIOnDevicePreviewModel);
        } else if (fast) {
            return LocaleController.getString(R.string.AIOnDeviceFastModel);
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    private static void notify(int modelKey, NotificationCompat.Builder builder) {
        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
            manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(LocaleController.getString(R.string.AIOnDeviceNotificationChannel))
                    .setLightsEnabled(false)
                    .setVibrationEnabled(false)
                    .build());
            manager.notify(NOTIFICATION_ID_BASE + modelKey, builder.build());
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_NOTIFICATION", e);
        }
    }
}
