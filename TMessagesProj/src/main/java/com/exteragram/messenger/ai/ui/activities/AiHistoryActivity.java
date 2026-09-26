package com.exteragram.messenger.ai.ui.activities;

import android.view.Gravity;
import android.view.View;

import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.ai.data.Message;
import com.exteragram.messenger.ai.ui.MarkdownPreview;
import com.exteragram.messenger.ai.ui.components.HistoryMessageCell;
import com.exteragram.messenger.preferences.BasePreferencesActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

public class AiHistoryActivity extends BasePreferencesActivity {

    private static final int ID_SAVE_HISTORY = 1;
    private static final int ID_CLEAR_HISTORY = 2;

    private final ArrayList<Entry> entries = new ArrayList<>();
    private int lastEntryId = 100;

    public static class Entry {
        final int id;
        final Message message;
        CharSequence content;
        boolean expanded;

        public Entry(int id, Message message) {
            this.id = id;
            this.message = message;
            this.content = message.content() == null ? "" : message.content();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        for (Message message : AiConfig.getConversationHistory()) {
            entries.add(new Entry(lastEntryId++, message));
        }
        formatContents();
        return super.onFragmentCreate();
    }

    private void formatContents() {
        if (entries.isEmpty()) {
            return;
        }
        ArrayList<Entry> snapshot = new ArrayList<>(entries);
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<CharSequence> formatted = new ArrayList<>(snapshot.size());
            for (Entry entry : snapshot) {
                formatted.add(MarkdownPreview.format(entry.message.content()));
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (getContext() == null) {
                    return;
                }
                for (int i = 0; i < snapshot.size(); i++) {
                    snapshot.get(i).content = formatted.get(i);
                }
                updateList();
            });
        });
    }

    private void updateList() {
        if (listView == null || listView.adapter == null) {
            return;
        }
        listView.adapter.update(true);
    }

    @Override
    public String getTitle() {
        return LocaleController.getString(R.string.MessageHistory);
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asRippleCheck(ID_SAVE_HISTORY, LocaleController.getString(R.string.MessageHistory)).setChecked(AiConfig.getSaveHistory()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.HistoryInfo)));
        if (entries.isEmpty()) {
            return;
        }
        items.add(UItem.asHeader(LocaleController.formatPluralString("messages", entries.size())));
        for (Entry entry : entries) {
            items.add(HistoryMessageCell.Factory.asHistoryCell(entry.id, entry.message, entry.content, entry.expanded));
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_CLEAR_HISTORY, R.drawable.msg_delete, LocaleController.getString(R.string.ClearHistory)).red());
        items.add(UItem.asShadow(null));
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SAVE_HISTORY) {
            toggleBooleanSettingAndRefresh(item, AiConfig::setSaveHistory);
        } else if (item.id == ID_CLEAR_HISTORY) {
            AiController.clearHistory(this, getResourceProvider(), true, () -> {
                entries.clear();
                updateList();
            });
        } else if (item.object instanceof Message) {
            Entry entry = findEntry(item.id);
            if (entry != null) {
                entry.expanded = !entry.expanded;
                updateList();
            }
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item == null || !(item.object instanceof Message)) {
            return false;
        }
        Message message = (Message) item.object;
        ItemOptions.makeOptions(this, view)
                .add(R.drawable.msg_copy, LocaleController.getString(R.string.Copy), () -> {
                    if (AndroidUtilities.addToClipboard(message.content())) {
                        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                    }
                })
                .add(R.drawable.msg_delete, LocaleController.getString(R.string.Delete), true, () -> deleteTurn(item.id))
                .setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
                .setScrimViewBackground(listView.getClipBackground(view))
                .show();
        return true;
    }

    private Entry findEntry(int id) {
        for (Entry entry : entries) {
            if (entry.id == id) {
                return entry;
            }
        }
        return null;
    }

    private void deleteTurn(int id) {
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id == id) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            return;
        }
        int from = "assistant".equals(entries.get(index).message.role()) ? index - 1 : index;
        int to = from + 1;
        if (from < 0 || to >= entries.size() || "assistant".equals(entries.get(from).message.role()) || !"assistant".equals(entries.get(to).message.role())) {
            from = index;
            to = index;
        }
        for (int i = to; i >= from; i--) {
            entries.remove(i);
        }
        ArrayList<Message> history = new ArrayList<>();
        for (Entry entry : entries) {
            history.add(entry.message);
        }
        AiConfig.saveConversationHistory(history);
        updateList();
    }
}
