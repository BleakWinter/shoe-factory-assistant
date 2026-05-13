package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum FileType {
    // Excel 订单原稿，当前 V1 唯一允许上传的类型。
    EXCEL,
    // 图片订单是早期/后续预留类型，当前 uploadOrderSource 会继续拦截。
    IMAGE;

    public static FileType fromExtension(String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        // 扩展名只负责识别“文件类型”，不代表当前业务一定允许上传。
        return switch (normalized) {
            case "xlsx", "xls" -> EXCEL;
            case "png", "jpg", "jpeg", "webp" -> IMAGE;
            default -> throw new IllegalArgumentException("Unsupported file extension: " + extension);
        };
    }
}
