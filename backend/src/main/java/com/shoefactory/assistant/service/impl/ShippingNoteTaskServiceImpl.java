package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteItemRequest;
import com.shoefactory.assistant.dto.ShippingNoteTaskResponse;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskItemMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskMapper;
import com.shoefactory.assistant.service.ShippingNoteTaskService;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ShippingNoteTaskServiceImpl implements ShippingNoteTaskService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ShippingNoteTaskMapper shippingNoteTaskMapper;
    private final ShippingNoteTaskItemMapper shippingNoteTaskItemMapper;
    private final OrderRecordMapper orderRecordMapper;

    public ShippingNoteTaskServiceImpl(
            ShippingNoteTaskMapper shippingNoteTaskMapper,
            ShippingNoteTaskItemMapper shippingNoteTaskItemMapper,
            OrderRecordMapper orderRecordMapper
    ) {
        this.shippingNoteTaskMapper = shippingNoteTaskMapper;
        this.shippingNoteTaskItemMapper = shippingNoteTaskItemMapper;
        this.orderRecordMapper = orderRecordMapper;
    }

    @Override
    @Transactional
    public ShippingNoteTaskResponse createShippingNoteTask(ShippingNoteCreateRequest request) {
        OrderRecord order = orderRecordMapper.selectById(request.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在: " + request.getOrderId());
        }
        List<ShippingNoteItemRequest> items = sanitizeItems(request.getItems());
        if (items.isEmpty()) {
            throw new BusinessException("出货单明细不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        ShippingNoteTask task = new ShippingNoteTask();
        task.setTaskNo(FileStorageUtil.newBusinessNo("SH"));
        task.setOrderId(order.getId());
        task.setOrderNo(firstText(order.getOrderNo(), items.get(0).getOrderNo()));
        task.setCustomerName(firstText(order.getCustomerName(), items.get(0).getCustomerName()));
        task.setRecipientName(firstText(request.getRecipientName(), "达为鞋业"));
        task.setShippingDate(request.getShippingDate() == null ? LocalDate.now() : request.getShippingDate());
        task.setDevelopmentNos(joinDevelopmentNos(items));
        task.setItemCount(items.size());
        task.setTotalPairs(sumPairs(items));
        task.setTotalCartonCount(sumCartons(items));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        shippingNoteTaskMapper.insert(task);

        for (int index = 0; index < items.size(); index++) {
            ShippingNoteTaskItem item = buildTaskItem(task.getId(), index + 1, items.get(index), now);
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
                .like(hasText(orderNo), ShippingNoteTask::getOrderNo, orderNo)
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
        List<ShippingNoteTaskItem> items = shippingNoteTaskItemMapper.selectList(
                new LambdaQueryWrapper<ShippingNoteTaskItem>()
                        .eq(ShippingNoteTaskItem::getTaskId, id)
                        .orderByAsc(ShippingNoteTaskItem::getLineNo)
                        .orderByAsc(ShippingNoteTaskItem::getId)
        );
        return ShippingNoteTaskResponse.from(task, items);
    }

    private ShippingNoteTaskItem buildTaskItem(
            Long taskId,
            int lineNo,
            ShippingNoteItemRequest source,
            LocalDateTime now
    ) {
        ShippingNoteTaskItem item = new ShippingNoteTaskItem();
        item.setTaskId(taskId);
        item.setLineNo(lineNo);
        item.setSourceDetailId(source.getSourceDetailId());
        item.setOrderNo(cleanText(source.getOrderNo()));
        item.setDevelopmentNo(cleanText(source.getDevelopmentNo()));
        item.setCustomerName(cleanText(source.getCustomerName()));
        item.setCustomerStyleNo(cleanText(source.getCustomerStyleNo()));
        item.setEnglishColor(cleanText(source.getEnglishColor()));
        item.setEnglishMaterial(cleanText(source.getEnglishMaterial()));
        item.setColorMaterial(cleanText(source.getColorMaterial()));
        item.setTrademark(cleanText(source.getTrademark()));
        item.setSizeQuantitiesJson(toJson(source.getSizeQuantities()));
        item.setPairCount(nullToZero(source.getPairCount()));
        item.setCartonCount(nullToZero(source.getCartonCount()));
        item.setTotalPairs(nullToZero(source.getTotalPairs()));
        item.setCartonStart(cleanText(source.getCartonStart()));
        item.setCartonEnd(cleanText(source.getCartonEnd()));
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }

    private List<ShippingNoteItemRequest> sanitizeItems(List<ShippingNoteItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null && item.getSourceDetailId() != null)
                .peek(this::normalizeItemNumbers)
                .toList();
    }

    private void normalizeItemNumbers(ShippingNoteItemRequest item) {
        int sizeTotal = sumSizeQuantities(item.getSizeQuantities());
        if (zeroOrNull(item.getPairCount()) && sizeTotal > 0) {
            item.setPairCount(sizeTotal);
        }
        if (zeroOrNull(item.getTotalPairs())) {
            item.setTotalPairs(zeroOrNull(item.getPairCount()) ? sizeTotal : item.getPairCount());
        }
        if (item.getCartonCount() == null) {
            item.setCartonCount(0);
        }
    }

    private String joinDevelopmentNos(List<ShippingNoteItemRequest> items) {
        Set<String> values = new LinkedHashSet<>();
        for (ShippingNoteItemRequest item : items) {
            if (hasText(item.getDevelopmentNo())) {
                values.add(item.getDevelopmentNo().trim());
            }
        }
        return String.join(",", values);
    }

    private int sumPairs(List<ShippingNoteItemRequest> items) {
        int total = 0;
        for (ShippingNoteItemRequest item : items) {
            if (!zeroOrNull(item.getTotalPairs())) {
                total += item.getTotalPairs();
            } else if (!zeroOrNull(item.getPairCount())) {
                total += item.getPairCount();
            } else {
                total += sumSizeQuantities(item.getSizeQuantities());
            }
        }
        return total;
    }

    private int sumCartons(List<ShippingNoteItemRequest> items) {
        int total = 0;
        for (ShippingNoteItemRequest item : items) {
            if (item.getCartonCount() != null && item.getCartonCount() > 0) {
                total += item.getCartonCount();
            }
        }
        return total;
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

    private String toJson(Map<String, Integer> sizeQuantities) {
        try {
            return OBJECT_MAPPER.writeValueAsString(sizeQuantities == null ? Map.of() : sizeQuantities);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("保存出货单明细失败", ex);
        }
    }

    private boolean zeroOrNull(Integer value) {
        return value == null || value <= 0;
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
