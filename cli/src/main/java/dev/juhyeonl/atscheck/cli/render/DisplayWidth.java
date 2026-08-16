package dev.juhyeonl.atscheck.cli.render;

import java.util.Objects;

public final class DisplayWidth {
    private static final String ELLIPSIS = "\u2026";

    private DisplayWidth() {
    }

    public static int width(String text) {
        Objects.requireNonNull(text, "text");

        int width = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            width += codePointWidth(codePoint);
            index += Character.charCount(codePoint);
        }
        return width;
    }

    public static String padRight(String text, int targetWidth) {
        Objects.requireNonNull(text, "text");

        int padding = targetWidth - width(text);
        if (padding <= 0) {
            return text;
        }
        return text + " ".repeat(padding);
    }

    public static String truncate(String text, int maxWidth) {
        Objects.requireNonNull(text, "text");

        if (maxWidth <= 0) {
            return "";
        }
        if (width(text) <= maxWidth) {
            return text;
        }

        int ellipsisWidth = width(ELLIPSIS);
        if (maxWidth <= ellipsisWidth) {
            return ELLIPSIS;
        }

        int contentWidth = maxWidth - ellipsisWidth;
        int usedWidth = 0;
        StringBuilder truncated = new StringBuilder();
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            int codePointWidth = codePointWidth(codePoint);
            if (usedWidth + codePointWidth > contentWidth) {
                break;
            }
            truncated.appendCodePoint(codePoint);
            usedWidth += codePointWidth;
            index += Character.charCount(codePoint);
        }
        return truncated.append(ELLIPSIS).toString();
    }

    private static int codePointWidth(int codePoint) {
        return isWideOrFullwidth(codePoint) ? 2 : 1;
    }

    private static boolean isWideOrFullwidth(int codePoint) {
        return inRange(codePoint, 0x1100, 0x11FF)
                || inRange(codePoint, 0x2E80, 0x2EFF)
                || inRange(codePoint, 0x2F00, 0x2FDF)
                || inRange(codePoint, 0x3000, 0x303F)
                || inRange(codePoint, 0x3040, 0x30FF)
                || inRange(codePoint, 0x3100, 0x312F)
                || inRange(codePoint, 0x3130, 0x318F)
                || inRange(codePoint, 0x3190, 0x31EF)
                || inRange(codePoint, 0x3400, 0x4DBF)
                || inRange(codePoint, 0x4E00, 0x9FFF)
                || inRange(codePoint, 0xA960, 0xA97F)
                || inRange(codePoint, 0xAC00, 0xD7A3)
                || inRange(codePoint, 0xD7B0, 0xD7FF)
                || inRange(codePoint, 0xF900, 0xFAFF)
                || inRange(codePoint, 0xFF00, 0xFF60)
                || inRange(codePoint, 0xFFE0, 0xFFE6)
                || inRange(codePoint, 0x20000, 0x2A6DF)
                || inRange(codePoint, 0x2A700, 0x2B73F)
                || inRange(codePoint, 0x2B740, 0x2B81F)
                || inRange(codePoint, 0x2B820, 0x2CEAF)
                || inRange(codePoint, 0x2CEB0, 0x2EBEF)
                || inRange(codePoint, 0x30000, 0x3134F);
    }

    private static boolean inRange(int codePoint, int startInclusive, int endInclusive) {
        return codePoint >= startInclusive && codePoint <= endInclusive;
    }
}
