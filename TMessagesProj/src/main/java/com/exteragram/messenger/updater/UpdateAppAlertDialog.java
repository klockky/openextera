package com.exteragram.messenger.updater;

import android.app.Activity;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;

// TODO(openextera): disabled, exteraSquad infrastructure. The original sheet offered to download
//  and install an unverified APK from the exteraSquad updater channel; it is never shown.
public class UpdateAppAlertDialog extends BottomSheet {

    public UpdateAppAlertDialog(Activity activity, TLRPC.TL_help_appUpdate update, int account) {
        super(activity, false);
    }

    @Override
    public void show() {
    }
}
