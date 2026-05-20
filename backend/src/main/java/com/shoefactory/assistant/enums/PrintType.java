package com.shoefactory.assistant.enums;

public enum PrintType {
    ORDER(1, "订单", "订单"),
    PACKING(2, "装箱单", "装箱单");

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

}
