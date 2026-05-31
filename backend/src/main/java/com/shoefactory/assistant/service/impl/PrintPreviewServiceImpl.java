package com.shoefactory.assistant.service.impl;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderSheetPrintTask;
import com.shoefactory.assistant.enums.PrintPreviewStatus;
import com.shoefactory.assistant.enums.PrintTaskStatus;
import com.shoefactory.assistant.enums.PrintType;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.OrderSheetPrintTaskMapper;
import com.shoefactory.assistant.service.ExcelPdfService;
import com.shoefactory.assistant.service.PrintPreviewService;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class PrintPreviewServiceImpl implements PrintPreviewService {

    private static final DateTimeFormatter PDF_VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final OrderRecordMapper orderRecordMapper;
    private final OrderSheetPrintTaskMapper orderSheetPrintTaskMapper;
    private final ExcelPdfService excelPdfService;
    private final FileStorageUtil fileStorageUtil;

    public PrintPreviewServiceImpl(
            OrderRecordMapper orderRecordMapper,
            OrderSheetPrintTaskMapper orderSheetPrintTaskMapper,
            ExcelPdfService excelPdfService,
            FileStorageUtil fileStorageUtil
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.orderSheetPrintTaskMapper = orderSheetPrintTaskMapper;
        this.excelPdfService = excelPdfService;
        this.fileStorageUtil = fileStorageUtil;
    }

    @Override
    public PrintPreviewResponse generatePreview(OrderSheetPrintTask task) {
        OrderSheetPrintTask requiredTask = requireTask(task);
        PrintType printType = PrintType.fromCode(requiredTask.getPrintType());
        if (printType.getSheetName() == null || printType.getSheetName().isBlank()) {
            throw new BusinessException(printType.getLabel() + "暂未配置来源 sheet，不能生成预览");
        }

        OrderRecord order = getRequiredOrder(requiredTask.getOrderId());
        Path sourceExcel = sourceExcelPath(requiredTask, printType);
        Path existingPdf = existingPdfPath(requiredTask);
        if (existingPdf != null && Files.isRegularFile(existingPdf)) {
            LocalDateTime generatedAt = existingGeneratedAt(requiredTask);
            markPreviewReady(requiredTask, generatedAt);
            return buildReadyResponse(requiredTask, order, printType, existingPdf, generatedAt);
        }

        Path targetPdf = fileStorageUtil.allocateOrderPdfPath(
                order.getOrderNo(),
                printType.name().toLowerCase(Locale.ROOT)
        );
        LocalDateTime now = LocalDateTime.now();

        try {
            excelPdfService.convertSheetToPdf(
                    sourceExcel,
                    targetPdf,
                    printType.getSheetName()
            );
            requiredTask.setPreviewPdfPath(targetPdf.toString());
            requiredTask.setPdfGeneratedAt(now);
            markPreviewReadyStatus(requiredTask);
            requiredTask.setErrorMessage(null);
            requiredTask.setUpdatedAt(now);
            orderSheetPrintTaskMapper.updateById(requiredTask);
            return buildReadyResponse(requiredTask, order, printType, targetPdf, now);
        } catch (Exception ex) {
            String message = failureMessage(ex);
            requiredTask.setStatus(PrintTaskStatus.FAILED.getCode());
            requiredTask.setErrorMessage(message);
            requiredTask.setUpdatedAt(LocalDateTime.now());
            orderSheetPrintTaskMapper.updateById(requiredTask);
            throw new BusinessException("生成打印预览失败: " + message, ex);
        }
    }

    @Override
    public PrintPreviewResponse regeneratePreview(OrderSheetPrintTask task) {
        OrderSheetPrintTask requiredTask = requireTask(task);
        deletePdfAndClearPath(requiredTask);
        return generatePreview(requiredTask);
    }

    @Override
    public Path loadTaskPdf(OrderSheetPrintTask task) {
        OrderSheetPrintTask requiredTask = requireTask(task);
        Path pdfPath = existingPdfPath(requiredTask);
        if (pdfPath == null) {
            throw new BusinessException("请先生成 PDF 预览");
        }
        fileStorageUtil.ensureExists(pdfPath);
        return pdfPath;
    }

    private OrderSheetPrintTask requireTask(OrderSheetPrintTask task) {
        if (task == null || task.getId() == null) {
            throw new BusinessException("打印任务不存在");
        }
        return task;
    }

    private OrderRecord getRequiredOrder(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("订单 ID 不能为空");
        }
        OrderRecord order = orderRecordMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }
        return order;
    }

    private Path existingPdfPath(OrderSheetPrintTask task) {
        String pdfPath = task.getPreviewPdfPath();
        if (pdfPath == null || pdfPath.isBlank()) {
            return null;
        }
        return fileStorageUtil.resolvePath(pdfPath);
    }

    private Path sourceExcelPath(OrderSheetPrintTask task, PrintType printType) {
        if (task.getOriginalFilePath() == null || task.getOriginalFilePath().isBlank()) {
            throw new BusinessException(printType.getLabel() + "原稿路径为空，不能生成预览");
        }
        return fileStorageUtil.resolvePath(task.getOriginalFilePath());
    }

    private LocalDateTime existingGeneratedAt(OrderSheetPrintTask task) {
        return task.getPdfGeneratedAt() == null ? LocalDateTime.now() : task.getPdfGeneratedAt();
    }

    private void markPreviewReady(OrderSheetPrintTask task, LocalDateTime generatedAt) {
        boolean changed = false;
        if (task.getPdfGeneratedAt() == null && generatedAt != null) {
            task.setPdfGeneratedAt(generatedAt);
            changed = true;
        }
        if (task.getErrorMessage() != null && !task.getErrorMessage().isBlank()) {
            task.setErrorMessage(null);
            changed = true;
        }
        changed = markPreviewReadyStatus(task) || changed;
        if (changed) {
            task.setUpdatedAt(LocalDateTime.now());
            orderSheetPrintTaskMapper.updateById(task);
        }
    }

    private boolean markPreviewReadyStatus(OrderSheetPrintTask task) {
        if (PrintTaskStatus.fromCode(task.getStatus()) != PrintTaskStatus.FAILED) {
            return false;
        }
        PrintTaskStatus readyStatus = task.getPrintCount() != null && task.getPrintCount() > 0
                ? PrintTaskStatus.PRINTED
                : PrintTaskStatus.PENDING;
        task.setStatus(readyStatus.getCode());
        return true;
    }

    private String failureMessage(Exception ex) {
        if (ex == null) {
            return "Unknown error";
        }
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private PrintPreviewResponse buildReadyResponse(
            OrderSheetPrintTask task,
            OrderRecord order,
            PrintType printType,
            Path pdfPath,
            LocalDateTime createdAt
    ) {
        long pdfSize;
        try {
            pdfSize = Files.size(pdfPath);
        } catch (IOException ex) {
            throw new BusinessException("读取 PDF 文件大小失败: " + pdfPath, ex);
        }
        return PrintPreviewResponse.generated(
                task.getId(),
                FileStorageUtil.newBusinessNo("PV"),
                order.getId(),
                order.getOrderNo(),
                printType.name(),
                previewUrl(task, createdAt),
                pdfSize,
                PrintPreviewStatus.READY.name(),
                null,
                createdAt
        );
    }

    private String previewUrl(OrderSheetPrintTask task, LocalDateTime createdAt) {
        String version = createdAt == null
                ? String.valueOf(System.currentTimeMillis())
                : PDF_VERSION_FORMATTER.format(createdAt);
        return "/api/print-tasks/" + task.getId() + "/pdf?v=" + version;
    }

    private void deletePdfAndClearPath(OrderSheetPrintTask task) {
        Path existingPdf = existingPdfPath(task);
        if (existingPdf != null) {
            try {
                Files.deleteIfExists(existingPdf);
            } catch (IOException ex) {
                throw new BusinessException("删除旧 PDF 失败: " + existingPdf, ex);
            }
        }
        task.setPreviewPdfPath(null);
        task.setPdfGeneratedAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        orderSheetPrintTaskMapper.updateById(task);
    }
}
