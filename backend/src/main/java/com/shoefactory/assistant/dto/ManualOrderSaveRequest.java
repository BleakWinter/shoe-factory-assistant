package com.shoefactory.assistant.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public record ManualOrderSaveRequest(
    @NotNull @Positive Long customerId,
    @NotBlank @Size(max=128) String orderNo,
    @NotNull LocalDate orderDate,
    LocalDate deliveryDate,
    @Size(max=1000) String remark,
    @NotEmpty @Size(max=100) List<@NotNull @Valid Item> items
) {
    public record Item(
        Long id,
        @NotBlank @Size(max=128) String developmentNo,
        @Size(max=255) String colorMaterial,
        @NotNull @Min(1) @Max(1000000) Integer quantity,
        @Size(max=1000) String remark
    ) {}
}
