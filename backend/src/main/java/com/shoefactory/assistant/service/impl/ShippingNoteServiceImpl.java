package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteItemRequest;
import com.shoefactory.assistant.dto.ShippingNoteRecordResponse;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.ShippingNoteRecord;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShippingNoteRecordMapper;
import com.shoefactory.assistant.service.ShippingNoteService;
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
public class ShippingNoteServiceImpl implements ShippingNoteService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ShippingNoteRecordMapper shippingNoteRecordMapper;
    private final OrderRecordMapper orderRecordMapper;

    public ShippingNoteServiceImpl(
            ShippingNoteRecordMapper shippingNoteRecordMapper,
            OrderRecordMapper orderRecordMapper
    ) {
        this.shippingNoteRecordMapper = shippingNoteRecordMapper;
        this.orderRecordMapper = orderRecordMapper;
    }

    @Override
    @Transactional
    public ShippingNoteRecordResponse createShippingNote(ShippingNoteCreateRequest request) {
        OrderRecord order = orderRecordMapper.selectById(request.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在: " + request.getOrderId());
        }
        List<ShippingNoteItemRequest> items = sanitizeItems(request.getItems());
        if (items.isEmpty()) {
            throw new BusinessException("出货单明细不能为空");
        }

        ShippingNoteRecord record = new ShippingNoteRecord();
        record.setPrintNo(FileStorageUtil.newBusinessNo("SH"));
        record.setOrderId(order.getId());
        record.setOrderNo(firstText(order.getOrderNo(), items.get(0).getOrderNo()));
        record.setCustomerName(firstText(order.getCustomerName(), items.get(0).getCustomerName()));
        record.setRecipientName(firstText(request.getRecipientName(), "达为鞋业"));
        record.setShippingDate(request.getShippingDate() == null ? LocalDate.now() : request.getShippingDate());
        record.setDevelopmentNos(joinDevelopmentNos(items));
        record.setItemCount(items.size());
        record.setTotalPairs(sumPairs(items));
        record.setTotalCartonCount(sumCartons(items));
        record.setDataJson(toJson(items));
        LocalDateTime now = LocalDateTime.now();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        shippingNoteRecordMapper.insert(record);
        return ShippingNoteRecordResponse.from(record);
    }

    @Override
    public PageResponse<ShippingNoteRecordResponse> listShippingNotes(
            String orderNo,
            String developmentNo,
            long page,
            long size
    ) {
        Page<ShippingNoteRecord> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<ShippingNoteRecord> wrapper = new LambdaQueryWrapper<ShippingNoteRecord>()
                .like(hasText(orderNo), ShippingNoteRecord::getOrderNo, orderNo)
                .like(hasText(developmentNo), ShippingNoteRecord::getDevelopmentNos, developmentNo)
                .orderByDesc(ShippingNoteRecord::getCreatedAt);
        Page<ShippingNoteRecord> resultPage = shippingNoteRecordMapper.selectPage(pageRequest, wrapper);
        List<ShippingNoteRecordResponse> records = resultPage.getRecords().stream()
                .map(ShippingNoteRecordResponse::from)
                .toList();
        return PageResponse.from(resultPage, records);
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

    private String toJson(List<ShippingNoteItemRequest> items) {
        try {
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("保存出货单数据失败", ex);
        }
    }

    private boolean zeroOrNull(Integer value) {
        return value == null || value <= 0;
    }

    private String firstText(String left, String right) {
        if (hasText(left)) {
            return left.trim();
        }
        return hasText(right) ? right.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
