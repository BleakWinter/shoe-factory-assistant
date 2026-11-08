package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.ShoeQuotationItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record QuotationResponse(
        Long id, String versionGroup, Integer versionNo, Integer latestVersionNo,
        Long customerId, String customerName, String developmentNo, String lastNo,
        String colorMaterial, LocalDate quotationDate, String imageKey, String imageUrl,
        BigDecimal laborCost, Boolean includeOutsole, String roundingMode, Boolean manualPrice,
        BigDecimal finalPrice, BigDecimal materialAmount, BigDecimal auxiliaryAmount,
        BigDecimal outsoleAmount, BigDecimal calculatedAmount, BigDecimal currentPrice,
        String note, LocalDateTime adoptedAt, String signedImageUrl, LocalDateTime createdAt,
        List<ShoeQuotationItem> items, List<Version> versions
) {
    public record Version(Long id, Integer versionNo, BigDecimal finalPrice,
                          LocalDateTime createdAt, LocalDateTime adoptedAt) {}
    public record Reference(Long sourceDetailId, String imageUrl, String colorMaterial, BigDecimal currentPrice) {}
    public record Image(String imageKey, String imageUrl) {}
    public record SignedPhotoReuse(Long id, Integer versionNo) {}
}
