package com.shoefactory.assistant.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record CustomerPriceSaveRequest(
    @NotNull @Positive Long customerId,
    @NotBlank @Size(max=128) String developmentNo,
    @DecimalMin("0.00") @Digits(integer=8, fraction=2) BigDecimal shoePrice
) {}
