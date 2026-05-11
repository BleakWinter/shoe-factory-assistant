package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum PrintTaskStatus {
    PENDING,
    PRINTING,
    SUCCESS,
    FAILED,
    CANCELED;

    public static PrintTaskStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task status is required");
        }
        try {
            return PrintTaskStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported task status: " + value, ex);
        }
    }
}
