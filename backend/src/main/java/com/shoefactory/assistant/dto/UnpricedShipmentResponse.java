package com.shoefactory.assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class UnpricedShipmentResponse {
    private UnpricedShipmentResponse() {}

    public enum QuotationStatus { NOT_QUOTED, DRAFT, QUOTED }

    public record QuotationCounts(int unquotedCount, int draftCount, int quotedCount) {}

    public record QuotationSummary(Long id, Integer versionNo, BigDecimal finalPrice, LocalDate quotationDate,
                                   LocalDateTime createdAt, LocalDateTime adoptedAt) {}

    public record MonthSummary(String month, int missingPriceCount, int mainStyleCount, int shippingQuantity,
                               QuotationCounts quotationCounts) {}

    public record MonthDetails(String month, int missingPriceCount, int shippingQuantity, List<StyleGroup> groups,
                               QuotationCounts quotationCounts) {}

    public record StyleGroup(String mainStyle, int missingPriceCount, int shippingQuantity, List<StyleSummary> styles,
                             QuotationCounts quotationCounts) {}

    public record StyleSummary(Long customerId, String customerName, String developmentNo, String imageUrl,
                               int shippingQuantity, List<Detail> details, QuotationStatus quotationStatus,
                               QuotationSummary latestQuotation, QuotationSummary latestPricedQuotation) {}

    public record Detail(Long taskItemId, Long shippingTaskId, String taskNo, LocalDate shippingDate,
                         Long customerId, String customerName, Long orderId, String orderNo,
                         String developmentNo, String colorMaterial, int shippingQuantity, String remark) {}
}
