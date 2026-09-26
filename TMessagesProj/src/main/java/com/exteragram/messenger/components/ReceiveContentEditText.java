package com.exteragram.messenger.components;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.os.Build;
import android.text.Editable;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.OnReceiveContentViewBehavior;
import androidx.core.view.ViewCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.widget.TextViewOnReceiveContentListener;

import com.exteragram.messenger.math.inline.InlineMathController;

@SuppressLint({"RestrictedApi", "AppCompatCustomView"})
public abstract class ReceiveContentEditText extends EditText implements OnReceiveContentViewBehavior {

    private final TextViewOnReceiveContentListener defaultOnReceiveContentListener = new TextViewOnReceiveContentListener();
    private InlineMathController inlineMath;

    public ReceiveContentEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public Editable getText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return super.getText();
        }
        return super.getEditableText();
    }

    @Nullable
    @Override
    public InputConnection onCreateInputConnection(@NonNull EditorInfo outAttrs) {
        InputConnection ic = super.onCreateInputConnection(outAttrs);
        if (ic != null && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            String[] mimeTypes = ViewCompat.getOnReceiveContentMimeTypes(this);
            if (mimeTypes != null) {
                EditorInfoCompat.setContentMimeTypes(outAttrs, mimeTypes);
                ic = InputConnectionCompat.createWrapper(this, ic, outAttrs);
            }
        }
        if (ic != null && inlineMath != null) {
            return inlineMath.wrap(ic);
        }
        return ic;
    }

    public void setInlineMath(InlineMathController inlineMath) {
        this.inlineMath = inlineMath;
        requestLayout();
    }

    public InlineMathController getInlineMath() {
        return inlineMath;
    }

    @Override
    public int getCompoundPaddingBottom() {
        return super.getCompoundPaddingBottom() + (inlineMath != null ? inlineMath.getExtraBottom() : 0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (inlineMath != null && inlineMath.updateOnMeasure()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (inlineMath != null) {
            inlineMath.onTextChanged();
        }
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (inlineMath != null) {
            inlineMath.invalidateState();
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (inlineMath != null) {
            inlineMath.onFocusChanged(focused);
        }
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        if (inlineMath != null) {
            inlineMath.cancel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (inlineMath != null) {
            inlineMath.cancel();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (inlineMath != null && event.getAction() == MotionEvent.ACTION_DOWN) {
            inlineMath.onTouchDown();
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (inlineMath != null && inlineMath.onKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        if (handleDragEventViaReceiveContent(event)) {
            return true;
        }
        return super.onDragEvent(event);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (handleMenuActionViaReceiveContent(id)) {
            return true;
        }
        return super.onTextContextMenuItem(id);
    }

    @Nullable
    @Override
    public ContentInfoCompat onReceiveContent(@NonNull ContentInfoCompat payload) {
        return defaultOnReceiveContentListener.onReceiveContent(this, payload);
    }

    private boolean handleMenuActionViaReceiveContent(int actionId) {
        if (ViewCompat.getOnReceiveContentMimeTypes(this) == null || (actionId != android.R.id.paste && actionId != android.R.id.pasteAsPlainText)) {
            return false;
        }
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            ContentInfoCompat payload = new ContentInfoCompat.Builder(clip, ContentInfoCompat.SOURCE_CLIPBOARD)
                    .setFlags(actionId == android.R.id.paste ? 0 : ContentInfoCompat.FLAG_CONVERT_TO_PLAIN_TEXT)
                    .build();
            ViewCompat.performReceiveContent(this, payload);
        }
        return true;
    }

    private boolean handleDragEventViaReceiveContent(DragEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || event.getLocalState() != null || ViewCompat.getOnReceiveContentMimeTypes(this) == null) {
            return false;
        }
        Activity activity = findActivity();
        if (activity == null) {
            return false;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            return false;
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            return OnDropApi24Impl.onDropForTextView(event, this, activity);
        }
        return false;
    }

    private Activity findActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static final class OnDropApi24Impl {

        public static boolean onDropForTextView(DragEvent event, ReceiveContentEditText view, Activity activity) {
            activity.requestDragAndDropPermissions(event);
            int offset = view.getOffsetForPosition(event.getX(), event.getY());
            view.beginBatchEdit();
            try {
                Selection.setSelection(view.getText(), offset);
                ContentInfoCompat payload = new ContentInfoCompat.Builder(event.getClipData(), ContentInfoCompat.SOURCE_DRAG_AND_DROP).build();
                ViewCompat.performReceiveContent(view, payload);
            } finally {
                view.endBatchEdit();
            }
            return true;
        }
    }
}
