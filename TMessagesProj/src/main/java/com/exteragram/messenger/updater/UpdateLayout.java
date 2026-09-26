package com.exteragram.messenger.updater;

import android.app.Activity;
import android.view.ViewGroup;

import org.telegram.ui.IUpdateLayout;

// TODO(openextera): disabled, exteraSquad infrastructure. The original layout showed the
//  "Update available" button for APKs published in the exteraSquad updater channel.
public class UpdateLayout extends IUpdateLayout {

    public UpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        super(activity, sideMenuContainer);
    }

    @Override
    public void updateFileProgress(Object[] args) {
    }

    @Override
    public void createUpdateUI(int currentAccount) {
    }

    @Override
    public void updateAppUpdateViews(int currentAccount, boolean animated) {
    }
}
