package com.exteragram.messenger.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.camera.core.Camera;
import androidx.camera.core.ZoomState;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraInfo;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.Arrays;
import java.util.List;

@SuppressLint("ViewConstructor")
public class InstantCameraZoomSlider extends CameraZoomSliderView {

    private static final int MAX_BIND_RETRIES = 25;
    private static final long BIND_RETRY_DELAY = 100L;
    private static final int DEFAULT_FRAME_RATE = 30;

    public static final AnimationProperties.FloatProperty<InstantCameraZoomSlider> OPEN_ALPHA = new AnimationProperties.FloatProperty<InstantCameraZoomSlider>("openAlpha") {
        @Override
        public void setValue(InstantCameraZoomSlider object, float value) {
            object.setOpenAlpha(value);
        }

        @Override
        public Float get(InstantCameraZoomSlider object) {
            return object.getOpenAlpha();
        }
    };

    public enum Backend {
        NONE,
        CAMERA_1,
        CAMERA_2,
        CAMERA_X
    }

    public interface OnCameraZoomChangeListener {
        void onCameraZoomChanged(float linearZoom, boolean fromSlider);
    }

    private final Theme.ResourcesProvider resourcesProvider;

    private Backend backend = Backend.NONE;
    private CameraXSession cameraXSession;
    private Camera2Session camera2Session;
    private CameraSession camera1Session;
    private float[] camera1ZoomRatios = new float[0];
    private float[] opticalZoomRatios = new float[0];
    private int camera1ZoomIndex = -1;
    private float camera1LinearZoom;
    private float defaultZoom = 1.0f;
    private float wideZoom = 1.0f;
    private float displayOneZoom = 1.0f;
    private boolean animateNextConfiguration;
    private boolean switchingCamera;
    private int bindRetries;

    private float pendingZoom = Float.NaN;
    private float lastAppliedZoom = Float.NaN;
    private long lastZoomAppliedAt;
    private boolean zoomFlushScheduled;

    private LiveData<ZoomState> cameraXZoomState;
    private OnCameraZoomChangeListener cameraZoomChangeListener;

    private BlurredBackgroundDrawable blurBackground;
    private float blurCornerRadius = -1.0f;

    private ValueAnimator appearAnimator;
    private float appearProgress;
    private float openAlpha;
    private int textureViewSize;
    private float baseTranslationY;

    private final Runnable bindRunnable = this::tryBind;
    private final Runnable zoomFlushRunnable = this::flushPendingZoom;
    private final Observer<ZoomState> cameraXZoomObserver = this::onCameraXZoomStateChanged;

    public InstantCameraZoomSlider(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setVisibility(View.GONE);
        applyAppearProgress();
        setOnZoomChangeListener(this::applyZoom);
        applyTelegramColors();
    }

    public float getOpenAlpha() {
        return openAlpha;
    }

    public void setOpenAlpha(float alpha) {
        if (openAlpha != alpha) {
            openAlpha = alpha;
            setAlpha(alpha * appearProgress);
        }
    }

    private void setAppearProgress(float progress) {
        if (appearProgress != progress) {
            appearProgress = progress;
            applyAppearProgress();
        }
    }

    private void applyAppearProgress() {
        setAlpha(openAlpha * appearProgress);
        float scale = 0.9f + 0.1f * appearProgress;
        setScaleX(scale);
        setScaleY(scale);
    }

    private void showAnimated() {
        setEnabled(true);
        if (!ExteraConfig.getZoomSlider()) {
            hideImmediately();
            return;
        }
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
        }
        if (appearAnimator != null || appearProgress >= 1.0f) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(appearProgress, 1.0f);
        appearAnimator = animator;
        animator.addUpdateListener(a -> setAppearProgress((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (appearAnimator == animation) {
                    appearAnimator = null;
                    setAppearProgress(1.0f);
                }
            }
        });
        animator.setDuration(180);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    private void hideImmediately() {
        cancelAppearAnimation();
        setAppearProgress(0.0f);
        setVisibility(View.GONE);
    }

    private void cancelAppearAnimation() {
        ValueAnimator animator = appearAnimator;
        if (animator != null) {
            appearAnimator = null;
            animator.cancel();
        }
    }

