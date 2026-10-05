package com.shoefactory.assistant.service;

import com.shoefactory.assistant.entity.*;
import com.shoefactory.assistant.service.impl.MonthlyReconciliationServiceImpl;
import com.shoefactory.assistant.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiCustomerLedgerTest {
    @Mock LedgerDataService ledger;
    @InjectMocks OrderServiceImpl statistics;
    @Mock CustomerConfigService customers;
    private MonthlyReconciliationServiceImpl reconciliation;
    private CustomerConfig customer;
    private LedgerDataService.Data data;

    @BeforeEach void prepare() {
        reconciliation=new MonthlyReconciliationServiceImpl(ledger,customers);
        customer=new CustomerConfig();customer.setId(2L);customer.setName("宝翔");customer.setRecipientName("宝翔");
        OrderRecord order=new OrderRecord();order.setId(10L);order.setCustomerId(2L);
        order.setOrderNo("B-001");order.setTotalQuantity(120);order.setOrderDate(LocalDate.of(2026,9,1));
        OrderRecordDetail first=detail(11L,"1001",100),second=detail(12L,"8801",20);
        data=new LedgerDataService.Data(List.of(order),List.of(first,second),
                List.of(task(20L,"2026-09-20"),task(21L,"2026-10-02")),
                List.of(item(30L,20L,11L),item(31L,21L,12L)),
                Map.of(11L,new BigDecimal("20"),12L,new BigDecimal("5")));
        when(ledger.load(2L)).thenReturn(data);
    }
    @Test void statisticsUsesEntireSourceQuantitiesAndCurrentCustomerPrices() {
        var result=statistics.getOrderStatistics(2L);
        assertEquals(120,result.getTotalPairs());assertEquals(120,result.getShippedPairs());assertEquals(0,result.getUnshippedPairs());
        assertEquals(0,result.getTotalPaymentAmount().compareTo(new BigDecimal("2100")));
        assertEquals(100,result.getShippedPairsTrend().get(0).getValue());
        assertEquals(20,result.getShippedPairsTrend().get(1).getValue());
        assertTrue(result.getDevelopmentNoTree().stream().anyMatch(node->"1001".equals(node.getLabel())));
        verify(ledger).load(2L);
    }
    @Test void reconciliationUsesTheConfirmationMonthForEachSelectedLine() {
        when(customers.require(2L)).thenReturn(customer);
        var sep=reconciliation.getMonthlyReconciliation("2026-09",2L);
        var oct=reconciliation.getMonthlyReconciliation("2026-10",2L);
        assertEquals(100,sep.getTotalShippingQuantity());assertEquals(20,oct.getTotalShippingQuantity());
        assertEquals(100,sep.getTotalOrderQuantity());assertEquals(20,oct.getTotalOrderQuantity());
        assertEquals(0,sep.getGoodsAmount().compareTo(new BigDecimal("2000.00")));
        assertEquals(0,oct.getGoodsAmount().compareTo(new BigDecimal("100.00")));
        assertEquals(0,reconciliation.getMonthlyReconciliation("2026-11",2L).getTotalShippingQuantity());
    }
    @Test void changingConfigurationRepricesHistoricalStatisticsAndReconciliations() {
        when(customers.require(2L)).thenReturn(customer);
        assertEquals(0,reconciliation.getMonthlyReconciliation("2026-09",2L).getGoodsAmount().compareTo(new BigDecimal("2000")));
        when(ledger.load(2L)).thenReturn(new LedgerDataService.Data(data.orders(),data.details(),data.tasks(),data.items(),
                Map.of(11L,new BigDecimal("30"),12L,new BigDecimal("7"))));
        assertEquals(0,reconciliation.getMonthlyReconciliation("2026-09",2L).getGoodsAmount().compareTo(new BigDecimal("3000")));
        assertEquals(0,reconciliation.getMonthlyReconciliation("2026-10",2L).getGoodsAmount().compareTo(new BigDecimal("140")));
        assertEquals(0,statistics.getOrderStatistics(2L).getTotalPaymentAmount().compareTo(new BigDecimal("3140")));
    }
    @Test void clearingAPriceMarksHistoricalGoodsAsUnpriced() {
        when(customers.require(2L)).thenReturn(customer);
        when(ledger.load(2L)).thenReturn(new LedgerDataService.Data(data.orders(),data.details(),data.tasks(),data.items(),Map.of()));
        var result=reconciliation.getMonthlyReconciliation("2026-09",2L);
        assertEquals(1,result.getMissingPriceCount());assertNull(result.getDetails().get(0).getAmount());
        assertEquals(0,result.getGoodsAmount().compareTo(BigDecimal.ZERO));
        assertEquals(120,statistics.getOrderStatistics(2L).getUnpricedShippedPairs());
    }
    @Test void anExplicitZeroPriceIsNotAMissingPrice() {
        when(customers.require(2L)).thenReturn(customer);
        when(ledger.load(2L)).thenReturn(new LedgerDataService.Data(data.orders(),data.details(),data.tasks(),data.items(),
                Map.of(11L,BigDecimal.ZERO)));
        var result=reconciliation.getMonthlyReconciliation("2026-09",2L);
        assertEquals(0,result.getMissingPriceCount());assertNotNull(result.getDetails().get(0).getAmount());
        assertEquals(0,result.getGoodsAmount().compareTo(BigDecimal.ZERO));
    }
    @Test void differentLinesCanBeConfirmedInTheSameMonth() {
        when(customers.require(2L)).thenReturn(customer);
        data.tasks().get(1).setShippingDate(LocalDate.of(2026,9,25));
        var result=reconciliation.getMonthlyReconciliation("2026-09",2L);
        assertEquals(120,result.getTotalOrderQuantity());assertEquals(120,result.getTotalShippingQuantity());
        assertEquals(2,result.getDetails().size());
    }
    private OrderRecordDetail detail(Long id,String style,int quantity) {
        var detail=new OrderRecordDetail();detail.setId(id);detail.setOrderId(10L);
        detail.setDevelopmentNo(style);detail.setQuantity(quantity);return detail;
    }
    private ShippingNoteTask task(Long id,String date) {
        var task=new ShippingNoteTask();task.setId(id);task.setCustomerId(2L);task.setTaskNo("SH-"+id);
        task.setShippingDate(LocalDate.parse(date));return task;
    }
    private ShippingNoteTaskItem item(Long id,Long task,Long detail) {
        var item=new ShippingNoteTaskItem();item.setId(id);item.setTaskId(task);item.setOrderId(10L);item.setSourceDetailId(detail);return item;
    }
}
