package com.shoefactory.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record QuotationSaveRequest(
        @Min(1) Integer expectedVersion,
        @Positive Long customerId,
        @Size(max = 128) String developmentNo,
        @Size(max = 64) String lastNo,
        @Size(max = 255) String colorMaterial,
        LocalDate quotationDate,
        @Size(max = 128) String imageKey,
        @Positive Long sourceDetailId,
        @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal laborCost,
        @NotNull Boolean includeOutsole,
        @NotBlank @Pattern(regexp = "CEIL|CENT|ROUND") String roundingMode,
        @NotNull Boolean manualPrice,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal finalPrice,
        @Size(max = 2000) String note,
        @NotNull @Size(max = 200) List<@NotNull @Valid Item> items
) {
    public record Item(
            @NotBlank @Pattern(regexp = "MATERIAL|FEE|OUTSOLE") String category,
            @Size(max = 100) String partName,
            @Size(max = 255) String materialName,
            @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal quantity,
            @Size(max = 16) String unit,
            @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal unitPrice,
            @Size(max = 1000) String remark
    ) {}
}
