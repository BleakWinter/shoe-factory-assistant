package com.shoefactory.assistant.enums;

/**
 * 明细处理状态，对应 order_detail_process.process_status。
 */
public enum OrderDetailProcessStatus {
    HANDLED(1, "已处理");

    private final int code;
    private final String label;

    OrderDetailProcessStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OrderDetailProcessStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderDetailProcessStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
