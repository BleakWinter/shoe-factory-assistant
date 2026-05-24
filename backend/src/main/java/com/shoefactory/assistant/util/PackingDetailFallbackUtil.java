package com.shoefactory.assistant.util;

import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecordDetail;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class PackingDetailFallbackUtil {

    private PackingDetailFallbackUtil() {
    }

    public static List<OrderPackingDetail> fromOrderDetails(Collection<OrderRecordDetail> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        return details.stream()
                .sorted(Comparator
                        .comparing(OrderRecordDetail::getRowIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(OrderRecordDetail::getLineNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(OrderRecordDetail::getId, Comparator.nullsLast(Long::compareTo)))
                .map(PackingDetailFallbackUtil::fromOrderDetail)
                .toList();
    }

    public static OrderPackingDetail fromOrderDetail(OrderRecordDetail detail) {
        OrderPackingDetail fallback = new OrderPackingDetail();
        Long id = detail.getId();
        fallback.setId(id == null ? null : -Math.abs(id));
        fallback.setOrderId(detail.getOrderId());
        fallback.setLineNo(detail.getLineNo());
        fallback.setCompanyStyleNo(detail.getDevelopmentNo());
        fallback.setCustomerName(detail.getCustomerName());
        fallback.setCustomerOrderNo(detail.getCustomerOrderNo());
        fallback.setWarehouseStoreNo(detail.getWarehouseStoreNo());
        fallback.setPoNo(detail.getPoNo());
        fallback.setCustomerStyleNo(detail.getCustomerStyleNo());
        fallback.setCustomerColor(detail.getEnglishColor());
        fallback.setMaterial(firstText(detail.getEnglishMaterial(), detail.getUpperMaterial()));
        fallback.setTrademark(detail.getTrademark());
        fallback.setSizeQuantitiesJson(detail.getSizeQuantitiesJson());
        fallback.setCartonCount(detail.getCartonCount());
        fallback.setTotalPairs(detail.getQuantity());
        fallback.setCartonStart(detail.getCartonStart());
        fallback.setCartonEnd(detail.getCartonEnd());
        fallback.setSourceSheetName(detail.getSourceSheetName());
        fallback.setRowIndex(detail.getRowIndex());
        fallback.setRemark("由订单明细临时生成");
        fallback.setCreatedAt(detail.getCreatedAt());
        fallback.setUpdatedAt(detail.getUpdatedAt());
        return fallback;
    }

    private static String firstText(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }
}
