package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum PrintType {
    ORDER_SHEET(1, "订单", "订单"),
    PACKING_SHEET(2, "装箱单", "装箱单"),
    SHIPPING_NOTE(3, "出货单", null),
    OUTER_BOX_LABEL(4, "外箱贴标", "外箱贴标"),
    INNER_BOX_LABEL(5, "内盒贴标", null),
    TAG_LABEL(6, "标签", null);

    private final int code;
    private final String label;
    private final String sheetName;

    PrintType(int code, String label, String sheetName) {
        this.code = code;
        this.label = label;
        this.sheetName = sheetName;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getSheetName() {
        return sheetName;
    }

    public static PrintType fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("Print type is required");
        }
        for (PrintType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported print type: " + code);
    }

    public static PrintType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Print type is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.chars().allMatch(Character::isDigit)) {
            return fromCode(Integer.parseInt(normalized));
        }
        if ("ORDER".equals(normalized)) {
            return ORDER_SHEET;
        }
        if ("PACKING".equals(normalized)) {
            return PACKING_SHEET;
        }
        try {
            return PrintType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported print type: " + value, ex);
        }
    }
}
