package com.shoefactory.assistant.dto;
import jakarta.validation.constraints.*;
public record CustomerSaveRequest(
    @NotBlank @Size(max=30) @Pattern(regexp="[A-Za-z0-9_-]+") String code,
    @NotBlank @Size(max=50) String name,
    @NotBlank @Size(max=128) String recipientName,
    @NotBlank @Pattern(regexp="EXCEL|MANUAL") String inputMode,
    @NotNull Boolean enabled
) {}