    private void applyTelegramColors() {
        int backgroundColor = Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        int onAccentColor = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
        int textColor = Theme.getColor(Theme.key_chat_messagePanelText, resourcesProvider);
        setColors(backgroundColor, textColor, accentColor, accentColor, onAccentColor);
        setToggleTextColor(textColor);
    }

    public void setBlurBackground(BlurredBackgroundDrawable drawable) {
        if (blurBackground != drawable) {
            blurBackground = drawable;
            blurCornerRadius = -1.0f;
            invalidate();
        }
    }

    @Override
    public boolean drawPillBackground(Canvas canvas, RectF bounds, float radius) {
        if (blurBackground == null) {
            return false;
        }
        if (blurCornerRadius != radius) {
            blurCornerRadius = radius;
            blurBackground.setRadius(radius);
        }
        blurBackground.setBounds(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
        blurBackground.draw(canvas);
        return true;
    }

    public void setOnCameraZoomChangeListener(OnCameraZoomChangeListener listener) {
        cameraZoomChangeListener = listener;
    }

    public void bindSession(CameraXSession session) {
        boolean animated = session != null && getVisibility() == View.VISIBLE && isLaidOut() && (switchingCamera || (backend == Backend.CAMERA_X && cameraXSession == session));
        switchingCamera = false;
        if (animated) {
            prepareZoomConfigurationTransition();
        }
        resetBinding(!animated);
        if (session == null) {
            return;
        }
        backend = Backend.CAMERA_X;
        cameraXSession = session;
        animateNextConfiguration = animated;
        if (animated) {
            setEnabled(false);
            setExpanded(false, true);
        }
        tryBind();
    }

    public void bindSession(Camera2Session session) {
        boolean animated = session != null && switchingCamera;
        switchingCamera = false;
        resetBinding(!animated);
        if (session == null) {
            return;
        }
        backend = Backend.CAMERA_2;
        camera2Session = session;
        animateNextConfiguration = animated;
        tryBind();
    }

    public void bindSession(CameraSession session, float linearZoom) {
        boolean animated = session != null && switchingCamera;
        switchingCamera = false;
        resetBinding(!animated);
        if (session == null) {
            return;
        }
        backend = Backend.CAMERA_1;
        camera1Session = session;
        camera1LinearZoom = Utilities.clamp01(linearZoom);
        animateNextConfiguration = animated;
        tryBind();
    }

    public void beginCameraSwitch() {
        if (backend != Backend.NONE && getVisibility() == View.VISIBLE && isLaidOut()) {
            switchingCamera = true;
            prepareZoomConfigurationTransition();
            setEnabled(false);
            setExpanded(false, true);
        }
    }

    public void unbindSession() {
        switchingCamera = false;
        resetBinding(true);
    }

    private void resetBinding(boolean hide) {
        setExternalZoomGestureActive(false);
        resetZoomThrottle();
        backend = Backend.NONE;
        detachCameraXZoomObserver();
        setZoom(getZoom());
        cameraXSession = null;
        camera2Session = null;
        camera1Session = null;
        camera1ZoomRatios = new float[0];
        opticalZoomRatios = new float[0];
        camera1ZoomIndex = -1;
        defaultZoom = 1.0f;
        wideZoom = 1.0f;
        displayOneZoom = 1.0f;
        bindRetries = 0;
        removeCallbacks(bindRunnable);
        animateNextConfiguration = false;
        if (hide) {
            cancelZoomConfigurationTransition();
            setEnabled(true);
            setExpanded(false, false);
            hideImmediately();
        }
    }

    public void syncZoom() {
        switch (backend) {
            case CAMERA_X:
                if (cameraXSession != null && cameraXSession.isReady()) {
                    setZoom(cameraXSession.getZoomRatio());
                }
                break;
            case CAMERA_2:
                if (camera2Session != null && camera2Session.isInitiated()) {
                    setZoom(camera2Session.getZoom());
                }
                break;
            case CAMERA_1:
                if (camera1ZoomRatios.length > 1) {
                    setZoom(camera1RatioForLinearZoom(camera1LinearZoom));
                }
                break;
            default:
                break;
        }
    }

    public void syncZoom(float zoom) {
        if (backend == Backend.CAMERA_1) {
            float linearZoom = Utilities.clamp01(zoom);
            camera1LinearZoom = linearZoom;
            camera1ZoomIndex = -1;
            if (camera1ZoomRatios.length > 1) {
                setZoom(camera1RatioForLinearZoom(linearZoom));
            }
        } else if (backend == Backend.CAMERA_2) {
            setZoom(zoom);
        } else {
            syncZoom();
        }
    }

    public void beginExternalZoomGesture() {
        if (backend != Backend.CAMERA_X) {
            return;
        }
        discardPendingZoom();
        setZoom(getZoom());
    }

    public void beginPinchZoomGesture() {
        beginExternalZoomGesture();
        setExternalZoomGestureActive(true);
    }

    public void endPinchZoomGesture() {
        setExternalZoomGestureActive(false);
    }

    public void beginSteppedZoomGesture() {
        beginExternalZoomGesture();
        setExpanded(true, true);
    }

    public void scaleCameraXZoom(float scale) {
        if (backend == Backend.CAMERA_X && cameraXSession != null && Float.isFinite(scale)) {
            setCameraXZoomRatio(getZoom() * scale);
        }
    }

    public void setCameraXZoomRatio(float ratio) {
        if (backend != Backend.CAMERA_X || cameraXSession == null) {
            return;
        }
        float clamped = Utilities.clamp(ratio, getMaximumZoom(), getMinimumZoom());
        setZoom(clamped);
        cameraXSession.setZoomRatio(clamped);
    }

    public float getCameraXResetZoom() {
        CameraXSession session = cameraXSession;
        if (!ExteraConfig.getStartWithWideAngleCamera() || (session != null && session.isActiveCameraFrontface())) {
            return defaultZoom;
        }
        return wideZoom;
    }

    public float[] getOpticalZoomRatios() {
        return Arrays.copyOf(opticalZoomRatios, opticalZoomRatios.length);
    }

    public float getDisplayOneZoom() {
        return displayOneZoom;
    }

    private void tryBind() {
        float minZoom;
        float maxZoom;
        boolean frontface = false;
        switch (backend) {
            case CAMERA_X: {
                if (cameraXSession == null || !cameraXSession.isReady()) {
                    retryBinding();
                    return;
                }
                ZoomState state = cameraXSession.camera.getCameraInfo().getZoomState().getValue();
                if (state == null) {
                    retryBinding();
                    return;
                }
                minZoom = state.getMinZoomRatio();
                maxZoom = state.getMaxZoomRatio();
                frontface = cameraXSession.isActiveCameraFrontface();
                break;
            }
            case CAMERA_2: {
                if (camera2Session == null || !camera2Session.isInitiated()) {
                    retryBinding();
                    return;
                }
                minZoom = camera2Session.getMinZoom();
                maxZoom = camera2Session.getMaxZoom();
                break;
            }
            case CAMERA_1: {
                if (camera1Session == null || !camera1Session.isInitied()) {
                    retryBinding();
                    return;
                }
                float[] ratios = readCamera1ZoomRatios(camera1Session);
                if (ratios == null) {
                    camera1ZoomRatios = new float[0];
                    retryBinding();
                    return;
                }
                camera1ZoomRatios = ratios;
                minZoom = ratios.length == 0 ? 1.0f : ratios[0];
                maxZoom = ratios.length == 0 ? 1.0f : ratios[ratios.length - 1];
                break;
            }
            default:
                return;
        }
        if (!Float.isFinite(minZoom) || !Float.isFinite(maxZoom) || minZoom <= 0.0f || maxZoom <= minZoom) {
            animateNextConfiguration = false;
            cancelZoomConfigurationTransition();
            setEnabled(true);
            hideImmediately();
            return;
        }
        defaultZoom = Utilities.clamp(1.0f, maxZoom, minZoom);
        wideZoom = minZoom;
        displayOneZoom = 1.0f;
        setDisplayNormalizationFactor(1.0f);
        if (backend == Backend.CAMERA_X) {
            opticalZoomRatios = CameraLensStops.opticalZoomRatios(getContext(), cameraXSession.getActiveCameraId(), minZoom);
        } else {
            opticalZoomRatios = new float[0];
        }
        float[] toggleStops = CameraLensStops.buildToggleStops(frontface, minZoom, maxZoom, opticalZoomRatios);
        float[] rulerStops = CameraLensStops.buildRulerStops(minZoom, maxZoom, toggleStops);
        float initialZoom;
        if (backend == Backend.CAMERA_X) {
            initialZoom = getCameraXResetZoom();
        } else if (backend == Backend.CAMERA_2) {
            initialZoom = camera2Session.getZoom();
        } else {
            initialZoom = camera1RatioForLinearZoom(camera1LinearZoom);
        }
        float zoom = Utilities.clamp(initialZoom, maxZoom, minZoom);
        boolean animated = animateNextConfiguration;
        animateNextConfiguration = false;
        setZoomConfiguration(minZoom, maxZoom, toggleStops, rulerStops, zoom, animated);
        if (!animated) {
            setExpanded(false, false);
        }
        if (backend == Backend.CAMERA_X) {
            attachCameraXZoomObserver();
            cameraXSession.setZoomRatio(zoom);
        }
        showAnimated();
    }

    private void retryBinding() {
        if (backend != Backend.NONE && bindRetries++ < MAX_BIND_RETRIES) {
            postDelayed(bindRunnable, BIND_RETRY_DELAY);
            return;
        }
        animateNextConfiguration = false;
        cancelZoomConfigurationTransition();
        setEnabled(true);
        hideImmediately();
    }

    private void applyZoom(float zoom) {
        pendingZoom = zoom;
        scheduleZoomFlush();
    }

    private void scheduleZoomFlush() {
        if (zoomFlushScheduled) {
            return;
        }
        zoomFlushScheduled = true;
        postOnAnimation(zoomFlushRunnable);
    }

    private void flushPendingZoom() {
        zoomFlushScheduled = false;
        float zoom = pendingZoom;
        if (Float.isNaN(zoom) || backend == Backend.NONE) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!Float.isNaN(lastAppliedZoom)) {
            if (zoom == lastAppliedZoom) {
                return;
            }
            if (now - lastZoomAppliedAt < getZoomUpdateIntervalMs()) {
                scheduleZoomFlush();
                return;
            }
        }
        lastAppliedZoom = zoom;
        lastZoomAppliedAt = now;
        sendZoomToCamera(zoom);
    }

