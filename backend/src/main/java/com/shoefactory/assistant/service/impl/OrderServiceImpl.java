package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.SourceFile;
import com.shoefactory.assistant.enums.FileType;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.SourceFileMapper;
import com.shoefactory.assistant.service.OrderRecognitionResult;
import com.shoefactory.assistant.service.OrderRecognitionService;
import com.shoefactory.assistant.service.OrderService;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.StoredFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final SourceFileMapper sourceFileMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final FileStorageUtil fileStorageUtil;
    private final OrderRecognitionService orderRecognitionService;

    public OrderServiceImpl(
            SourceFileMapper sourceFileMapper,
            OrderRecordMapper orderRecordMapper,
            FileStorageUtil fileStorageUtil,
            OrderRecognitionService orderRecognitionService
    ) {
        this.sourceFileMapper = sourceFileMapper;
        this.orderRecordMapper = orderRecordMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.orderRecognitionService = orderRecognitionService;
    }

    @Override
    @Transactional
    public OrderRecordResponse uploadOrderSource(MultipartFile file) {
        String extension = fileStorageUtil.extractExtension(file == null ? null : file.getOriginalFilename());
        FileType fileType = toFileType(extension);
        String fileNo = FileStorageUtil.newBusinessNo("SF");
        StoredFile storedFile = fileStorageUtil.saveOriginal(file, fileNo);

        SourceFile sourceFile = buildSourceFile(fileNo, storedFile, fileType);
        sourceFileMapper.insert(sourceFile);

        OrderRecord orderRecord = switch (fileType) {
            case EXCEL -> recognizeExcelOrder(sourceFile);
            case IMAGE -> buildPendingManualOrder(sourceFile);
        };
        orderRecordMapper.insert(orderRecord);
        return OrderRecordResponse.from(orderRecord, sourceFile);
    }

    @Override
    public PageResponse<OrderRecordResponse> listOrders(
            String orderNo,
            String styleNo,
            String customerName,
            LocalDate deliveryDate,
            String recognitionStatus,
            long page,
            long size
    ) {
        OrderRecognitionStatus parsedStatus = OrderRecognitionStatus.parseNullable(recognitionStatus);
        Page<OrderRecord> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<OrderRecord>()
                .like(hasText(orderNo), OrderRecord::getOrderNo, orderNo)
                .like(hasText(styleNo), OrderRecord::getStyleNo, styleNo)
                .like(hasText(customerName), OrderRecord::getCustomerName, customerName)
                .eq(deliveryDate != null, OrderRecord::getDeliveryDate, deliveryDate)
                .eq(parsedStatus != null, OrderRecord::getRecognitionStatus, parsedStatus == null ? null : parsedStatus.name())
                .orderByDesc(OrderRecord::getCreatedAt);
        Page<OrderRecord> resultPage = orderRecordMapper.selectPage(pageRequest, wrapper);
        if (resultPage.getRecords().isEmpty()) {
            return PageResponse.from(resultPage, Collections.emptyList());
        }
        List<Long> sourceFileIds = resultPage.getRecords().stream()
                .map(OrderRecord::getSourceFileId)
                .distinct()
                .toList();
        Map<Long, SourceFile> sourceFileMap = sourceFileMapper.selectBatchIds(sourceFileIds)
                .stream()
                .collect(Collectors.toMap(SourceFile::getId, Function.identity()));
        List<OrderRecordResponse> records = resultPage.getRecords().stream()
                .map(order -> OrderRecordResponse.from(order, sourceFileMap.get(order.getSourceFileId())))
                .toList();
        return PageResponse.from(resultPage, records);
    }

    private SourceFile buildSourceFile(String fileNo, StoredFile storedFile, FileType fileType) {
        LocalDateTime now = LocalDateTime.now();
        SourceFile sourceFile = new SourceFile();
        sourceFile.setFileNo(fileNo);
        sourceFile.setOriginalName(storedFile.getOriginalName());
        sourceFile.setFileExt(storedFile.getExtension());
        sourceFile.setFileType(fileType.name());
        sourceFile.setMimeType(storedFile.getMimeType());
        sourceFile.setFileSize(storedFile.getSize());
        sourceFile.setOriginalPath(storedFile.getPath().toString());
        sourceFile.setCreatedAt(now);
        sourceFile.setUpdatedAt(now);
        return sourceFile;
    }

    private OrderRecord recognizeExcelOrder(SourceFile sourceFile) {
        LocalDateTime now = LocalDateTime.now();
        OrderRecord orderRecord = new OrderRecord();
        orderRecord.setSourceFileId(sourceFile.getId());
        orderRecord.setCreatedAt(now);
        orderRecord.setUpdatedAt(now);
        try {
            OrderRecognitionResult result = orderRecognitionService.recognizeExcelOrder(fileStorageUtil.resolvePath(sourceFile.getOriginalPath()));
            orderRecord.setOrderNo(blankToNull(result.getOrderNo()));
            orderRecord.setCustomerName(blankToNull(result.getCustomerName()));
            orderRecord.setStyleNo(blankToNull(result.getStyleNo()));
            orderRecord.setColor(blankToNull(result.getColor()));
            orderRecord.setQuantity(result.getQuantity());
            orderRecord.setCartonCount(result.getCartonCount());
            orderRecord.setDeliveryDate(result.getDeliveryDate());
            orderRecord.setSourceSheetName(result.getSourceSheetName());
            orderRecord.setErrorMessage(result.getErrorMessage());
            orderRecord.setRecognitionStatus(result.hasCoreFields()
                    ? OrderRecognitionStatus.RECOGNIZED.name()
                    : OrderRecognitionStatus.FAILED.name());
        } catch (Exception ex) {
            orderRecord.setRecognitionStatus(OrderRecognitionStatus.FAILED.name());
            orderRecord.setErrorMessage(ex.getMessage());
        }
        return orderRecord;
    }

    private OrderRecord buildPendingManualOrder(SourceFile sourceFile) {
        LocalDateTime now = LocalDateTime.now();
        OrderRecord orderRecord = new OrderRecord();
        orderRecord.setSourceFileId(sourceFile.getId());
        orderRecord.setRecognitionStatus(OrderRecognitionStatus.PENDING_MANUAL.name());
        orderRecord.setErrorMessage("图片订单暂不做自动识别，请手动补充订单信息");
        orderRecord.setCreatedAt(now);
        orderRecord.setUpdatedAt(now);
        return orderRecord;
    }

    private FileType toFileType(String extension) {
        try {
            return FileType.fromExtension(extension);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("订单原稿仅支持 Excel 或图片文件: " + extension, ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
