package com.exteragram.messenger.updater;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

/**
 * exteraGram fetched update metadata and APKs from an exteraSquad Telegram channel and installed
 * them through {@link android.content.pm.PackageInstaller} without any signature verification.
 * TODO(openextera): disabled, exteraSquad infrastructure. All entry points are no-ops until
 *  OpenExtera has its own verified update channel.
 */
public abstract class UpdaterUtils {

    public static void getAppUpdate(Utilities.Callback2<TLRPC.TL_help_appUpdate, TLRPC.TL_error> callback) {
        // TODO(openextera): disabled, exteraSquad infrastructure
        if (callback != null) {
            TLRPC.TL_error error = new TLRPC.TL_error();
            error.text = "UPDATER_DISABLED";
            callback.run(null, error);
        }
    }

    public static void installUpdate(Activity activity, TLRPC.Document document) {
        // TODO(openextera): disabled, exteraSquad infrastructure
    }

    /**
     * Registered in the manifest for {@code MY_PACKAGE_REPLACED}; originally relaunched the app
     * after a self-update.
     */
    public static class UpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO(openextera): disabled, exteraSquad infrastructure
        }
    }
}
