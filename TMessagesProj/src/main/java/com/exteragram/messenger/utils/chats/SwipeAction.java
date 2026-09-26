package com.exteragram.messenger.utils.chats;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum SwipeAction {
    REACTION(1, R.drawable.msg_reactions2, R.string.DoubleTapSetting, 1.0f),
    REPLY(2, R.drawable.menu_reply, R.string.Reply, 1.09f),
    COPY(3, R.drawable.msg_copy, R.string.Copy, 1.0f),
    FORWARD(4, R.drawable.msg_forward, R.string.Forward, 1.09f),
    EDIT(5, R.drawable.msg_edit, R.string.Edit, 1.0f),
    SAVE(6, R.drawable.msg_saved, R.string.Save, 1.0f),
    REPEAT(7, R.drawable.msg_repeat, R.string.Repeat, 1.09f),
    DELETE(8, R.drawable.msg_delete, R.string.Delete, 1.09f),
    TRANSLATE(9, R.drawable.msg_translate, R.string.TranslateMessage, 1.09f);

    public final int actionId;
    public final int iconRes;
    public final int titleRes;
    public final float iconTrim;

    SwipeAction(int actionId, int iconRes, int titleRes, float iconTrim) {
        this.actionId = actionId;
        this.iconRes = iconRes;
        this.titleRes = titleRes;
        this.iconTrim = iconTrim;
    }

    public static String quickReactionEmoticon(int account) {
        MediaDataController mediaDataController = MediaDataController.getInstance(account);
        String reaction = mediaDataController.getDoubleTapReaction();
        if (reaction == null) {
            return null;
        }
        if (reaction.startsWith("animated_")) {
            return reaction;
        }
        TLRPC.TL_availableReaction availableReaction = mediaDataController.getReactionsMap().get(reaction);
        return availableReaction != null ? availableReaction.reaction : null;
    }

    public static SwipeAction of(int actionId) {
        for (SwipeAction action : values()) {
            if (action.actionId == actionId) {
                return action;
            }
        }
        return null;
    }

    public static List<SwipeAction> enabled() {
        ArrayList<SwipeAction> result = new ArrayList<>();
        for (String id : ExteraConfig.getSwipeActions().split(",")) {
            try {
                SwipeAction action = of(Integer.parseInt(id.trim()));
                if (action != null && !result.contains(action)) {
                    result.add(action);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return result;
    }

    public static List<SwipeAction> disabled() {
        ArrayList<SwipeAction> result = new ArrayList<>(Arrays.asList(values()));
        result.removeAll(enabled());
        return result;
    }

    public static void setEnabled(List<SwipeAction> actions) {
        StringBuilder sb = new StringBuilder();
        for (SwipeAction action : actions) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(action.actionId);
        }
        ExteraConfig.setSwipeActions(sb.toString());
    }

    public boolean isEnabled() {
        return enabled().contains(this);
    }

    public void setEnabled(boolean enabled) {
        List<SwipeAction> actions = enabled();
        if (enabled == actions.contains(this)) {
            return;
        }
        if (enabled) {
            int index = 0;
            for (SwipeAction action : values()) {
                if (action == this) {
                    break;
                }
                if (actions.contains(action)) {
                    index++;
                }
            }
            actions.add(index, this);
        } else {
            actions.remove(this);
        }
        setEnabled(actions);
    }
}
