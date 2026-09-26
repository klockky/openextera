package com.exteragram.messenger.backup;

import android.text.TextUtils;

import java.nio.charset.StandardCharsets;

public abstract class InvisibleEncryptor {

    private static final String PREFIX = "  ";
    private static final String SEPARATOR = " ";
    private static final String ALPHABET = " ​‌‏ ⁪⁫⁬⁭⁮⁯";
    private static final int BASE = ALPHABET.length();

    private static String toStr(int value) {
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.insert(0, ALPHABET.charAt(value % BASE));
            value /= BASE;
        }
        return sb.toString();
    }

    private static int toNum(String value) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            int end = value.length() - i;
            result += ALPHABET.indexOf(value.substring(end - 1, end)) * Math.pow(BASE, i);
        }
        return result;
    }

    public static String encode(String text) {
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            String[] encoded = new String[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                encoded[i] = toStr(bytes[i] & 0xFF);
            }
            return PREFIX + String.join(SEPARATOR, encoded);
        } catch (Exception e) {
            e.printStackTrace();
            return text;
        }
    }

    public static String decode(String text) {
        try {
            String[] parts = text.replaceFirst("^" + PREFIX, "").split(SEPARATOR);
            byte[] bytes = new byte[parts.length];
            for (int i = 0; i < parts.length; i++) {
                bytes[i] = (byte) toNum(parts[i]);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return text;
        }
    }

    public static boolean isEncrypted(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        return text.matches("^" + PREFIX + "([" + ALPHABET + "\\s]*)");
    }
}
