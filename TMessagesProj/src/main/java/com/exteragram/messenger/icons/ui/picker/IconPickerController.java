package com.exteragram.messenger.icons.ui.picker;

import android.annotation.SuppressLint;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

public abstract class IconPickerController {

    private static boolean isInitializing;

    @SuppressLint("StaticFieldLeak")
    private static IconPickerView pickerView;

    public static void onDestroy() {
        if (pickerView != null) {
            pickerView.saveConfig();
            if (pickerView.getParent() instanceof ViewGroup) {
                ((ViewGroup) pickerView.getParent()).removeView(pickerView);
            }
        }
        pickerView = null;
    }

    public static boolean onBackPressed(boolean invoked) {
        return pickerView != null && pickerView.onBackPressed(invoked);
    }

    public static void setActive(LaunchActivity activity, boolean active) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (active == (pickerView != null)) {
            return;
        }
        if (active) {
            showPicker(activity);
        } else {
            hidePicker(activity);
        }
    }

    private static void showPicker(LaunchActivity activity) {
        if (ExteraConfig.getEditingIconPackId() == null || pickerView != null || isInitializing) {
            return;
        }
        isInitializing = true;
        try {
            pickerView = new IconPickerView(activity);
            FrameLayout container = activity.getMainContainerFrameLayout();
            if (container != null) {
                container.addView(pickerView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                pickerView.showFab();
            }
        } finally {
            isInitializing = false;
        }
    }

    private static void hidePicker(LaunchActivity activity) {
        if (pickerView == null) {
            return;
        }
        IconObserver.INSTANCE.clear();
        final IconPickerView view = pickerView;
        pickerView = null;
        view.dismiss(() -> {
            FrameLayout container = activity.getMainContainerFrameLayout();
            if (container != null) {
                container.removeView(view);
            } else if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
        });
    }
}
