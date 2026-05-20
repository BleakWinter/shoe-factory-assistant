package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum PrintTaskStatus {
    PENDING(1, "待打印"),
    PRINTED(2, "已打印"),
    FAILED(3, "失败"),
    INVALID(4, "已失效");

    private final int code;
    private final String label;

    PrintTaskStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static PrintTaskStatus fromCode(Integer code) {
        if (code == null) {
            return PENDING;
        }
        for (PrintTaskStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unsupported task status: " + code);
    }

    public static PrintTaskStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.chars().allMatch(Character::isDigit)) {
            return fromCode(Integer.parseInt(normalized));
        }
        try {
            return PrintTaskStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported task status: " + value, ex);
        }
    }
}
