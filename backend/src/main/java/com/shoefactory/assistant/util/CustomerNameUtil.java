package com.shoefactory.assistant.util;

public final class CustomerNameUtil {

    private CustomerNameUtil() {
    }

    public static String normalizeWithoutChinese(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        String text = value.trim();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                break;
            }
            builder.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        String cleaned = builder.toString()
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("[\\s\\(\\[\\{\\uFF08\\uFF3B\\uFF5B\\-_/,:;]+$", "")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
