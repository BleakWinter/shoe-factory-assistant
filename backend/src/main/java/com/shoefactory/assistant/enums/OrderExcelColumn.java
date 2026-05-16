package com.shoefactory.assistant.enums;

import java.util.List;

/**
 * 订单 Excel 明细表里的业务字段定义。
 *
 * fallbackIndex 是样本订单里的兜底列号，aliases 是可能出现的表头文字。
 * headerScoreWeight 用来判断某一行像不像表头，权重越高越关键。
 */
public enum OrderExcelColumn {
    IMAGE(5, 2, "图片"),
    LAST_NO(6, 2, "楦头", "楦头号"),
    DEVELOPMENT_NO(7, 2, "开发编号", "开发号"),
    CUSTOMER(8, 1, "客人", "客户"),
    CUSTOMER_ORDER_NO(9, 1, "客人订单号", "客户订单号"),
    DELIVERY_DATE(10, 0, "出货时间", "出货日期"),
    PO(11, 1, "PO", "PONO", "PO号", "PO号码"),
    CUSTOMER_STYLE_NO(12, 0, "客人型体号", "客户型体号"),
    ENGLISH_COLOR(13, 0, "英文颜色"),
    ENGLISH_MATERIAL(14, 0, "英文材质"),
    UPPER_MATERIAL(15, 0, "面料"),
    LINING_MATERIAL(16, 0, "里料/垫脚", "里料", "垫脚"),
    ACCESSORY(17, 0, "饰扣/鞋带", "饰扣", "鞋带"),
    INSOLE_PLATFORM(18, 0, "中底/包中底", "中底", "包中底"),
    OUTSOLE(19, 1, "大底"),
    TRADEMARK(20, 0, "商标"),
    QUANTITY(38, 1, "双数", "数量"),
    CARTON_COUNT(39, 0, "箱数"),
    CARTON_START(-1, 0, "CTN START", "CTNSTART", "CTN START/开始箱号", "CTNSTART开始箱号", "开始箱号", "起始箱号"),
    CARTON_END(-1, 0, "CTN END", "CTNEND", "CTN END/结束箱号", "CTNEND结束箱号", "结束箱号"),
    TOTAL_QUANTITY(40, 0, "总数量", "总数");

    private final int fallbackIndex;
    private final int headerScoreWeight;
    private final List<String> aliases;

    OrderExcelColumn(int fallbackIndex, int headerScoreWeight, String... aliases) {
        this.fallbackIndex = fallbackIndex;
        this.headerScoreWeight = headerScoreWeight;
        this.aliases = List.of(aliases);
    }

    public int getFallbackIndex() {
        return fallbackIndex;
    }

    public int getHeaderScoreWeight() {
        return headerScoreWeight;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public boolean participatesInHeaderScore() {
        return headerScoreWeight > 0;
    }
}
