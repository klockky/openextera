package com.exteragram.messenger.utils.text;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;

import java.util.ArrayList;

public abstract class ZalgoFilter {

    private static final int MAX_MARKS = 4;
    private static final int MAX_ZALGO_MARKS = 2;
    private static final char WORD_JOINER = '⁠';

    private static boolean isDirectionControl(int codePoint) {
        return codePoint == 0x061C
                || codePoint == 0x200E || codePoint == 0x200F
                || codePoint >= 0x202A && codePoint <= 0x202E
                || codePoint >= 0x2066 && codePoint <= 0x2069;
    }

    private static boolean isZalgoMarkRange(int codePoint) {
        return codePoint >= 0x0300 && codePoint <= 0x036F
                || codePoint >= 0x1AB0 && codePoint <= 0x1AFF
                || codePoint >= 0x1DC0 && codePoint <= 0x1DFF
                || codePoint >= 0x20D0 && codePoint <= 0x20FF
                || codePoint >= 0xFE20 && codePoint <= 0xFE2F;
    }

    public static boolean canFilter(CharSequence text) {
        return ExteraConfig.getFilterZalgo() && !TextUtils.isEmpty(text) && findReplacementRanges(text) != null;
    }

    public static CharSequence filterSpannable(CharSequence text) {
        if (!canFilter(text)) {
            return text;
        }
        if (!(text instanceof Spannable)) {
            return filter(text);
        }
        Spannable source = (Spannable) text;
        SpannableString result = new SpannableString(filterEnabled(source.toString()));
        for (Object span : source.getSpans(0, source.length(), Object.class)) {
            int start = Math.max(0, Math.min(source.getSpanStart(span), result.length()));
            int end = Math.min(source.getSpanEnd(span), result.length());
            if (start > end) {
                start = end;
            }
            result.setSpan(span, start, end, source.getSpanFlags(span));
        }
        return result;
    }

    public static String filter(CharSequence text) {
        if (text == null) {
            return null;
        }
        return filter(text.toString());
    }

    public static String filter(String text) {
        if (!ExteraConfig.getFilterZalgo() || TextUtils.isEmpty(text)) {
            return text;
        }
        return filterEnabled(text);
    }

    private static String filterEnabled(String text) {
        ArrayList<int[]> ranges = findReplacementRanges(text);
        if (ranges == null) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int position = 0;
        for (int[] range : ranges) {
            sb.append(text, position, range[0]);
            appendReplacement(sb, range[1] - range[0]);
            position = range[1];
        }
        sb.append(text, position, text.length());
        return sb.toString();
    }

    private static ArrayList<int[]> findReplacementRanges(CharSequence text) {
        int length = text.length();
        ArrayList<int[]> ranges = null;
        int sequenceStart = -1;
        int marksCount = 0;
        int allowedMarks = MAX_MARKS;
        int i = 0;
        while (i < length) {
            int codePoint = Character.codePointAt(text, i);
            int charCount = Character.charCount(codePoint);
            int allowed = getAllowedMarksPerSequence(codePoint);
            if (allowed > 0) {
                if (sequenceStart < 0) {
                    sequenceStart = i;
                    marksCount = 0;
                } else {
                    allowed = Math.min(allowedMarks, allowed);
                }
                marksCount++;
                allowedMarks = allowed;
            } else {
                ranges = addMarkSequenceRange(ranges, sequenceStart, i, marksCount, allowedMarks);
                if (isDirectionControl(codePoint)) {
                    ranges = addRange(ranges, i, i + charCount);
                }
                sequenceStart = -1;
                marksCount = 0;
                allowedMarks = MAX_MARKS;
            }
            i += charCount;
        }
        return addMarkSequenceRange(ranges, sequenceStart, length, marksCount, allowedMarks);
    }

    private static ArrayList<int[]> addMarkSequenceRange(ArrayList<int[]> ranges, int start, int end, int marksCount, int allowedMarks) {
        if (start < 0 || marksCount <= allowedMarks) {
            return ranges;
        }
        return addRange(ranges, start, end);
    }

    private static ArrayList<int[]> addRange(ArrayList<int[]> ranges, int start, int end) {
        if (ranges == null) {
            ranges = new ArrayList<>();
        }
        ranges.add(new int[]{start, end});
        return ranges;
    }

    private static void appendReplacement(StringBuilder sb, int count) {
        for (int i = 0; i < count; i++) {
            sb.append(WORD_JOINER);
        }
    }

    private static int getAllowedMarksPerSequence(int codePoint) {
        if (codePoint < 0x0300) {
            return 0;
        }
        int type = Character.getType(codePoint);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) {
            return isZalgoMarkRange(codePoint) ? MAX_ZALGO_MARKS : MAX_MARKS;
        }
        return 0;
    }
}
