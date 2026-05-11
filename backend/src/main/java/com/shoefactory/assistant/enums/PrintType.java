package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum PrintType {
    ORDER("订单"),
    PACKING("装箱单");

    private final String sheetName;

    PrintType(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getSheetName() {
        return sheetName;
    }

    public static PrintType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Print type is required");
        }
        try {
            return PrintType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported print type: " + value, ex);
        }
    }
}
