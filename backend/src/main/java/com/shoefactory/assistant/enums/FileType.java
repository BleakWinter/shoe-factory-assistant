package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum FileType {
    EXCEL,
    IMAGE;

    public static FileType fromExtension(String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "xlsx", "xls" -> EXCEL;
            case "png", "jpg", "jpeg", "webp" -> IMAGE;
            default -> throw new IllegalArgumentException("Unsupported file extension: " + extension);
        };
    }
}
