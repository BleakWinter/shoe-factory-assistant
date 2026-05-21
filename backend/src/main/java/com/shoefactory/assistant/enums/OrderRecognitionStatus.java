package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum OrderRecognitionStatus {
    // 和 order_record 的订单/装箱单识别状态 tinyint 编码保持一致。
    PENDING(0, "待识别"),
    RECOGNIZED(1, "已识别"),
    PENDING_MANUAL(2, "待人工处理"),
    FAILED(3, "识别失败");

    private final int code;
    private final String label;

    OrderRecognitionStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OrderRecognitionStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderRecognitionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    public static OrderRecognitionStatus parseNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return fromCode(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            // 前端也可以继续传 RECOGNIZED 这类枚举名。
        }
        try {
            return OrderRecognitionStatus.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported recognition status: " + value, ex);
        }
    }
}
