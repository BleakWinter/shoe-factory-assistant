package com.shoefactory.assistant.enums;

/**
 * 明细处理类型，对应 order_detail_process.process_type。
 */
public enum OrderDetailProcessType {
    ORDER_PACKING(1, "订包装"),
    ORDER_OUTSOLE(2, "定大底"),
    ORDER_INSOLE(3, "定中底"),
    ORDER_HEEL(4, "定跟"),
    INNER_BOX_LABEL(5, "内盒贴标"),
    OUTER_CARTON_LABEL(6, "外箱贴标"),
    SHIPPING_NOTE_PRINT(7, "出货单打印");

    private final int code;
    private final String label;

    OrderDetailProcessType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OrderDetailProcessType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderDetailProcessType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
