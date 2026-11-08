package com.shoefactory.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.MonthlyReconciliationResponse;
import com.shoefactory.assistant.dto.MonthlySettlementData.*;
import com.shoefactory.assistant.dto.UnpricedShipmentResponse.QuotationSummary;
import com.shoefactory.assistant.entity.CustomerConfig;
import com.shoefactory.assistant.entity.MonthlySettlement;
import com.shoefactory.assistant.mapper.CustomerConfigMapper;
import com.shoefactory.assistant.mapper.MonthlySettlementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MonthlySettlementServiceTest {
    private static final long CUSTOMER = 2L;
    private static final String SEPTEMBER = "2026-09";
    private static final String OCTOBER = "2026-10";
    private static final String NOVEMBER = "2026-11";
    private static final String UNPRICED_STYLE = "JCD-200-1-01";

    private final Map<String, MonthlySettlement> rows = new TreeMap<>();
    private final Map<String, MonthlyReconciliationResponse> shipments = new HashMap<>();
    private final CustomerPriceService prices = mock(CustomerPriceService.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private MonthlySettlementService service;

    @BeforeEach void setup() {
        CustomerConfig customer = new CustomerConfig();
        customer.setId(CUSTOMER);
        customer.setCode("DAWEI");
        var customerLocks = mock(CustomerConfigMapper.class);
        var customers = mock(CustomerConfigService.class);
        var bills = mock(MonthlySettlementMapper.class);
        var reconciliation = mock(MonthlyReconciliationService.class);
        var unpriced = mock(UnpricedShipmentService.class);
        when(customers.require(CUSTOMER)).thenReturn(customer);
        when(customerLocks.selectForUpdate(CUSTOMER)).thenReturn(customer);
        when(bills.selectList(any())).thenAnswer(call -> new ArrayList<>(rows.values()));
        when(bills.insert(any(MonthlySettlement.class))).thenAnswer(call -> {
            MonthlySettlement row = call.getArgument(0);
            ReflectionTestUtils.setField(row, "id", rows.size() + 1L);
            rows.put(row.getBillMonth(), row);
            return 1;
        });
        when(bills.updateById(any(MonthlySettlement.class))).thenReturn(1);
        when(reconciliation.getMonthlyReconciliation(anyString(), eq(CUSTOMER)))
                .thenAnswer(call -> shipments.getOrDefault(call.getArgument(0), new MonthlyReconciliationResponse()));
        var quote = new QuotationSummary(7L, 1, amount("80.00"), LocalDate.of(2026, 9, 5), null, null);
        when(unpriced.priceProgress(eq(CUSTOMER), anyCollection()))
                .thenReturn(Map.of(UNPRICED_STYLE, new UnpricedShipmentService.QuotationHistory(quote, quote)));
        service = new MonthlySettlementService(bills, customerLocks, customers, reconciliation, prices, unpriced, json);

        shipments.put(SEPTEMBER, reconciliation(shipment(1L, 11L, "2026-09-03", "JCD-200-1-02", 50, "100.00")));
    }

    @Test void anUnapprovedQuoteStaysPendingThenTheShipmentCanBeSettledOnce() {
        shipments.put(SEPTEMBER, reconciliation(shipments.get(SEPTEMBER).getDetails().get(0),
                shipment(1L, 12L, "2026-09-03", UNPRICED_STYLE, 100, null)));
        View saved = service.save(SEPTEMBER, CUSTOMER, new SaveRequest(0, service.get(SEPTEMBER, CUSTOMER).draft()));
        var rejected = assertThrows(BusinessException.class, () -> service.confirm(SEPTEMBER, CUSTOMER,
                new ConfirmRequest(saved.revision(), saved.previewToken())));
        assertTrue(rejected.getMessage().contains("1 笔出货未确认客户单价"));
        assertNull(service.get(SEPTEMBER, CUSTOMER).confirmedAt());
        Draft initial = saved.draft();
        View september = close(SEPTEMBER, new Draft(initial.preparedOn(), initial.holdRate(), initial.holdBase(),
                List.of("1:12"), initial.carryKeys(), initial.materials(), initial.adjustments(), initial.refunds(), initial.historicalRefunds(), List.of()));
        assertEquals(amount("5000.00"), september.statement().totals().goods());
        assertEquals(150, september.statement().totals().shippedPairs());
        var unapproved = september.statement().pending().get(0);
        assertEquals("QUOTED", unapproved.priceStatus());
        assertEquals(amount("80.00"), unapproved.quotedPrice());
        assertNull(unapproved.unitPrice());
        assertEquals("1:12", service.get(OCTOBER, CUSTOMER).statement().pending().get(0).key());

        when(prices.prices()).thenReturn(Map.of(CustomerPriceService.key(CUSTOMER, UNPRICED_STYLE), amount("80.00")));
        View october = service.get(OCTOBER, CUSTOMER);
        assertEquals(amount("0.00"), october.statement().totals().carryover());
        assertEquals(unapproved.key(), october.statement().carryovers().get(0).key());
        Draft octDraft = october.draft();
        View closedOctober = close(OCTOBER, new Draft(octDraft.preparedOn(), octDraft.holdRate(), null,
                List.of(), List.of(unapproved.key()), List.of(), List.of(), List.of(), List.of(), List.of()));
        assertEquals(amount("8000.00"), closedOctober.statement().totals().carryover());
        assertEquals(0, closedOctober.statement().totals().shippedPairs());
        assertTrue(service.get(NOVEMBER, CUSTOMER).statement().carryovers().isEmpty());

        shipments.put(SEPTEMBER, reconciliation(shipment(1L, 12L, "2026-09-03", UNPRICED_STYLE, 100, "90.00")));
        assertEquals(september.statement(), service.get(SEPTEMBER, CUSTOMER).statement());
    }

    @Test void aShipmentWithoutAQuoteRequiresApprovalOrDeferralBeforeClosing() {
        String style = "JCD-200-9-01";
        shipments.put(SEPTEMBER, reconciliation(shipment(1L, 13L, "2026-09-03", style, 10, null)));
        View saved = service.save(SEPTEMBER, CUSTOMER, new SaveRequest(0, service.get(SEPTEMBER, CUSTOMER).draft()));
        assertEquals("NOT_QUOTED", saved.statement().pending().get(0).priceStatus());
        assertNull(saved.statement().pending().get(0).quotedPrice());
        assertThrows(BusinessException.class, () -> service.confirm(SEPTEMBER, CUSTOMER,
                new ConfirmRequest(saved.revision(), saved.previewToken())));

        shipments.put(SEPTEMBER, reconciliation(shipment(1L, 13L, "2026-09-03", style, 10, "73.00")));
        View refreshed = service.get(SEPTEMBER, CUSTOMER);
        View confirmed = service.confirm(SEPTEMBER, CUSTOMER, new ConfirmRequest(refreshed.revision(), refreshed.previewToken()));
        assertNotNull(confirmed.confirmedAt());
        assertEquals(amount("730.00"), confirmed.statement().totals().goods());
    }

    @Test void holdIsDueTwoBillMonthsLaterAndPartialRefundLeavesABalance() {
        View september = close(SEPTEMBER, service.get(SEPTEMBER, CUSTOMER).draft());
        assertEquals(amount("500.00"), september.statement().totals().hold());
        assertTrue(service.get(OCTOBER, CUSTOMER).statement().refunds().isEmpty());
        View november = service.get(NOVEMBER, CUSTOMER);
        Refund due = november.statement().refunds().get(0);
        assertEquals(SEPTEMBER, due.originMonth());
        assertEquals(NOVEMBER, due.dueMonth());
        assertEquals(amount("500.00"), due.remaining());
        Draft original = november.draft();
        View closed = close(NOVEMBER, new Draft(original.preparedOn(), original.holdRate(), null, List.of(), List.of(),
                List.of(), List.of(), List.of(new RefundInput(due.originId(), amount("300.00"))), List.of(), List.of()));
        assertEquals(amount("300.00"), closed.statement().totals().refund());
        assertEquals(amount("200.00"), service.get("2026-12", CUSTOMER).statement().refunds().get(0).remaining());
    }

    @Test void omittingARefundSkipsThisBillWithoutRecordingPayment() {
        close(SEPTEMBER, service.get(SEPTEMBER, CUSTOMER).draft());
        View november = service.get(NOVEMBER, CUSTOMER);
        long originId = november.statement().refunds().get(0).originId();
        Draft original = november.draft();
        Draft conflicting = new Draft(original.preparedOn(), original.holdRate(), null, List.of(), List.of(), List.of(), List.of(),
                List.of(new RefundInput(originId, amount("100.00"))), List.of(), List.of(originId));
        assertThrows(BusinessException.class, () -> service.save(NOVEMBER, CUSTOMER, new SaveRequest(0, conflicting)));

        Draft omitted = new Draft(original.preparedOn(), original.holdRate(), null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(originId));
        View closed = close(NOVEMBER, omitted);
        assertEquals(List.of(originId), closed.draft().omittedRefundIds());
        assertEquals(amount("0.00"), closed.statement().totals().refund());
        assertEquals(amount("500.00"), service.get("2026-12", CUSTOMER).statement().refunds().get(0).remaining());
    }

    @Test void confirmationRejectsAChangedPriceAndRequiresReviewingTheFreshPreview() {
        View saved = service.save(SEPTEMBER, CUSTOMER, new SaveRequest(0, service.get(SEPTEMBER, CUSTOMER).draft()));
        shipments.put(SEPTEMBER, reconciliation(shipment(1L, 11L, "2026-09-03", "JCD-200-1-02", 50, "102.00")));
        assertThrows(BusinessException.class, () -> service.confirm(SEPTEMBER, CUSTOMER,
                new ConfirmRequest(saved.revision(), saved.previewToken())));
        View refreshed = service.get(SEPTEMBER, CUSTOMER);
        View confirmed = service.confirm(SEPTEMBER, CUSTOMER, new ConfirmRequest(refreshed.revision(), refreshed.previewToken()));
        assertEquals(amount("5100.00"), confirmed.statement().totals().goods());
    }

    @Test void identicalSourcesInOneShippingTaskCountOnce() {
        var original = shipments.get(SEPTEMBER).getDetails().get(0);
        var anotherBatch = shipment(3L, 11L, "2026-09-13", "JCD-200-1-02", 50, "100.00");
        shipments.put(SEPTEMBER, reconciliation(original, original, anotherBatch));
        var preview = service.get(SEPTEMBER, CUSTOMER).statement();
        assertEquals(2, preview.current().size());
        assertEquals(100, preview.totals().shippedPairs());
        assertEquals(amount("10000.00"), preview.totals().goods());
    }

    @Test void adjustmentsAndMaterialRoundingAreIncludedInTheLockedTotal() {
        Draft draft = service.get(SEPTEMBER, CUSTOMER).draft();
        View confirmed = close(SEPTEMBER, new Draft(draft.preparedOn(), draft.holdRate(), amount("4000"), List.of(), List.of(),
                List.of(new Material(LocalDate.of(2026, 9, 1), "贴标", amount("3.5000"), "卷", amount("1.2300"), "MAT-1")),
                List.of(new Adjustment(AdjustmentType.CREDIT, "退回材料", amount("20.00")),
                        new Adjustment(AdjustmentType.DEBIT, "打天皮", amount("10.00"))), List.of(), List.of(), List.of()));
        assertEquals(amount("4.31"), confirmed.statement().totals().materials());
        assertEquals(amount("4605.69"), confirmed.statement().totals().receivable());
    }

    @Test void importedLegacyRefundAppearsUnderDepositsAndReducesTheAvailableBalance() throws Exception {
        close(SEPTEMBER, service.get(SEPTEMBER, CUSTOMER).draft());
        Draft november = service.get(NOVEMBER, CUSTOMER).draft();
        service.save(NOVEMBER, CUSTOMER, new SaveRequest(0, november));
        writeLegacyAdjustment(NOVEMBER, new Adjustment(AdjustmentType.CREDIT, "退料", amount("20.00")));

        View preview = service.get(NOVEMBER, CUSTOMER);
        assertEquals(List.of(new HistoricalRefund(SEPTEMBER, amount("300.00"), "原 Excel 2026-11 11月对账单!I99")),
                preview.draft().historicalRefunds());
        assertEquals(1, preview.draft().adjustments().size());
        assertEquals("退料", preview.draft().adjustments().get(0).reason());
        assertEquals(0, amount("20.00").compareTo(preview.draft().adjustments().get(0).amount()));
        assertEquals(amount("300.00"), preview.statement().totals().refund());
        assertEquals(amount("320.00"), preview.statement().totals().receivable());

        Draft draft = preview.draft();
        long originId = preview.statement().refunds().get(0).originId();
        var overpayment = new Draft(draft.preparedOn(), draft.holdRate(), draft.holdBase(), draft.deferredKeys(), draft.carryKeys(),
                draft.materials(), draft.adjustments(), List.of(new RefundInput(originId, amount("200.01"))), draft.historicalRefunds(), List.of());
        assertThrows(BusinessException.class, () -> service.save(NOVEMBER, CUSTOMER, new SaveRequest(preview.revision(), overpayment)));
        service.confirm(NOVEMBER, CUSTOMER, new ConfirmRequest(preview.revision(), preview.previewToken()));

        assertEquals(amount("300.00"), service.get(NOVEMBER, CUSTOMER).statement().totals().refund());
        assertEquals(amount("200.00"), service.get("2026-12", CUSTOMER).statement().refunds().get(0).remaining());
        assertTrue(json.readTree(rows.get(NOVEMBER).getDraftJson()).get("historicalRefunds").isArray());
    }

    @Test void viewingAnAlreadyConfirmedLegacyRefundPreservesTheLockedReceivable() throws Exception {
        close(SEPTEMBER, service.get(SEPTEMBER, CUSTOMER).draft());
        Draft november = service.get(NOVEMBER, CUSTOMER).draft();
        var original = new Draft(november.preparedOn(), november.holdRate(), null, List.of(), List.of(), List.of(),
                List.of(new Adjustment(AdjustmentType.CREDIT, "旧版以其他加款存入", amount("300.00"))), List.of(), List.of(), List.of());
        View locked = close(NOVEMBER, original);
        writeLegacyAdjustment(NOVEMBER);
        String originalSnapshot = rows.get(NOVEMBER).getSnapshotJson();

        View readback = service.get(NOVEMBER, CUSTOMER);
        assertEquals(amount("300.00"), readback.statement().totals().refund());
        assertEquals(amount("0.00"), readback.statement().totals().credits());
        assertEquals(locked.statement().totals().receivable(), readback.statement().totals().receivable());
        assertEquals(originalSnapshot, rows.get(NOVEMBER).getSnapshotJson());
        assertEquals(amount("200.00"), service.get("2026-12", CUSTOMER).statement().refunds().get(0).remaining());
    }

    private void writeLegacyAdjustment(String month, Adjustment... other) throws Exception {
        MonthlySettlement row = rows.get(month);
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(row.getDraftJson());
        legacy.remove("historicalRefunds");
        List<Adjustment> adjustments = new ArrayList<>(List.of(other));
        adjustments.add(new Adjustment(AdjustmentType.CREDIT, "历史质保金返还｜2026-09（原 Excel 2026-11 11月对账单!I99）", amount("300.00")));
        legacy.set("adjustments", json.valueToTree(adjustments));
        row.setDraftJson(json.writeValueAsString(legacy));
    }

    private View close(String month, Draft draft) {
        View saved = service.save(month, CUSTOMER, new SaveRequest(service.get(month, CUSTOMER).revision(), draft));
        return service.confirm(month, CUSTOMER, new ConfirmRequest(saved.revision(), saved.previewToken()));
    }

    private static MonthlyReconciliationResponse reconciliation(MonthlyReconciliationResponse.Detail... lines) {
        var response = new MonthlyReconciliationResponse();
        response.setDetails(List.of(lines));
        response.setTotalShippingQuantity(Arrays.stream(lines).mapToInt(MonthlyReconciliationResponse.Detail::getShippingQuantity).sum());
        return response;
    }

    private static MonthlyReconciliationResponse.Detail shipment(long taskId, long sourceId, String day, String style, int quantity, String price) {
        var row = new MonthlyReconciliationResponse.Detail();
        row.setShippingTaskId(taskId);
        row.setSourceDetailId(sourceId);
        row.setShippingDate(LocalDate.parse(day));
        row.setDevelopmentNo(style);
        row.setShippingQuantity(quantity);
        if (price != null) {
            row.setUnitPrice(amount(price));
            row.setAmount(amount(price).multiply(BigDecimal.valueOf(quantity)));
        }
        return row;
    }

    private static BigDecimal amount(String value) { return new BigDecimal(value); }
}
