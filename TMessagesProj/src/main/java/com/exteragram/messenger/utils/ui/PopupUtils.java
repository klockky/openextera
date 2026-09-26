package com.exteragram.messenger.utils.ui;

import android.content.Context;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public abstract class PopupUtils {

    public interface OnItemClickListener {
        void onClick(int i);
    }

    public interface OnMultiSelectListener {
        void onClick(boolean[] checked);
    }

    public static void showDialog(CharSequence[] items, String title, int checkedIndex, Context context, OnItemClickListener listener) {
        showDialog(items, null, title, checkedIndex, context, listener, null, true);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int checkedIndex, Context context, OnItemClickListener listener) {
        showDialog(items, icons, title, checkedIndex, context, listener, null, true);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int checkedIndex, Context context, OnItemClickListener listener, Theme.ResourcesProvider resourcesProvider, boolean withRadio) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        if (withRadio) {
            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setView(linearLayout);
            for (int a = 0; a < items.length; a++) {
                RadioColorCell cell = new RadioColorCell(context);
                cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                cell.setTag(a);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
                cell.setTextAndValue(items[a], checkedIndex == a);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    Integer which = (Integer) v.getTag();
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            }
        } else {
            if (icons != null) {
                builder.setItems(items, icons, (dialog, which) -> {
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            } else {
                builder.setItems(items, (dialog, which) -> {
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            }
            builder.create();
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static void showDialogWithoutRadio(ArrayList<? extends CharSequence> items, String title, Context context, OnItemClickListener listener) {
        CharSequence[] array = items.stream().map(String::valueOf).toArray(CharSequence[]::new);
        showDialog(array, null, title, -1, context, listener, null, false);
    }

    public static void showMultiSelectDialog(CharSequence[] items, boolean[] checked, String title, Context context, OnMultiSelectListener listener, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        for (int a = 0; a < items.length; a++) {
            CheckBoxCell cell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, true, resourcesProvider);
            cell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
            cell.setText(items[a], null, a < checked.length && checked[a], a < items.length - 1);
            cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked(), true));
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        builder.setView(linearLayout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            int count = linearLayout.getChildCount();
            boolean[] result = new boolean[count];
            for (int a = 0; a < count; a++) {
                result[a] = ((CheckBoxCell) linearLayout.getChildAt(a)).isChecked();
            }
            listener.onClick(result);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }
}
