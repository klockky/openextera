package com.exteragram.messenger.utils.chats;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.StickerTimeMode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;

public abstract class StickerTime {

    public static final class Row {
        public MessageObject message;
        public float cellWidth;
        public float anchorY;
        public float stickerLeft;
        public float stickerRight;
        public float stickerWidth;
        public float timeWidth;
        public boolean hasName;
        public float nameLeft;
        public float nameTop;
        public float nameWidth;
        public float nameHeight;
        public boolean sideButton;
        public float overStickerOffsetX;
    }

    public static boolean isHidden(MessageObject messageObject) {
        return ExteraConfig.getStickerTimeMode() == StickerTimeMode.HIDDEN && messageObject != null && messageObject.isAnyKindOfSticker();
    }

    public static boolean shouldPreserveOnPreview(MessageObject current, MessageObject preview) {
        return current != null && preview != null
                && current.getId() == preview.getId()
                && preview.preview
                && preview.isAnyKindOfSticker()
                && ExteraConfig.getStickerTimeMode() != StickerTimeMode.HIDDEN;
    }

    public static float getTimeOffsetX(Row row, float timeX) {
        if (row.message == null || !row.message.isAnyKindOfSticker()) {
            return 0;
        }
        if (!isBeside(row)) {
            return row.overStickerOffsetX;
        }
        return getTimeLeft(row) + AndroidUtilities.dp(6) - timeX;
    }

    public static float getSideButtonX(Row row, float x) {
        if (!isBeside(row)) {
            return x;
        }
        float buttonSpace = AndroidUtilities.dp(8) + AndroidUtilities.dp(32);
        if (row.message.isOutOwner()) {
            return Math.min(x, getTimeLeft(row) - buttonSpace);
        }
        return Math.max(x, getTimeLeft(row) + getTimeWidth(row) + AndroidUtilities.dp(8));
    }

    public static float getSideButtonTouchLeft(Row row, float left) {
        return !isBeside(row) || row.message.isOutOwner() ? left : AndroidUtilities.dp(8);
    }

    public static float getSideButtonTouchRight(Row row, float right) {
        return isBeside(row) && row.message.isOutOwner() ? AndroidUtilities.dp(8) + AndroidUtilities.dp(32) : right;
    }

    private static boolean isBeside(Row row) {
        MessageObject message = row.message;
        if (message == null || !message.isAnyKindOfSticker() || ExteraConfig.getStickerTimeMode() != StickerTimeMode.SIDE
                || message.type == MessageObject.TYPE_EMOJIS || row.stickerWidth <= 0) {
            return false;
        }
        float padding = AndroidUtilities.dp(8);
        float left = getTimeLeft(row);
        float right = left + getTimeWidth(row);
        if (row.sideButton) {
            if (message.isOutOwner()) {
                left -= AndroidUtilities.dp(32) + padding;
            } else {
                right += AndroidUtilities.dp(32) + padding;
            }
        }
        return left >= padding && right <= row.cellWidth - padding;
    }

    private static float getTimeWidth(Row row) {
        return row.timeWidth + AndroidUtilities.dp(12) + (row.message.isOutOwner() ? AndroidUtilities.dp(20) : 0);
    }

    private static float getTimeLeft(Row row) {
        float padding = AndroidUtilities.dp(8);
        boolean nameOverlaps = row.hasName && overlapsRow(row, row.nameTop, row.nameTop + row.nameHeight);
        if (row.message.isOutOwner()) {
            float right = row.stickerLeft - padding;
            if (nameOverlaps) {
                right = Math.min(right, row.nameLeft - padding);
            }
            return right - getTimeWidth(row);
        }
        float left = row.stickerRight + padding;
        return nameOverlaps ? Math.max(left, row.nameLeft + row.nameWidth + padding) : left;
    }

    private static boolean overlapsRow(Row row, float top, float bottom) {
        float rowTop = row.anchorY - AndroidUtilities.dp(23);
        float rowHeight = Math.max(AndroidUtilities.dp(17), Theme.chat_timePaint.getTextSize() + AndroidUtilities.dp(5));
        return top < rowTop + rowHeight && bottom > rowTop;
    }
}
