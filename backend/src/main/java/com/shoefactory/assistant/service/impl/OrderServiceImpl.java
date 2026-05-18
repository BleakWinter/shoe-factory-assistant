package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import com.shoefactory.assistant.dto.OrderPackingDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShoeStyleConfig;
import com.shoefactory.assistant.enums.FileType;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.enums.OrderSourceType;
import com.shoefactory.assistant.mapper.OrderDetailProcessMapper;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShoeStyleConfigMapper;
import com.shoefactory.assistant.service.OrderExcelImportService;
import com.shoefactory.assistant.service.OrderImportResult;
import com.shoefactory.assistant.service.OrderService;
import com.shoefactory.assistant.service.StyleConfigService;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.StoredFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };

    private enum RecognitionFilter {
        ORDER_PENDING,
        PACKING_PENDING,
        ORDER_RECOGNIZED,
        PACKING_RECOGNIZED,
        FAILED
    }

    private final OrderRecordMapper orderRecordMapper;
    private final OrderRecordDetailMapper orderRecordDetailMapper;
    private final OrderPackingDetailMapper orderPackingDetailMapper;
    private final OrderDetailProcessMapper orderDetailProcessMapper;
    private final ShoeStyleConfigMapper shoeStyleConfigMapper;
    private final FileStorageUtil fileStorageUtil;
    private final OrderExcelImportService orderExcelImportService;
    private final StyleConfigService styleConfigService;

    public OrderServiceImpl(
            OrderRecordMapper orderRecordMapper,
            OrderRecordDetailMapper orderRecordDetailMapper,
            OrderPackingDetailMapper orderPackingDetailMapper,
            OrderDetailProcessMapper orderDetailProcessMapper,
            ShoeStyleConfigMapper shoeStyleConfigMapper,
            FileStorageUtil fileStorageUtil,
            OrderExcelImportService orderExcelImportService,
            StyleConfigService styleConfigService
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.orderRecordDetailMapper = orderRecordDetailMapper;
        this.orderPackingDetailMapper = orderPackingDetailMapper;
        this.orderDetailProcessMapper = orderDetailProcessMapper;
        this.shoeStyleConfigMapper = shoeStyleConfigMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.orderExcelImportService = orderExcelImportService;
        this.styleConfigService = styleConfigService;
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
        styleConfigService.ensureConfigsForDevelopmentNos(splitDevelopmentNos(orderRecord.getDevelopmentNos()));
        return buildUploadResponse(orderRecord, 0);
    }

    @Override
    public PageResponse<OrderRecordResponse> listOrders(
            String orderNo,
            String developmentNos,
            String recognitionStatus,
            long page,
            long size
    ) {
        List<RecognitionFilter> parsedRecognitionFilters = parseRecognitionFilters(recognitionStatus);
        List<String> parsedDevelopmentNos = splitDevelopmentNos(developmentNos);
        List<Long> developmentOrderIds = findOrderIdsByDevelopmentNos(parsedDevelopmentNos);
        Page<OrderRecord> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<OrderRecord> wrapper = new LambdaQueryWrapper<OrderRecord>()
                .like(hasText(orderNo), OrderRecord::getOrderNo, orderNo)
                .orderByDesc(OrderRecord::getCreatedAt);
        applyDevelopmentNoFilter(wrapper, parsedDevelopmentNos, developmentOrderIds);
        applyRecognitionFilters(wrapper, parsedRecognitionFilters);
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

    private List<Long> findOrderIdsByDevelopmentNos(List<String> developmentNos) {
        if (developmentNos.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<OrderRecordDetail> wrapper = new LambdaQueryWrapper<OrderRecordDetail>()
                .and(nested -> {
                    for (int index = 0; index < developmentNos.size(); index++) {
                        if (index > 0) {
                            nested.or();
                        }
                        nested.like(OrderRecordDetail::getDevelopmentNo, developmentNos.get(index));
                    }
                });
        return orderRecordDetailMapper.selectList(wrapper)
                .stream()
                .map(OrderRecordDetail::getOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private void applyDevelopmentNoFilter(
            LambdaQueryWrapper<OrderRecord> wrapper,
            List<String> developmentNos,
            List<Long> developmentOrderIds
    ) {
        if (developmentNos.isEmpty()) {
            return;
        }
        wrapper.and(nested -> {
            for (int index = 0; index < developmentNos.size(); index++) {
                if (index > 0) {
                    nested.or();
                }
                nested.like(OrderRecord::getDevelopmentNos, developmentNos.get(index));
            }
            if (!developmentOrderIds.isEmpty()) {
                nested.or().in(OrderRecord::getId, developmentOrderIds);
            }
        });
    }

    private List<RecognitionFilter> parseRecognitionFilters(String values) {
        if (!hasText(values)) {
            return List.of();
        }
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .filter(this::hasText)
                .map(this::parseRecognitionFilter)
                .distinct()
                .toList();
    }

    private RecognitionFilter parseRecognitionFilter(String value) {
        return RecognitionFilter.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private void applyRecognitionFilters(
            LambdaQueryWrapper<OrderRecord> wrapper,
            List<RecognitionFilter> filters
    ) {
        if (filters.isEmpty()) {
            return;
        }
        int pending = OrderRecognitionStatus.PENDING.getCode();
        int recognized = OrderRecognitionStatus.RECOGNIZED.getCode();
        int failed = OrderRecognitionStatus.FAILED.getCode();
        wrapper.and(nested -> {
            for (int index = 0; index < filters.size(); index++) {
                if (index > 0) {
                    nested.or();
                }
                RecognitionFilter filter = filters.get(index);
                switch (filter) {
                    case ORDER_PENDING -> nested.eq(OrderRecord::getOrderRecognitionStatus, pending);
                    case PACKING_PENDING -> nested.eq(OrderRecord::getPackingRecognitionStatus, pending);
                    case ORDER_RECOGNIZED -> nested.eq(OrderRecord::getOrderRecognitionStatus, recognized);
                    case PACKING_RECOGNIZED -> nested.eq(OrderRecord::getPackingRecognitionStatus, recognized);
                    case FAILED -> nested.and(failedWrapper -> failedWrapper
                            .eq(OrderRecord::getOrderRecognitionStatus, failed)
                            .or()
                            .eq(OrderRecord::getPackingRecognitionStatus, failed));
                }
            }
        });
    }

    @Override
    public List<DevelopmentNoOptionResponse> listDevelopmentNoOptions() {
        Set<String> developmentNos = new LinkedHashSet<>();
        orderRecordMapper.selectList(new LambdaQueryWrapper<OrderRecord>()
                        .isNotNull(OrderRecord::getDevelopmentNos)
                        .ne(OrderRecord::getDevelopmentNos, ""))
                .stream()
                .flatMap(order -> splitDevelopmentNos(order.getDevelopmentNos()).stream())
                .forEach(developmentNos::add);
        orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                        .isNotNull(OrderRecordDetail::getDevelopmentNo)
                        .ne(OrderRecordDetail::getDevelopmentNo, ""))
                .stream()
                .map(OrderRecordDetail::getDevelopmentNo)
                .filter(this::hasText)
                .map(String::trim)
                .forEach(developmentNos::add);

        List<DevelopmentNoOptionResponse> options = new ArrayList<>();
        developmentNos.forEach(developmentNo ->
                appendDevelopmentNoOption(options, parseDevelopmentNoParts(developmentNo), List.of()));
        sortDevelopmentNoOptions(options);
        return options;
    }

    private List<String> parseDevelopmentNoParts(String value) {
        List<String> parts = Arrays.stream(value.trim().split("-"))
                .map(String::trim)
                .filter(this::hasText)
                .toList();
        if (parts.size() >= 3) {
            return parts.subList(parts.size() - 3, parts.size());
        }
        return parts;
    }

    private void appendDevelopmentNoOption(
            List<DevelopmentNoOptionResponse> nodes,
            List<String> parts,
            List<String> path
    ) {
        if (parts.isEmpty()) {
            return;
        }
        String part = parts.get(0);
        List<String> nextPath = new ArrayList<>(path);
        nextPath.add(part);
        DevelopmentNoOptionResponse node = findDevelopmentNoNode(nodes, part);
        if (node == null) {
            node = new DevelopmentNoOptionResponse(String.join("-", nextPath), part,
                    parts.size() > 1 ? new ArrayList<>() : null);
            nodes.add(node);
        }
        if (parts.size() > 1) {
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            appendDevelopmentNoOption(node.getChildren(), parts.subList(1, parts.size()), nextPath);
        }
    }

    private DevelopmentNoOptionResponse findDevelopmentNoNode(
            List<DevelopmentNoOptionResponse> nodes,
            String label
    ) {
        return nodes.stream()
                .filter(node -> label.equals(node.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private void sortDevelopmentNoOptions(List<DevelopmentNoOptionResponse> options) {
        options.sort((left, right) -> compareDevelopmentNoLabel(left.getLabel(), right.getLabel()));
        options.stream()
                .filter(option -> option.getChildren() != null)
                .forEach(option -> sortDevelopmentNoOptions(option.getChildren()));
    }

    private int compareDevelopmentNoLabel(String left, String right) {
        Integer leftNumber = parseInteger(left);
        Integer rightNumber = parseInteger(right);
        if (leftNumber != null && rightNumber != null && !leftNumber.equals(rightNumber)) {
            return leftNumber.compareTo(rightNumber);
        }
        return left.compareToIgnoreCase(right);
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
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
            styleConfigService.ensureConfigsForDevelopmentNos(importResult.getDetails().stream()
                    .map(OrderRecordDetail::getDevelopmentNo)
                    .toList());
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
        Map<String, String> boxSpecByDevelopmentNo = loadBoxSpecsByDevelopmentNo(details);
        return details.stream()
                .map(detail -> OrderRecordDetailResponse.from(
                        detail,
                        processMap.getOrDefault(detail.getId(), List.of()),
                        boxSpecByDevelopmentNo.get(normalizeDevelopmentNo(detail.getDevelopmentNo()))
                ))
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

    private List<String> splitDevelopmentNos(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(this::hasText)
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private Map<String, String> loadBoxSpecsByDevelopmentNo(List<OrderRecordDetail> details) {
        List<String> developmentNos = details.stream()
                .map(detail -> normalizeDevelopmentNo(detail.getDevelopmentNo()))
                .filter(this::hasText)
                .distinct()
                .toList();
        if (developmentNos.isEmpty()) {
            return Collections.emptyMap();
        }
        return shoeStyleConfigMapper.selectList(new LambdaQueryWrapper<ShoeStyleConfig>()
                        .select(ShoeStyleConfig::getDevelopmentNo, ShoeStyleConfig::getBoxSpec)
                        .in(ShoeStyleConfig::getDevelopmentNo, developmentNos))
                .stream()
                .filter(config -> hasText(config.getDevelopmentNo()))
                .filter(config -> hasText(config.getBoxSpec()))
                .collect(Collectors.toMap(
                        config -> normalizeDevelopmentNo(config.getDevelopmentNo()),
                        ShoeStyleConfig::getBoxSpec,
                        (left, right) -> hasText(left) ? left : right
                ));
    }

    private String normalizeDevelopmentNo(String value) {
        return hasText(value) ? value.trim() : "";
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
            int perCartonPairs = sumSizeQuantities(detail.getSizeQuantitiesJson());
            if (perCartonPairs > 0 && detail.getCartonCount() != null && detail.getCartonCount() > 0) {
                quantity += perCartonPairs * detail.getCartonCount();
            } else if (detail.getTotalPairs() != null && detail.getTotalPairs() > 0) {
                quantity += detail.getTotalPairs();
            } else {
                quantity += perCartonPairs;
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
