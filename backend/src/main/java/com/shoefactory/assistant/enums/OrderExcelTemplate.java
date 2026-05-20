package com.shoefactory.assistant.enums;

import java.util.regex.Pattern;

/**
 * 订单 Excel 模板级规则。
 *
 * 如果以后支持多个客户/多个模板，可以继续在这里增加枚举值。
 */
public enum OrderExcelTemplate {
    DEFAULT(PrintType.ORDER_SHEET.getSheetName(), 4, 30, Pattern.compile("^(\\d{4,})"));

    private final String orderSheetName;
    private final int fallbackHeaderRowIndex;
    private final int maxHeaderScanRows;
    private final Pattern leadingOrderNoPattern;

    OrderExcelTemplate(
            String orderSheetName,
            int fallbackHeaderRowIndex,
            int maxHeaderScanRows,
            Pattern leadingOrderNoPattern
    ) {
        this.orderSheetName = orderSheetName;
        this.fallbackHeaderRowIndex = fallbackHeaderRowIndex;
        this.maxHeaderScanRows = maxHeaderScanRows;
        this.leadingOrderNoPattern = leadingOrderNoPattern;
    }

    public String getOrderSheetName() {
        return orderSheetName;
    }

    public int getFallbackHeaderRowIndex() {
        return fallbackHeaderRowIndex;
    }

    public int getMaxHeaderScanRows() {
        return maxHeaderScanRows;
    }

    public Pattern getLeadingOrderNoPattern() {
        return leadingOrderNoPattern;
    }
}
