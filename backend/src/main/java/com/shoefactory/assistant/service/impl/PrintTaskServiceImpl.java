package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PrintTaskCreateRequest;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.PrintPreview;
import com.shoefactory.assistant.entity.PrintTask;
import com.shoefactory.assistant.enums.PrintPreviewStatus;
import com.shoefactory.assistant.enums.PrintTaskStatus;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.PrintPreviewMapper;
import com.shoefactory.assistant.mapper.PrintTaskMapper;
import com.shoefactory.assistant.service.PrintTaskService;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PrintTaskServiceImpl implements PrintTaskService {

    private final PrintTaskMapper printTaskMapper;
    private final PrintPreviewMapper printPreviewMapper;
    private final OrderRecordMapper orderRecordMapper;

    public PrintTaskServiceImpl(
            PrintTaskMapper printTaskMapper,
            PrintPreviewMapper printPreviewMapper,
            OrderRecordMapper orderRecordMapper
    ) {
        this.printTaskMapper = printTaskMapper;
        this.printPreviewMapper = printPreviewMapper;
        this.orderRecordMapper = orderRecordMapper;
    }

    @Override
    @Transactional
    public PrintTaskResponse createTask(PrintTaskCreateRequest request) {
        PrintPreview preview = getReadyPreview(request.getPreviewId());
        OrderRecord order = getRequiredOrder(preview.getOrderId());
        LocalDateTime now = LocalDateTime.now();

        PrintTask task = new PrintTask();
        task.setTaskNo(FileStorageUtil.newBusinessNo("PT"));
        task.setOrderId(order.getId());
        task.setPreviewId(preview.getId());
        task.setPrintType(preview.getPrintType());
        task.setPrinterName(blankToNull(request.getPrinterName()));
        task.setCopies(request.getCopies() == null ? 1 : request.getCopies());
        task.setStatus(PrintTaskStatus.PENDING.name());
        task.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        printTaskMapper.insert(task);
        return PrintTaskResponse.from(task, order, preview);
    }

    @Override
    public List<PrintTaskResponse> listPendingTasks(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<PrintTask> wrapper = new LambdaQueryWrapper<PrintTask>()
                .eq(PrintTask::getStatus, PrintTaskStatus.PENDING.name())
                .orderByDesc(PrintTask::getPriority)
                .orderByAsc(PrintTask::getCreatedAt)
                .last("LIMIT " + safeLimit);
        List<PrintTask> tasks = printTaskMapper.selectList(wrapper);
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, OrderRecord> orderMap = orderRecordMapper.selectBatchIds(tasks.stream()
                        .map(PrintTask::getOrderId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(OrderRecord::getId, Function.identity()));
        Map<Long, PrintPreview> previewMap = printPreviewMapper.selectBatchIds(tasks.stream()
                        .map(PrintTask::getPreviewId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PrintPreview::getId, Function.identity()));
        return tasks.stream()
                .map(task -> PrintTaskResponse.from(task, orderMap.get(task.getOrderId()), previewMap.get(task.getPreviewId())))
                .toList();
    }

    @Override
    @Transactional
    public PrintTaskResponse updateTaskStatus(Long id, PrintTaskStatusUpdateRequest request) {
        PrintTask task = printTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("打印任务不存在: " + id);
        }
        PrintTaskStatus status = PrintTaskStatus.parse(request.getStatus());
        LocalDateTime now = LocalDateTime.now();

        task.setStatus(status.name());
        task.setErrorMessage(blankToNull(request.getErrorMessage()));
        task.setUpdatedAt(now);
        if (status == PrintTaskStatus.PRINTING && task.getPickedAt() == null) {
            task.setPickedAt(now);
        }
        if (status == PrintTaskStatus.SUCCESS || status == PrintTaskStatus.FAILED || status == PrintTaskStatus.CANCELED) {
            task.setPrintedAt(now);
        }
        printTaskMapper.updateById(task);

        OrderRecord order = orderRecordMapper.selectById(task.getOrderId());
        PrintPreview preview = printPreviewMapper.selectById(task.getPreviewId());
        return PrintTaskResponse.from(task, order, preview);
    }

    private PrintPreview getReadyPreview(Long previewId) {
        if (previewId == null) {
            throw new BusinessException("打印预览 ID 不能为空");
        }
        PrintPreview preview = printPreviewMapper.selectById(previewId);
        if (preview == null) {
            throw new BusinessException("打印预览不存在: " + previewId);
        }
        if (!PrintPreviewStatus.READY.name().equals(preview.getStatus())) {
            throw new BusinessException("打印预览未就绪: " + preview.getStatus());
        }
        return preview;
    }

    private OrderRecord getRequiredOrder(Long orderId) {
        OrderRecord order = orderRecordMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }
        return order;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
