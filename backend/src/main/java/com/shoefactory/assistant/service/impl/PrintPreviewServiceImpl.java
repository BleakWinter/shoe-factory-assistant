package com.shoefactory.assistant.service.impl;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.PrintPreview;
import com.shoefactory.assistant.entity.SourceFile;
import com.shoefactory.assistant.enums.FileType;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.enums.PrintPreviewStatus;
import com.shoefactory.assistant.enums.PrintType;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.PrintPreviewMapper;
import com.shoefactory.assistant.mapper.SourceFileMapper;
import com.shoefactory.assistant.service.ExcelPdfService;
import com.shoefactory.assistant.service.PrintPreviewService;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class PrintPreviewServiceImpl implements PrintPreviewService {

    private final PrintPreviewMapper printPreviewMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final SourceFileMapper sourceFileMapper;
    private final ExcelPdfService excelPdfService;
    private final FileStorageUtil fileStorageUtil;

    public PrintPreviewServiceImpl(
            PrintPreviewMapper printPreviewMapper,
            OrderRecordMapper orderRecordMapper,
            SourceFileMapper sourceFileMapper,
            ExcelPdfService excelPdfService,
            FileStorageUtil fileStorageUtil
    ) {
        this.printPreviewMapper = printPreviewMapper;
        this.orderRecordMapper = orderRecordMapper;
        this.sourceFileMapper = sourceFileMapper;
        this.excelPdfService = excelPdfService;
        this.fileStorageUtil = fileStorageUtil;
    }

    @Override
    @Transactional
    public PrintPreviewResponse generatePreview(Long orderId, PrintType printType) {
        OrderRecord order = getRequiredOrder(orderId);
        SourceFile sourceFile = getRequiredSourceFile(order.getSourceFileId());
        if (!FileType.EXCEL.name().equals(sourceFile.getFileType())) {
            throw new BusinessException("图片订单暂不支持自动生成打印预览");
        }
        if (OrderRecognitionStatus.FAILED.name().equals(order.getRecognitionStatus())) {
            throw new BusinessException("订单识别失败，不能生成打印预览: " + order.getErrorMessage());
        }

        String previewNo = FileStorageUtil.newBusinessNo("PV");
        Path targetPdf = fileStorageUtil.allocatePreviewPdfPath(previewNo, printType.name().toLowerCase(Locale.ROOT));
        LocalDateTime now = LocalDateTime.now();

        PrintPreview preview = new PrintPreview();
        preview.setPreviewNo(previewNo);
        preview.setOrderId(order.getId());
        preview.setSourceFileId(sourceFile.getId());
        preview.setPrintType(printType.name());
        preview.setPdfPath(targetPdf.toString());
        preview.setPreviewUrl("");
        preview.setStatus(PrintPreviewStatus.READY.name());
        preview.setCreatedAt(now);
        preview.setUpdatedAt(now);

        try {
            excelPdfService.convertSheetToPdf(fileStorageUtil.resolvePath(sourceFile.getOriginalPath()), targetPdf, printType.getSheetName());
            preview.setPdfSize(Files.size(targetPdf));
        } catch (Exception ex) {
            preview.setStatus(PrintPreviewStatus.FAILED.name());
            preview.setErrorMessage(ex.getMessage());
            preview.setPdfSize(null);
        }

        printPreviewMapper.insert(preview);
        preview.setPreviewUrl(fileStorageUtil.buildPreviewUrl(preview.getId()));
        preview.setUpdatedAt(LocalDateTime.now());
        printPreviewMapper.updateById(preview);

        if (PrintPreviewStatus.FAILED.name().equals(preview.getStatus())) {
            throw new BusinessException("生成打印预览失败: " + preview.getErrorMessage());
        }
        return PrintPreviewResponse.from(preview, order);
    }

    @Override
    public Path loadPreviewPdf(Long previewId) {
        PrintPreview preview = printPreviewMapper.selectById(previewId);
        if (preview == null) {
            throw new BusinessException("打印预览不存在: " + previewId);
        }
        if (!PrintPreviewStatus.READY.name().equals(preview.getStatus())) {
            throw new BusinessException("打印预览未就绪: " + preview.getStatus());
        }
        Path pdfPath = fileStorageUtil.resolvePath(preview.getPdfPath());
        fileStorageUtil.ensureExists(pdfPath);
        return pdfPath;
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

    private SourceFile getRequiredSourceFile(Long sourceFileId) {
        SourceFile sourceFile = sourceFileMapper.selectById(sourceFileId);
        if (sourceFile == null) {
            throw new BusinessException("订单原稿不存在: " + sourceFileId);
        }
        return sourceFile;
    }
}
