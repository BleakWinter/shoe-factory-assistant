package com.shoefactory.assistant.enums;

/**
 * 订单来源，对应 order_record.source_type。
 */
public enum OrderSourceType {
    EXCEL(1, "Excel"),
    IMAGE(2, "图片"),
    MANUAL(3, "手动录入");

    private final int code;
    private final String label;

    OrderSourceType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OrderSourceType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderSourceType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
