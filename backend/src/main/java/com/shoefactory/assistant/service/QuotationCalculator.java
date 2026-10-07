package com.shoefactory.assistant.service;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.QuotationSaveRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 用十进制计算；空值是待填写，明确的 0 是零价。 */
public final class QuotationCalculator {
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private QuotationCalculator() {}
    public record Result(BigDecimal materials, BigDecimal auxiliary, BigDecimal outsole,
                         BigDecimal calculated, BigDecimal finalPrice, List<BigDecimal> itemAmounts) {}

    public static Result calculate(QuotationSaveRequest request) {
        BigDecimal materials = BigDecimal.ZERO, auxiliary = BigDecimal.ZERO, outsole = BigDecimal.ZERO;
        boolean missingMaterial = false, missingFee = false, missingOutsole = false;
        int outsoleCount = 0;
        List<BigDecimal> amounts = new ArrayList<>();
        for (var item : request.items()) {
            BigDecimal amount = item.quantity() == null || item.unitPrice() == null ? null : item.quantity().multiply(item.unitPrice());
            amounts.add(amount);
            switch (item.category()) {
                case "MATERIAL" -> { missingMaterial |= amount == null; if (amount != null) materials = materials.add(amount); }
                case "FEE" -> { missingFee |= amount == null; if (amount != null) auxiliary = auxiliary.add(amount); }
                case "OUTSOLE" -> { outsoleCount++; missingOutsole |= amount == null; if (amount != null) outsole = outsole.add(amount); }
                default -> throw new BusinessException("不支持的报价项目类别");
            }
        }
        if (missingMaterial) materials = null;
        if (missingFee) auxiliary = null;
        if (!Boolean.TRUE.equals(request.includeOutsole())) outsole = BigDecimal.ZERO;
        else if (missingOutsole || outsoleCount == 0) outsole = null;
        BigDecimal calculated = materials == null || auxiliary == null || outsole == null || request.laborCost() == null
                ? null : materials.add(auxiliary).add(outsole).add(request.laborCost());
        BigDecimal finalPrice = Boolean.TRUE.equals(request.manualPrice()) ? request.finalPrice() : rounded(calculated, request.roundingMode());
        if (finalPrice != null) {
            if (finalPrice.signum() < 0 || finalPrice.compareTo(MAX_PRICE) > 0) throw new BusinessException("最终报价应在 0 至 99999999.99 元之间");
            try { finalPrice = finalPrice.setScale(2, RoundingMode.UNNECESSARY); }
            catch (ArithmeticException ex) { throw new BusinessException("最终报价最多保留两位小数"); }
        }
        return new Result(materials, auxiliary, outsole, calculated, finalPrice, amounts);
    }
    private static BigDecimal rounded(BigDecimal value, String mode) {
        if (value == null) return null;
        return switch (mode) {
            case "CEIL" -> value.setScale(0, RoundingMode.CEILING);
            case "CENT" -> value.setScale(2, RoundingMode.HALF_UP);
            case "ROUND" -> value.setScale(0, RoundingMode.HALF_UP);
            default -> throw new BusinessException("不支持的报价取整方式");
        };
    }
}
