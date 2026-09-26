package com.exteragram.messenger.ai.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.exteragram.messenger.ai.data.Role;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

@SuppressLint("ViewConstructor")
public class RoleCell extends FrameLayout {

    private static final long DEFAULT_EMOJI_ID = 5359441070201513074L;

    private final int currentAccount;
    private final BackupImageView emojiView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final RadioButton radioButton;
    private boolean needDivider;

    public RoleCell(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        setWillNotDraw(false);

        emojiView = new BackupImageView(context);
        addView(emojiView, LayoutHelper.createFrame(32, 32, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 0, 0));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 68, 0, 62, 0));

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine(true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.LEFT);
        titleView.setIncludeFontPadding(false);
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setSingleLine(true);
        subtitleView.setMaxLines(1);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setGravity(Gravity.LEFT);
        subtitleView.setIncludeFontPadding(false);
        layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        radioButton = new RadioButton(context);
        radioButton.setSize(AndroidUtilities.dp(20));
        radioButton.setColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_radioBackgroundChecked));
        addView(radioButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
    }

    public RadioButton getRadioButton() {
        return radioButton;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(68), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    public void set(Role role, boolean checked, boolean divider) {
        needDivider = divider;
        titleView.setText(role.getName());
        subtitleView.setText(role.getPrompt());
        subtitleView.setVisibility(View.VISIBLE);
        radioButton.setChecked(checked, true);
        long emojiId = role.getEmojiId() != 0 ? role.getEmojiId() : DEFAULT_EMOJI_ID;
        emojiView.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, emojiId));
    }

    public static class Factory extends UItem.UItemFactory<RoleCell> {
        static {
            setup(new Factory());
        }

        @Override
        public RoleCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RoleCell(context, currentAccount, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            if (view instanceof RoleCell && item.object instanceof Role) {
                RoleCell cell = (RoleCell) view;
                cell.set((Role) item.object, item.checked, divider);
                cell.getRadioButton().setOnClickListener(item.clickCallback);
            }
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            if (!(a.object instanceof Role) || !(b.object instanceof Role)) {
                return false;
            }
            Role roleA = (Role) a.object;
            Role roleB = (Role) b.object;
            return a.checked == b.checked
                    && TextUtils.equals(roleA.getName(), roleB.getName())
                    && TextUtils.equals(roleA.getPrompt(), roleB.getPrompt())
                    && roleA.getEmojiId() == roleB.getEmojiId();
        }

        public static UItem asRoleCell(Role role, View.OnClickListener onClick) {
            UItem item = UItem.ofFactory(Factory.class);
            item.object = role;
            item.checked = role.isSelected();
            item.clickCallback = onClick;
            return item;
        }
    }
}
