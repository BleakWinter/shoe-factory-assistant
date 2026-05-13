package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.enums.PrintPreviewStatus;
import com.shoefactory.assistant.enums.PrintType;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PrintPreviewServiceImpl implements PrintPreviewService {

    // 新表结构不再持久化 print_preview，这里保存本次进程生成的 PDF 临时映射。
    private final Map<Long, Path> previewPathCache = new ConcurrentHashMap<>();
    private final AtomicLong previewIdSequence = new AtomicLong(System.currentTimeMillis());

    private final OrderRecordMapper orderRecordMapper;
    private final ExcelPdfService excelPdfService;
    private final FileStorageUtil fileStorageUtil;

    public PrintPreviewServiceImpl(
            OrderRecordMapper orderRecordMapper,
            ExcelPdfService excelPdfService,
            FileStorageUtil fileStorageUtil
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.excelPdfService = excelPdfService;
        this.fileStorageUtil = fileStorageUtil;
    }

    @Override
    public PrintPreviewResponse generatePreview(Long orderId, PrintType printType) {
        OrderRecord order = getRequiredOrder(orderId);
        if (OrderRecognitionStatus.FAILED.getCode() == nullToZero(order.getRecognitionStatus())) {
            throw new BusinessException("订单识别失败，不能生成打印预览: " + order.getErrorMessage());
        }
        if (order.getOriginalFilePath() == null || order.getOriginalFilePath().isBlank()) {
            throw new BusinessException("订单原稿路径为空，不能生成打印预览");
        }

        String previewNo = FileStorageUtil.newBusinessNo("PV");
        Long previewId = previewIdSequence.incrementAndGet();
        Path existingPdf = existingPdfPath(order, printType);
        if (existingPdf != null && Files.isRegularFile(existingPdf)) {
            previewPathCache.put(previewId, existingPdf);
            return buildReadyResponse(
                    previewId,
                    previewNo,
                    order,
                    printType,
                    existingPdf,
                    existingGeneratedAt(order, printType)
            );
        }

        Path targetPdf = fileStorageUtil.allocateOrderPdfPath(order.getOrderNo(), printType.name().toLowerCase(Locale.ROOT));
        LocalDateTime now = LocalDateTime.now();

        try {
            // PrintType 决定取“订单”sheet 还是“装箱单”sheet。
            excelPdfService.convertSheetToPdf(fileStorageUtil.resolvePath(order.getOriginalFilePath()), targetPdf, printType.getSheetName());
            previewPathCache.put(previewId, targetPdf);
            bindPdfPath(order, printType, targetPdf, now);
            orderRecordMapper.updateById(order);
            return buildReadyResponse(previewId, previewNo, order, printType, targetPdf, now);
        } catch (Exception ex) {
            throw new BusinessException("生成打印预览失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public PrintPreviewResponse regeneratePreview(Long orderId, PrintType printType) {
        OrderRecord order = getRequiredOrder(orderId);
        deletePdfAndClearPath(order, printType);
        return generatePreview(orderId, printType);
    }

    @Override
    public Path loadPreviewPdf(Long previewId) {
        Path pdfPath = previewPathCache.get(previewId);
        if (pdfPath == null) {
            throw new BusinessException("打印预览不存在或已过期: " + previewId);
        }
        // 返回 PDF 前再次检查路径和文件存在性，防止临时文件被清理。
        Path resolvedPath = fileStorageUtil.resolvePath(pdfPath.toString());
        fileStorageUtil.ensureExists(resolvedPath);
        return resolvedPath;
    }

    @Override
    public Path loadOrderPdf(Long orderId, PrintType printType) {
        OrderRecord order = getRequiredOrder(orderId);
        String pdfPath = printType == PrintType.ORDER ? order.getOrderPdfPath() : order.getPackingPdfPath();
        if (pdfPath == null || pdfPath.isBlank()) {
            throw new BusinessException("请先生成" + printType.getSheetName() + "PDF 预览");
        }
        Path resolvedPath = fileStorageUtil.resolvePath(pdfPath);
        fileStorageUtil.ensureExists(resolvedPath);
        return resolvedPath;
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

    private void bindPdfPath(OrderRecord order, PrintType printType, Path targetPdf, LocalDateTime generatedAt) {
        if (printType == PrintType.ORDER) {
            order.setOrderPdfPath(targetPdf.toString());
            order.setOrderPdfGeneratedAt(generatedAt);
            return;
        }
        order.setPackingPdfPath(targetPdf.toString());
        order.setPackingPdfGeneratedAt(generatedAt);
    }

    private Path existingPdfPath(OrderRecord order, PrintType printType) {
        String pdfPath = pdfPath(order, printType);
        if (pdfPath == null || pdfPath.isBlank()) {
            return null;
        }
        return fileStorageUtil.resolvePath(pdfPath);
    }

    private String pdfPath(OrderRecord order, PrintType printType) {
        return printType == PrintType.ORDER ? order.getOrderPdfPath() : order.getPackingPdfPath();
    }

    private LocalDateTime existingGeneratedAt(OrderRecord order, PrintType printType) {
        LocalDateTime generatedAt = printType == PrintType.ORDER
                ? order.getOrderPdfGeneratedAt()
                : order.getPackingPdfGeneratedAt();
        return generatedAt == null ? LocalDateTime.now() : generatedAt;
    }

    private PrintPreviewResponse buildReadyResponse(
            Long previewId,
            String previewNo,
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
                previewId,
                previewNo,
                order,
                printType.name(),
                "/api/orders/" + order.getId() + "/pdf/" + printType.name(),
                pdfSize,
                PrintPreviewStatus.READY.name(),
                null,
                createdAt
        );
    }

    private void deletePdfAndClearPath(OrderRecord order, PrintType printType) {
        Path existingPdf = existingPdfPath(order, printType);
        if (existingPdf != null) {
            try {
                Files.deleteIfExists(existingPdf);
            } catch (IOException ex) {
                throw new BusinessException("删除旧 PDF 失败: " + existingPdf, ex);
            }
        }

        LambdaUpdateWrapper<OrderRecord> wrapper = new LambdaUpdateWrapper<OrderRecord>()
                .eq(OrderRecord::getId, order.getId())
                .set(printType == PrintType.ORDER, OrderRecord::getOrderPdfPath, null)
                .set(printType == PrintType.ORDER, OrderRecord::getOrderPdfGeneratedAt, null)
                .set(printType == PrintType.PACKING, OrderRecord::getPackingPdfPath, null)
                .set(printType == PrintType.PACKING, OrderRecord::getPackingPdfGeneratedAt, null)
                .set(OrderRecord::getUpdatedAt, LocalDateTime.now());
        orderRecordMapper.update(null, wrapper);
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
