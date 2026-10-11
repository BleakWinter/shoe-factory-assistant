package com.shoefactory.assistant.dto;

import java.time.LocalDate;
import java.util.List;

public final class UnpricedShipmentResponse {
    private UnpricedShipmentResponse() {}

    public record MonthSummary(String month, int missingPriceCount, int mainStyleCount, int shippingQuantity) {}

    public record MonthDetails(String month, int missingPriceCount, int shippingQuantity, List<StyleGroup> groups) {}

    public record StyleGroup(String mainStyle, int missingPriceCount, int shippingQuantity, List<StyleSummary> styles) {}

    public record StyleSummary(Long customerId, String customerName, String developmentNo, String imageUrl,
                               int shippingQuantity, List<Detail> details) {}

    public record Detail(Long taskItemId, Long shippingTaskId, String taskNo, LocalDate shippingDate,
                         Long customerId, String customerName, Long orderId, String orderNo,
                         String developmentNo, String colorMaterial, int shippingQuantity, String remark) {}
}
