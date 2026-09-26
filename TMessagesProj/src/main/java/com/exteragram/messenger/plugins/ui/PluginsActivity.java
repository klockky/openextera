package com.exteragram.messenger.plugins.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.ui.ActionBar.BaseFragment;

// Lite build: plugins are not available.
public final class PluginsActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        return fragmentView = new FrameLayout(context);
    }
}
