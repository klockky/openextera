package com.exteragram.messenger.debug;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatTextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

public class DebugOverlayView extends AppCompatTextView {

    private final ContentBuilder contentBuilder = new ContentBuilder();
    private DataSource dataSource;
    private long updateIntervalMs = 250;

    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            if (isAttachedToWindow()) {
                refresh();
                postDelayed(this, updateIntervalMs);
            }
        }
    };

    public interface DataSource {
        void build(ContentBuilder builder);
    }

    public static class ContentBuilder {
        private final StringBuilder stringBuilder = new StringBuilder(512);

        public ContentBuilder reset() {
            stringBuilder.setLength(0);
            return this;
        }

        public ContentBuilder title(CharSequence title) {
            return line(title);
        }

        public ContentBuilder section(CharSequence name) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append('\n');
            }
            stringBuilder.append('[').append(name).append(']');
            return this;
        }

        public ContentBuilder kv(String key, Object value) {
            return line(key + "=" + value);
        }

        public ContentBuilder line(CharSequence line) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append('\n');
            }
            stringBuilder.append(line);
            return this;
        }

        public CharSequence build() {
            return stringBuilder;
        }
    }

    public DebugOverlayView(Context context) {
        super(context);
        setTextColor(0xFFFFFFFF);
        setTextSize(10);
        setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        setGravity(Gravity.TOP | Gravity.LEFT);
        setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), 0xB0000000));
        setOnClickListener(v -> {
            if (dataSource != null && AndroidUtilities.addToClipboard(contentBuilder.build()) && AndroidUtilities.shouldShowClipboardToast()) {
                Toast.makeText(getContext(), LocaleController.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
            }
        });
        setLongClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        refresh();
    }

    public void setUpdateInterval(long intervalMs) {
        updateIntervalMs = Math.max(16, intervalMs);
    }

    public void refresh() {
        if (dataSource == null) {
            setText("");
            return;
        }
        contentBuilder.reset();
        dataSource.build(contentBuilder);
        setText(contentBuilder.build());
    }

    public static FrameLayout.LayoutParams createLayoutParams() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(AndroidUtilities.dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        layoutParams.setMargins(AndroidUtilities.dp(10), AndroidUtilities.dp(AndroidUtilities.statusBarHeight) - ActionBar.getCurrentActionBarHeight(), AndroidUtilities.dp(10), 0);
        return layoutParams;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(updater);
        post(updater);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(updater);
    }
}
