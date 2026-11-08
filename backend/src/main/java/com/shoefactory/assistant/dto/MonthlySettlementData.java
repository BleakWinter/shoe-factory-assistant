package com.shoefactory.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class MonthlySettlementData {
    private MonthlySettlementData() {}

    public record Material(@NotNull LocalDate date, @NotBlank @Size(max = 100) String name,
            @NotNull @Positive @Digits(integer = 6, fraction = 4) BigDecimal quantity,
            @Size(max = 16) String unit, @NotNull @PositiveOrZero @Digits(integer = 5, fraction = 4) BigDecimal unitPrice,
            @Size(max = 100) String reference) {}

    public enum AdjustmentType { CREDIT, DEBIT }

    public record Adjustment(@NotNull AdjustmentType type, @NotBlank @Size(max = 200) String reason,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount) {}

    public record RefundInput(@NotNull @Positive Long originId,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount) {}

    public record Draft(@NotNull @PastOrPresent LocalDate preparedOn,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal holdRate,
            @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal holdBase,
            @NotNull @Size(max = 10000) List<@NotBlank @Pattern(regexp = "[0-9]+:[0-9]+") String> deferredKeys,
            @NotNull @Size(max = 10000) List<@NotBlank @Pattern(regexp = "[0-9]+:[0-9]+") String> carryKeys,
            @NotNull @Size(max = 500) List<@NotNull @Valid Material> materials,
            @NotNull @Size(max = 500) List<@NotNull @Valid Adjustment> adjustments,
            @NotNull @Size(max = 500) List<@NotNull @Valid RefundInput> refunds) {}

    public record SaveRequest(@PositiveOrZero int revision, @NotNull @Valid Draft draft) {}
    public record ConfirmRequest(@Positive int revision, @NotBlank @Size(max = 64) String previewToken) {}
    public record ReceiptRequest(@Positive int revision, @PastOrPresent LocalDate receivedOn) {}

    public record Shipment(String key, String taskNo, LocalDate shippingDate, String orderNo,
            String developmentNo, String colorMaterial, int quantity, BigDecimal unitPrice, BigDecimal amount,
            String priceStatus, Long quotationId, BigDecimal quotedPrice) {}

    public record Refund(long originId, String originMonth, String dueMonth, BigDecimal remaining, BigDecimal amount) {}

    public record Totals(int shippedPairs, BigDecimal goods, BigDecimal carryover, BigDecimal holdBase,
            BigDecimal hold, BigDecimal refund, BigDecimal materials, BigDecimal credits, BigDecimal debits,
            BigDecimal receivable) {}

    public record Snapshot(List<Shipment> current, List<Shipment> pending, List<Shipment> carryovers,
            List<Refund> refunds, Totals totals) {}

    public record View(String month, int revision, LocalDateTime confirmedAt, LocalDate receivedOn,
            Draft draft, Snapshot statement, String previewToken) {}
}
