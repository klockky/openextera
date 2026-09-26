package com.exteragram.messenger.utils.chats;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public abstract class DoubleTapUtils {

    public static final int ACTION_DISABLED = 0;
    public static final int ACTION_REACTION = 1;
    public static final int ACTION_REPLY = 2;
    public static final int ACTION_COPY = 3;
    public static final int ACTION_FORWARD = 4;
    public static final int ACTION_EDIT = 5;
    public static final int ACTION_SAVE = 6;
    public static final int ACTION_REPEAT = 7;
    public static final int ACTION_DELETE = 8;
    public static final int ACTION_TRANSLATE = 9;

    public static CharSequence[] getDoubleTapActions(boolean outOwner) {
        if (!outOwner) {
            return new CharSequence[]{
                    LocaleController.getString(R.string.Disable),
                    LocaleController.getString(R.string.Reactions),
                    LocaleController.getString(R.string.Reply),
                    LocaleController.getString(R.string.Copy),
                    LocaleController.getString(R.string.Forward),
                    LocaleController.getString(R.string.Save),
                    LocaleController.getString(R.string.Repeat),
                    LocaleController.getString(R.string.Delete),
                    LocaleController.getString(R.string.TranslateMessage)
            };
        }
        return new CharSequence[]{
                LocaleController.getString(R.string.Disable),
                LocaleController.getString(R.string.Reactions),
                LocaleController.getString(R.string.Reply),
                LocaleController.getString(R.string.Copy),
                LocaleController.getString(R.string.Forward),
                LocaleController.getString(R.string.Edit),
                LocaleController.getString(R.string.Save),
                LocaleController.getString(R.string.Repeat),
                LocaleController.getString(R.string.Delete),
                LocaleController.getString(R.string.TranslateMessage)
        };
    }

    public static CharSequence getDoubleTapActionLabel(int index, boolean outOwner) {
        CharSequence[] actions = getDoubleTapActions(outOwner);
        return actions[Math.min(Math.max(index, 0), actions.length - 1)];
    }

    public static int getDoubleTapActionIcon(int index, boolean outOwner) {
        int[] icons = getDoubleTapIcons(outOwner);
        return icons[Math.min(Math.max(index, 0), icons.length - 1)];
    }

    public static int[] getDoubleTapIcons(boolean outOwner) {
        if (!outOwner) {
            return new int[]{R.drawable.msg_block, R.drawable.msg_reactions2, R.drawable.menu_reply, R.drawable.msg_copy, R.drawable.msg_forward, R.drawable.msg_saved, R.drawable.msg_repeat, R.drawable.msg_delete, R.drawable.msg_translate};
        }
        return new int[]{R.drawable.msg_block, R.drawable.msg_reactions2, R.drawable.menu_reply, R.drawable.msg_copy, R.drawable.msg_forward, R.drawable.msg_edit, R.drawable.msg_saved, R.drawable.msg_repeat, R.drawable.msg_delete, R.drawable.msg_translate};
    }

    public static int getActionId(int setting, boolean outOwner) {
        int index = setting == ACTION_TRANSLATE ? ACTION_TRANSLATE : sanitizeSetting(setting);
        if (outOwner) {
            return index >= ACTION_REACTION && index <= ACTION_TRANSLATE ? index : ACTION_DISABLED;
        }
        switch (index) {
            case 1:
                return ACTION_REACTION;
            case 2:
                return ACTION_REPLY;
            case 3:
                return ACTION_COPY;
            case 4:
                return ACTION_FORWARD;
            case 5:
                return ACTION_SAVE;
            case 6:
                return ACTION_REPEAT;
            case 7:
                return ACTION_DELETE;
            case 8:
                return ACTION_TRANSLATE;
            default:
                return ACTION_DISABLED;
        }
    }

    public static int sanitizeSetting(int setting) {
        if (setting < 0) {
            return 0;
        }
        return Math.min(setting, 9);
    }
}
