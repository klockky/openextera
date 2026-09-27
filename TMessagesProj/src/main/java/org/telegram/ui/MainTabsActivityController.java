package org.telegram.ui;

import android.view.View;

public interface MainTabsActivityController {
    boolean openAccountSelector(View button, View touchRelayView);

    void setTabsVisible(boolean visible);
}
