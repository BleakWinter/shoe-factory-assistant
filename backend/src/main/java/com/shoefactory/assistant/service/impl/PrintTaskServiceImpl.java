package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.dto.PrintTaskCreateRequest;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.enums.PrintTaskStatus;
import com.shoefactory.assistant.enums.PrintType;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.service.PrintPreviewService;
import com.shoefactory.assistant.service.PrintTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class PrintTaskServiceImpl implements PrintTaskService {

    // 新表结构没有 print_task 表，打印列表直接展示 order_record。
    private final OrderRecordMapper orderRecordMapper;
    private final PrintPreviewService printPreviewService;

    public PrintTaskServiceImpl(
            OrderRecordMapper orderRecordMapper,
            PrintPreviewService printPreviewService
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.printPreviewService = printPreviewService;
    }

    @Override
    public PrintTaskResponse createTask(PrintTaskCreateRequest request) {
        throw new BusinessException("当前版本上传订单后直接进入打印列表，无需单独创建打印任务");
    }

    @Override
    public List<PrintTaskResponse> listTasks() {
        // 打印列表页面直接按上传时间倒序展示订单主表。
        return orderRecordMapper.selectList(new LambdaQueryWrapper<OrderRecord>()
                        .orderByDesc(OrderRecord::getCreatedAt))
                .stream()
                .map(PrintTaskResponse::fromOrder)
                .toList();
    }

    @Override
    public List<PrintTaskResponse> listPendingTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<OrderRecord> orders = orderRecordMapper.selectList(new LambdaQueryWrapper<OrderRecord>()
                .and(wrapper -> wrapper
                        .eq(OrderRecord::getOrderPrinted, false)
                        .or()
                        .eq(OrderRecord::getPackingPrinted, false))
                .orderByAsc(OrderRecord::getCreatedAt)
                .last("LIMIT " + safeLimit));
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        return orders.stream().map(PrintTaskResponse::fromOrder).toList();
    }

    @Override
    public PrintPreviewResponse generateTaskPreview(Long taskId, PrintType printType) {
        // 这里的 taskId 兼容旧接口名，实际就是 order_record.id。
        return printPreviewService.generatePreview(taskId, printType);
    }

    @Override
    @Transactional
    public PrintTaskResponse updateTaskStatus(Long id, PrintTaskStatusUpdateRequest request) {
        OrderRecord order = orderRecordMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在: " + id);
        }
        PrintTaskStatus status = PrintTaskStatus.parse(request.getStatus());
        if (status == PrintTaskStatus.SUCCESS) {
            order.setOrderPrinted(true);
            order.setPackingPrinted(true);
        }
        if (status == PrintTaskStatus.FAILED) {
            order.setErrorMessage(blankToNull(request.getErrorMessage()));
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderRecordMapper.updateById(order);
        return PrintTaskResponse.fromOrder(order);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
