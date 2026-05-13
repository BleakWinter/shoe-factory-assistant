package com.shoefactory.assistant.enums;

import java.util.Locale;

public enum PrintType {
    // 打印订单本体，对应 Excel 的“订单”sheet。
    ORDER("订单"),
    // 打印装箱单，对应 Excel 的“装箱单”sheet。
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
            // 前端传 ORDER/PACKING；这里统一做大小写兼容。
            return PrintType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported print type: " + value, ex);
        }
    }
}
