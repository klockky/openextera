package com.exteragram.messenger.export.ui;

import android.view.View;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.export.output.FileManager;
import com.exteragram.messenger.preferences.BasePreferencesActivity;
import com.google.gson.annotations.SerializedName;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class DialogsView extends BasePreferencesActivity {

    private ArrayList<ParsedDialogObject> dialogObjects;
    private final String mainPath;

    public enum DialogsItem {
        GENERAL_HEADER
    }

    public DialogsView(String mainPath) {
        this.mainPath = mainPath;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadDialogs();
        return true;
    }

    @Override
    public String getTitle() {
        return "Dialogs";
    }

    private void loadDialogs() {
        String fileContent = FileManager.readFileContent(new File(mainPath, "result.json"));
        if (fileContent == null) {
            BulletinFactory.of(this).createErrorBulletin("Failed to read result.json!").show();
            dialogObjects = new ArrayList<>();
            return;
        }
        try {
            ParsedUserInfo userInfo = ExteraConfig.getGSON().fromJson(fileContent, ParsedUserInfo.class);
            if (userInfo != null && userInfo.chats != null) {
                dialogObjects = new ArrayList<>(Arrays.asList(userInfo.chats));
            } else {
                dialogObjects = new ArrayList<>();
            }
        } catch (Throwable e) {
            FileLog.e("Export read from file failed!", e);
            BulletinFactory.of(this).createErrorBulletin("Failed to parse result.json!").show();
            dialogObjects = new ArrayList<>();
        }
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Dialogs"));
        if (dialogObjects == null || dialogObjects.isEmpty()) {
            items.add(UItem.asShadow("No dialogs found in the export file."));
            return;
        }
        for (int i = 0; i < dialogObjects.size(); i++) {
            items.add(UItem.asButton(DialogsItem.values().length + 1 + i, dialogObjects.get(i).name).showDivider(i < dialogObjects.size() - 1));
        }
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        int firstDialogId = DialogsItem.values().length + 1;
        if (item.id >= firstDialogId) {
            int index = item.id - firstDialogId;
            if (dialogObjects == null || index < 0 || index >= dialogObjects.size()) {
                return;
            }
            presentFragment(new ChatViewer(mainPath + "/" + dialogObjects.get(index).path));
        }
    }

    public static class ParsedDialogObject {

        @SerializedName("id")
        public long id;

        @SerializedName("left")
        public boolean left;

        @SerializedName("name")
        public String name;

        @SerializedName("relativePath")
        public String path;

        private ParsedDialogObject() {
        }
    }

    public static class ParsedUserInfo {

        @SerializedName("about")
        public String about;

        @SerializedName("chats")
        public ParsedDialogObject[] chats;

        private ParsedUserInfo() {
        }
    }
}
