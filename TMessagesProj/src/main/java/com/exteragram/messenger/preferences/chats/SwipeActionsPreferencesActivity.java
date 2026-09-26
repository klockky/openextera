package com.exteragram.messenger.preferences.chats;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.BasePreferencesActivity;
import com.exteragram.messenger.preferences.chats.components.SwipeActionsCell;
import com.exteragram.messenger.utils.chats.SwipeAction;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;

public class SwipeActionsPreferencesActivity extends BasePreferencesActivity {

    private static final int PREVIEW = 1;
    private static final int LOOP = 2;
    private static final int REVERSED = 3;
    private static final int ACTION_ID_OFFSET = 100;

    private SwipeActionsCell previewCell;
    private Drawable reorderIcon;

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.SwipeActions);
    }

    @Override
    public View createView(Context context) {
        previewCell = new SwipeActionsCell(context);
        View view = super.createView(context);
        if (listView != null) {
            listView.allowReorder(true);
            listView.listenReorder(this::onReorder);
        }
        return view;
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (reorderIcon == null) {
            reorderIcon = ContextCompat.getDrawable(getContext(), R.drawable.list_reorder);
        }
        items.add(UItem.asCustom(PREVIEW, previewCell));
        items.add(UItem.asShadow(LocaleController.getString(R.string.SwipeActionsInfo)));

        adapter.whiteSectionStart();
        items.add(UItem.asHeader(LocaleController.getString(R.string.SwipeActionsBehavior)));
        items.add(UItem.asCheck(LOOP, LocaleController.getString(R.string.SwipeActionsLoop)).setChecked(ExteraConfig.getSwipeActionsLoop()).setSearchable(this).setLinkAlias("swipeActionsLoop", this));
        items.add(UItem.asCheck(REVERSED, LocaleController.getString(R.string.SwipeActionsReversed)).setChecked(ExteraConfig.getSwipeActionsReversed()).setSearchable(this).setLinkAlias("swipeActionsReversed", this));
        adapter.whiteSectionEnd();
        items.add(UItem.asShadow(LocaleController.getString(R.string.SwipeActionsBehaviorInfo)));

        addSection(items, adapter, LocaleController.getString(R.string.SwipeActionsEnabled), SwipeAction.enabled());
        items.add(UItem.asShadow(LocaleController.getString(R.string.SwipeActionsOrderInfo)));

        List<SwipeAction> disabled = SwipeAction.disabled();
        if (disabled.isEmpty()) {
            return;
        }
        addSection(items, adapter, LocaleController.getString(R.string.SwipeActionsDisabled), disabled);
        items.add(UItem.asShadow(null));
    }

    private void addSection(ArrayList<UItem> items, UniversalAdapter adapter, String header, List<SwipeAction> actions) {
        adapter.whiteSectionStart();
        items.add(UItem.asHeader(header));
        adapter.reorderSectionStart();
        for (SwipeAction action : actions) {
            UItem item = UItem.asButton(action.ordinal() + ACTION_ID_OFFSET, action.iconRes, LocaleController.getString(action.titleRes));
            item.object2 = reorderIcon;
            items.add(item);
        }
        adapter.reorderSectionEnd();
        adapter.whiteSectionEnd();
    }

    private void onReorder(int sectionId, ArrayList<UItem> items) {
        if (sectionId != 0) {
            return;
        }
        ArrayList<SwipeAction> order = new ArrayList<>();
        for (UItem item : items) {
            SwipeAction action = fromId(item.id);
            if (action != null) {
                order.add(action);
            }
        }
        SwipeAction.setEnabled(order);
        refresh();
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == LOOP) {
            toggleBooleanSettingAndRefresh(item, ExteraConfig::setSwipeActionsLoop);
            previewCell.updateActions();
        } else if (item.id == REVERSED) {
            toggleBooleanSettingAndRefresh(item, ExteraConfig::setSwipeActionsReversed);
        } else {
            SwipeAction action = fromId(item.id);
            if (action == null) {
                return;
            }
            action.setEnabled(!action.isEnabled());
            refresh();
        }
    }

    private void refresh() {
        if (previewCell != null) {
            previewCell.updateActions();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private SwipeAction fromId(int id) {
        int index = id - ACTION_ID_OFFSET;
        if (index < 0 || index >= SwipeAction.values().length) {
            return null;
        }
        return SwipeAction.values()[index];
    }
}