    private long getZoomUpdateIntervalMs() {
        int frameRate;
        if (backend == Backend.CAMERA_X && cameraXSession != null) {
            frameRate = cameraXSession.getRecordingFrameRate();
        } else if (backend == Backend.CAMERA_2 && camera2Session != null) {
            frameRate = camera2Session.getRecordingFrameRate();
        } else {
            frameRate = 0;
        }
        if (frameRate <= 0) {
            frameRate = DEFAULT_FRAME_RATE;
        }
        return Math.max(1L, 1000L / frameRate);
    }

    private void discardPendingZoom() {
        removeCallbacks(zoomFlushRunnable);
        zoomFlushScheduled = false;
        pendingZoom = Float.NaN;
    }

    private void resetZoomThrottle() {
        discardPendingZoom();
        lastAppliedZoom = Float.NaN;
        lastZoomAppliedAt = 0L;
    }

    private void sendZoomToCamera(float zoom) {
        float linearZoom;
        switch (backend) {
            case CAMERA_X:
                if (cameraXSession == null) {
                    return;
                }
                cameraXSession.setZoomRatio(zoom);
                linearZoom = cameraXSession.getLinearZoom();
                break;
            case CAMERA_2:
                if (camera2Session == null) {
                    return;
                }
                camera2Session.setZoom(zoom);
                linearZoom = zoom;
                break;
            case CAMERA_1: {
                if (camera1Session == null || camera1ZoomRatios.length < 2) {
                    return;
                }
                int index = camera1ZoomIndexForRatio(zoom);
                camera1LinearZoom = camera1LinearZoomForIndex(index);
                if (index != camera1ZoomIndex) {
                    camera1ZoomIndex = index;
                    camera1Session.setZoom(camera1LinearZoom);
                }
                linearZoom = camera1LinearZoom;
                break;
            }
            default:
                return;
        }
        if (cameraZoomChangeListener != null) {
            cameraZoomChangeListener.onCameraZoomChanged(linearZoom, true);
        }
    }

