package com.exteragram.messenger.icons.ui.components;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.caverock.androidsvg.SVG;
import com.exteragram.messenger.icons.ExteraResources;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.icons.IconPack;
import com.exteragram.messenger.icons.IconPackStorage;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

import kotlin.Unit;

public class ReplaceIconBottomSheet extends BottomSheet {

    private final IconPack iconPack;
    private final int resId;
    private final String resourceName;

    private Drawable originalDrawable;
    private int loadedOriginalWidth = 0;
    private int loadedOriginalHeight = 0;

    private Drawable newDrawable;
    private File newIconTempFile;
    private String newIconOriginalName;
    private int savedCustomFileWidth = 0;
    private int savedCustomFileHeight = 0;

    private IconInfoView originalIconInfoView;
    private IconInfoView newIconInfoView;
    private ButtonWithCounterView resetButton;

    private boolean needSave = false;
    private boolean needReset = false;
    private boolean waitingForResult = false;

    public ReplaceIconBottomSheet(Context context, int resId, IconPack iconPack) {
        super(context, false);
        fixNavigationBar();
        this.resId = resId;
        this.iconPack = iconPack;
        this.resourceName = context.getResources().getResourceEntryName(resId);
        setCustomView(createView(context));
        loadDrawables(context);
    }

    private static int[] getSvgSize(SVG svg) {
        int width = (int) (svg.getDocumentWidth() > 0 ? svg.getDocumentWidth() : svg.getDocumentViewBox().width());
        int height = (int) (svg.getDocumentHeight() > 0 ? svg.getDocumentHeight() : svg.getDocumentViewBox().height());
        return new int[]{width, height};
    }

    private void loadDrawables(Context context) {
        Utilities.globalQueue.postRunnable(() -> {
            Drawable original = null;
            if (context.getResources() instanceof ExteraResources) {
                try {
                    original = ((ExteraResources) context.getResources()).getOriginalDrawable(resId);
                } catch (Exception ignore) {
                }
            }
            if (original == null) {
                original = ResourcesCompat.getDrawable(context.getResources(), resId, context.getTheme());
            }
            final Drawable originalFinal = original;
            final int originalWidth = original != null ? original.getIntrinsicWidth() : 0;
            final int originalHeight = original != null ? original.getIntrinsicHeight() : 0;

            Drawable custom = null;
            int customWidth = 0;
            int customHeight = 0;
            String customFileName = iconPack.getIcons().get(resourceName);
            if (customFileName != null) {
                File file = new File(IconPackStorage.INSTANCE.getIconPacksDirectory(), iconPack.getId() + "/" + customFileName);
                if (file.exists()) {
                    try {
                        if (file.getName().toLowerCase().endsWith(".svg")) {
                            try (FileInputStream stream = new FileInputStream(file)) {
                                int[] size = getSvgSize(SVG.getFromInputStream(stream));
                                customWidth = size[0];
                                customHeight = size[1];
                            }
                        } else {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                            customWidth = options.outWidth;
                            customHeight = options.outHeight;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    Bitmap bitmap = IconManager.INSTANCE.createBitmapFromFile(file.getAbsolutePath(), resId, AndroidUtilities.displayMetrics.densityDpi, context.getTheme());
                    if (bitmap != null) {
                        custom = new BitmapDrawable(context.getResources(), bitmap);
                    }
                }
            }
            final Drawable customFinal = custom;
            final int customWidthFinal = customWidth;
            final int customHeightFinal = customHeight;
            AndroidUtilities.runOnUIThread(() -> {
                this.originalDrawable = originalFinal;
                loadedOriginalWidth = originalWidth;
                loadedOriginalHeight = originalHeight;
                newDrawable = customFinal;
                savedCustomFileWidth = customWidthFinal;
                savedCustomFileHeight = customHeightFinal;
                if (originalIconInfoView != null) {
                    originalIconInfoView.update(originalFinal, resourceName, originalWidth, originalHeight);
                }
                if (resetButton != null) {
                    resetButton.setText(LocaleController.getString(newDrawable != null ? R.string.Reset : R.string.Cancel), false);
                }
                updateNewInfo(newDrawable, iconPack.getIcons().get(resourceName), savedCustomFileWidth, savedCustomFileHeight);
            });
        });
    }

    private View createView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, AndroidUtilities.dp(16), 0, 0);

        LinearLayout iconsLayout = new LinearLayout(context);
        iconsLayout.setOrientation(LinearLayout.HORIZONTAL);
        iconsLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        iconsLayout.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

        originalIconInfoView = new IconInfoView(context, false);
        originalIconInfoView.update(originalDrawable, resourceName, loadedOriginalWidth, loadedOriginalHeight);
        iconsLayout.addView(originalIconInfoView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        LinearLayout arrowLayout = new LinearLayout(context);
        arrowLayout.setOrientation(LinearLayout.VERTICAL);
        arrowLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        arrowLayout.addView(new ArrowView(context), LayoutHelper.createLinear(24, 60));
        iconsLayout.addView(arrowLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.NO_GRAVITY, 24, 0, 24, 0));

        newIconInfoView = new IconInfoView(context, true);
        newIconInfoView.setTargetDimensions(loadedOriginalWidth, loadedOriginalHeight);
        newIconInfoView.getIconView().setFocusable(true);
        newIconInfoView.getIconView().setOnClickListener(v -> showSourceOptions(context, v));
        iconsLayout.addView(newIconInfoView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        updateNewInfo(newDrawable, iconPack.getIcons().get(resourceName), savedCustomFileWidth, savedCustomFileHeight);

        layout.addView(iconsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));

        LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        buttonsLayout.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        ButtonWithCounterView saveButton = new ButtonWithCounterView(context, true, resourcesProvider);
        saveButton.setRound();
        saveButton.setText(LocaleController.getString(R.string.Save), false);
        saveButton.setOnClickListener(v -> {
            if (newIconTempFile != null) {
                needSave = true;
            }
            dismiss();
        });
        buttonsLayout.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        resetButton = new ButtonWithCounterView(context, false, resourcesProvider);
        resetButton.setRound().setNeutral();
        resetButton.setText(LocaleController.getString(iconPack.getIcons().get(resourceName) != null ? R.string.Reset : R.string.Cancel), false);
        resetButton.setOnClickListener(v -> {
            if (newDrawable != null) {
                needReset = true;
            }
            dismiss();
        });
        buttonsLayout.addView(resetButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 8, 0, 0));

