package com.shoefactory.assistant.service;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnpricedShipmentServiceTest {
    @Mock LedgerDataService ledger;
    @Mock CustomerConfigService customers;
    private UnpricedShipmentService service;
    private CustomerConfig customer;
    private LedgerDataService.Data data;

    @BeforeEach void prepare() {
        service = new UnpricedShipmentService(ledger, customers);
        customer = new CustomerConfig();
        customer.setId(1L); customer.setName("达为");
        OrderRecord order = new OrderRecord();
        order.setId(10L); order.setCustomerId(1L); order.setOrderNo("DW-001");
        data = new LedgerDataService.Data(List.of(order),
                List.of(detail(11L, "JCD-162-15-03", 100), detail(12L, "JCD-162-15-03", 200),
                        detail(13L, "JCD-205-1-34", 60), detail(14L, "JCD-205-1-34", 40),
                        detail(15L, "JCD-379-1-12", 80), detail(16L, "JCD-172-63-01", 20),
                        detail(17L, "JCD-361-1-12", 50)),
                List.of(task(20L, "2026-10-02"), task(21L, "2026-09-30"), task(22L, "2025-10-01")),
                List.of(item(30L, 20L, 11L), item(31L, 20L, 12L), item(32L, 20L, 13L),
                        item(33L, 21L, 14L), item(34L, 20L, 15L), item(35L, 20L, 16L), item(36L, 22L, 14L)),
                Map.of(15L, new BigDecimal("20"), 16L, BigDecimal.ZERO));
    }

    @Test void monthsDeduplicateStylesKeepAllPairsAndSeparateYearsByActualShippingMonth() {
        when(ledger.load(null)).thenReturn(data);
        when(customers.list()).thenReturn(List.of(customer));
        var result = service.listMonths(null);
        assertEquals(List.of("2026-10", "2026-09", "2025-10"), result.stream().map(row -> row.month()).toList());
        assertEquals(2, result.get(0).missingPriceCount());
        assertEquals(2, result.get(0).mainStyleCount());
        assertEquals(360, result.get(0).shippingQuantity());
        assertEquals(1, result.get(1).missingPriceCount());
        verify(ledger).load(null);
    }

    @Test void monthGroupsMatchTheExampleAndKeepSourceContextForEachRepeatedStyle() {
        when(customers.require(1L)).thenReturn(customer);
        when(customers.list()).thenReturn(List.of(customer));
        when(ledger.load(1L)).thenReturn(data);
        var result = service.getMonth("2026-10", 1L);
        assertEquals(2, result.missingPriceCount());
        assertEquals(List.of("162", "205"), result.groups().stream().map(group -> group.mainStyle()).toList());
        assertEquals(1, result.groups().get(0).missingPriceCount());
        assertEquals(1, result.groups().get(1).missingPriceCount());
        assertEquals(300, result.groups().get(0).shippingQuantity());
        var styles = result.groups().get(0).styles();
        assertEquals(1, styles.size());
        assertEquals("JCD-162-15-03", styles.get(0).developmentNo());
        assertEquals(300, styles.get(0).shippingQuantity());
        var rows = styles.get(0).details();
        assertEquals(List.of(30L, 31L), rows.stream().map(row -> row.taskItemId()).toList());
        assertEquals("JCD-162-15-03", rows.get(0).developmentNo());
        assertEquals("达为", rows.get(0).customerName());
        assertEquals(1L, rows.get(0).customerId());
        assertEquals("DW-001", rows.get(0).orderNo());
        assertEquals("SH-20", rows.get(0).taskNo());
        assertEquals("黑色 PU", rows.get(0).colorMaterial());
        verify(customers).require(1L);
        verify(ledger).load(1L);
    }

    @Test void adoptingCurrentPricesRemovesHistoricalMissingRecordsAndZeroPricesAreComplete() {
        when(customers.list()).thenReturn(List.of(customer));
        Map<Long, BigDecimal> prices = new HashMap<>(data.pricesByDetailId());
        prices.put(11L, BigDecimal.ZERO); prices.put(12L, new BigDecimal("15"));
        when(ledger.load(null)).thenReturn(new LedgerDataService.Data(data.orders(), data.details(), data.tasks(), data.items(), prices));
        var result = service.getMonth("2026-10", null);
        assertEquals(1, result.missingPriceCount());
        assertEquals("205", result.groups().get(0).mainStyle());
        assertEquals(60, result.shippingQuantity());
    }

    @Test void excludesUnshippedOrBrokenReferencesAndUsesLegacyCreationDateFallback() {
        when(customers.list()).thenReturn(List.of(customer));
        var legacyTask = task(23L, null);
        legacyTask.setCreatedAt(LocalDateTime.of(2026, 8, 31, 23, 0));
        var undatedTask = task(24L, null);
        var zeroDetail = detail(18L, "JCD-999-1-01", 0);
        var items = new ArrayList<>(data.items());
        items.add(item(37L, 23L, 14L)); items.add(item(38L, 999L, 11L));
        items.add(item(39L, 20L, 999L)); items.add(item(40L, 24L, 14L)); items.add(item(41L, 20L, 18L));
        var tasks = new ArrayList<>(data.tasks()); tasks.add(legacyTask); tasks.add(undatedTask);
        var details = new ArrayList<>(data.details()); details.add(zeroDetail);
        when(ledger.load(null)).thenReturn(new LedgerDataService.Data(data.orders(), details, tasks, items, data.pricesByDetailId()));
        var months = service.listMonths(null);
        assertEquals(List.of("2026-10", "2026-09", "2026-08", "2025-10"), months.stream().map(row -> row.month()).toList());
        assertEquals(2, months.get(0).missingPriceCount());
        assertEquals(40, months.get(2).shippingQuantity());
    }

    @Test void keepsAllCustomerContextAndSupportsNumericAndBlankManualStyles() {
        var secondCustomer = new CustomerConfig(); secondCustomer.setId(2L); secondCustomer.setName("宝翔");
        var order = new OrderRecord(); order.setId(50L); order.setCustomerId(2L);
        var manual = detail(51L, "1001", 75); manual.setOrderId(50L);
        var blank = detail(52L, "", 25); blank.setOrderId(50L);
        var letterStyle = detail(53L, " jcd-162A-15-03 ", 55); letterStyle.setOrderId(50L);
        var task = task(60L, "2026-10-05"); task.setCustomerId(2L);
        when(customers.list()).thenReturn(List.of(customer, secondCustomer));
        when(ledger.load(null)).thenReturn(new LedgerDataService.Data(List.of(order), List.of(manual, blank, letterStyle), List.of(task),
                List.of(item(70L, 60L, 51L), item(71L, 60L, 52L), item(72L, 60L, 53L)), Map.of()));
        var result = service.getMonth("2026-10", null);
        assertEquals(Set.of("1001", "未填款号", "162A"), new HashSet<>(result.groups().stream().map(group -> group.mainStyle()).toList()));
        assertTrue(result.groups().stream().flatMap(group -> group.styles().stream()).allMatch(row -> "宝翔".equals(row.customerName()) && row.customerId() == 2L));
    }

    @Test void deduplicatesNormalizedStylesAcrossOrdersAndShipmentDatesWithinMonth() {
        var anotherOrder = new OrderRecord(); anotherOrder.setId(50L); anotherOrder.setCustomerId(1L);
        var sameStyle = detail(51L, " jcd－162－15－03 ", 25); sameStyle.setOrderId(50L);
        var differentStyle = detail(52L, "JCD-162-15-04", 35); differentStyle.setOrderId(50L);
        var laterTask = task(60L, "2026-10-08");
        var orders = new ArrayList<>(data.orders()); orders.add(anotherOrder);
        var details = new ArrayList<>(data.details()); details.add(sameStyle); details.add(differentStyle);
        var tasks = new ArrayList<>(data.tasks()); tasks.add(laterTask);
        var items = new ArrayList<>(data.items()); items.add(item(70L, 60L, 51L)); items.add(item(71L, 60L, 52L));
        when(customers.list()).thenReturn(List.of(customer));
        when(ledger.load(null)).thenReturn(new LedgerDataService.Data(orders, details, tasks, items, data.pricesByDetailId()));
        var month = service.getMonth("2026-10", null);
        var group = month.groups().get(0);
        assertEquals("162", group.mainStyle());
        assertEquals(3, month.missingPriceCount());
        assertEquals(2, group.missingPriceCount());
        assertEquals(360, group.shippingQuantity());
        assertEquals(2, group.styles().size());
        var first = group.styles().get(0);
        assertEquals("JCD-162-15-03", first.developmentNo());
        assertEquals(325, first.shippingQuantity());
        assertEquals(3, first.details().size());
        assertEquals(3, service.listMonths(null).get(0).missingPriceCount());
    }

    @Test void differentCustomersWithTheSameStyleRemainSeparatePricingEntries() {
        var anotherCustomer = new CustomerConfig(); anotherCustomer.setId(2L); anotherCustomer.setName("宝翔");
        var anotherOrder = new OrderRecord(); anotherOrder.setId(50L); anotherOrder.setCustomerId(2L);
        var sameStyle = detail(51L, "JCD-162-15-03", 25); sameStyle.setOrderId(50L);
        var otherTask = task(60L, "2026-10-08"); otherTask.setCustomerId(2L);
        var orders = new ArrayList<>(data.orders()); orders.add(anotherOrder);
        var details = new ArrayList<>(data.details()); details.add(sameStyle);
        var tasks = new ArrayList<>(data.tasks()); tasks.add(otherTask);
        var items = new ArrayList<>(data.items()); items.add(item(70L, 60L, 51L));
        when(customers.list()).thenReturn(List.of(customer, anotherCustomer));
        when(ledger.load(null)).thenReturn(new LedgerDataService.Data(orders, details, tasks, items, data.pricesByDetailId()));
        var month = service.getMonth("2026-10", null);
        var styles = month.groups().get(0).styles();
        assertEquals(3, month.missingPriceCount());
        assertEquals(2, styles.size());
        assertEquals(List.of(1L, 2L), styles.stream().map(row -> row.customerId()).toList());
        assertEquals(List.of(300, 25), styles.stream().map(row -> row.shippingQuantity()).toList());
        assertEquals(3, service.listMonths(null).get(0).missingPriceCount());
    }

    @Test void emptyMonthsAndInvalidMonthInputAreHandled() {
        when(ledger.load(null)).thenReturn(data);
        when(customers.list()).thenReturn(List.of(customer));
        assertTrue(service.getMonth("2026-11", null).groups().isEmpty());
        assertEquals(0, service.getMonth("2026-11", null).missingPriceCount());
        assertThrows(BusinessException.class, () -> service.getMonth("2026-13", null));
        assertThrows(BusinessException.class, () -> service.getMonth("2026-1", null));
    }

    @Test void findsImagesInOtherOrdersForTheSameNormalizedStyleWithoutChangingShipmentTotals() {
        var olderImage = data.details().get(0);
        olderImage.setStyleImagePath("/files/older-shoe.jpeg");
        olderImage.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
        data.details().get(1).setCreatedAt(LocalDateTime.of(2026, 10, 2, 0, 0));
        var referenceOrder = new OrderRecord(); referenceOrder.setId(50L); referenceOrder.setCustomerId(1L);
        var reference = detail(51L, " jcd－162－15－03 ", 999); reference.setOrderId(50L);
        reference.setStyleImagePath("/files/reference-shoe.jpeg");
        reference.setCreatedAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        var orders = new ArrayList<>(data.orders()); orders.add(referenceOrder);
        var details = new ArrayList<>(data.details()); details.add(reference);
        when(customers.list()).thenReturn(List.of(customer));
        when(ledger.load(null)).thenReturn(new LedgerDataService.Data(orders, details, data.tasks(), data.items(), data.pricesByDetailId()));

        var month = service.getMonth("2026-10", null);
        var style = month.groups().get(0).styles().get(0);
        assertEquals("/api/orders/details/51/image", style.imageUrl());
        assertEquals(2, month.missingPriceCount());
        assertEquals(300, style.shippingQuantity());
        assertEquals(2, style.details().size());
    }

    @Test void choosesTheLatestAvailableImageAndIgnoresBlankImagePaths() {
        var older = data.details().get(0);
        older.setStyleImagePath("/files/older.jpeg");
        older.setCreatedAt(LocalDateTime.of(2026, 9, 1, 0, 0));
        var newer = data.details().get(1);
        newer.setStyleImagePath("/files/newer.jpeg");
        newer.setCreatedAt(LocalDateTime.of(2026, 9, 20, 0, 0));
        data.details().get(2).setStyleImagePath("   ");
        when(customers.list()).thenReturn(List.of(customer));
        when(ledger.load(null)).thenReturn(data);

        var month = service.getMonth("2026-10", null);
        assertEquals("/api/orders/details/12/image", month.groups().get(0).styles().get(0).imageUrl());
        assertNull(month.groups().get(1).styles().get(0).imageUrl());
    }

    @Test void neverUsesAnotherCustomersImageOrAnotherFullStyleOrAnUnidentifiedStyle() {
        var anotherCustomer = new CustomerConfig(); anotherCustomer.setId(2L); anotherCustomer.setName("宝翔");
        var anotherOrder = new OrderRecord(); anotherOrder.setId(50L); anotherOrder.setCustomerId(2L);
        var otherCustomerStyle = detail(51L, "JCD-162-15-03", 25); otherCustomerStyle.setOrderId(50L);
        otherCustomerStyle.setStyleImagePath("/files/other-customer.jpeg");
        var differentStyle = detail(52L, "JCD-162-15-04", 50); differentStyle.setStyleImagePath("/files/different-style.jpeg");
        var unidentified = detail(53L, "", 10); unidentified.setStyleImagePath("/files/unidentified.jpeg");
        var blank = detail(54L, "", 20);
        var otherTask = task(60L, "2026-10-08"); otherTask.setCustomerId(2L);
        var orders = new ArrayList<>(data.orders()); orders.add(anotherOrder);
        var details = new ArrayList<>(data.details()); details.addAll(List.of(otherCustomerStyle, differentStyle, unidentified, blank));
        var tasks = new ArrayList<>(data.tasks()); tasks.add(otherTask);
        var items = new ArrayList<>(data.items()); items.add(item(70L, 60L, 51L)); items.add(item(71L, 20L, 54L));
        when(customers.list()).thenReturn(List.of(customer, anotherCustomer));
        when(ledger.load(null)).thenReturn(new LedgerDataService.Data(orders, details, tasks, items, data.pricesByDetailId()));

        var month = service.getMonth("2026-10", null);
        var styles = month.groups().stream().filter(group -> group.mainStyle().equals("162")).findFirst().orElseThrow().styles();
        assertNull(styles.get(0).imageUrl());
        assertEquals("/api/orders/details/51/image", styles.get(1).imageUrl());
        assertNull(month.groups().stream().filter(group -> group.mainStyle().equals("未填款号")).findFirst().orElseThrow().styles().get(0).imageUrl());
    }

    private OrderRecordDetail detail(Long id, String style, int quantity) {
        var detail = new OrderRecordDetail(); detail.setId(id); detail.setOrderId(10L);
        detail.setDevelopmentNo(style); detail.setQuantity(quantity); detail.setUpperMaterial("黑色 PU"); return detail;
    }

    private ShippingNoteTask task(Long id, String date) {
        var task = new ShippingNoteTask(); task.setId(id); task.setCustomerId(1L); task.setTaskNo("SH-" + id);
        if (date != null) task.setShippingDate(LocalDate.parse(date));
        return task;
    }

    private ShippingNoteTaskItem item(Long id, Long taskId, Long detailId) {
        var item = new ShippingNoteTaskItem(); item.setId(id); item.setTaskId(taskId); item.setSourceDetailId(detailId); return item;
    }
}
