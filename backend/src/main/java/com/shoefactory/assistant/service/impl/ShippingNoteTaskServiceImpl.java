package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteItemRequest;
import com.shoefactory.assistant.dto.ShippingNoteTaskResponse;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskItemMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskMapper;
import com.shoefactory.assistant.service.ShippingNoteTaskService;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShippingNoteTaskServiceImpl implements ShippingNoteTaskService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };

    private final ShippingNoteTaskMapper shippingNoteTaskMapper;
    private final ShippingNoteTaskItemMapper shippingNoteTaskItemMapper;
    private final OrderPackingDetailMapper orderPackingDetailMapper;
    private final OrderRecordMapper orderRecordMapper;

    public ShippingNoteTaskServiceImpl(
            ShippingNoteTaskMapper shippingNoteTaskMapper,
            ShippingNoteTaskItemMapper shippingNoteTaskItemMapper,
            OrderPackingDetailMapper orderPackingDetailMapper,
            OrderRecordMapper orderRecordMapper
    ) {
        this.shippingNoteTaskMapper = shippingNoteTaskMapper;
        this.shippingNoteTaskItemMapper = shippingNoteTaskItemMapper;
        this.orderPackingDetailMapper = orderPackingDetailMapper;
        this.orderRecordMapper = orderRecordMapper;
    }

    @Override
    @Transactional
    public ShippingNoteTaskResponse createShippingNoteTask(ShippingNoteCreateRequest request) {
        List<ShippingNoteItemRequest> items = sanitizeItems(request.getItems());
        if (items.isEmpty()) {
            throw new BusinessException("出货单明细不能为空");
        }

        Map<Long, OrderPackingDetail> packingDetailMap = loadPackingDetailsByRequestItems(items);
        List<OrderPackingDetail> selectedPackingDetails = items.stream()
                .map(item -> packingDetailMap.get(item.getSourceDetailId()))
                .toList();
        Map<Long, OrderRecord> orderMap = loadOrdersByPackingDetails(selectedPackingDetails);

        LocalDateTime now = LocalDateTime.now();
        ShippingNoteTask task = new ShippingNoteTask();
        task.setTaskNo(FileStorageUtil.newBusinessNo("SH"));
        task.setRecipientName(firstText(request.getRecipientName(), "达为鞋业"));
        task.setShippingDate(request.getShippingDate() == null ? LocalDate.now() : request.getShippingDate());
        task.setInvoiceNos(joinInvoiceNos(selectedPackingDetails, orderMap));
        task.setDevelopmentNos(joinDevelopmentNos(selectedPackingDetails));
        task.setItemCount(selectedPackingDetails.size());
        task.setTotalPairs(sumPairs(selectedPackingDetails));
        task.setTotalCartonCount(sumCartons(selectedPackingDetails));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        shippingNoteTaskMapper.insert(task);

        for (int index = 0; index < selectedPackingDetails.size(); index++) {
            ShippingNoteTaskItem item = buildTaskItem(task.getId(), index + 1, selectedPackingDetails.get(index), now);
            shippingNoteTaskItemMapper.insert(item);
        }

        return getShippingNoteTask(task.getId());
    }

    @Override
    public PageResponse<ShippingNoteTaskResponse> listShippingNoteTasks(
            String orderNo,
            String developmentNo,
            long page,
            long size
    ) {
        Page<ShippingNoteTask> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<ShippingNoteTask> wrapper = new LambdaQueryWrapper<ShippingNoteTask>()
                .like(hasText(orderNo), ShippingNoteTask::getInvoiceNos, orderNo)
                .like(hasText(developmentNo), ShippingNoteTask::getDevelopmentNos, developmentNo)
                .orderByDesc(ShippingNoteTask::getCreatedAt);
        Page<ShippingNoteTask> resultPage = shippingNoteTaskMapper.selectPage(pageRequest, wrapper);
        List<ShippingNoteTaskResponse> records = resultPage.getRecords().stream()
                .map(task -> ShippingNoteTaskResponse.from(task, List.of()))
                .toList();
        return PageResponse.from(resultPage, records);
    }

    @Override
    public ShippingNoteTaskResponse getShippingNoteTask(Long id) {
        ShippingNoteTask task = shippingNoteTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("出货单任务不存在: " + id);
        }
        List<ShippingNoteTaskItem> taskItems = shippingNoteTaskItemMapper.selectList(
                new LambdaQueryWrapper<ShippingNoteTaskItem>()
                        .eq(ShippingNoteTaskItem::getTaskId, id)
                        .orderByAsc(ShippingNoteTaskItem::getLineNo)
                        .orderByAsc(ShippingNoteTaskItem::getId)
        );
        if (taskItems.isEmpty()) {
            return ShippingNoteTaskResponse.from(task, List.of());
        }

        Map<Long, OrderPackingDetail> packingDetailMap = loadPackingDetailsByTaskItems(taskItems);
        Map<Long, OrderRecord> orderMap = loadOrdersByPackingDetails(packingDetailMap.values());
        List<ShippingNoteItemRequest> responseItems = taskItems.stream()
                .map(item -> toItemResponse(item, packingDetailMap.get(item.getSourceDetailId()), orderMap))
                .toList();
        return ShippingNoteTaskResponse.from(task, responseItems);
    }

    private ShippingNoteTaskItem buildTaskItem(
            Long taskId,
            int lineNo,
            OrderPackingDetail packingDetail,
            LocalDateTime now
    ) {
        ShippingNoteTaskItem item = new ShippingNoteTaskItem();
        item.setTaskId(taskId);
        item.setLineNo(lineNo);
        item.setOrderId(packingDetail.getOrderId());
        item.setSourceDetailId(packingDetail.getId());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }

    private ShippingNoteItemRequest toItemResponse(
            ShippingNoteTaskItem taskItem,
            OrderPackingDetail packingDetail,
            Map<Long, OrderRecord> orderMap
    ) {
        if (packingDetail == null) {
            throw new BusinessException("装箱单明细不存在: " + taskItem.getSourceDetailId());
        }
        Long orderId = packingDetail.getOrderId() == null ? taskItem.getOrderId() : packingDetail.getOrderId();
        OrderRecord order = orderId == null ? null : orderMap.get(orderId);
        Map<String, Integer> sizeQuantities = parseSizeQuantities(packingDetail.getSizeQuantitiesJson());
        int sizeTotal = sumSizeQuantities(sizeQuantities);
        int totalPairs = getPackingTotalPairs(packingDetail, sizeTotal);

        ShippingNoteItemRequest response = new ShippingNoteItemRequest();
        response.setSourceDetailId(packingDetail.getId());
        response.setOrderId(orderId);
        response.setOrderNo(firstText(order == null ? null : order.getOrderNo(), packingDetail.getCustomerOrderNo()));
        response.setDevelopmentNo(cleanText(packingDetail.getCompanyStyleNo()));
        response.setCustomerName(firstText(packingDetail.getCustomerName(), order == null ? null : order.getCustomerName()));
        response.setCustomerStyleNo(cleanText(packingDetail.getCustomerStyleNo()));
        response.setEnglishColor(cleanText(packingDetail.getCustomerColor()));
        response.setEnglishMaterial(cleanText(packingDetail.getMaterial()));
        response.setColorMaterial(firstText(packingDetail.getMaterial(), packingDetail.getCustomerColor()));
        response.setTrademark(cleanText(packingDetail.getTrademark()));
        response.setSizeQuantities(sizeQuantities);
        response.setPairCount(sizeTotal > 0 ? sizeTotal : totalPairs);
        response.setCartonCount(nullToZero(packingDetail.getCartonCount()));
        response.setTotalPairs(totalPairs);
        response.setCartonStart(cleanText(packingDetail.getCartonStart()));
        response.setCartonEnd(cleanText(packingDetail.getCartonEnd()));
        return response;
    }

    private Map<Long, OrderPackingDetail> loadPackingDetailsByRequestItems(List<ShippingNoteItemRequest> items) {
        List<Long> sourceDetailIds = items.stream()
                .map(ShippingNoteItemRequest::getSourceDetailId)
                .distinct()
                .toList();
        return loadPackingDetailsByIds(sourceDetailIds);
    }

    private Map<Long, OrderPackingDetail> loadPackingDetailsByTaskItems(List<ShippingNoteTaskItem> items) {
        List<Long> sourceDetailIds = items.stream()
                .map(ShippingNoteTaskItem::getSourceDetailId)
                .distinct()
                .toList();
        return loadPackingDetailsByIds(sourceDetailIds);
    }

    private Map<Long, OrderPackingDetail> loadPackingDetailsByIds(List<Long> sourceDetailIds) {
        if (sourceDetailIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, OrderPackingDetail> details = orderPackingDetailMapper.selectBatchIds(sourceDetailIds)
                .stream()
                .collect(Collectors.toMap(OrderPackingDetail::getId, Function.identity()));
        for (Long sourceDetailId : sourceDetailIds) {
            if (!details.containsKey(sourceDetailId)) {
                throw new BusinessException("装箱单明细不存在: " + sourceDetailId);
            }
        }
        return details;
    }

    private Map<Long, OrderRecord> loadOrdersByPackingDetails(Collection<OrderPackingDetail> packingDetails) {
        List<Long> orderIds = packingDetails.stream()
                .map(OrderPackingDetail::getOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRecordMapper.selectBatchIds(orderIds)
                .stream()
                .collect(Collectors.toMap(OrderRecord::getId, Function.identity()));
    }

    private List<ShippingNoteItemRequest> sanitizeItems(List<ShippingNoteItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null && item.getSourceDetailId() != null)
                .toList();
    }

    private String joinDevelopmentNos(List<OrderPackingDetail> items) {
        Set<String> values = new LinkedHashSet<>();
        for (OrderPackingDetail item : items) {
            String developmentNo = cleanText(item.getCompanyStyleNo());
            if (hasText(developmentNo)) {
                values.add(developmentNo);
            }
        }
        return String.join(",", values);
    }

    private String joinInvoiceNos(List<OrderPackingDetail> items, Map<Long, OrderRecord> orderMap) {
        Set<String> values = new LinkedHashSet<>();
        for (OrderPackingDetail item : items) {
            OrderRecord order = item.getOrderId() == null ? null : orderMap.get(item.getOrderId());
            String orderNo = firstText(order == null ? null : order.getOrderNo(), item.getCustomerOrderNo());
            if (hasText(orderNo)) {
                values.add(orderNo);
            }
        }
        return String.join(",", values);
    }

    private int sumPairs(List<OrderPackingDetail> items) {
        int total = 0;
        for (OrderPackingDetail item : items) {
            total += getPackingTotalPairs(item, sumSizeQuantities(parseSizeQuantities(item.getSizeQuantitiesJson())));
        }
        return total;
    }

    private int sumCartons(List<OrderPackingDetail> items) {
        int total = 0;
        for (OrderPackingDetail item : items) {
            total += nullToZero(item.getCartonCount());
        }
        return total;
    }

    private int getPackingTotalPairs(OrderPackingDetail item, int sizeTotal) {
        int cartonCount = nullToZero(item.getCartonCount());
        if (sizeTotal > 0 && cartonCount > 0) {
            return sizeTotal * cartonCount;
        }
        if (item.getTotalPairs() != null && item.getTotalPairs() > 0) {
            return item.getTotalPairs();
        }
        return sizeTotal;
    }

    private Map<String, Integer> parseSizeQuantities(String json) {
        if (!hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, SIZE_MAP_TYPE);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private int sumSizeQuantities(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.values().stream()
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstText(String left, String right) {
        if (hasText(left)) {
            return left.trim();
        }
        return hasText(right) ? right.trim() : null;
    }

    private String cleanText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
