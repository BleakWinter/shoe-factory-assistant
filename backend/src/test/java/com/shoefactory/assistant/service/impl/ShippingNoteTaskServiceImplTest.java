package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.ShippingNoteItemRequest;
import com.shoefactory.assistant.dto.ShippingNoteUpdateRequest;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskItemMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskMapper;
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
    private final ShippingNoteTaskServiceImpl service = new ShippingNoteTaskServiceImpl(
            taskMapper, itemMapper, detailMapper, packingMapper, orderMapper
    );
    private final List<ShippingNoteTaskItem> storedItems = new ArrayList<>();
    private ShippingNoteTask task;

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ShippingNoteTaskItem.class);
    }

    @BeforeEach
    void setUp() {
        task = new ShippingNoteTask();
        task.setId(42L);
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
