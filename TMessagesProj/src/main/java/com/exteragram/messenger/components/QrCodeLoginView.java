package com.exteragram.messenger.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.SlideView;

import java.util.HashMap;

public abstract class QrCodeLoginView extends SlideView {

    private final TextView titleView;
    private final TextView subtitleView;
    private final QrRenderView qrRenderView;

    public QrCodeLoginView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(LocaleController.getString(R.string.LoginQrTitle));
        titleView.setGravity(Gravity.CENTER);
        titleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 16, 32, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        subtitleView.setText(LocaleController.getString(R.string.LoginQrSubtitle));
        addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 8, 12, 0));

        qrRenderView = new QrRenderView(context);
        addView(qrRenderView, LayoutHelper.createLinear(280, 280, Gravity.CENTER_HORIZONTAL, 30, 30, 30, 30));
    }

    @Override
    public boolean needBackButton() {
        return true;
    }

    @Override
    public String getHeaderName() {
        return LocaleController.getString(R.string.LoginQrTitle);
    }

    public void setData(String link) {
        qrRenderView.setData(link);
    }

    public void clear() {
        qrRenderView.clear();
    }

    public void clear(boolean showLoading) {
        qrRenderView.clear(showLoading);
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        qrRenderView.updateColors();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        qrRenderView.dispose();
    }

    public static class QrRenderView extends View {

        private static final int QR_MODULES = 37;
        private static final int QR_PADDING = 16;

        private final int crossfadeWidthDp = 140;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AnimatedFloat contentBitmapAlpha = new AnimatedFloat(1f, this, 0, 2000, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final Paint crossfadeFromPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint crossfadeToPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final Path qrAreaClipPath = new Path();

        private Bitmap contentBitmap;
        private Bitmap oldContentBitmap;
        private RLottieDrawable loadingMatrix;
        private Bitmap qrLogo;
        private int qrLogoSize;

        private String link;
        private String hadLink;
        private Integer hadWidth;
        private Integer hadHeight;
        private boolean firstPrepare = true;
        private boolean loadingVisible = true;
        private int transitionDirection = 0;

        public QrRenderView(Context context) {
            super(context);
            crossfadeFromPaint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(crossfadeWidthDp), new int[]{0xFFFFFFFF, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            crossfadeFromPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            crossfadeToPaint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(crossfadeWidthDp), new int[]{0, 0xFFFFFFFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            crossfadeToPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            clear();
        }

        public void setData(String link) {
            this.link = link;
            if (!TextUtils.isEmpty(link) && contentBitmap == null) {
                loadingVisible = true;
            }
            int width = getWidth();
            int height = getHeight();
            Utilities.themeQueue.postRunnable(() -> prepareContent(width, height, link));
            invalidate();
        }

        public void clear() {
            clear(true);
        }

        public void clear(boolean showLoading) {
            link = null;
            hadLink = null;
            hadWidth = null;
            hadHeight = null;
            firstPrepare = true;
            if (oldContentBitmap != null) {
                oldContentBitmap.recycle();
                oldContentBitmap = null;
            }
            if (contentBitmap != null && showLoading) {
                oldContentBitmap = contentBitmap;
                contentBitmap = null;
                loadingVisible = true;
                transitionDirection = 1;
                contentBitmapAlpha.set(0f, true);
                contentBitmapAlpha.set(1f);
            } else {
                if (contentBitmap != null) {
                    contentBitmap.recycle();
                    contentBitmap = null;
                }
                loadingVisible = showLoading;
                contentBitmapAlpha.set(1f, true);
            }
            if (!showLoading && loadingMatrix != null && loadingMatrix.isRunning()) {
                loadingMatrix.stop();
            }
            invalidate();
        }

        public void updateColors() {
            invalidate();
        }

        private void prepareContent(int width, int height, String link) {
            if (width == 0 || height == 0) {
                return;
            }
            if (TextUtils.isEmpty(link)) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!TextUtils.equals(this.link, link)) {
                        return;
                    }
                    firstPrepare = false;
                    if (contentBitmap != null) {
                        Bitmap bitmap = contentBitmap;
                        contentBitmap = null;
                        contentBitmapAlpha.set(0f, true);
                        if (oldContentBitmap != null) {
                            oldContentBitmap.recycle();
                        }
                        oldContentBitmap = bitmap;
                        invalidate();
                    }
                });
                return;
            }
            if (TextUtils.equals(link, hadLink) && hadWidth != null && hadHeight != null && hadWidth == width && hadHeight == height) {
                return;
            }
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            int size = Math.max(1, width / QR_MODULES) * QR_MODULES + QR_PADDING * 2;
            Bitmap encoded;
            try {
                encoded = new TelegramQRCodeWriter().encode(link, size, size, hints, null, 0.75f, 0, 0xFF000000);
            } catch (Exception e) {
                FileLog.e(e);
                encoded = null;
            }
            if (encoded == null) {
                return;
            }
            Bitmap bitmap = encoded;
            AndroidUtilities.runOnUIThread(() -> {
                if (!TextUtils.equals(this.link, link)) {
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    return;
                }
                hadWidth = width;
                hadHeight = height;
                hadLink = link;

                boolean wasFirstPrepare = firstPrepare;
                boolean keepLoading = wasFirstPrepare && loadingVisible;
                Bitmap previous = contentBitmap;
                contentBitmap = bitmap;
                if (!wasFirstPrepare || keepLoading) {
                    transitionDirection = 0;
                    contentBitmapAlpha.set(0f, true);
                }
                firstPrepare = false;
                if (oldContentBitmap != null) {
                    oldContentBitmap.recycle();
                }
                oldContentBitmap = previous;
                loadingVisible = keepLoading;
                invalidate();
            });
        }

        private void drawLoading(Canvas canvas, int moduleSize, int qrSize, float scale) {
            if (loadingMatrix == null) {
                loadingMatrix = new RLottieDrawable(R.raw.qr_matrix, AndroidUtilities.dp(200), AndroidUtilities.dp(200));
                loadingMatrix.setMasterParent(this);
                loadingMatrix.setAutoRepeat(1);
                loadingMatrix.setColorFilter(0xFF000000, PorterDuff.Mode.MULTIPLY);
                loadingMatrix.start();
            } else if (!loadingMatrix.isRunning()) {
                loadingMatrix.start();
            }
            int width = getWidth();
            int inset = Math.round(QR_PADDING * scale);
            loadingMatrix.setBounds(inset, inset, width - inset, width - inset);
            loadingMatrix.setAlpha(0xFF);
            loadingMatrix.draw(canvas);

            int logoModules = Math.round((qrSize - QR_PADDING * 2) / 4.65f / moduleSize);
            if (logoModules % 2 != 1) {
                logoModules++;
            }
            int logoAreaSize = logoModules * moduleSize;
            int logoSize = logoAreaSize - 24;
            int logoAreaOffset = (qrSize - logoAreaSize) / 2;
            int logoOffset = (qrSize - logoSize) / 2;

            canvas.save();
            canvas.scale(scale, scale);
            drawFinderPatterns(canvas, qrSize, moduleSize);
            paint.setColor(0xFFFFFFFF);
            float radius = moduleSize * 7f / 4f * 0.75f;
            canvas.drawRoundRect(logoAreaOffset, logoAreaOffset, logoAreaOffset + logoAreaSize, logoAreaOffset + logoAreaSize, radius, radius, paint);
            if (qrLogo == null) {
                qrLogo = SvgHelper.getBitmap(AndroidUtilities.readRes(null, R.raw.qr_logo), logoSize, logoSize, false);
                qrLogoSize = logoSize;
            } else if (qrLogoSize != logoSize) {
                qrLogo.recycle();
                qrLogo = SvgHelper.getBitmap(AndroidUtilities.readRes(null, R.raw.qr_logo), logoSize, logoSize, false);
                qrLogoSize = logoSize;
            }
            if (qrLogo != null) {
                canvas.drawBitmap(qrLogo, logoOffset, logoOffset, null);
            }
            canvas.restore();
        }

        private void drawFinderPatterns(Canvas canvas, int qrSize, int moduleSize) {
            float size = 7f * moduleSize;
            float outerRadius = size / 3f * 0.75f;
            float middleRadius = size / 4f * 0.75f;
            float innerRadius = 5f * moduleSize / 4f * 0.75f;
            for (int i = 0; i < 3; i++) {
                float x, y;
                if (i == 0) {
                    x = QR_PADDING;
                    y = QR_PADDING;
                } else if (i == 1) {
                    x = qrSize - size - QR_PADDING;
                    y = QR_PADDING;
                } else {
                    x = QR_PADDING;
                    y = qrSize - size - QR_PADDING;
                }
                paint.setColor(0xFF000000);
                canvas.drawRoundRect(x, y, x + size, y + size, outerRadius, outerRadius, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawRoundRect(x + moduleSize, y + moduleSize, x + 6f * moduleSize, y + 6f * moduleSize, middleRadius, middleRadius, paint);
                paint.setColor(0xFF000000);
                canvas.drawRoundRect(x + 2f * moduleSize, y + 2f * moduleSize, x + size - 2f * moduleSize, y + size - 2f * moduleSize, innerRadius, innerRadius, paint);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), AndroidUtilities.dp(14), AndroidUtilities.dp(14), paint);
            clipPath.reset();
            clipPath.addRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), AndroidUtilities.dp(14), AndroidUtilities.dp(14), Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);

            int width = getWidth();
            int moduleSize = Math.max(1, width / QR_MODULES);
            int qrSize = moduleSize * QR_MODULES + QR_PADDING * 2;
            float scale = (float) width / qrSize;
            float qrRadius = moduleSize * 7f / 4f * 0.75f * scale;

            qrAreaClipPath.reset();
            qrAreaClipPath.addRoundRect(0, 0, width, width, qrRadius, qrRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(qrAreaClipPath);

            float alpha = contentBitmapAlpha.set(1f);
            boolean crossfade = alpha > 0 && alpha < 1;
            boolean reverse = transitionDirection == 1;

            if (alpha < 1f) {
                if (crossfade) {
                    RectF rect = AndroidUtilities.rectTmp;
                    rect.set(0, 0, width, width);
                    canvas.saveLayerAlpha(rect, 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                if (oldContentBitmap != null) {
                    canvas.save();
                    canvas.scale(scale, scale);
                    canvas.drawBitmap(oldContentBitmap, 0, 0, null);
                    canvas.restore();
                } else if (loadingVisible) {
                    drawLoading(canvas, moduleSize, qrSize, scale);
                }
                if (crossfade) {
                    float crossfadeWidth = AndroidUtilities.dp(crossfadeWidthDp);
                    canvas.save();
                    canvas.translate(0, getScanLineY(alpha, width, crossfadeWidth));
                    canvas.drawRect(0, reverse ? -crossfadeWidth - width : 0, width, crossfadeWidth + width, getOldLayerMaskPaint());
                    canvas.restore();
                    canvas.restore();
                }
            }
            if (alpha > 0) {
                if (crossfade) {
                    RectF rect = AndroidUtilities.rectTmp;
                    rect.set(0, 0, width, width);
                    canvas.saveLayerAlpha(rect, 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                if (contentBitmap != null) {
                    canvas.save();
                    canvas.scale(scale, scale);
                    canvas.drawBitmap(contentBitmap, 0, 0, null);
                    canvas.restore();
                } else if (loadingVisible) {
                    drawLoading(canvas, moduleSize, qrSize, scale);
                }
                if (crossfade) {
                    float crossfadeWidth = AndroidUtilities.dp(crossfadeWidthDp);
                    canvas.save();
                    canvas.translate(0, getScanLineY(alpha, width, crossfadeWidth));
                    canvas.drawRect(0, reverse ? 0 : -crossfadeWidth - width, width, width + crossfadeWidth, getNewLayerMaskPaint());
                    canvas.restore();
                    canvas.restore();
                }
            }
            canvas.restore();

            if (loadingVisible && contentBitmap != null && !contentBitmapAlpha.isInProgress() && alpha >= 1f) {
                loadingVisible = false;
                invalidate();
            }
            if (oldContentBitmap != null && contentBitmap == null && !contentBitmapAlpha.isInProgress() && alpha >= 1f) {
                oldContentBitmap.recycle();
                oldContentBitmap = null;
                invalidate();
            }
            if (!loadingVisible && loadingMatrix != null && loadingMatrix.isRunning()) {
                loadingMatrix.stop();
            }
            canvas.restore();
        }

        private float getScanLineY(float progress, int width, float crossfadeWidth) {
            if (transitionDirection == 1) {
                return -crossfadeWidth + (width + crossfadeWidth) * progress;
            }
            return -crossfadeWidth + (width + crossfadeWidth) * (1f - progress);
        }

        private Paint getOldLayerMaskPaint() {
            return transitionDirection == 1 ? crossfadeFromPaint : crossfadeToPaint;
        }

        private Paint getNewLayerMaskPaint() {
            return transitionDirection == 1 ? crossfadeToPaint : crossfadeFromPaint;
        }

        public void dispose() {
            if (loadingMatrix != null) {
                loadingMatrix.stop();
                loadingMatrix.recycle(false);
                loadingMatrix = null;
            }
            if (qrLogo != null) {
                qrLogo.recycle();
                qrLogo = null;
                qrLogoSize = 0;
            }
            if (contentBitmap != null) {
                contentBitmap.recycle();
                contentBitmap = null;
            }
            if (oldContentBitmap != null) {
                oldContentBitmap.recycle();
                oldContentBitmap = null;
            }
        }
    }
}
