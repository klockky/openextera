package com.exteragram.messenger.utils;

import androidx.exifinterface.media.ExifInterface;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class MediaUtils {

    private static final String[] GEO_TAGS = {
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD
    };

    private static final String[] GEO_TAGS_API_24 = {
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF
    };

    private static final String[] MOTION_PHOTO_XMP_MARKERS = {
            "Camera:MotionPhoto", "GCamera:MotionPhoto",
            "Camera:MicroVideo", "GCamera:MicroVideo",
            "Camera:MotionPhotoPresentationTimestampUs", "GCamera:MotionPhotoPresentationTimestampUs",
            "Camera:MicroVideoPresentationTimestampUs", "GCamera:MicroVideoPresentationTimestampUs",
            "Camera:MicroVideoOffset", "GCamera:MicroVideoOffset",
            "Container:Directory", "GContainer:Directory"
    };

    public static boolean removeGeolocation(String sourcePath, String destinationPath) throws IOException {
        File source = new File(sourcePath);
        if (!source.exists()) {
            return false;
        }
        File destination = new File(destinationPath);
        if (!AndroidUtilities.copyFile(source, destination)) {
            destination.delete();
            throw new IOException("failed to copy " + sourcePath + " to " + destinationPath);
        }

        List<String> tags = new ArrayList<>(Arrays.asList(GEO_TAGS));
        tags.addAll(getApi24GeoTags());

        ExifInterface exif = new ExifInterface(destination.getAbsolutePath());
        boolean changed = false;
        for (String tag : tags) {
            if (exif.getAttribute(tag) != null) {
                try {
                    exif.setAttribute(tag, null);
                    changed = true;
                } catch (Exception ignore) {
                }
            }
        }
        if (changed) {
            try {
                exif.saveAttributes();
                return true;
            } catch (IOException e) {
                destination.delete();
            }
        }
        return false;
    }

    private static List<String> getApi24GeoTags() {
        return Arrays.asList(GEO_TAGS_API_24);
    }

    public static String getPhotoPlatform(JpegFingerprint fingerprint) {
        if (fingerprint == null || fingerprint.jfifUnits < 0) {
            return null;
        }
        boolean skia = fingerprint.iccVersion == 1072 && fingerprint.iccYear == 2016
                || fingerprint.iccDescription != null && fingerprint.iccDescription.startsWith("Google/Skia/");
        if (fingerprint.jfifUnits == 0) {
            if (fingerprint.jfifDensityX != 1) {
                return null;
            }
            if (skia) {
                return "Android";
            }
            if (fingerprint.iccLength == 0) {
                if (fingerprint.quality == 72) {
                    return "iOS";
                }
                if (fingerprint.quality == 75) {
                    return "macOS";
                }
            }
            return null;
        }
        if (fingerprint.jfifUnits != 1 || fingerprint.jfifDensityX != 72) {
            return fingerprint.iccVersion == 576 ? "macOS" : "Desktop";
        }
        if (skia) {
            return "Android";
        }
        if (fingerprint.iccVersion == 576) {
            return "macOS";
        }
        if (fingerprint.iccVersion == 1088 && fingerprint.iccLength == 480) {
            return "Desktop";
        }
        return null;
    }
}
