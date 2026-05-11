package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum OrderRecognitionStatus {
    RECOGNIZED,
    PENDING_MANUAL,
    FAILED;

    public static OrderRecognitionStatus parseNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OrderRecognitionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported recognition status: " + value, ex);
        }
    }
}
