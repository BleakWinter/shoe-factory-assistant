package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.ComponentOrderCreateRequest;
import com.shoefactory.assistant.dto.ComponentOrderItemRequest;
import com.shoefactory.assistant.dto.ComponentOrderTaskItemResponse;
import com.shoefactory.assistant.dto.ComponentOrderTaskResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.ComponentOrderTask;
import com.shoefactory.assistant.entity.ComponentOrderTaskItem;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.enums.OrderDetailProcessType;
import com.shoefactory.assistant.mapper.ComponentOrderTaskItemMapper;
import com.shoefactory.assistant.mapper.ComponentOrderTaskMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.service.ComponentOrderTaskService;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class ComponentOrderTaskServiceImpl implements ComponentOrderTaskService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };

    private final ComponentOrderTaskMapper componentOrderTaskMapper;
    private final ComponentOrderTaskItemMapper componentOrderTaskItemMapper;
    private final OrderRecordDetailMapper orderRecordDetailMapper;
    private final OrderRecordMapper orderRecordMapper;

    public ComponentOrderTaskServiceImpl(
            ComponentOrderTaskMapper componentOrderTaskMapper,
            ComponentOrderTaskItemMapper componentOrderTaskItemMapper,
            OrderRecordDetailMapper orderRecordDetailMapper,
            OrderRecordMapper orderRecordMapper
    ) {
        this.componentOrderTaskMapper = componentOrderTaskMapper;
        this.componentOrderTaskItemMapper = componentOrderTaskItemMapper;
        this.orderRecordDetailMapper = orderRecordDetailMapper;
        this.orderRecordMapper = orderRecordMapper;
    }

    @Override
    @Transactional
    public ComponentOrderTaskResponse createComponentOrderTask(ComponentOrderCreateRequest request) {
        OrderDetailProcessType processType = getComponentProcessType(request.getProcessType());
        List<Long> detailIds = sanitizeDetailIds(request.getItems());
        if (detailIds.isEmpty()) {
            throw new BusinessException(processType.getLabel() + "明细不能为空");
        }

        Map<Long, OrderRecordDetail> detailMap = loadOrderDetails(detailIds);
        List<OrderRecordDetail> details = detailIds.stream().map(detailMap::get).toList();
        Map<Long, OrderRecord> orderMap = loadOrders(details);
        LocalDateTime now = LocalDateTime.now();

        ComponentOrderTask task = new ComponentOrderTask();
        task.setTaskNo(FileStorageUtil.newBusinessNo(getBusinessPrefix(processType)));
        task.setProcessType(processType.getCode());
        task.setOrderNos(joinOrderNos(details, orderMap));
        task.setDevelopmentNos(joinDevelopmentNos(details));
        task.setItemCount(details.size());
        task.setTotalPairs(details.stream().mapToInt(this::getPairCount).sum());
        task.setTotalCartonCount(details.stream().mapToInt(item -> nullToZero(item.getCartonCount())).sum());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        componentOrderTaskMapper.insert(task);

        for (int index = 0; index < details.size(); index++) {
            OrderRecordDetail detail = details.get(index);
            ComponentOrderTaskItem item = new ComponentOrderTaskItem();
            item.setTaskId(task.getId());
            item.setLineNo(index + 1);
            item.setOrderId(detail.getOrderId());
            item.setSourceDetailId(detail.getId());
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            componentOrderTaskItemMapper.insert(item);
        }
        return getComponentOrderTask(task.getId());
    }

    @Override
    public PageResponse<ComponentOrderTaskResponse> listComponentOrderTasks(
            Integer processType,
            String orderNo,
            String developmentNo,
            long page,
            long size
    ) {
        getComponentProcessType(processType);
        Page<ComponentOrderTask> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<ComponentOrderTask> wrapper = new LambdaQueryWrapper<ComponentOrderTask>()
                .eq(ComponentOrderTask::getProcessType, processType)
                .like(hasText(orderNo), ComponentOrderTask::getOrderNos, orderNo)
                .like(hasText(developmentNo), ComponentOrderTask::getDevelopmentNos, developmentNo)
                .orderByDesc(ComponentOrderTask::getCreatedAt);
        Page<ComponentOrderTask> resultPage = componentOrderTaskMapper.selectPage(pageRequest, wrapper);
        List<ComponentOrderTaskResponse> records = resultPage.getRecords().stream()
                .map(task -> ComponentOrderTaskResponse.from(task, List.of()))
                .toList();
        return PageResponse.from(resultPage, records);
    }

    @Override
    public ComponentOrderTaskResponse getComponentOrderTask(Long id) {
        ComponentOrderTask task = componentOrderTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("下单任务不存在: " + id);
        }
        List<ComponentOrderTaskItem> taskItems = componentOrderTaskItemMapper.selectList(
                new LambdaQueryWrapper<ComponentOrderTaskItem>()
                        .eq(ComponentOrderTaskItem::getTaskId, id)
                        .orderByAsc(ComponentOrderTaskItem::getLineNo)
                        .orderByAsc(ComponentOrderTaskItem::getId)
        );
        if (taskItems.isEmpty()) {
            return ComponentOrderTaskResponse.from(task, List.of());
        }

        List<Long> detailIds = taskItems.stream().map(ComponentOrderTaskItem::getSourceDetailId).toList();
        Map<Long, OrderRecordDetail> detailMap = loadOrderDetails(detailIds);
        Map<Long, OrderRecord> orderMap = loadOrders(detailMap.values());
        List<ComponentOrderTaskItemResponse> items = taskItems.stream()
                .map(item -> toItemResponse(item, detailMap.get(item.getSourceDetailId()), orderMap))
                .toList();
        return ComponentOrderTaskResponse.from(task, items);
    }

    private ComponentOrderTaskItemResponse toItemResponse(
            ComponentOrderTaskItem taskItem,
            OrderRecordDetail detail,
            Map<Long, OrderRecord> orderMap
    ) {
        if (detail == null) {
            throw new BusinessException("订单明细不存在: " + taskItem.getSourceDetailId());
        }
        Long orderId = detail.getOrderId() == null ? taskItem.getOrderId() : detail.getOrderId();
        OrderRecord order = orderId == null ? null : orderMap.get(orderId);
        ComponentOrderTaskItemResponse response = new ComponentOrderTaskItemResponse();
        response.setSourceDetailId(detail.getId());
        response.setOrderId(orderId);
        response.setOrderNo(firstText(order == null ? null : order.getOrderNo(), detail.getCustomerOrderNo()));
        response.setDevelopmentNo(cleanText(detail.getDevelopmentNo()));
        response.setLastNo(cleanText(detail.getLastNo()));
        response.setSizeQuantities(parseSizeQuantities(detail.getSizeQuantitiesJson()));
        response.setQuantity(getPairCount(detail));
        response.setCartonCount(nullToZero(detail.getCartonCount()));
        response.setCartonStart(cleanText(detail.getCartonStart()));
        response.setCartonEnd(cleanText(detail.getCartonEnd()));
        return response;
    }

    private List<Long> sanitizeDetailIds(List<ComponentOrderItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null && item.getSourceDetailId() != null)
                .map(ComponentOrderItemRequest::getSourceDetailId)
                .distinct()
                .toList();
    }

    private Map<Long, OrderRecordDetail> loadOrderDetails(List<Long> detailIds) {
        if (detailIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, OrderRecordDetail> detailMap = orderRecordDetailMapper.selectBatchIds(detailIds).stream()
                .collect(Collectors.toMap(OrderRecordDetail::getId, Function.identity()));
        for (Long detailId : detailIds) {
            if (!detailMap.containsKey(detailId)) {
                throw new BusinessException("订单明细不存在: " + detailId);
            }
        }
        return detailMap;
    }

    private Map<Long, OrderRecord> loadOrders(Collection<OrderRecordDetail> details) {
        List<Long> orderIds = details.stream()
                .map(OrderRecordDetail::getOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRecordMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(OrderRecord::getId, Function.identity()));
    }

    private String joinOrderNos(List<OrderRecordDetail> details, Map<Long, OrderRecord> orderMap) {
        Set<String> values = new LinkedHashSet<>();
        for (OrderRecordDetail detail : details) {
            OrderRecord order = detail.getOrderId() == null ? null : orderMap.get(detail.getOrderId());
            String orderNo = firstText(order == null ? null : order.getOrderNo(), detail.getCustomerOrderNo());
            if (hasText(orderNo)) {
                values.add(orderNo);
            }
        }
        return String.join(",", values);
    }

    private String joinDevelopmentNos(List<OrderRecordDetail> details) {
        Set<String> values = new LinkedHashSet<>();
        for (OrderRecordDetail detail : details) {
            String developmentNo = cleanText(detail.getDevelopmentNo());
            if (hasText(developmentNo)) {
                values.add(developmentNo);
            }
        }
        return String.join(",", values);
    }

    private int getPairCount(OrderRecordDetail detail) {
        if (detail.getQuantity() != null && detail.getQuantity() > 0) {
            return detail.getQuantity();
        }
        return parseSizeQuantities(detail.getSizeQuantitiesJson()).values().stream()
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private Map<String, Integer> parseSizeQuantities(String json) {
        if (!hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, SIZE_MAP_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private OrderDetailProcessType getComponentProcessType(Integer code) {
        OrderDetailProcessType processType = OrderDetailProcessType.fromCode(code);
        if (processType == null || processType.getCode() < 1 || processType.getCode() > 4) {
            throw new BusinessException("下单类型无效");
        }
        return processType;
    }

    private String getBusinessPrefix(OrderDetailProcessType processType) {
        return switch (processType) {
            case ORDER_PACKING -> "PK";
            case ORDER_OUTSOLE -> "OS";
            case ORDER_INSOLE -> "IS";
            case ORDER_HEEL -> "HE";
            default -> "CO";
        };
    }

    private String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private String cleanText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
