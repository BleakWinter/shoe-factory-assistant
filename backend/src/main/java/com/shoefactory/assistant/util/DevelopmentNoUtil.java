package com.shoefactory.assistant.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DevelopmentNoUtil {

    private static final Pattern CANDIDATE_PATTERN = Pattern.compile("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*");
    private static final Pattern FULL_PATTERN = Pattern.compile("^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$");

    private DevelopmentNoUtil() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = CANDIDATE_PATTERN.matcher(normalizeHyphens(value));
        while (matcher.find()) {
            String candidate = matcher.group().trim();
            if (isValid(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static String normalizeSearchTerm(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = CANDIDATE_PATTERN.matcher(normalizeHyphens(value));
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalizeHyphens(value).trim();
        return FULL_PATTERN.matcher(normalized).matches()
                && containsAsciiLetter(normalized)
                && containsDigit(normalized);
    }

    private static String normalizeHyphens(String value) {
        return value
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-')
                .replace('\uFF0D', '-')
                .replaceAll("\\s*-\\s*", "-")
                .trim();
    }

    private static boolean containsAsciiLetter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDigit(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