    private void attachCameraXZoomObserver() {
        if (cameraXSession == null) {
            return;
        }
        Camera camera = cameraXSession.camera;
        if (camera == null) {
            return;
        }
        LiveData<ZoomState> zoomState = camera.getCameraInfo().getZoomState();
        if (cameraXZoomState == zoomState) {
            return;
        }
        detachCameraXZoomObserver();
        cameraXZoomState = zoomState;
        zoomState.observeForever(cameraXZoomObserver);
    }

    private void detachCameraXZoomObserver() {
        if (cameraXZoomState != null) {
            cameraXZoomState.removeObserver(cameraXZoomObserver);
            cameraXZoomState = null;
        }
    }

    private void onCameraXZoomStateChanged(ZoomState zoomState) {
        if (backend != Backend.CAMERA_X || zoomState == null || cameraZoomChangeListener == null) {
            return;
        }
        cameraZoomChangeListener.onCameraZoomChanged(zoomState.getLinearZoom(), false);
    }

    private static float[] readCamera1ZoomRatios(CameraSession session) {
        try {
            CameraInfo info = session.cameraInfo;
            android.hardware.Camera camera = info != null ? info.getCamera() : null;
            if (camera == null) {
                return null;
            }
            android.hardware.Camera.Parameters parameters = camera.getParameters();
            if (parameters == null || !parameters.isZoomSupported()) {
                return new float[0];
            }
            List<Integer> zoomRatios = parameters.getZoomRatios();
            if (zoomRatios == null || zoomRatios.size() < 2) {
                return new float[0];
            }
            int size = zoomRatios.size();
            float[] ratios = new float[size];
            for (int i = 0; i < size; i++) {
                Integer value = zoomRatios.get(i);
                ratios[i] = value != null ? Math.max(1.0f, value / 100.0f) : 1.0f;
            }
            return ratios;
        } catch (Exception e) {
            return null;
        }
    }

