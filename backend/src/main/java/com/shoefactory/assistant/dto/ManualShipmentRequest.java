package com.shoefactory.assistant.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
public record ManualShipmentRequest(
    @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{16,64}") String requestId,
    @NotNull LocalDate shippingDate,
    @NotEmpty @Size(max=100) List<@NotNull @Positive Long> detailIds
) {}
