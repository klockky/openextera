package com.exteragram.messenger.camera;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Size;
import android.util.SizeF;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class CameraLensStops {

    private static final float EPSILON = 1e-4f;

    private static final float[] NO_RATIOS = new float[0];
    private static final float[] SNAP_RATIOS = {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 10f, 12f, 15f, 20f};
    private static final float[] ROUND_RATIOS = {1.5f, 2f, 3f, 5f, 10f, 15f, 20f, 30f};
    private static final float[] RULER_LADDER = {1f, 2f, 5f, 10f, 30f};
    private static final Map<String, float[]> RATIO_CACHE = new HashMap<>();

    public static float[] opticalZoomRatios(Context context, String cameraId, float intrinsicZoomRatio) {
        if (context == null || cameraId == null || Build.VERSION.SDK_INT < 28) {
            return NO_RATIOS;
        }
        float[] ratios;
        synchronized (RATIO_CACHE) {
            ratios = RATIO_CACHE.get(cameraId);
        }
        if (ratios == null) {
            ratios = readOpticalZoomRatios(context, cameraId);
            synchronized (RATIO_CACHE) {
                RATIO_CACHE.put(cameraId, ratios);
            }
        }
        return normalizeRatios(ratios, intrinsicZoomRatio);
    }

    private static float[] normalizeRatios(float[] ratios, float intrinsicZoomRatio) {
        if (ratios.length < 2) {
            return NO_RATIOS;
        }
        float scale = 1f;
        if (intrinsicZoomRatio > 0f && ratios[0] > intrinsicZoomRatio * 1.25f) {
            scale = intrinsicZoomRatio / ratios[0];
        }
        float mainRatio = 0f;
        double bestDistance = Double.MAX_VALUE;
        for (float ratio : ratios) {
            float scaled = ratio * scale;
            double distance = Math.abs(octaves(1f, scaled));
            if (distance < bestDistance) {
                mainRatio = scaled;
                bestDistance = distance;
            }
        }
        if (Math.abs(mainRatio - 1f) > 0.12f) {
            return NO_RATIOS;
        }
        ArrayList<Float> stops = new ArrayList<>(ratios.length);
        for (float ratio : ratios) {
            addDistinctStop(stops, snapToNiceRatio(ratio * scale / mainRatio));
        }
        return toArray(stops);
    }

    @RequiresApi(28)
    private static float[] readOpticalZoomRatios(Context context, String cameraId) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                return NO_RATIOS;
            }
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Set<String> physicalCameraIds = characteristics.getPhysicalCameraIds();
            if (physicalCameraIds == null || physicalCameraIds.size() < 2) {
                return NO_RATIOS;
            }
            float logicalTangent = halfFieldOfViewTangent(characteristics);
            if (logicalTangent <= 0f) {
                return NO_RATIOS;
            }
            ArrayList<Float> ratios = new ArrayList<>(physicalCameraIds.size());
            for (String physicalId : physicalCameraIds) {
                try {
                    float physicalTangent = halfFieldOfViewTangent(cameraManager.getCameraCharacteristics(physicalId));
                    if (physicalTangent > 0f) {
                        addDistinctStop(ratios, logicalTangent / physicalTangent);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            return toArray(ratios);
        } catch (Exception e) {
            FileLog.e(e);
            return NO_RATIOS;
        }
    }

    private static float halfFieldOfViewTangent(CameraCharacteristics characteristics) {
        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Size pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (focalLengths == null || focalLengths.length == 0 || physicalSize == null || activeArray == null || pixelArray == null || sensorOrientation == null) {
            return 0f;
        }
        float sensorSize;
        float activeSize;
        float pixelSize;
        if (sensorOrientation % 180 == 90) {
            sensorSize = physicalSize.getHeight();
            activeSize = activeArray.height();
            pixelSize = pixelArray.getHeight();
        } else {
            sensorSize = physicalSize.getWidth();
            activeSize = activeArray.width();
            pixelSize = pixelArray.getWidth();
        }
        float focalLength = focalLengths[0];
        if (focalLength > 0f && sensorSize > 0f && activeSize > 0f && pixelSize > 0f) {
            return sensorSize * activeSize / pixelSize / (focalLength * 2f);
        }
        return 0f;
    }

    public static float[] buildToggleStops(boolean round, float minZoom, float maxZoom, float[] opticalRatios) {
        float[] telephoto = telephotoRatios(opticalRatios, maxZoom);
        if (telephoto.length == 0) {
            return boundStops(round ? new float[]{minZoom, 1f, 2f} : new float[]{minZoom, 1f, 2f, 5f}, minZoom, maxZoom, false);
        }
        ArrayList<Float> stops = new ArrayList<>(6);
        if (minZoom < 0.9999f) {
            addDistinctStop(stops, minZoom);
        }
        addDistinctStop(stops, Utilities.clamp(1f, maxZoom, minZoom));
        for (float ratio : telephoto) {
            addDistinctStop(stops, ratio);
        }
        fillWideGaps(stops);
        addReachStop(stops, telephoto[telephoto.length - 1], maxZoom);
        dropCrowdedStops(stops);
        return toArray(stops);
    }

    public static float[] buildRulerStops(float minZoom, float maxZoom, float[] opticalRatios) {
        ArrayList<Float> stops = new ArrayList<>(opticalRatios.length + RULER_LADDER.length + 2);
        addDistinctStop(stops, minZoom);
        for (float ratio : opticalRatios) {
            addDistinctStop(stops, Utilities.clamp(ratio, maxZoom, minZoom));
        }
        addDistinctStop(stops, maxZoom);
        for (float step : RULER_LADDER) {
            if (step >= minZoom - EPSILON && step <= maxZoom + EPSILON && nearestOctaveDistance(stops, step) >= 0.55d) {
                addDistinctStop(stops, step);
            }
        }
        return toArray(stops);
    }

    private static float[] telephotoRatios(float[] ratios, float maxZoom) {
        if (ratios == null || ratios.length == 0) {
            return NO_RATIOS;
        }
        ArrayList<Float> result = new ArrayList<>(ratios.length);
        for (float ratio : ratios) {
            if (ratio >= 1.15f && ratio <= maxZoom + EPSILON) {
                addDistinctStop(result, ratio);
            }
        }
        return toArray(result);
    }

    private static void fillWideGaps(ArrayList<Float> stops) {
        while (stops.size() < 4) {
            int widestIndex = -1;
            double widestGap = 2.0d;
            for (int i = 1; i < stops.size(); i++) {
                double gap = octaves(stops.get(i - 1), stops.get(i));
                if (gap > widestGap) {
                    widestIndex = i;
                    widestGap = gap;
                }
            }
            if (widestIndex < 0) {
                return;
            }
            float ratio = chooseRoundRatio(stops.get(widestIndex - 1), stops.get(widestIndex));
            if (ratio <= 0f) {
                return;
            }
            int size = stops.size();
            addDistinctStop(stops, ratio);
            if (stops.size() == size) {
                return;
            }
        }
    }

    private static float chooseRoundRatio(float from, float to) {
        double middle = Math.sqrt((double) from * (double) to);
        float result = 0f;
        double bestDistance = Double.MAX_VALUE;
        for (float ratio : ROUND_RATIOS) {
            if (ratio > from + EPSILON && ratio < to - EPSILON) {
                double distance = Math.abs(octaves(ratio, (float) middle));
                if (distance < bestDistance) {
                    result = ratio;
                    bestDistance = distance;
                }
            }
        }
        return result;
    }

    private static void addReachStop(ArrayList<Float> stops, float longestRatio, float maxZoom) {
        if (stops.size() >= 4 || stops.isEmpty()) {
            return;
        }
        float reach = snapToNiceRatio(longestRatio * 2f);
        if (reach > maxZoom + EPSILON || reach <= stops.get(stops.size() - 1) + EPSILON) {
            return;
        }
        addDistinctStop(stops, reach);
    }

    private static void dropCrowdedStops(ArrayList<Float> stops) {
        while (stops.size() > 5) {
            int crowdedIndex = -1;
            double smallestGap = Double.MAX_VALUE;
            for (int i = 1; i < stops.size() - 1; i++) {
                double gap = octaves(stops.get(i - 1), stops.get(i));
                if (gap < smallestGap) {
                    crowdedIndex = i;
                    smallestGap = gap;
                }
            }
            if (crowdedIndex < 0) {
                return;
            }
            stops.remove(crowdedIndex);
        }
    }

    private static float snapToNiceRatio(float ratio) {
        if (!Float.isFinite(ratio) || ratio <= 0f) {
            return 0f;
        }
        float bestDelta = Float.MAX_VALUE;
        float result = 0f;
        for (float nice : SNAP_RATIOS) {
            float delta = (nice - ratio) / ratio;
            if (delta >= -0.03f && delta <= 0.1f) {
                float absDelta = Math.abs(delta);
                if (absDelta < bestDelta) {
                    result = nice;
                    bestDelta = absDelta;
                }
            }
        }
        return result > 0f ? result : Math.round(ratio * 10f) / 10f;
    }

    private static double nearestOctaveDistance(ArrayList<Float> stops, float value) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < stops.size(); i++) {
            min = Math.min(min, Math.abs(octaves(stops.get(i), value)));
        }
        return min;
    }

    private static double octaves(float from, float to) {
        if (from <= 0f || to <= 0f) {
            return 0d;
        }
        return Math.log((double) to / (double) from) / Math.log(2d);
    }

    private static float[] boundStops(float[] candidates, float minZoom, float maxZoom, boolean includeBounds) {
        ArrayList<Float> stops = new ArrayList<>(candidates.length + 2);
        if (includeBounds) {
            addDistinctStop(stops, minZoom);
        }
        for (float stop : candidates) {
            if (stop <= 0f || !Float.isFinite(stop)) {
                continue;
            }
            if (stop < minZoom - EPSILON) {
                if (!includeBounds) {
                    addDistinctStop(stops, minZoom);
                }
            } else if (stop <= maxZoom + EPSILON) {
                addDistinctStop(stops, Utilities.clamp(stop, maxZoom, minZoom));
            }
        }
        if (includeBounds) {
            addDistinctStop(stops, maxZoom);
        }
        return toArray(stops);
    }

    private static void addDistinctStop(ArrayList<Float> stops, float value) {
        if (value <= 0f || !Float.isFinite(value)) {
            return;
        }
        for (int i = 0; i < stops.size(); i++) {
            float existing = stops.get(i);
            if (Math.abs(existing - value) <= EPSILON) {
                return;
            }
            if (existing > value) {
                stops.add(i, value);
                return;
            }
        }
        stops.add(value);
    }

    private static float[] toArray(ArrayList<Float> list) {
        int size = list.size();
        float[] array = new float[size];
        for (int i = 0; i < size; i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}