    private float camera1RatioForLinearZoom(float linearZoom) {
        int last = camera1ZoomRatios.length - 1;
        return camera1ZoomRatios[Math.min((int) (Utilities.clamp01(linearZoom) * last), last)];
    }

    private int camera1ZoomIndexForRatio(float ratio) {
        int bestIndex = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < camera1ZoomRatios.length; i++) {
            float distance = Math.abs(camera1ZoomRatios[i] - ratio);
            if (distance < bestDistance) {
                bestIndex = i;
                bestDistance = distance;
            }
        }
        return bestIndex;
    }

    private float camera1LinearZoomForIndex(int index) {
        int last = camera1ZoomRatios.length - 1;
        if (index <= 0) {
            return 0.0f;
        }
        if (index >= last) {
            return 1.0f;
        }
        return (index + 0.001f) / last;
    }

    public void setTextureViewSize(int size) {
        if (textureViewSize != size) {
            textureViewSize = size;
            applyPosition();
        }
    }

    public void setBaseTranslationY(float translationY) {
        if (baseTranslationY != translationY) {
            baseTranslationY = translationY;
            applyPosition();
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || getAlpha() < 0.5f) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyPosition();
    }

    private void applyPosition() {
        setTranslationY(baseTranslationY + textureViewSize / 2.0f + AndroidUtilities.dp(80) - getMeasuredHeight() / 2.0f);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (backend != Backend.NONE && (getVisibility() != View.VISIBLE || !isEnabled() || animateNextConfiguration)) {
            bindRetries = 0;
            tryBind();
        } else if (backend == Backend.CAMERA_X) {
            attachCameraXZoomObserver();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(bindRunnable);
        discardPendingZoom();
        detachCameraXZoomObserver();
        if (appearAnimator != null) {
            cancelAppearAnimation();
            setAppearProgress(getVisibility() == View.VISIBLE ? 1.0f : 0.0f);
        }
    }
}
