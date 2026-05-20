package com.shoefactory.assistant.service.impl;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.entity.OrderPrintTask;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.enums.PrintPreviewStatus;
import com.shoefactory.assistant.enums.PrintType;
import com.shoefactory.assistant.mapper.OrderPrintTaskMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.service.ExcelPdfService;
import com.shoefactory.assistant.service.PrintPreviewService;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PrintPreviewServiceImpl implements PrintPreviewService {

    private final OrderRecordMapper orderRecordMapper;
    private final OrderPrintTaskMapper orderPrintTaskMapper;
    private final ExcelPdfService excelPdfService;
    private final FileStorageUtil fileStorageUtil;

    public PrintPreviewServiceImpl(
            OrderRecordMapper orderRecordMapper,
            OrderPrintTaskMapper orderPrintTaskMapper,
            ExcelPdfService excelPdfService,
            FileStorageUtil fileStorageUtil
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.orderPrintTaskMapper = orderPrintTaskMapper;
        this.excelPdfService = excelPdfService;
        this.fileStorageUtil = fileStorageUtil;
    }

    @Override
    public PrintPreviewResponse generatePreview(OrderPrintTask task) {
        OrderPrintTask requiredTask = requireTask(task);
        PrintType printType = PrintType.fromCode(requiredTask.getPrintType());
        if (printType.getSheetName() == null || printType.getSheetName().isBlank()) {
            throw new BusinessException(printType.getLabel() + "暂未配置来源 sheet，不能生成预览");
        }

        OrderRecord order = getRequiredOrder(requiredTask.getOrderId());
        if (order.getOriginalFilePath() == null || order.getOriginalFilePath().isBlank()) {
            throw new BusinessException("订单原稿路径为空，不能生成预览");
        }
        Path existingPdf = existingPdfPath(requiredTask);
        if (existingPdf != null && Files.isRegularFile(existingPdf)) {
            return buildReadyResponse(requiredTask, order, printType, existingPdf, existingGeneratedAt(requiredTask));
        }

        Path targetPdf = fileStorageUtil.allocateOrderPdfPath(
                order.getOrderNo(),
                printType.name().toLowerCase(Locale.ROOT)
        );
        LocalDateTime now = LocalDateTime.now();

        try {
            excelPdfService.convertSheetToPdf(
                    fileStorageUtil.resolvePath(order.getOriginalFilePath()),
                    targetPdf,
                    printType.getSheetName()
            );
            requiredTask.setPreviewPdfPath(targetPdf.toString());
            requiredTask.setPdfGeneratedAt(now);
            requiredTask.setErrorMessage(null);
            requiredTask.setUpdatedAt(now);
            orderPrintTaskMapper.updateById(requiredTask);
            return buildReadyResponse(requiredTask, order, printType, targetPdf, now);
        } catch (Exception ex) {
            requiredTask.setErrorMessage(ex.getMessage());
            requiredTask.setUpdatedAt(LocalDateTime.now());
            orderPrintTaskMapper.updateById(requiredTask);
            throw new BusinessException("生成打印预览失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public PrintPreviewResponse regeneratePreview(OrderPrintTask task) {
        OrderPrintTask requiredTask = requireTask(task);
        deletePdfAndClearPath(requiredTask);
        return generatePreview(requiredTask);
    }

    @Override
    public Path loadTaskPdf(OrderPrintTask task) {
        OrderPrintTask requiredTask = requireTask(task);
        Path pdfPath = existingPdfPath(requiredTask);
        if (pdfPath == null) {
            throw new BusinessException("请先生成 PDF 预览");
        }
        fileStorageUtil.ensureExists(pdfPath);
        return pdfPath;
    }

    private OrderPrintTask requireTask(OrderPrintTask task) {
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

    private Path existingPdfPath(OrderPrintTask task) {
        String pdfPath = task.getPreviewPdfPath();
        if (pdfPath == null || pdfPath.isBlank()) {
            return null;
        }
        return fileStorageUtil.resolvePath(pdfPath);
    }

    private LocalDateTime existingGeneratedAt(OrderPrintTask task) {
        return task.getPdfGeneratedAt() == null ? LocalDateTime.now() : task.getPdfGeneratedAt();
    }

    private PrintPreviewResponse buildReadyResponse(
            OrderPrintTask task,
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
                "/api/print-tasks/" + task.getId() + "/pdf",
                pdfSize,
                PrintPreviewStatus.READY.name(),
                null,
                createdAt
        );
    }

    private void deletePdfAndClearPath(OrderPrintTask task) {
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
        orderPrintTaskMapper.updateById(task);
    }
}
