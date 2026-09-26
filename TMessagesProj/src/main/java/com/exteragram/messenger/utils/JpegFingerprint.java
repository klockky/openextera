package com.exteragram.messenger.utils;

import org.telegram.messenger.FileLog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class JpegFingerprint {

    private static final int MAX_HEADER_BYTES = 256 * 1024;

    private static final int M_SOF0 = 0xC0;
    private static final int M_SOF2 = 0xC2;
    private static final int M_DHT = 0xC4;
    private static final int M_RST0 = 0xD0;
    private static final int M_RST7 = 0xD7;
    private static final int M_SOI = 0xD8;
    private static final int M_EOI = 0xD9;
    private static final int M_SOS = 0xDA;
    private static final int M_DQT = 0xDB;
    private static final int M_DRI = 0xDD;
    private static final int M_APP0 = 0xE0;
    private static final int M_APP1 = 0xE1;
    private static final int M_APP2 = 0xE2;
    private static final int M_TEM = 0x01;

    private static final int[] ZIGZAG = {
            0, 1, 8, 16, 9, 2, 3, 10,
            17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34,
            27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36,
            29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46,
            53, 60, 61, 54, 47, 55, 62, 63
    };

    private static final int[] STANDARD_LUMA = {
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99
    };

    public int jfifVersion = -1;
    public int jfifUnits = -1;
    public int jfifDensityX;
    public int jfifDensityY;
    public boolean jfifThumbnail;

    public boolean exif;
    public boolean xmp;

    public int iccLength;
    public int iccVersion;
    public int iccYear;
    public String iccDescription;

    public boolean quantizationTable;
    public int quality = -1;
    public int huffmanTables;
    public boolean restartInterval;

    public boolean progressive;
    public boolean frameBeforeQuantization;
    public int width;
    public int height;
    public int components;
    public int horizontalSampling;
    public int verticalSampling;

    public String markerOrder = "";

    public static JpegFingerprint parse(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), 16384)) {
            if (in.read() != 0xFF || in.read() != M_SOI) {
                return null;
            }
            JpegFingerprint fingerprint = new JpegFingerprint();
            StringBuilder order = new StringBuilder();
            int position = 2;
            while (position < MAX_HEADER_BYTES && in.read() == 0xFF) {
                int marker = in.read();
                while (marker == 0xFF) {
                    marker = in.read();
                    position++;
                }
                position += 2;
                if (marker < 0 || marker == M_EOI || marker == M_SOS) {
                    break;
                }
                if (marker == M_TEM || marker >= M_RST0 && marker <= M_RST7) {
                    continue;
                }
                int hi = in.read();
                int lo = in.read();
                if (hi < 0 || lo < 0) {
                    break;
                }
                int segmentLength = (hi << 8) | lo;
                int payloadLength = segmentLength - 2;
                if (payloadLength < 0) {
                    break;
                }
                position += segmentLength;
                if (order.length() > 0) {
                    order.append('>');
                }
                order.append(String.format(Locale.US, "%02X", marker));

                boolean interesting = marker == M_APP0 || marker == M_APP1 || marker == M_APP2
                        || marker == M_DQT || marker == M_DHT || marker == M_DRI
                        || marker >= M_SOF0 && marker <= M_SOF2;
                if (!interesting) {
                    long skipped = 0;
                    while (skipped < payloadLength) {
                        long count = in.skip(payloadLength - skipped);
                        if (count <= 0) {
                            break;
                        }
                        skipped += count;
                    }
                    if (skipped < payloadLength) {
                        break;
                    }
                    continue;
                }

                byte[] payload = new byte[payloadLength];
                int read = 0;
                while (read < payloadLength) {
                    int count = in.read(payload, read, payloadLength - read);
                    if (count < 0) {
                        break;
                    }
                    read += count;
                }
                if (read < payloadLength) {
                    break;
                }
                fingerprint.readSegment(marker, payload);
            }
            fingerprint.markerOrder = order.toString();
            return fingerprint;
        } catch (IOException e) {
            FileLog.e(e);
            return null;
        }
    }

    private void readSegment(int marker, byte[] data) {
        if (marker == M_APP0) {
            if (data.length < 14 || !startsWith(data, "JFIF")) {
                return;
            }
            jfifVersion = ((data[5] & 0xFF) << 8) | (data[6] & 0xFF);
            jfifUnits = data[7] & 0xFF;
            jfifDensityX = ((data[8] & 0xFF) << 8) | (data[9] & 0xFF);
            jfifDensityY = ((data[10] & 0xFF) << 8) | (data[11] & 0xFF);
            jfifThumbnail = data[12] != 0 || data[13] != 0;
        } else if (marker == M_APP1) {
            exif = exif || startsWith(data, "Exif");
            xmp = xmp || startsWith(data, "http://ns.adobe.com/xap/1.0/");
        } else if (marker == M_APP2) {
            if (iccLength == 0 && data.length >= 50 && startsWith(data, "ICC_PROFILE")) {
                iccLength = readInt(data, 14);
                iccVersion = ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
                iccYear = ((data[38] & 0xFF) << 8) | (data[39] & 0xFF);
                iccDescription = readIccDescription(data, 14);
            }
        } else if (marker == M_DQT) {
            int offset = 0;
            while (offset < data.length) {
                int precision = (data[offset] & 0xF0) >> 4;
                int tableId = data[offset] & 0x0F;
                int tableStart = offset + 1;
                int next = tableStart + (precision == 0 ? 64 : 128);
                if (next > data.length) {
                    return;
                }
                if (tableId == 0 && precision == 0 && !quantizationTable) {
                    quantizationTable = true;
                    quality = standardQuality(data, tableStart);
                }
                offset = next;
            }
        } else if (marker == M_DHT) {
            int offset = 0;
            while (offset + 17 <= data.length) {
                int symbols = 0;
                for (int i = 0; i < 16; i++) {
                    symbols += data[offset + 1 + i] & 0xFF;
                }
                huffmanTables++;
                offset += symbols + 17;
            }
        } else if (marker == M_DRI) {
            restartInterval = data.length >= 2 && (((data[0] & 0xFF) << 8) | (data[1] & 0xFF)) > 0;
        } else if (marker >= M_SOF0 && marker <= M_SOF2 && data.length >= 9) {
            progressive = marker == M_SOF2;
            frameBeforeQuantization = !quantizationTable;
            height = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
            width = ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
            components = data[5] & 0xFF;
            horizontalSampling = (data[7] & 0xF0) >> 4;
            verticalSampling = data[7] & 0x0F;
        }
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static String readIccDescription(byte[] data, int base) {
        try {
            int tagCount = readInt(data, base + 128);
            for (int i = 0; i < Math.min(tagCount, 64); i++) {
                int entry = base + 132 + i * 12;
                if (entry + 12 > data.length) {
                    break;
                }
                if (data[entry] != 'd' || data[entry + 1] != 'e' || data[entry + 2] != 's' || data[entry + 3] != 'c') {
                    continue;
                }
                int offset = readInt(data, entry + 4) + base;
                int size = readInt(data, entry + 8);
                int end = offset + size;
                if (offset < base || size < 12 || end > data.length) {
                    break;
                }
                byte type = data[offset];
                if (type == 'd') {
                    int length = Math.min(readInt(data, offset + 8) - 1, size - 12);
                    return length > 0 ? new String(data, offset + 12, length, StandardCharsets.US_ASCII) : null;
                } else if (type == 'm') {
                    int length = readInt(data, offset + 20);
                    int start = readInt(data, offset + 24) + offset;
                    if (length <= 0 || start < offset || start + length > end) {
                        break;
                    }
                    return new String(data, start, length, StandardCharsets.UTF_16BE);
                }
                break;
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static int standardQuality(byte[] data, int offset) {
        for (int q = 100; q >= 1; q--) {
            int scale = q < 50 ? 5000 / q : 200 - q * 2;
            boolean matches = true;
            for (int i = 0; i < 64; i++) {
                int expected = (STANDARD_LUMA[ZIGZAG[i]] * scale + 50) / 100;
                expected = Math.max(1, Math.min(255, expected));
                if (expected != (data[offset + i] & 0xFF)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return q;
            }
        }
        return -1;
    }

    private static boolean startsWith(byte[] data, String prefix) {
        if (data.length <= prefix.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (data[i] != (byte) prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (jfifUnits >= 0) {
            sb.append(String.format(Locale.US, "jfif=%d.%02d/u%d/%dx%d", jfifVersion >> 8, jfifVersion & 0xFF, jfifUnits, jfifDensityX, jfifDensityY));
        } else {
            sb.append("jfif=none");
        }
        if (jfifThumbnail) {
            sb.append("/thumb");
        }
        if (iccLength > 0) {
            sb.append(String.format(Locale.US, " icc=%d/%04x/%d/%s", iccLength, iccVersion, iccYear, iccDescription != null ? iccDescription : "?"));
        } else {
            sb.append(" icc=none");
        }
        if (quality > 0) {
            sb.append(String.format(Locale.US, " dqt=q%d", quality));
        } else {
            sb.append(quantizationTable ? " dqt=custom" : " dqt=none");
        }
        sb.append(String.format(Locale.US, " sof=%s/%dc/%dx%d/%dx%d", progressive ? "prog" : "base", components, horizontalSampling, verticalSampling, width, height));
        sb.append(String.format(Locale.US, " dht=%d", huffmanTables));
        if (exif) {
            sb.append(" exif");
        }
        if (xmp) {
            sb.append(" xmp");
        }
        if (restartInterval) {
            sb.append(" dri");
        }
        sb.append(" order=").append(markerOrder);
        return sb.toString();
    }
}
