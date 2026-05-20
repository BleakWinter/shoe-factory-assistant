package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderSheetPrintTask;
import com.shoefactory.assistant.enums.PrintTaskStatus;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.OrderSheetPrintTaskMapper;
import com.shoefactory.assistant.service.PrintPreviewService;
import com.shoefactory.assistant.service.PrintTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PrintTaskServiceImpl implements PrintTaskService {

    private final OrderSheetPrintTaskMapper orderSheetPrintTaskMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final PrintPreviewService printPreviewService;

    public PrintTaskServiceImpl(
            OrderSheetPrintTaskMapper orderSheetPrintTaskMapper,
            OrderRecordMapper orderRecordMapper,
            PrintPreviewService printPreviewService
    ) {
        this.orderSheetPrintTaskMapper = orderSheetPrintTaskMapper;
        this.orderRecordMapper = orderRecordMapper;
        this.printPreviewService = printPreviewService;
    }

    @Override
    public List<PrintTaskResponse> listTasks() {
        List<OrderSheetPrintTask> tasks = orderSheetPrintTaskMapper.selectList(new LambdaQueryWrapper<OrderSheetPrintTask>()
                .ne(OrderSheetPrintTask::getStatus, PrintTaskStatus.INVALID.getCode())
                .orderByDesc(OrderSheetPrintTask::getCreatedAt));
        return toResponses(tasks);
    }

    @Override
    public List<PrintTaskResponse> listPendingTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<OrderSheetPrintTask> tasks = orderSheetPrintTaskMapper.selectList(new LambdaQueryWrapper<OrderSheetPrintTask>()
                .eq(OrderSheetPrintTask::getStatus, PrintTaskStatus.PENDING.getCode())
                .orderByAsc(OrderSheetPrintTask::getCreatedAt)
                .last("LIMIT " + safeLimit));
        return toResponses(tasks);
    }

    @Override
    public PrintPreviewResponse generateTaskPreview(Long taskId) {
        return printPreviewService.generatePreview(getRequiredTask(taskId));
    }

    @Override
    public PrintPreviewResponse regenerateTaskPreview(Long taskId) {
        return printPreviewService.regeneratePreview(getRequiredTask(taskId));
    }

    @Override
    @Transactional
    public PrintTaskResponse markTaskPrinted(Long id) {
        OrderSheetPrintTask task = getRequiredTask(id);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(PrintTaskStatus.PRINTED.getCode());
        task.setPrintCount((task.getPrintCount() == null ? 0 : task.getPrintCount()) + 1);
        task.setLastPrintTime(now);
        task.setErrorMessage(null);
        task.setUpdatedAt(now);
        orderSheetPrintTaskMapper.updateById(task);
        return toResponse(task);
    }

    @Override
    @Transactional
    public PrintTaskResponse updateTaskStatus(Long id, PrintTaskStatusUpdateRequest request) {
        OrderSheetPrintTask task = getRequiredTask(id);
        PrintTaskStatus status = PrintTaskStatus.parse(request.getStatus());
        task.setStatus(status.getCode());
        task.setErrorMessage(status == PrintTaskStatus.FAILED ? blankToNull(request.getErrorMessage()) : null);
        if (status == PrintTaskStatus.PRINTED) {
            task.setPrintCount((task.getPrintCount() == null ? 0 : task.getPrintCount()) + 1);
            task.setLastPrintTime(LocalDateTime.now());
        }
        task.setUpdatedAt(LocalDateTime.now());
        orderSheetPrintTaskMapper.updateById(task);
        return toResponse(task);
    }

    @Override
    public Path loadTaskPdf(Long id) {
        return printPreviewService.loadTaskPdf(getRequiredTask(id));
    }

    private OrderSheetPrintTask getRequiredTask(Long taskId) {
        if (taskId == null) {
            throw new BusinessException("打印任务 ID 不能为空");
        }
        OrderSheetPrintTask task = orderSheetPrintTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("打印任务不存在: " + taskId);
        }
        if (PrintTaskStatus.fromCode(task.getStatus()) == PrintTaskStatus.INVALID) {
            throw new BusinessException("打印任务已失效，请使用新上传文件对应的任务");
        }
        return task;
    }

    private List<PrintTaskResponse> toResponses(List<OrderSheetPrintTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, OrderRecord> orderMap = loadOrderMap(tasks);
        return tasks.stream()
                .map(task -> PrintTaskResponse.fromTask(task, orderMap.get(task.getOrderId())))
                .toList();
    }

    private PrintTaskResponse toResponse(OrderSheetPrintTask task) {
        OrderRecord order = task.getOrderId() == null ? null : orderRecordMapper.selectById(task.getOrderId());
        return PrintTaskResponse.fromTask(task, order);
    }

    private Map<Long, OrderRecord> loadOrderMap(List<OrderSheetPrintTask> tasks) {
        List<Long> orderIds = tasks.stream()
                .map(OrderSheetPrintTask::getOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRecordMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(OrderRecord::getId, Function.identity()));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
