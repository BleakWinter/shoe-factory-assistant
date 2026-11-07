package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.ShippingNoteItemRequest;
import com.shoefactory.assistant.dto.ShippingNoteUpdateRequest;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderDetailProcessMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskItemMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskMapper;
import com.shoefactory.assistant.service.CustomerConfigService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class ShippingNoteTaskServiceImplTest {

    private final ShippingNoteTaskMapper taskMapper = mock(ShippingNoteTaskMapper.class);
    private final ShippingNoteTaskItemMapper itemMapper = mock(ShippingNoteTaskItemMapper.class);
    private final OrderRecordDetailMapper detailMapper = mock(OrderRecordDetailMapper.class);
    private final OrderPackingDetailMapper packingMapper = mock(OrderPackingDetailMapper.class);
    private final OrderRecordMapper orderMapper = mock(OrderRecordMapper.class);
    private final OrderDetailProcessMapper processMapper = mock(OrderDetailProcessMapper.class);
    private final CustomerConfigService customers = mock(CustomerConfigService.class);
    private final ShippingNoteTaskServiceImpl service = new ShippingNoteTaskServiceImpl(
            taskMapper, itemMapper, detailMapper, packingMapper, orderMapper, processMapper, customers
    );
    private final List<ShippingNoteTaskItem> storedItems = new ArrayList<>();
    private ShippingNoteTask task;

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ShippingNoteTaskItem.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderDetailProcess.class);
    }

    @BeforeEach
    void setUp() {
        task = new ShippingNoteTask();
        task.setId(42L);
        task.setCustomerId(99L);
        task.setSourceType(1);
        task.setTaskNo("SH-ORIGINAL");
        task.setRecipientName("原收货单位");
        task.setShippingDate(LocalDate.of(2026, 9, 1));
        task.setInvoiceNos("INV-OLD");
        task.setDevelopmentNos("STYLE-OLD");
        task.setItemCount(1);
        task.setTotalPairs(12);
        task.setTotalCartonCount(1);
        task.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));
        task.setUpdatedAt(task.getCreatedAt());
        when(taskMapper.selectById(42L)).thenReturn(task);
    }

    @Test
    void replacesSelectedDetailsAndRefreshesSummaryAndPrintableItemsOnTheSameTask() {
        OrderRecordDetail first = detail(11L, 101L, "STYLE-A", 24, 2);
        OrderRecordDetail second = detail(22L, 202L, "STYLE-B", 36, 3);
        when(detailMapper.selectBatchIds(anyCollection())).thenReturn(List.of(first, second));
        when(orderMapper.selectBatchIds(anyCollection())).thenReturn(List.of(order(101L, "INV-A"), order(202L, "INV-B")));
        when(packingMapper.selectList(any())).thenReturn(List.of(packing(first), packing(second)));
        when(itemMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(storedItems));
        when(itemMapper.delete(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<?> filter = invocation.getArgument(0);
            assertTrue(filter.getSqlSegment().contains("task_id ="));
            assertEquals(List.of(42L), new ArrayList<>(filter.getParamNameValuePairs().values()));
            storedItems.clear();
            return 1;
        });
        when(itemMapper.insert(any(ShippingNoteTaskItem.class))).thenAnswer(invocation -> {
            storedItems.add(invocation.getArgument(0));
            return 1;
        });
        ShippingNoteTaskItem oldItem = new ShippingNoteTaskItem();
        oldItem.setSourceDetailId(99L);
        storedItems.add(oldItem);
        ShippingNoteUpdateRequest request = update(22L, 11L);
        request.setRecipientName("新收货单位");
        request.setShippingDate(LocalDate.of(2026, 10, 5));

        var response = service.updateShippingNoteTask(42L, request);

        assertEquals(42L, response.getId());
        assertEquals(1L, task.getCustomerId());
        assertEquals(1, task.getSourceType());
        assertEquals("SH-ORIGINAL", response.getTaskNo());
        assertEquals(LocalDateTime.of(2026, 9, 1, 9, 0), response.getCreatedAt());
        assertEquals("新收货单位", response.getRecipientName());
        assertEquals(LocalDate.of(2026, 10, 5), response.getShippingDate());
        assertEquals("INV-B,INV-A", response.getInvoiceNos());
        assertEquals("STYLE-B,STYLE-A", response.getDevelopmentNos());
        assertEquals(2, response.getItemCount());
        assertEquals(60, response.getTotalPairs());
        assertEquals(5, response.getTotalCartonCount());
        assertEquals(List.of(22L, 11L), response.getItems().stream().map(ShippingNoteItemRequest::getSourceDetailId).toList());
        assertEquals(List.of(202L, 101L), storedItems.stream().map(ShippingNoteTaskItem::getOrderId).toList());
        assertEquals(List.of(1, 2), storedItems.stream().map(ShippingNoteTaskItem::getLineNo).toList());
        assertTrue(storedItems.stream().allMatch(item -> item.getTaskId().equals(42L)));
        assertEquals(36, response.getItems().get(0).getPackingItems().get(0).getTotalPairs());
        assertTrue(task.getUpdatedAt().isAfter(task.getCreatedAt()));
        verify(taskMapper).updateById(task);
        verify(taskMapper, never()).insert(any(ShippingNoteTask.class));
        verifyNoInteractionsOnSourceWrites();
    }

    @Test
    void headerOnlyUpdateKeepsExistingDetailsAndTotals() {
        when(itemMapper.selectList(any())).thenReturn(List.of());
        ShippingNoteUpdateRequest request = new ShippingNoteUpdateRequest();
        request.setRecipientName("另一个收货单位");

        var response = service.updateShippingNoteTask(42L, request);

        assertEquals("另一个收货单位", response.getRecipientName());
        assertEquals(12, response.getTotalPairs());
        assertEquals("INV-OLD", response.getInvoiceNos());
        verify(itemMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(itemMapper, never()).insert(any(ShippingNoteTaskItem.class));
    }

    @Test
    void emptyDetailsCannotEraseTheExistingTask() {
        assertRejectedWithoutWrites(update());
    }

    @Test
    void duplicateDetailsCannotDoubleCountTheShipment() {
        assertRejectedWithoutWrites(update(11L, 11L));
    }

    @Test
    void aNullDetailCannotSilentlyRemoveAnExistingLine() {
        ShippingNoteUpdateRequest request = update(11L);
        request.setItems(java.util.Arrays.asList(request.getItems().get(0), null));
        assertRejectedWithoutWrites(request);
    }

    @Test
    void differentCustomersCannotBeCombinedWhenEditing() {
        OrderRecord firstOrder = order(101L, "INV-A");
        OrderRecord secondOrder = order(202L, "INV-B");
        secondOrder.setCustomerId(2L);
        when(detailMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                detail(11L, 101L, "STYLE-A", 24, 2), detail(22L, 202L, "STYLE-B", 36, 3)));
        when(orderMapper.selectBatchIds(anyCollection())).thenReturn(List.of(firstOrder, secondOrder));
        assertRejectedWithoutWrites(update(11L, 22L));
        assertEquals(99L, task.getCustomerId());
        verifyNoInteractions(packingMapper);
    }

    @Test
    void manualOrderDetailsCannotBeAddedToAPrintTask() {
        OrderRecord manualOrder = order(101L, "MANUAL-A");
        manualOrder.setSourceType(3);
        when(detailMapper.selectBatchIds(anyCollection())).thenReturn(List.of(detail(11L, 101L, "STYLE-A", 24, 2)));
        when(orderMapper.selectBatchIds(anyCollection())).thenReturn(List.of(manualOrder));
        assertRejectedWithoutWrites(update(11L));
        verifyNoInteractions(packingMapper);
    }

    @Test
    void manualShipmentCannotBeChangedThroughThePrintEditor() {
        task.setSourceType(3);
        assertRejectedWithoutWrites(update(11L));
        verifyNoInteractions(detailMapper, packingMapper, orderMapper);
    }

    @Test
    void missingSourceDetailKeepsTheExistingTask() {
        when(detailMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        assertRejectedWithoutWrites(update(999L));
    }

    @Test
    void unmatchedPackingDetailsKeepTheExistingTask() {
        when(detailMapper.selectBatchIds(anyCollection())).thenReturn(List.of(detail(11L, 101L, "STYLE-A", 24, 2)));
        when(orderMapper.selectBatchIds(anyCollection())).thenReturn(List.of(order(101L, "INV-A")));
        when(packingMapper.selectList(any())).thenReturn(List.of());
        assertRejectedWithoutWrites(update(11L));
    }

    @Test
    void unknownTaskCannotBeCreatedThroughUpdate() {
        assertThrows(BusinessException.class, () -> service.updateShippingNoteTask(999L, update(11L)));
        verify(taskMapper, never()).updateById(any(ShippingNoteTask.class));
        verifyNoInteractions(itemMapper, detailMapper, packingMapper, orderMapper);
    }

    @Test
    void deletingTaskRemovesOnlyItsShipmentItemsAndKeepsSourceOrders() {
        ShippingNoteTaskItem selected = new ShippingNoteTaskItem();
        selected.setTaskId(42L);
        selected.setSourceDetailId(11L);
        ShippingNoteTaskItem unrelated = new ShippingNoteTaskItem();
        unrelated.setTaskId(43L);
        unrelated.setSourceDetailId(22L);
        storedItems.addAll(List.of(selected, unrelated));
        when(itemMapper.selectList(any())).thenReturn(List.of(selected), List.of());
        when(itemMapper.delete(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<?> filter = invocation.getArgument(0);
            assertEquals("(task_id = #{ew.paramNameValuePairs.MPGENVAL1})", filter.getSqlSegment());
            assertEquals(List.of(42L), new ArrayList<>(filter.getParamNameValuePairs().values()));
            return storedItems.removeIf(item -> item.getTaskId().equals(42L)) ? 1 : 0;
        });

        service.deleteShippingNoteTask(42L);

        assertEquals(List.of(unrelated), storedItems);
        var deletionOrder = inOrder(itemMapper, processMapper, taskMapper);
        deletionOrder.verify(itemMapper).delete(any(LambdaQueryWrapper.class));
        deletionOrder.verify(processMapper).delete(any(LambdaQueryWrapper.class));
        deletionOrder.verify(taskMapper).deleteById(42L);
        verify(taskMapper, never()).deleteById(43L);
        verifyNoInteractions(detailMapper, packingMapper, orderMapper, customers);
    }

    @Test
    void deletingShipmentClearsOnlyShippingPrintRecordsWithoutOtherShipmentReferences() {
        ShippingNoteTaskItem first = shipmentItem(42L, 11L);
        ShippingNoteTaskItem shared = shipmentItem(42L, 22L);
        when(itemMapper.selectList(any())).thenReturn(
                List.of(first, shared, first), List.of(shipmentItem(43L, 22L)));
        OrderDetailProcess shippingPrint = process(11L, 7);
        OrderDetailProcess innerLabel = process(11L, 5);
        OrderDetailProcess outerLabel = process(11L, 6);
        OrderDetailProcess sharedShippingPrint = process(22L, 7);
        OrderDetailProcess unrelatedShippingPrint = process(33L, 7);
        List<OrderDetailProcess> processes = new ArrayList<>(List.of(
                shippingPrint, innerLabel, outerLabel, sharedShippingPrint, unrelatedShippingPrint));
        when(processMapper.delete(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<?> filter = invocation.getArgument(0);
            assertTrue(filter.getSqlSegment().contains("order_detail_id IN"));
            assertTrue(filter.getSqlSegment().contains("process_type ="));
            assertEquals(java.util.Set.of(11L, 7), new java.util.HashSet<>(filter.getParamNameValuePairs().values()));
            return processes.removeIf(p -> p.getOrderDetailId().equals(11L) && p.getProcessType() == 7) ? 1 : 0;
        });

        service.deleteShippingNoteTask(42L);

        assertEquals(List.of(innerLabel, outerLabel, sharedShippingPrint, unrelatedShippingPrint), processes);
        verify(processMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        verifyNoInteractions(detailMapper, packingMapper, orderMapper, customers);
    }

    @Test
    void sharedDetailsKeepTheirShippingPrintHistoryWhenAnotherShipmentRemains() {
        when(itemMapper.selectList(any())).thenReturn(
                List.of(shipmentItem(42L, 11L)), List.of(shipmentItem(43L, 11L)));

        service.deleteShippingNoteTask(42L);

        verifyNoInteractions(processMapper);
        verify(taskMapper).deleteById(42L);
    }

    @Test
    void emptyShipmentCannotClearAnyProcessingHistory() {
        when(itemMapper.selectList(any())).thenReturn(List.of());

        service.deleteShippingNoteTask(42L);

        verifyNoInteractions(processMapper);
        verify(taskMapper).deleteById(42L);
    }

    @Test
    void processingCleanupFailureFailsTheShipmentDeletion() {
        when(itemMapper.selectList(any())).thenReturn(List.of(shipmentItem(42L, 11L)), List.of());
        when(processMapper.delete(any(LambdaQueryWrapper.class))).thenThrow(new IllegalStateException("cleanup failed"));

        assertThrows(IllegalStateException.class, () -> service.deleteShippingNoteTask(42L));

        verify(taskMapper, never()).deleteById(any(Long.class));
        verifyNoInteractions(detailMapper, packingMapper, orderMapper, customers);
    }

    private ShippingNoteTaskItem shipmentItem(Long taskId, Long detailId) {
        ShippingNoteTaskItem item = new ShippingNoteTaskItem();
        item.setTaskId(taskId);
        item.setSourceDetailId(detailId);
        return item;
    }

    private OrderDetailProcess process(Long detailId, int type) {
        OrderDetailProcess process = new OrderDetailProcess();
        process.setOrderDetailId(detailId);
        process.setProcessType(type);
        process.setProcessCount(3);
        return process;
    }

    @Test
    void unknownTaskCannotDeleteAnyShipmentItems() {
        var error = assertThrows(BusinessException.class, () -> service.deleteShippingNoteTask(999L));

        assertEquals("出货单任务不存在: 999", error.getMessage());
        verify(taskMapper, never()).deleteById(any(Long.class));
        verifyNoInteractions(itemMapper, detailMapper, packingMapper, orderMapper);
    }

    @Test
    void manualShipmentCannotBeDeletedThroughPrintTasks() {
        task.setSourceType(3);

        assertThrows(BusinessException.class, () -> service.deleteShippingNoteTask(42L));

        verify(taskMapper, never()).deleteById(any(Long.class));
        verifyNoInteractions(itemMapper, detailMapper, packingMapper, orderMapper);
    }

    @Test
    void failureToDeleteItemsDoesNotRemoveTheTask() {
        when(itemMapper.delete(any(LambdaQueryWrapper.class))).thenThrow(new IllegalStateException("delete failed"));

        assertThrows(IllegalStateException.class, () -> service.deleteShippingNoteTask(42L));

        verify(taskMapper, never()).deleteById(any(Long.class));
        verifyNoInteractions(detailMapper, packingMapper, orderMapper);
    }

    private void assertRejectedWithoutWrites(ShippingNoteUpdateRequest request) {
        assertThrows(BusinessException.class, () -> service.updateShippingNoteTask(42L, request));
        verify(taskMapper, never()).updateById(any(ShippingNoteTask.class));
        verifyNoInteractions(itemMapper);
        assertEquals("INV-OLD", task.getInvoiceNos());
        assertEquals(12, task.getTotalPairs());
    }

    private void verifyNoInteractionsOnSourceWrites() {
        verify(detailMapper, never()).updateById(any(OrderRecordDetail.class));
        verify(packingMapper, never()).updateById(any(OrderPackingDetail.class));
        verify(orderMapper, never()).updateById(any(OrderRecord.class));
    }

    private ShippingNoteUpdateRequest update(Long... detailIds) {
        ShippingNoteUpdateRequest request = new ShippingNoteUpdateRequest();
        request.setItems(java.util.Arrays.stream(detailIds).map(id -> {
            ShippingNoteItemRequest item = new ShippingNoteItemRequest();
            item.setSourceDetailId(id);
            item.setOrderId(-1L); // 来源订单以服务端订单明细为准。
            return item;
        }).toList());
        return request;
    }

    private OrderRecordDetail detail(Long id, Long orderId, String style, int pairs, int cartons) {
        OrderRecordDetail detail = new OrderRecordDetail();
        detail.setId(id);
        detail.setOrderId(orderId);
        detail.setDevelopmentNo(style);
        detail.setQuantity(pairs);
        detail.setCartonCount(cartons);
        detail.setCartonStart("1");
        detail.setCartonEnd(String.valueOf(cartons));
        return detail;
    }

    private OrderRecord order(Long id, String invoice) {
        OrderRecord order = new OrderRecord();
        order.setId(id);
        order.setOrderNo(invoice);
        order.setCustomerId(1L);
        order.setSourceType(1);
        return order;
    }

    private OrderPackingDetail packing(OrderRecordDetail detail) {
        OrderPackingDetail packing = new OrderPackingDetail();
        packing.setId(detail.getId() + 1000);
        packing.setOrderId(detail.getOrderId());
        packing.setCompanyStyleNo(detail.getDevelopmentNo());
        packing.setCartonStart(detail.getCartonStart());
        packing.setCartonEnd(detail.getCartonEnd());
        packing.setCartonCount(detail.getCartonCount());
        packing.setTotalPairs(detail.getQuantity());
        return packing;
    }
}
