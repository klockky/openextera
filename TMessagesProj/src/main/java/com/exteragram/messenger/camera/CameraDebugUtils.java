package com.exteragram.messenger.camera;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Range;

import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class CameraDebugUtils {

    public static String getCameraXAvailableCameraList(CameraXSession session) {
        ProcessCameraProvider provider = session.provider;
        if (provider == null) {
            return "provider=null";
        }
        StringBuilder sb = new StringBuilder();
        try {
            if (session.isDualMode()) {
                int pairIndex = 0;
                for (List<CameraInfo> infos : provider.getAvailableConcurrentCameraInfos()) {
                    StringBuilder pair = new StringBuilder();
                    boolean hasFront = false;
                    boolean hasBack = false;
                    for (CameraInfo info : infos) {
                        int lensFacing = info.getLensFacing();
                        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            hasFront = true;
                        } else if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            hasBack = true;
                        }
                        if (pair.length() > 0) {
                            pair.append(" + ");
                        }
                        pair.append(formatCameraXInfo(info));
                    }
                    if (hasFront && hasBack) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append("pair").append(pairIndex).append(": ").append(pair);
                        pairIndex++;
                    }
                }
            } else {
                for (CameraInfo info : provider.getAvailableCameraInfos()) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(formatCameraXInfo(info));
                }
            }
            return sb.length() == 0 ? "none" : sb.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getCameraXBoundCameraList(CameraXSession session) {
        StringBuilder sb = new StringBuilder();
        Camera front = session.cameraFront;
        Camera back = session.cameraBack;
        Camera active = session.camera;
        if (front != null) {
            sb.append("front=").append(formatCameraXInfo(front.getCameraInfo()));
        }
        if (back != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("back=").append(formatCameraXInfo(back.getCameraInfo()));
        }
        if (sb.length() == 0 && active != null) {
            sb.append("active=").append(formatCameraXInfo(active.getCameraInfo()));
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    public static String getCameraXPhysicalCameraList(CameraXSession session) {
        Camera camera = session.camera;
        if (camera == null) {
            return "none";
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (CameraInfo info : camera.getCameraInfo().getPhysicalCameraInfos()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(formatCameraXInfo(info));
            }
            return sb.length() == 0 ? "none" : sb.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getCamera2CameraList(Context context) {
        try {
            CameraManager cameraManager = context.getSystemService(CameraManager.class);
            if (cameraManager == null) {
                return "manager=null";
            }
            StringBuilder sb = new StringBuilder();
            for (String cameraId : cameraManager.getCameraIdList()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(lensFacingToShortString(cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING)));
                sb.append(cameraId);
            }
            return sb.length() == 0 ? "none" : sb.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getLegacyCameraList() {
        ArrayList<org.telegram.messenger.camera.CameraInfo> cameras = CameraController.getInstance().getCameras();
        if (cameras == null || cameras.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cameras.size(); i++) {
            org.telegram.messenger.camera.CameraInfo info = cameras.get(i);
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(info.isFrontface() ? 'f' : 'b');
            sb.append(info.getCameraId());
        }
        return sb.toString();
    }

    public static String getCameraXSupportedFpsRanges(CameraXSession session) {
        if (session == null) {
            return "none";
        }
        try {
            CameraInfo cameraInfo = null;
            if (session.camera != null) {
                cameraInfo = session.camera.getCameraInfo();
            } else if (session.isDualMode()) {
                Camera camera = session.isFrontface() ? session.cameraFront : session.cameraBack;
                if (camera != null) {
                    cameraInfo = camera.getCameraInfo();
                }
            } else if (session.provider != null) {
                CameraSelector selector = session.isFrontface() ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                if (session.provider.hasCamera(selector)) {
                    cameraInfo = session.provider.getCameraInfo(selector);
                }
            }
            if (cameraInfo == null) {
                return "none";
            }
            return formatCamera2FpsRanges(Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getCamera2SupportedFpsRanges(Camera2Session session) {
        if (session == null) {
            return "none";
        }
        try {
            return formatCamera2FpsRanges(session.getAvailableFpsRanges());
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getLegacySupportedFpsRanges(CameraSession session) {
        if (session == null || session.cameraInfo == null || session.cameraInfo.getCamera() == null) {
            return "none";
        }
        try {
            return formatLegacyFpsRanges(session.cameraInfo.getCamera().getParameters().getSupportedPreviewFpsRange());
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String formatCameraXInfo(CameraInfo cameraInfo) {
        String cameraId;
        float intrinsicZoomRatio;
        try {
            cameraId = Camera2CameraInfo.from(cameraInfo).getCameraId();
        } catch (Exception e) {
            FileLog.e(e);
            cameraId = "?";
        }
        try {
            intrinsicZoomRatio = cameraInfo.getIntrinsicZoomRatio();
        } catch (Exception e) {
            FileLog.e(e);
            intrinsicZoomRatio = 1f;
        }
        return lensFacingToShortString(cameraInfo.getLensFacing()) + cameraId + "@" + intrinsicZoomRatio;
    }

    public static String formatZoomStops(float[] stops) {
        if (stops == null || stops.length == 0) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (float stop : stops) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(formatZoomStop(stop));
        }
        return sb.toString();
    }

    private static String formatZoomStop(float stop) {
        String str = String.format(Locale.US, "%.3f", stop);
        int length = str.length();
        while (length > 0 && str.charAt(length - 1) == '0') {
            length--;
        }
        if (length > 0 && str.charAt(length - 1) == '.') {
            length--;
        }
        return str.substring(0, length);
    }

    public static char lensFacingToShortString(Integer lensFacing) {
        if (lensFacing == null) {
            return '?';
        }
        if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            return 'f';
        }
        return lensFacing == CameraCharacteristics.LENS_FACING_BACK ? 'b' : '?';
    }

    public static String formatCameraSize(Size size) {
        if (size == null) {
            return "n/a";
        }
        return size.getWidth() + "x" + size.getHeight();
    }

    public static float safeCameraXZoomRatio(CameraXSession session) {
        if (session == null) {
            return 1f;
        }
        try {
            return session.getZoomRatio();
        } catch (Exception e) {
            FileLog.e(e);
            return -1f;
        }
    }

    public static float safeCameraXMinZoomRatio(CameraXSession session) {
        if (session == null) {
            return 1f;
        }
        try {
            return session.getMinZoomRatio();
        } catch (Exception e) {
            FileLog.e(e);
            return -1f;
        }
    }

    public static float safeCameraXMaxZoomRatio(CameraXSession session) {
        if (session == null) {
            return 1f;
        }
        try {
            return session.getMaxZoomRatio();
        } catch (Exception e) {
            FileLog.e(e);
            return -1f;
        }
    }

    private static String formatCamera2FpsRanges(Range<Integer>[] ranges) {
        if (ranges == null || ranges.length == 0) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (Range<Integer> range : ranges) {
            if (range == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(formatFpsRange(range.getLower(), range.getUpper(), false));
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static String formatLegacyFpsRanges(List<int[]> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int[] range : ranges) {
            if (range == null || range.length < 2) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(formatFpsRange(range[0], range[1], true));
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static String formatFpsRange(int lower, int upper, boolean scaled) {
        return formatFpsValue(lower, scaled) + "-" + formatFpsValue(upper, scaled);
    }

    private static String formatFpsValue(int value, boolean scaled) {
        float fps = value;
        if (scaled) {
            fps /= 1000f;
        }
        if (Math.abs(fps - Math.round(fps)) < 0.05f) {
            return Integer.toString(Math.round(fps));
        }
        return String.format(Locale.US, "%.1f", fps);
    }
}
