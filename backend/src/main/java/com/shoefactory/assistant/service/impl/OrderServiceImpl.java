package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.OrderPackingDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.enums.FileType;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.enums.OrderSourceType;
import com.shoefactory.assistant.mapper.OrderDetailProcessMapper;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.service.OrderExcelImportService;
import com.shoefactory.assistant.service.OrderImportResult;
import com.shoefactory.assistant.service.OrderService;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.StoredFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };

    private final OrderRecordMapper orderRecordMapper;
    private final OrderRecordDetailMapper orderRecordDetailMapper;
    private final OrderPackingDetailMapper orderPackingDetailMapper;
    private final OrderDetailProcessMapper orderDetailProcessMapper;
    private final FileStorageUtil fileStorageUtil;
    private final OrderExcelImportService orderExcelImportService;

    public OrderServiceImpl(
            OrderRecordMapper orderRecordMapper,
            OrderRecordDetailMapper orderRecordDetailMapper,
            OrderPackingDetailMapper orderPackingDetailMapper,
            OrderDetailProcessMapper orderDetailProcessMapper,
            FileStorageUtil fileStorageUtil,
            OrderExcelImportService orderExcelImportService
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.orderRecordDetailMapper = orderRecordDetailMapper;
        this.orderPackingDetailMapper = orderPackingDetailMapper;
        this.orderDetailProcessMapper = orderDetailProcessMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.orderExcelImportService = orderExcelImportService;
    }

    @Override
    @Transactional
    public OrderUploadResponse uploadOrderSource(MultipartFile file) {
        String extension = fileStorageUtil.extractExtension(file == null ? null : file.getOriginalFilename());
        FileType fileType = toFileType(extension);
        if (fileType != FileType.EXCEL) {
            throw new BusinessException("V1 仅支持上传 Excel 订单文件");
        }

        String fileNo = FileStorageUtil.newBusinessNo("SF");
        StoredFile storedFile = fileStorageUtil.saveOriginal(file, fileNo);
        OrderRecord orderRecord = readUploadSummary(storedFile, fileNo);
        initializeRecognitionFields(orderRecord);
        orderRecordMapper.insert(orderRecord);
        return buildUploadResponse(orderRecord, 0);
    }

    @Override
    public PageResponse<OrderRecordResponse> listOrders(
            String orderNo,
            String customerName,
            String developmentNo,
            String orderRecognitionStatus,
            String packingRecognitionStatus,
            long page,
            long size
    ) {
        OrderRecognitionStatus parsedOrderStatus = OrderRecognitionStatus.parseNullable(orderRecognitionStatus);
        OrderRecognitionStatus parsedPackingStatus = OrderRecognitionStatus.parseNullable(packingRecognitionStatus);
        Page<OrderRecord> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<OrderRecord>()
                .like(hasText(orderNo), OrderRecord::getOrderNo, orderNo)
                .like(hasText(customerName), OrderRecord::getCustomerName, customerName)
                .like(hasText(developmentNo), OrderRecord::getDevelopmentNos, developmentNo)
                .eq(parsedOrderStatus != null, OrderRecord::getOrderRecognitionStatus,
                        parsedOrderStatus == null ? null : parsedOrderStatus.getCode())
                .eq(parsedPackingStatus != null, OrderRecord::getPackingRecognitionStatus,
                        parsedPackingStatus == null ? null : parsedPackingStatus.getCode())
                .orderByDesc(OrderRecord::getCreatedAt);
        Page<OrderRecord> resultPage = orderRecordMapper.selectPage(pageRequest, wrapper);
        Map<Long, List<OrderRecordDetail>> detailsByOrderId = loadDetailsByOrderId(resultPage.getRecords());
        Map<Long, List<OrderPackingDetail>> packingDetailsByOrderId = loadPackingDetailsByOrderId(resultPage.getRecords());
        List<OrderRecordResponse> records = resultPage.getRecords().stream()
                .map(order -> buildOrderResponse(
                        order,
                        detailsByOrderId.getOrDefault(order.getId(), List.of()),
                        packingDetailsByOrderId.getOrDefault(order.getId(), List.of())
                ))
                .toList();
        return PageResponse.from(resultPage, records);
    }

    @Override
    @Transactional
    public OrderRecordResponse recognizeOrder(Long orderId) {
        OrderRecord order = getRequiredOrder(orderId);
        try {
            StoredFile storedFile = storedFileFromOrder(order);
            deleteOrderDetailRows(orderId);
            OrderImportResult importResult = orderExcelImportService.importOrderDetails(
                    storedFile.getPath(),
                    storedFile,
                    FileStorageUtil.newBusinessNo("SF")
            );
            applyOrderSummary(order, importResult.getOrder());
            for (OrderRecordDetail detail : importResult.getDetails()) {
                detail.setOrderId(order.getId());
                orderRecordDetailMapper.insert(detail);
            }
            fillTotalsFromDetails(order, importResult.getDetails());
            fillTotalsFromPackingDetails(order, loadPackingDetails(orderId));
            order.setOrderRecognitionStatus(OrderRecognitionStatus.RECOGNIZED.getCode());
            order.setOrderErrorMessage(null);
            order.setUpdatedAt(LocalDateTime.now());
            syncLegacyRecognitionFields(order);
            orderRecordMapper.updateById(order);
        } catch (RuntimeException ex) {
            markRecognitionFailed(order, true, ex);
        }
        return buildOrderResponse(order, loadDetails(orderId), loadPackingDetails(orderId));
    }

    @Override
    @Transactional
    public OrderRecordResponse recognizePacking(Long orderId) {
        OrderRecord order = getRequiredOrder(orderId);
        try {
            StoredFile storedFile = storedFileFromOrder(order);
            deletePackingDetailRows(orderId);
            List<OrderPackingDetail> packingDetails = orderExcelImportService.importPackingDetails(
                    storedFile.getPath(),
                    order,
                    FileStorageUtil.newBusinessNo("SF")
            );
            if (packingDetails.isEmpty()) {
                throw new BusinessException("装箱单未解析到明细行");
            }
            for (OrderPackingDetail detail : packingDetails) {
                detail.setOrderId(order.getId());
                orderPackingDetailMapper.insert(detail);
            }
            fillTotalsFromPackingDetails(order, packingDetails);
            order.setPackingRecognitionStatus(OrderRecognitionStatus.RECOGNIZED.getCode());
            order.setPackingErrorMessage(null);
            order.setUpdatedAt(LocalDateTime.now());
            syncLegacyRecognitionFields(order);
            orderRecordMapper.updateById(order);
        } catch (RuntimeException ex) {
            markRecognitionFailed(order, false, ex);
        }
        return buildOrderResponse(order, loadDetails(orderId), loadPackingDetails(orderId));
    }

    @Override
    public List<OrderRecordDetailResponse> listOrderDetails(Long orderId) {
        getRequiredOrder(orderId);
        List<OrderRecordDetail> details = loadDetails(orderId);
        if (details.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<OrderDetailProcess>> processMap = orderDetailProcessMapper.selectList(new LambdaQueryWrapper<OrderDetailProcess>()
                        .eq(OrderDetailProcess::getOrderId, orderId)
                        .orderByAsc(OrderDetailProcess::getProcessType))
                .stream()
                .collect(Collectors.groupingBy(OrderDetailProcess::getOrderDetailId));
        return details.stream()
                .map(detail -> OrderRecordDetailResponse.from(detail, processMap.getOrDefault(detail.getId(), List.of())))
                .toList();
    }

    @Override
    public List<OrderPackingDetailResponse> listOrderPackingDetails(Long orderId) {
        getRequiredOrder(orderId);
        return loadPackingDetails(orderId).stream()
                .map(OrderPackingDetailResponse::from)
                .toList();
    }

    @Override
    public Path loadOrderDetailImage(Long detailId) {
        OrderRecordDetail detail = orderRecordDetailMapper.selectById(detailId);
        if (detail == null || detail.getStyleImagePath() == null || detail.getStyleImagePath().isBlank()) {
            throw new BusinessException("订单图片不存在: " + detailId);
        }
        Path imagePath = fileStorageUtil.resolvePath(detail.getStyleImagePath());
        fileStorageUtil.ensureExists(imagePath);
        return imagePath;
    }

    @Override
    public Path loadOrderPackingDetailImage(Long detailId) {
        OrderPackingDetail detail = orderPackingDetailMapper.selectById(detailId);
        if (detail == null || detail.getStyleImagePath() == null || detail.getStyleImagePath().isBlank()) {
            throw new BusinessException("装箱单图片不存在: " + detailId);
        }
        Path imagePath = fileStorageUtil.resolvePath(detail.getStyleImagePath());
        fileStorageUtil.ensureExists(imagePath);
        return imagePath;
    }

    private OrderRecord readUploadSummary(StoredFile storedFile, String fileNo) {
        try {
            return orderExcelImportService.readOrderSummary(
                    fileStorageUtil.resolvePath(storedFile.getPath().toString()),
                    storedFile
            );
        } catch (RuntimeException ex) {
            OrderRecord fallback = new OrderRecord();
            fallback.setOriginalFileName(storedFile.getOriginalName());
            fallback.setOriginalFilePath(storedFile.getPath().toString());
            fallback.setOrderNo(fileNo);
            fallback.setCustomerName(null);
            fallback.setOrderPrinted(false);
            fallback.setPackingPrinted(false);
            fallback.setTotalQuantity(0);
            fallback.setTotalCartonCount(0);
            fallback.setSourceType(OrderSourceType.EXCEL.getCode());
            fallback.setCreatedAt(LocalDateTime.now());
            fallback.setUpdatedAt(LocalDateTime.now());
            return fallback;
        }
    }

    private void initializeRecognitionFields(OrderRecord order) {
        if (!hasText(order.getOrderNo())) {
            order.setOrderNo(FileStorageUtil.newBusinessNo("SF"));
        }
        if (order.getOrderPrinted() == null) {
            order.setOrderPrinted(false);
        }
        if (order.getPackingPrinted() == null) {
            order.setPackingPrinted(false);
        }
        if (order.getTotalQuantity() == null) {
            order.setTotalQuantity(0);
        }
        if (order.getTotalCartonCount() == null) {
            order.setTotalCartonCount(0);
        }
        order.setSourceType(OrderSourceType.EXCEL.getCode());
        order.setOrderRecognitionStatus(OrderRecognitionStatus.PENDING.getCode());
        order.setPackingRecognitionStatus(OrderRecognitionStatus.PENDING.getCode());
        order.setOrderErrorMessage(null);
        order.setPackingErrorMessage(null);
        syncLegacyRecognitionFields(order);
        LocalDateTime now = LocalDateTime.now();
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(now);
        }
        order.setUpdatedAt(now);
    }

    private OrderUploadResponse buildUploadResponse(OrderRecord orderRecord, int lineCount) {
        OrderUploadResponse response = new OrderUploadResponse();
        response.setOrderId(orderRecord.getId());
        response.setOrderNo(orderRecord.getOrderNo());
        response.setCustomerName(orderRecord.getCustomerName());
        response.setLineCount(lineCount);
        response.setTotalPairs(orderRecord.getTotalQuantity());
        response.setPrintTaskId(orderRecord.getId());
        response.setPrintTaskNo(orderRecord.getOrderNo());
        return response;
    }

    private FileType toFileType(String extension) {
        try {
            return FileType.fromExtension(extension);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("订单原稿仅支持 Excel 或图片文件: " + extension, ex);
        }
    }

    private OrderRecord getRequiredOrder(Long orderId) {
        OrderRecord order = orderRecordMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在: " + orderId);
        }
        return order;
    }

    private StoredFile storedFileFromOrder(OrderRecord order) {
        if (!hasText(order.getOriginalFilePath())) {
            throw new BusinessException("订单原稿路径为空，不能识别");
        }
        Path path = fileStorageUtil.resolvePath(order.getOriginalFilePath());
        String originalName = hasText(order.getOriginalFileName())
                ? order.getOriginalFileName()
                : path.getFileName().toString();
        String extension = fileStorageUtil.extractExtension(originalName);
        return new StoredFile(originalName, extension, null, 0, path);
    }

    private void deleteOrderDetailRows(Long orderId) {
        orderDetailProcessMapper.delete(new LambdaQueryWrapper<OrderDetailProcess>()
                .eq(OrderDetailProcess::getOrderId, orderId));
        orderRecordDetailMapper.delete(new LambdaQueryWrapper<OrderRecordDetail>()
                .eq(OrderRecordDetail::getOrderId, orderId));
    }

    private void deletePackingDetailRows(Long orderId) {
        orderPackingDetailMapper.delete(new LambdaQueryWrapper<OrderPackingDetail>()
                .eq(OrderPackingDetail::getOrderId, orderId));
    }

    private void applyOrderSummary(OrderRecord target, OrderRecord parsed) {
        if (parsed == null) {
            return;
        }
        if (hasText(parsed.getOrderNo())) {
            target.setOrderNo(parsed.getOrderNo());
        }
        if (hasText(parsed.getCustomerName())) {
            target.setCustomerName(parsed.getCustomerName());
        }
        target.setDevelopmentNos(parsed.getDevelopmentNos());
        target.setTotalQuantity(nullToZero(parsed.getTotalQuantity()));
        target.setTotalCartonCount(nullToZero(parsed.getTotalCartonCount()));
        target.setSourceType(OrderSourceType.EXCEL.getCode());
    }

    private void markRecognitionFailed(OrderRecord order, boolean orderRecognition, RuntimeException ex) {
        String message = ex.getMessage();
        if (!hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        if (orderRecognition) {
            order.setOrderRecognitionStatus(OrderRecognitionStatus.FAILED.getCode());
            order.setOrderErrorMessage(message);
        } else {
            order.setPackingRecognitionStatus(OrderRecognitionStatus.FAILED.getCode());
            order.setPackingErrorMessage(message);
        }
        order.setUpdatedAt(LocalDateTime.now());
        syncLegacyRecognitionFields(order);
        orderRecordMapper.updateById(order);
    }

    private void syncLegacyRecognitionFields(OrderRecord order) {
        int orderStatus = statusOrPending(order.getOrderRecognitionStatus());
        int packingStatus = statusOrPending(order.getPackingRecognitionStatus());
        int failed = OrderRecognitionStatus.FAILED.getCode();
        int manual = OrderRecognitionStatus.PENDING_MANUAL.getCode();
        int recognized = OrderRecognitionStatus.RECOGNIZED.getCode();
        if (orderStatus == failed || packingStatus == failed) {
            order.setRecognitionStatus(failed);
        } else if (orderStatus == manual || packingStatus == manual) {
            order.setRecognitionStatus(manual);
        } else if (orderStatus == recognized && packingStatus == recognized) {
            order.setRecognitionStatus(recognized);
        } else {
            order.setRecognitionStatus(OrderRecognitionStatus.PENDING.getCode());
        }
        order.setErrorMessage(firstText(order.getOrderErrorMessage(), order.getPackingErrorMessage()));
    }

    private int statusOrPending(Integer status) {
        return status == null ? OrderRecognitionStatus.PENDING.getCode() : status;
    }

    private String firstText(String left, String right) {
        if (hasText(left)) {
            return left;
        }
        return hasText(right) ? right : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private List<OrderRecordDetail> loadDetails(Long orderId) {
        return orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                .eq(OrderRecordDetail::getOrderId, orderId)
                .orderByAsc(OrderRecordDetail::getRowIndex)
                .orderByAsc(OrderRecordDetail::getLineNo));
    }

    private List<OrderPackingDetail> loadPackingDetails(Long orderId) {
        return orderPackingDetailMapper.selectList(new LambdaQueryWrapper<OrderPackingDetail>()
                .eq(OrderPackingDetail::getOrderId, orderId)
                .orderByAsc(OrderPackingDetail::getRowIndex)
                .orderByAsc(OrderPackingDetail::getLineNo));
    }

    private Map<Long, List<OrderRecordDetail>> loadDetailsByOrderId(List<OrderRecord> orders) {
        List<Long> orderIds = orders.stream()
                .map(OrderRecord::getId)
                .filter(id -> id != null)
                .toList();
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                        .in(OrderRecordDetail::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderRecordDetail::getOrderId));
    }

    private Map<Long, List<OrderPackingDetail>> loadPackingDetailsByOrderId(List<OrderRecord> orders) {
        List<Long> orderIds = orders.stream()
                .map(OrderRecord::getId)
                .filter(id -> id != null)
                .toList();
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderPackingDetailMapper.selectList(new LambdaQueryWrapper<OrderPackingDetail>()
                        .in(OrderPackingDetail::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderPackingDetail::getOrderId));
    }

    private OrderRecordResponse buildOrderResponse(
            OrderRecord order,
            List<OrderRecordDetail> details,
            List<OrderPackingDetail> packingDetails
    ) {
        OrderRecordResponse response = OrderRecordResponse.from(order);
        DetailTotals totals = summarizeDetails(details);
        DetailTotals packingTotals = summarizePackingDetails(packingDetails);
        if (totals.quantity() > 0) {
            response.setTotalQuantity(totals.quantity());
        } else if (packingTotals.quantity() > 0) {
            response.setTotalQuantity(packingTotals.quantity());
        }
        if (packingTotals.cartonCount() > 0) {
            response.setTotalCartonCount(packingTotals.cartonCount());
        } else if (totals.cartonCount() > 0) {
            response.setTotalCartonCount(totals.cartonCount());
        }
        return response;
    }

    private DetailTotals summarizeDetails(List<OrderRecordDetail> details) {
        int quantity = 0;
        int cartonCount = 0;
        for (OrderRecordDetail detail : details) {
            int sizeTotal = sumSizeQuantities(detail.getSizeQuantitiesJson());
            if (sizeTotal > 0) {
                quantity += sizeTotal;
            } else if (detail.getQuantity() != null && detail.getQuantity() > 0) {
                quantity += detail.getQuantity();
            }
            if (detail.getCartonCount() != null && detail.getCartonCount() > 0) {
                cartonCount += detail.getCartonCount();
            }
        }
        return new DetailTotals(quantity, cartonCount);
    }

    private DetailTotals summarizePackingDetails(List<OrderPackingDetail> details) {
        int quantity = 0;
        int cartonCount = 0;
        for (OrderPackingDetail detail : details) {
            if (detail.getTotalPairs() != null && detail.getTotalPairs() > 0) {
                quantity += detail.getTotalPairs();
            } else {
                quantity += sumSizeQuantities(detail.getSizeQuantitiesJson());
            }
            if (detail.getCartonCount() != null && detail.getCartonCount() > 0) {
                cartonCount += detail.getCartonCount();
            }
        }
        return new DetailTotals(quantity, cartonCount);
    }

    private void fillTotalsFromDetails(OrderRecord order, List<OrderRecordDetail> details) {
        DetailTotals totals = summarizeDetails(details);
        if (totals.quantity() > 0) {
            order.setTotalQuantity(totals.quantity());
        }
        if (totals.cartonCount() > 0) {
            order.setTotalCartonCount(totals.cartonCount());
        }
    }

    private void fillTotalsFromPackingDetails(OrderRecord order, List<OrderPackingDetail> packingDetails) {
        DetailTotals totals = summarizePackingDetails(packingDetails);
        if ((order.getTotalQuantity() == null || order.getTotalQuantity() <= 0) && totals.quantity() > 0) {
            order.setTotalQuantity(totals.quantity());
        }
        if (totals.cartonCount() > 0) {
            order.setTotalCartonCount(totals.cartonCount());
        }
    }

    private int sumSizeQuantities(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            return OBJECT_MAPPER.readValue(json, SIZE_MAP_TYPE)
                    .values()
                    .stream()
                    .filter(value -> value != null && value > 0)
                    .mapToInt(Integer::intValue)
                    .sum();
        } catch (Exception ex) {
            return 0;
        }
    }

    private record DetailTotals(int quantity, int cartonCount) {
    }
}