        layout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return layout;
    }

    private void showSourceOptions(Context context, View view) {
        if (isDismissed()) {
            return;
        }
        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
        if (lastFragment == null) {
            return;
        }
        Activity activity = lastFragment.getParentActivity();
        if (activity == null) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        boolean canPaste = false;
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                ClipData.Item item = clip.getItemAt(0);
                if (item.getUri() != null) {
                    canPaste = true;
                } else if (item.getText() != null) {
                    String text = item.getText().toString().trim();
                    canPaste = !text.isEmpty() && (text.contains("<svg") || text.contains("<SVG") || text.startsWith("/"));
                }
            }
        }
        ItemOptions.makeOptions(containerView, view)
            .addIf(canPaste, R.drawable.msg_copy, LocaleController.getString(R.string.PasteFromClipboard), () -> pasteFromClipboard(clipboard, context))
            .add(R.drawable.msg_photos, LocaleController.getString(R.string.SelectFromGallery), () -> startPicker(activity, false))
            .add(R.drawable.msg2_folder, LocaleController.getString(R.string.StoryMusicSelectFromFiles), () -> startPicker(activity, true))
            .setDrawScrim(false)
            .setOnTopOfScrim()
            .setDimAlpha(0)
            .setGravity(Gravity.CENTER_HORIZONTAL)
            .show();
    }

    private void pasteFromClipboard(ClipboardManager clipboard, Context context) {
        if (clipboard == null || clipboard.getPrimaryClip() == null || clipboard.getPrimaryClip().getItemCount() <= 0) {
            return;
        }
        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
        Uri uri = item.getUri();
        if (uri != null) {
            processSelectedImage(context, uri);
            return;
        }
        CharSequence text = item.getText();
        if (text != null) {
            processClipboardText(context, text);
        }
    }

    private void updateNewInfo(Drawable drawable, String name, int width, int height) {
        if (newIconInfoView == null) {
            return;
        }
        if (loadedOriginalWidth > 0 && loadedOriginalHeight > 0) {
            newIconInfoView.setTargetDimensions(loadedOriginalWidth, loadedOriginalHeight);
        }
        newIconInfoView.update(drawable, name, width, height);
    }

    private void startPicker(Activity activity, boolean fromFiles) {
        waitingForResult = true;
        IconManager.INSTANCE.startIconPicker(activity, fromFiles, uri -> {
            waitingForResult = false;
            if (uri != null) {
                processSelectedImage(activity, uri);
            }
            return Unit.INSTANCE;
        });
    }

    private void updateNewIconFromFile(Context context, File file, String name, int width, int height) {
        Bitmap bitmap = IconManager.INSTANCE.createBitmapFromFile(file.getAbsolutePath(), resId, AndroidUtilities.displayMetrics.densityDpi, context.getTheme());
        if (bitmap == null) {
            return;
        }
        Drawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        AndroidUtilities.runOnUIThread(() -> {
            if (isDismissed()) {
                file.delete();
                return;
            }
            if (newIconTempFile != null && newIconTempFile.exists() && !newIconTempFile.equals(file)) {
                newIconTempFile.delete();
            }
            newIconTempFile = file;
            newIconOriginalName = name;
            newDrawable = drawable;
            if (resetButton != null) {
                resetButton.setText(LocaleController.getString(R.string.Reset), false);
            }
            updateNewInfo(newDrawable, newIconOriginalName, width, height);
        });
    }

    private void processClipboardText(Context context, CharSequence text) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String string = text.toString();
                if (!string.contains("<svg") && !string.contains("<SVG")) {
                    if (string.trim().startsWith("/")) {
                        File file = new File(string.trim());
                        if (file.exists()) {
                            processSelectedImage(context, Uri.fromFile(file));
                        }
                    }
                    return;
                }
                File file = new File(ApplicationLoader.applicationContext.getCacheDir(), "temp_import_" + System.currentTimeMillis() + ".svg");
                try (FileOutputStream stream = new FileOutputStream(file)) {
                    stream.write(string.getBytes());
                }
                int[] size;
                try (FileInputStream stream = new FileInputStream(file)) {
                    size = getSvgSize(SVG.getFromInputStream(stream));
                }
                updateNewIconFromFile(context, file, resourceName + ".svg", size[0], size[1]);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void processSelectedImage(Context context, Uri uri) {
        Utilities.globalQueue.postRunnable(() -> {
            File rawFile = null;
            try {
                String name = null;
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (columnIndex != -1) {
                            name = cursor.getString(columnIndex);
                        }
                    }
                }
                if (TextUtils.isEmpty(name)) {
                    name = "icon_" + System.currentTimeMillis();
                }

                rawFile = new File(ApplicationLoader.applicationContext.getCacheDir(), "temp_import_" + System.currentTimeMillis() + "_raw");
                try (InputStream input = context.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(rawFile)) {
                    if (input != null) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    }
                }

                boolean isSvg = false;
                try (FileInputStream stream = new FileInputStream(rawFile)) {
                    byte[] header = new byte[1024];
                    int read = stream.read(header);
                    if (read > 0) {
                        String head = new String(header, 0, read).trim().toLowerCase(Locale.ROOT);
                        isSvg = head.contains("<svg");
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }

                String lowerName = name.toLowerCase();
                String extension;
                if (isSvg) {
                    extension = "svg";
                } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                    extension = "jpg";
                } else if (lowerName.endsWith(".webp")) {
                    extension = "webp";
                } else {
                    extension = "png";
                }
                if (!lowerName.endsWith("." + extension)) {
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) {
                        name = name.substring(0, dot);
                    }
                    name = name + "." + extension;
                }

                File file = new File(ApplicationLoader.applicationContext.getCacheDir(), "temp_import_" + System.currentTimeMillis() + "." + extension);
                if (rawFile.renameTo(file)) {
                    int width;
                    int height;
                    if (isSvg) {
                        try (FileInputStream stream = new FileInputStream(file)) {
                            int[] size = getSvgSize(SVG.getFromInputStream(stream));
                            width = size[0];
                            height = size[1];
                        }
                    } else {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                        width = options.outWidth;
                        height = options.outHeight;
                    }
                    updateNewIconFromFile(context, file, name, width, height);
                }
                if (rawFile.exists()) {
                    rawFile.delete();
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (rawFile != null && rawFile.exists()) {
                    rawFile.delete();
                }
            }
        });
    }

    public static class IconInfoView extends LinearLayout {

        private final BorderedImageView iconView;
        private final TextView infoName;
        private final TextView infoResolution;
        private float targetAspectRatio = -1f;

        public IconInfoView(Context context, boolean dashed) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);

            iconView = new BorderedImageView(context);
            iconView.setDashed(dashed);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            iconView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(iconView, LayoutHelper.createLinear(60, 60));

            infoName = new TextView(context);
            infoName.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            infoName.setTypeface(AndroidUtilities.bold());
            infoName.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            infoName.setGravity(Gravity.CENTER);
            infoName.setSingleLine(true);
            infoName.setEllipsize(TextUtils.TruncateAt.END);
            addView(infoName, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

            infoResolution = new TextView(context);
            infoResolution.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            infoResolution.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            infoResolution.setGravity(Gravity.CENTER);
            addView(infoResolution, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        public void setTargetDimensions(int width, int height) {
            if (width > 0 && height > 0) {
                targetAspectRatio = (float) width / height;
            } else {
                targetAspectRatio = -1f;
            }
        }

        public void update(Drawable drawable, String name, int width, int height) {
            if (drawable != null) {
                if (width <= 0) {
                    width = drawable.getIntrinsicWidth();
                }
                if (height <= 0) {
                    height = drawable.getIntrinsicHeight();
                }
                infoResolution.setText(String.format("%s (%s)", String.format(Locale.ROOT, "%d×%d", width, height), getAspectRatioString(width, height)));
                infoResolution.setVisibility(VISIBLE);
                if (targetAspectRatio > 0 && height > 0 && Math.abs((float) width / height - targetAspectRatio) > 0.1f) {
                    infoResolution.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                } else {
                    infoResolution.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                }
                iconView.setImageDrawable(drawable);
            } else {
                infoResolution.setVisibility(INVISIBLE);
                iconView.setImageDrawable(null);
            }
            if (name != null) {
                infoName.setText(name);
                infoName.setVisibility(VISIBLE);
            } else {
                infoName.setVisibility(INVISIBLE);
            }
        }

        public BorderedImageView getIconView() {
            return iconView;
        }

        private String getAspectRatioString(int width, int height) {
            if (height == 0) {
                return "?";
            }
            int divisor = gcd(width, height);
            return (width / divisor) + ":" + (height / divisor);
        }

        private int gcd(int a, int b) {
            return b == 0 ? a : gcd(b, a % b);
        }
    }

    public static class ArrowView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public ArrowView(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float halfLength = AndroidUtilities.dp(18) / 2f;
            float end = cx + halfLength;
            path.reset();
            path.moveTo(cx - halfLength, cy);
            path.lineTo(end, cy);
            path.moveTo(end - AndroidUtilities.dp(7), cy - AndroidUtilities.dp(7));
            path.lineTo(end, cy);
            path.lineTo(end - AndroidUtilities.dp(7), cy + AndroidUtilities.dp(7));
            canvas.drawPath(path, paint);
        }
    }

    public static class BorderedImageView extends ImageView {

        private final Paint bgPaint;
        private final float cornerRadius;
        private final Paint dashedPaint;
        private boolean isDashed = false;
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private final Paint solidPaint;
        private final float strokeWidth;

        public BorderedImageView(Context context) {
            this(context, null);
        }

        public BorderedImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
            cornerRadius = AndroidUtilities.dp(12);
            strokeWidth = AndroidUtilities.dpf2(1.25f);

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            int borderColor = AndroidUtilities.multiplyAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.3f);

            solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            solidPaint.setStyle(Paint.Style.STROKE);
            solidPaint.setColor(borderColor);
            solidPaint.setStrokeWidth(strokeWidth);

            dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dashedPaint.setStyle(Paint.Style.STROKE);
            dashedPaint.setColor(borderColor);
            dashedPaint.setStrokeWidth(strokeWidth);
            dashedPaint.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(8), AndroidUtilities.dp(8)}, 0));
        }

        public void setDashed(boolean dashed) {
            isDashed = dashed;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float inset = strokeWidth / 2f;
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint);
            super.onDraw(canvas);
            path.reset();
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.drawPath(path, isDashed ? dashedPaint : solidPaint);
        }
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (needSave && newIconTempFile != null && !needReset) {
            IconManager.INSTANCE.saveCustomIcon(iconPack.getId(), resId, newIconTempFile, newIconOriginalName);
            return;
        }
        if (newIconTempFile != null && newIconTempFile.exists()) {
            newIconTempFile.delete();
        }
        if (needReset) {
            IconManager.INSTANCE.resetCustomIcon(iconPack.getId(), resId);
        }
    }

    @Override
    public void dismiss() {
        if (waitingForResult) {
            return;
        }
        super.dismiss();
    }
}
