package com.exteragram.messenger.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Looper;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ConcurrentCamera;
import androidx.camera.core.DisplayOrientedMeteringPointFactory;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MirrorMode;
import androidx.camera.core.Preview;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.system.SystemUtils;
import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Stories.recorder.DualCameraView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CameraXSession {

    private static final Map<CameraSelector, Boolean> STABILIZATION_SUPPORT_CACHE = new ConcurrentHashMap<>();
    private static final Range<Integer> FPS_60_RANGE = new Range<>(60, 60);
    private static final Range<Integer> FPS_30_RANGE = new Range<>(30, 30);
    private static final Size FALLBACK_SENSOR_ASPECT = new Size(4, 3);
    private static final long FRAME_DURATION_60_FPS_NS = 16_666_667L;

    private static volatile Boolean seamlessSwitchingAvailableCache;

    private final CameraLifecycle lifecycle;
    private final Preview.SurfaceProvider surfaceProviderPrimary;
    private Preview.SurfaceProvider surfaceProviderSecondary;

    ProcessCameraProvider provider;
    Camera camera;
    Camera cameraFront;
    Camera cameraBack;
    private CameraControl cameraControl;
    private CameraControl cameraControlFront;
    private CameraControl cameraControlBack;
    private CameraSelector cameraSelector;
    private Preview previewUseCase;
    private Preview previewUseCaseBack;

    private boolean isFrontface;
    private boolean isInitiated = false;
    private boolean isDualMode = false;
    private boolean isBinding = false;
    private volatile int recordingFrameRate = 30;
    private boolean isTorchOn = false;

    public interface PreviewSizeListener {
        void onPreviewSize(int width, int height);
    }

    public static class CameraLifecycle implements LifecycleOwner {

        private final LifecycleRegistry lifecycleRegistry;

        public CameraLifecycle() {
            lifecycleRegistry = new LifecycleRegistry(this);
            lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        }

        public void start() {
            try {
                if (lifecycleRegistry.getCurrentState() != Lifecycle.State.DESTROYED) {
                    lifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public void stop() {
            try {
                lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return lifecycleRegistry;
        }
    }

    public CameraXSession(CameraLifecycle lifecycle, Preview.SurfaceProvider surfaceProvider) {
        this.lifecycle = lifecycle;
        this.surfaceProviderPrimary = surfaceProvider;
    }

    public static Preview.SurfaceProvider createSurfaceProvider(Context context, SurfaceTexture surfaceTexture, PreviewSizeListener previewSizeListener) {
        return request -> {
            try {
                Size resolution = request.getResolution();
                previewSizeListener.onPreviewSize(resolution.getWidth(), resolution.getHeight());
                request.setTransformationInfoListener(ContextCompat.getMainExecutor(context), info -> {
                    Rect cropRect = info.getCropRect();
                    previewSizeListener.onPreviewSize(
                        cropRect.width() > 0 ? cropRect.width() : resolution.getWidth(),
                        cropRect.height() > 0 ? cropRect.height() : resolution.getHeight()
                    );
                });
                surfaceTexture.setDefaultBufferSize(resolution.getWidth(), resolution.getHeight());
                Surface surface = new Surface(surfaceTexture);
                request.provideSurface(surface, ContextCompat.getMainExecutor(context), result -> {
                    request.clearTransformationInfoListener();
                    surface.release();
                });
            } catch (Exception e) {
                FileLog.e(e);
                request.willNotProvideSurface();
            }
        };
    }

    public void setSecondSurfaceProvider(Preview.SurfaceProvider surfaceProvider) {
        surfaceProviderSecondary = surfaceProvider;
        if (isInitiated && isDualMode && previewUseCaseBack != null) {
            previewUseCaseBack.setSurfaceProvider(surfaceProvider);
        }
    }

    public boolean isInitiated() {
        return isInitiated;
    }

    public boolean isReady() {
        return isInitiated && !isBinding && camera != null;
    }

    public boolean isDualMode() {
        return isDualMode;
    }

    public boolean isFrontface() {
        return isFrontface;
    }

    public boolean isActiveCameraFrontface() {
        if (camera == null) {
            return isFrontface;
        }
        int lensFacing = camera.getCameraInfo().getLensFacing();
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            return true;
        }
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            return false;
        }
        return isFrontface;
    }

    public int getRecordingFrameRate() {
        return recordingFrameRate;
    }

    public void initCamera(Context context, boolean frontface, boolean dual, Runnable onInitialized) {
        isFrontface = frontface;
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            try {
                provider = providerFuture.get();
                if (lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                    return;
                }
                isDualMode = dual && supportsConcurrentFrontBackPair();
                rebindCamera();
                lifecycle.start();
                if (onInitialized != null) {
                    onInitialized.run();
                }
            } catch (Exception e) {
                FileLog.e(e);
                isInitiated = false;
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private boolean supportsConcurrentFrontBackPair() {
        if (provider == null) {
            return false;
        }
        for (List<CameraInfo> infos : provider.getAvailableConcurrentCameraInfos()) {
            boolean hasFront = false;
            boolean hasBack = false;
            for (CameraInfo info : infos) {
                if (info.getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                    hasFront = true;
                }
                if (info.getLensFacing() == CameraSelector.LENS_FACING_BACK) {
                    hasBack = true;
                }
            }
            if (hasFront && hasBack) {
                return true;
            }
        }
        return false;
    }

    public void switchCamera() {
        isFrontface = !isFrontface;
        if (isDualMode && cameraFront != null && cameraBack != null) {
            updateActiveControl(isFrontface);
            updateTorchState();
        } else {
            rebindCamera();
        }
    }

    private void updateActiveControl(boolean front) {
        if (front) {
            camera = cameraFront;
            cameraControl = cameraControlFront;
        } else {
            camera = cameraBack;
            cameraControl = cameraControlBack;
        }
    }

    public void closeCamera() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(this::closeCamera);
            return;
        }
        try {
            if (provider != null) {
                provider.unbindAll();
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (previewUseCase != null) {
                    previewUseCase.setSurfaceProvider(null);
                }
                if (previewUseCaseBack != null) {
                    previewUseCaseBack.setSurfaceProvider(null);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            lifecycle.stop();
            isInitiated = false;
            clearCameraReferences();
        }
    }

    private void clearCameraReferences() {
        cameraBack = null;
        cameraFront = null;
        camera = null;
        cameraControlBack = null;
        cameraControlFront = null;
        cameraControl = null;
        previewUseCaseBack = null;
        previewUseCase = null;
    }

    public void setTorchEnabled(boolean enabled) {
        isTorchOn = enabled;
        updateTorchState();
    }

    private void updateTorchState() {
        try {
            if (isDualMode) {
                if (cameraControlFront != null) {
                    cameraControlFront.enableTorch(false);
                }
                if (cameraControlBack == null || cameraBack == null || !cameraBack.getCameraInfo().hasFlashUnit()) {
                    return;
                }
                cameraControlBack.enableTorch(isTorchOn && !isFrontface);
                return;
            }
            if (camera == null || cameraControl == null || !camera.getCameraInfo().hasFlashUnit()) {
                return;
            }
            boolean isBack = camera.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_BACK;
            cameraControl.enableTorch(isTorchOn && isBack);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private Preview buildPreview(CameraSelector selector, boolean stabilization, Range<Integer> frameRate, boolean prefer60Fps) {
        Size sensorAspect = getSensorAspect(selector);
        Set<Size> highFpsSizes = prefer60Fps ? get60FpsCapableSizes(selector) : Collections.emptySet();
        Preview.Builder builder = new Preview.Builder()
            .setResolutionSelector(new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionFilter((sizes, rotationDegrees) -> sortRoundPreviewSizes(sizes, sensorAspect, highFpsSizes))
                .setAllowedResolutionMode(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                .build());
        if (frameRate != null) {
            builder.setTargetFrameRate(frameRate);
        }
        builder.setPreviewStabilizationEnabled(!isDualMode && stabilization);
        if (!ExteraConfig.getCameraMirrorMode()) {
            builder.setMirrorMode(MirrorMode.MIRROR_MODE_OFF);
        }
        return builder.build();
    }

    private boolean isPreviewStabilizationSupported(CameraSelector selector) {
        if (selector == null) {
            return false;
        }
        try {
            if (provider != null && provider.hasCamera(selector)) {
                Boolean cached = STABILIZATION_SUPPORT_CACHE.get(selector);
                if (cached != null) {
                    return cached;
                }
                boolean supported = Preview.getPreviewCapabilities(provider.getCameraInfo(selector)).isStabilizationSupported();
                STABILIZATION_SUPPORT_CACHE.put(selector, supported);
                return supported;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private Camera tryBindSingleAtFrameRate(Range<Integer> frameRate) {
        try {
            CameraInfo cameraInfo = provider.getCameraInfo(cameraSelector);
            SessionConfig.Builder builder = new SessionConfig.Builder(previewUseCase);
            if (!cameraInfo.getSupportedFrameRateRanges(builder.build()).contains(frameRate)) {
                return null;
            }
            builder.setFrameRateRange(frameRate);
            return tryBindSingleSession(builder.build(), frameRate.getUpper());
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private Camera tryBindSingleAtExtendedFrameRate() {
        try {
            SessionConfig.Builder builder = new SessionConfig.Builder(previewUseCase);
            Range<Integer> frameRate = selectExtendedFpsRange(provider.getCameraInfo(cameraSelector).getSupportedFrameRateRanges(builder.build()));
            if (frameRate == null) {
                return null;
            }
            builder.setFrameRateRange(frameRate);
            return tryBindSingleSession(builder.build(), frameRate.getUpper());
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Range<Integer> selectExtendedFpsRange(Collection<Range<Integer>> ranges) {
        if (ranges == null) {
            return null;
        }
        Range<Integer> best = null;
        for (Range<Integer> range : ranges) {
            if (range == null || !range.getUpper().equals(FPS_60_RANGE.getUpper())) {
                continue;
            }
            if (FPS_60_RANGE.equals(range)) {
                return range;
            }
            if (best == null || range.getLower() > best.getLower()) {
                best = range;
            }
        }
        return best;
    }

    private Camera tryBindSingleSession(SessionConfig sessionConfig, int frameRate) {
        try {
            if (!provider.getCameraInfo(cameraSelector).isSessionConfigSupported(sessionConfig)) {
                return null;
            }
            Camera boundCamera = provider.bindToLifecycle(lifecycle, cameraSelector, sessionConfig);
            if (boundCamera != null) {
                recordingFrameRate = frameRate;
            }
            return boundCamera;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void rebindCamera() {
        if (provider == null || lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED || isBinding) {
            return;
        }
        isBinding = true;
        recordingFrameRate = 30;
        clearCameraReferences();
        try {
            provider.unbindAll();
            if (isDualMode) {
                bindDualUseCases();
            } else {
                bindSingleUseCases();
            }
            isInitiated = camera != null;
        } catch (Exception e) {
            FileLog.e(e);
            isInitiated = false;
            try {
                provider.unbindAll();
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            clearCameraReferences();
        } finally {
            isBinding = false;
        }
    }

    private void bindSingleUseCases() {
        try {
            cameraSelector = isFrontface ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
            if (!provider.hasCamera(cameraSelector)) {
                isInitiated = false;
                return;
            }
            boolean extendedFps = ExteraConfig.getExtendedFramesPerSecond();
            boolean stabilization = ExteraConfig.getCameraStabilization() && isPreviewStabilizationSupported(cameraSelector);
            setPrimaryPreview(buildPreview(cameraSelector, stabilization, null, extendedFps));
            if (extendedFps) {
                camera = tryBindSingleAtExtendedFrameRate();
                if (camera == null && stabilization) {
                    setPrimaryPreview(buildPreview(cameraSelector, false, null, true));
                    camera = tryBindSingleAtExtendedFrameRate();
                }
                if (camera == null) {
                    setPrimaryPreview(buildPreview(cameraSelector, stabilization, null, false));
                    camera = tryBindSingleAtFrameRate(FPS_30_RANGE);
                }
            } else {
                camera = tryBindSingleAtFrameRate(FPS_30_RANGE);
            }
            if (camera == null) {
                try {
                    camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (camera == null && stabilization) {
                setPrimaryPreview(buildPreview(cameraSelector, false, null, false));
                camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase);
            }
            if (camera == null) {
                isInitiated = false;
                return;
            }
            cameraControl = camera.getCameraControl();
            observeCameraState(camera);
            applyInitialZoom(camera, cameraControl);
            updateTorchState();
        } catch (Exception e) {
            FileLog.e(e);
            isInitiated = false;
        }
    }

    private void setPrimaryPreview(Preview preview) {
        previewUseCase = preview;
        preview.setSurfaceProvider(surfaceProviderPrimary);
    }

    private void bindDualUseCases() {
        boolean extendedFps = ExteraConfig.getExtendedFramesPerSecond();
        Range<Integer> frameRate = extendedFps ? FPS_60_RANGE : FPS_30_RANGE;
        ConcurrentCamera concurrentCamera = tryBindConcurrentCameras(frameRate);
        if (concurrentCamera == null && extendedFps) {
            frameRate = FPS_30_RANGE;
            concurrentCamera = tryBindConcurrentCameras(frameRate);
        }
        if (concurrentCamera == null && frameRate != null) {
            frameRate = null;
            concurrentCamera = tryBindConcurrentCameras(null);
        }
        if (concurrentCamera == null) {
            isDualMode = false;
            bindSingleUseCases();
            return;
        }
        if (frameRate != null) {
            recordingFrameRate = frameRate.getLower();
        }
        for (Camera boundCamera : concurrentCamera.getCameras()) {
            if (boundCamera.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                cameraFront = boundCamera;
                cameraControlFront = boundCamera.getCameraControl();
                applyInitialZoom(cameraFront, cameraControlFront);
            } else {
                cameraBack = boundCamera;
                cameraControlBack = boundCamera.getCameraControl();
                applyInitialZoom(cameraBack, cameraControlBack);
            }
            observeCameraState(boundCamera);
        }
        updateActiveControl(isFrontface);
        updateTorchState();
    }

    private ConcurrentCamera tryBindConcurrentCameras(Range<Integer> frameRate) {
        try {
            boolean prefer60Fps = FPS_60_RANGE.equals(frameRate);
            CameraSelector frontSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            setPrimaryPreview(buildPreview(frontSelector, false, frameRate, prefer60Fps));
            CameraSelector backSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            previewUseCaseBack = buildPreview(backSelector, false, frameRate, prefer60Fps);
            if (surfaceProviderSecondary != null) {
                previewUseCaseBack.setSurfaceProvider(surfaceProviderSecondary);
            }
            ConcurrentCamera.SingleCameraConfig frontConfig = new ConcurrentCamera.SingleCameraConfig(frontSelector, new UseCaseGroup.Builder().addUseCase(previewUseCase).build(), lifecycle);
            ConcurrentCamera.SingleCameraConfig backConfig = new ConcurrentCamera.SingleCameraConfig(backSelector, new UseCaseGroup.Builder().addUseCase(previewUseCaseBack).build(), lifecycle);
            List<ConcurrentCamera.SingleCameraConfig> configs = new ArrayList<>(2);
            configs.add(frontConfig);
            configs.add(backConfig);
            return provider.bindToLifecycle(configs);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                provider.unbindAll();
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            return null;
        }
    }

    private void observeCameraState(Camera camera) {
        try {
            camera.getCameraInfo().getCameraState().observe(lifecycle, state -> {
                CameraState.StateError error = state.getError();
                if (error != null) {
                    FileLog.e("CameraX camera state error: code=" + error.getCode() + " type=" + state.getType());
                    if (error.getCause() != null) {
                        FileLog.e(error.getCause());
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void applyInitialZoom(Camera camera, CameraControl control) {
        if (control == null || camera == null) {
            return;
        }
        control.setZoomRatio(1f);
        if (!wantsWideAngleStart(camera)) {
            return;
        }
        LiveData<ZoomState> zoomState = camera.getCameraInfo().getZoomState();
        if (zoomState.getValue() != null) {
            applyWideAngle(control, zoomState.getValue());
        } else {
            zoomState.observe(lifecycle, new Observer<ZoomState>() {
                @Override
                public void onChanged(ZoomState state) {
                    if (state == null) {
                        return;
                    }
                    zoomState.removeObserver(this);
                    if (wantsWideAngleStart(camera)) {
                        applyWideAngle(control, state);
                    }
                }
            });
        }
    }

    private void applyWideAngle(CameraControl control, ZoomState zoomState) {
        if (zoomState == null || zoomState.getMinZoomRatio() >= 1f) {
            return;
        }
        control.setZoomRatio(zoomState.getMinZoomRatio());
    }

    private boolean wantsWideAngleStart(Camera camera) {
        return ExteraConfig.getStartWithWideAngleCamera() && camera != null && camera.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_BACK;
    }

    private ZoomState getZoomState() {
        if (camera == null) {
            return null;
        }
        return camera.getCameraInfo().getZoomState().getValue();
    }

    public float getLinearZoom() {
        ZoomState state = getZoomState();
        return state == null ? 0f : state.getLinearZoom();
    }

    public float getZoomRatio() {
        ZoomState state = getZoomState();
        return state == null ? 1f : state.getZoomRatio();
    }

    public void setZoomRatio(float ratio) {
        if (cameraControl == null) {
            return;
        }
        cameraControl.setZoomRatio(ratio);
    }

    public float getMinZoomRatio() {
        ZoomState state = getZoomState();
        return state == null ? 1f : state.getMinZoomRatio();
    }

    public float getMaxZoomRatio() {
        ZoomState state = getZoomState();
        return state == null ? 1f : state.getMaxZoomRatio();
    }

    public String getActiveCameraId() {
        if (camera == null) {
            return null;
        }
        try {
            return Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public void focusToPoint(float x, float y, float width, float height) {
        if (cameraControl == null || camera == null || width <= 0f || height <= 0f) {
            return;
        }
        Display display = getDefaultDisplay();
        if (display == null) {
            return;
        }
        try {
            DisplayOrientedMeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(display, camera.getCameraInfo(), width, height);
            FocusMeteringAction action = new FocusMeteringAction.Builder(factory.createPoint(x, y), FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE).build();
            cameraControl.startFocusAndMetering(action);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public int getDisplayOrientation() {
        Display display = getDefaultDisplay();
        int rotation = display != null ? display.getRotation() : Surface.ROTATION_0;
        switch (rotation) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private Size getSensorAspect(CameraSelector selector) {
        try {
            if (provider != null && selector != null && provider.hasCamera(selector)) {
                Rect activeArray = Camera2CameraInfo.from(provider.getCameraInfo(selector)).getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if (activeArray != null && activeArray.width() > 0 && activeArray.height() > 0) {
                    return new Size(activeArray.width(), activeArray.height());
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return FALLBACK_SENSOR_ASPECT;
    }

    private Set<Size> get60FpsCapableSizes(CameraSelector selector) {
        HashSet<Size> sizes = new HashSet<>();
        try {
            if (provider != null && selector != null && provider.hasCamera(selector)) {
                StreamConfigurationMap map = Camera2CameraInfo.from(provider.getCameraInfo(selector)).getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] outputSizes = map != null ? map.getOutputSizes(SurfaceTexture.class) : null;
                if (outputSizes != null) {
                    for (Size size : outputSizes) {
                        long minFrameDuration = map.getOutputMinFrameDuration(SurfaceTexture.class, size);
                        if (minFrameDuration > 0 && minFrameDuration <= FRAME_DURATION_60_FPS_NS) {
                            sizes.add(size);
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return sizes;
    }

    private static List<Size> sortRoundPreviewSizes(List<Size> sizes, Size sensorAspect, Set<Size> highFpsSizes) {
        ArrayList<Size> result = new ArrayList<>(sizes);
        int roundResolution = SystemUtils.getRoundVideoResolution();
        int targetShortSide = Math.min(1440, Math.max(1024, roundResolution * 2));
        double sensorAspectRatio = aspectRatioOf(sensorAspect);
        boolean preferHighFps = !highFpsSizes.isEmpty();
        result.sort(Comparator.<Size>comparingInt(size -> previewTier(size, roundResolution, preferHighFps, highFpsSizes))
            .thenComparingDouble(size -> previewScore(size, sensorAspectRatio, targetShortSide))
            .thenComparingLong(size -> (long) size.getWidth() * size.getHeight()));
        return result;
    }

    private static int previewTier(Size size, int roundResolution, boolean preferHighFps, Set<Size> highFpsSizes) {
        if (Math.min(size.getWidth(), size.getHeight()) < roundResolution) {
            return 3;
        }
        if (Math.max(size.getWidth(), size.getHeight()) > 1920 || (long) size.getWidth() * size.getHeight() > 2_100_000L) {
            return 2;
        }
        return (!preferHighFps || highFpsSizes.contains(size)) ? 0 : 1;
    }

    private static double previewScore(Size size, double sensorAspectRatio, int targetShortSide) {
        int shortSide = Math.min(size.getWidth(), size.getHeight());
        int longSide = Math.max(size.getWidth(), size.getHeight());
        if (shortSide <= 0 || longSide <= 0 || targetShortSide <= 0) {
            return Double.MAX_VALUE;
        }
        double aspectPenalty = Math.max(0d, (double) longSide / shortSide - sensorAspectRatio) / sensorAspectRatio;
        double sizePenalty = (double) Math.abs(shortSide - targetShortSide) / targetShortSide;
        double squarenessPenalty = 1d - (double) shortSide / longSide;
        return aspectPenalty * 2d + sizePenalty * 1d + squarenessPenalty * 0.5d;
    }

    private static double aspectRatioOf(Size size) {
        int shortSide = Math.min(size.getWidth(), size.getHeight());
        int longSide = Math.max(size.getWidth(), size.getHeight());
        if (shortSide > 0) {
            return (double) longSide / shortSide;
        }
        return 4d / 3d;
    }

    private static Display getDefaultDisplay() {
        DisplayManager displayManager = (DisplayManager) ApplicationLoader.applicationContext.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            return displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        }
        return null;
    }

    public static boolean isRoundDualAvailable(Context context) {
        return DualCameraView.roundDualAvailableStatic(context) && isSeamlessSwitchingAvailable(context);
    }

    public static boolean isSeamlessSwitchingAvailable(Context context) {
        if (seamlessSwitchingAvailableCache != null) {
            return seamlessSwitchingAvailableCache;
        }
        seamlessSwitchingAvailableCache = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE
            && SharedConfig.allowPreparingHevcPlayers()
            && hasConcurrentFrontBackPair(context);
        return seamlessSwitchingAvailableCache;
    }

    private static boolean hasConcurrentFrontBackPair(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 30) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null || !packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)) {
            return false;
        }
        CameraManager cameraManager = context.getSystemService(CameraManager.class);
        if (cameraManager == null) {
            return false;
        }
        try {
            for (Set<String> ids : cameraManager.getConcurrentCameraIds()) {
                boolean hasFront = false;
                boolean hasBack = false;
                for (String id : ids) {
                    Integer lensFacing = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing != null) {
                        if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                            hasFront = true;
                        } else if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                            hasBack = true;
                        }
                    }
                }
                if (hasFront && hasBack) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }
}
