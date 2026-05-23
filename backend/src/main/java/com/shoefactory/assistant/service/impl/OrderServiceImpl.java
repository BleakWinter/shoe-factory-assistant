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
import com.shoefactory.assistant.dto.OrderStatisticsResponse;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.DevelopmentNoStatisticNode;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.OrderSheetPrintTask;
import com.shoefactory.assistant.entity.ShoeStyleConfig;
import com.shoefactory.assistant.enums.FileType;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.enums.OrderSourceType;
import com.shoefactory.assistant.enums.PrintTaskStatus;
import com.shoefactory.assistant.enums.PrintType;
import com.shoefactory.assistant.mapper.OrderDetailProcessMapper;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.OrderSheetPrintTaskMapper;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private final OrderSheetPrintTaskMapper orderSheetPrintTaskMapper;
    private final OrderRecordDetailMapper orderRecordDetailMapper;
    private final OrderPackingDetailMapper orderPackingDetailMapper;
    private final OrderDetailProcessMapper orderDetailProcessMapper;
    private final ShoeStyleConfigMapper shoeStyleConfigMapper;
    private final FileStorageUtil fileStorageUtil;
    private final OrderExcelImportService orderExcelImportService;
    private final StyleConfigService styleConfigService;

    public OrderServiceImpl(
            OrderRecordMapper orderRecordMapper,
            OrderSheetPrintTaskMapper orderSheetPrintTaskMapper,
            OrderRecordDetailMapper orderRecordDetailMapper,
            OrderPackingDetailMapper orderPackingDetailMapper,
            OrderDetailProcessMapper orderDetailProcessMapper,
            ShoeStyleConfigMapper shoeStyleConfigMapper,
            FileStorageUtil fileStorageUtil,
            OrderExcelImportService orderExcelImportService,
            StyleConfigService styleConfigService
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.orderSheetPrintTaskMapper = orderSheetPrintTaskMapper;
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
        ensureOrderNoNotUploaded(orderRecord.getOrderNo());
        orderRecordMapper.insert(orderRecord);
        List<OrderSheetPrintTask> printTasks = createSheetPrintTasks(orderRecord, storedFile);
        styleConfigService.ensureConfigsForDevelopmentNos(splitDevelopmentNos(orderRecord.getDevelopmentNos()));
        return buildUploadResponse(orderRecord, firstTaskId(printTasks), 0);
    }

    @Override
    @Transactional
    public OrderUploadResponse reuploadOrderSource(Long orderId, MultipartFile file) {
        OrderRecord existingOrder = getRequiredOrder(orderId);
        String extension = fileStorageUtil.extractExtension(file == null ? null : file.getOriginalFilename());
        FileType fileType = toFileType(extension);
        if (fileType != FileType.EXCEL) {
            throw new BusinessException("V1 仅支持上传 Excel 订单文件");
        }

        String fileNo = FileStorageUtil.newBusinessNo("SF");
        StoredFile storedFile = fileStorageUtil.saveOriginal(file, fileNo);
        OrderRecord parsedOrder = readUploadSummary(storedFile, fileNo);
        initializeRecognitionFields(parsedOrder);
        ensureReuploadOrderNoMatches(existingOrder, parsedOrder);

        applyReuploadSummary(existingOrder, parsedOrder);
        deleteOrderDetailRows(orderId);
        deletePackingDetailRows(orderId);
        invalidateSheetPrintTasks(orderId);
        orderRecordMapper.updateById(existingOrder);

        List<OrderSheetPrintTask> printTasks = createSheetPrintTasks(existingOrder, storedFile);
        styleConfigService.ensureConfigsForDevelopmentNos(splitDevelopmentNos(existingOrder.getDevelopmentNos()));
        return buildUploadResponse(existingOrder, firstTaskId(printTasks), 0);
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

    @Override
    public OrderStatisticsResponse getOrderStatistics() {
        List<OrderRecordDetail> details = orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                .select(
                        OrderRecordDetail::getDevelopmentNo,
                        OrderRecordDetail::getQuantity,
                        OrderRecordDetail::getSizeQuantitiesJson
                ));
        Map<String, DevelopmentNoBucket> buckets = new LinkedHashMap<>();
        int totalPairs = 0;
        for (OrderRecordDetail detail : details) {
            int pairCount = detailPairCount(detail);
            totalPairs += pairCount;
            String developmentNo = normalizeStatisticsDevelopmentNo(detail.getDevelopmentNo());
            buckets.computeIfAbsent(developmentNo, DevelopmentNoBucket::new).add(pairCount);
        }

        OrderStatisticsResponse response = new OrderStatisticsResponse();
        response.setTotalPairs(totalPairs);
        response.setStyleCount(buckets.size());
        response.setDetailCount(details.size());
        response.setDevelopmentNoTree(buildDevelopmentNoStatisticsTree(buckets.values()));
        response.setTopDevelopmentNos(buildTopDevelopmentNoStatistics(buckets.values()));
        return response;
    }

    private List<DevelopmentNoStatisticNode> buildDevelopmentNoStatisticsTree(
            Collection<DevelopmentNoBucket> buckets
    ) {
        List<DevelopmentNoStatisticNode> nodes = new ArrayList<>();
        for (DevelopmentNoBucket bucket : buckets) {
            appendDevelopmentNoStatistic(nodes, bucket);
        }
        sortDevelopmentNoStatisticsTree(nodes);
        return nodes;
    }

    private void appendDevelopmentNoStatistic(
            List<DevelopmentNoStatisticNode> nodes,
            DevelopmentNoBucket bucket
    ) {
        List<String> parts = parseDevelopmentNoParts(bucket.getDevelopmentNo());
        if (parts.isEmpty()) {
            parts = List.of(bucket.getDevelopmentNo());
        }
        List<DevelopmentNoStatisticNode> siblings = nodes;
        List<String> path = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            String part = parts.get(index);
            path.add(part);
            String key = String.join("-", path);
            DevelopmentNoStatisticNode node = findDevelopmentNoStatisticNode(siblings, key);
            if (node == null) {
                node = buildDevelopmentNoStatisticNode(key, part, index + 1);
                siblings.add(node);
            }
            node.setPairCount(nullToZero(node.getPairCount()) + bucket.getPairCount());
            node.setDetailCount(nullToZero(node.getDetailCount()) + bucket.getDetailCount());
            node.setStyleCount(nullToZero(node.getStyleCount()) + 1);
            if (index == parts.size() - 1) {
                node.setFullDevelopmentNo(bucket.getDevelopmentNo());
            }
            siblings = node.getChildren();
        }
    }

    private DevelopmentNoStatisticNode buildDevelopmentNoStatisticNode(String key, String label, int level) {
        DevelopmentNoStatisticNode node = new DevelopmentNoStatisticNode();
        node.setKey(key);
        node.setLabel(label);
        node.setLevel(level);
        node.setPairCount(0);
        node.setDetailCount(0);
        node.setStyleCount(0);
        node.setChildren(new ArrayList<>());
        return node;
    }

    private DevelopmentNoStatisticNode findDevelopmentNoStatisticNode(
            List<DevelopmentNoStatisticNode> nodes,
            String key
    ) {
        return nodes.stream()
                .filter(node -> key.equals(node.getKey()))
                .findFirst()
                .orElse(null);
    }

    private void sortDevelopmentNoStatisticsTree(List<DevelopmentNoStatisticNode> nodes) {
        nodes.sort((left, right) -> compareDevelopmentNoLabel(left.getLabel(), right.getLabel()));
        nodes.forEach(node -> sortDevelopmentNoStatisticsTree(node.getChildren()));
    }

    private List<DevelopmentNoStatisticNode> buildTopDevelopmentNoStatistics(
            Collection<DevelopmentNoBucket> buckets
    ) {
        return buckets.stream()
                .sorted(Comparator.comparingInt(DevelopmentNoBucket::getPairCount)
                        .reversed()
                        .thenComparing(DevelopmentNoBucket::getDevelopmentNo))
                .limit(10)
                .map(this::buildDevelopmentNoStatisticLeaf)
                .toList();
    }

    private DevelopmentNoStatisticNode buildDevelopmentNoStatisticLeaf(DevelopmentNoBucket bucket) {
        DevelopmentNoStatisticNode node = new DevelopmentNoStatisticNode();
        node.setKey(bucket.getDevelopmentNo());
        node.setLabel(bucket.getDevelopmentNo());
        node.setFullDevelopmentNo(bucket.getDevelopmentNo());
        node.setLevel(parseDevelopmentNoParts(bucket.getDevelopmentNo()).size());
        node.setPairCount(bucket.getPairCount());
        node.setDetailCount(bucket.getDetailCount());
        node.setStyleCount(1);
        node.setChildren(List.of());
        return node;
    }

    private int detailPairCount(OrderRecordDetail detail) {
        int sizeTotal = sumSizeQuantities(detail.getSizeQuantitiesJson());
        if (sizeTotal > 0) {
            return sizeTotal;
        }
        return detail.getQuantity() != null && detail.getQuantity() > 0 ? detail.getQuantity() : 0;
    }

    private String normalizeStatisticsDevelopmentNo(String value) {
        return hasText(value) ? value.trim() : "未填款号";
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
            StoredFile storedFile = storedFileFromTask(orderId, PrintType.ORDER);
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
            syncRecognitionErrorMessage(order);
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
            StoredFile storedFile = storedFileFromTask(orderId, PrintType.PACKING);
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
            syncRecognitionErrorMessage(order);
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

    @Override
    public Boolean removeOrderDetailById(Long id) {
        return orderRecordDetailMapper.deleteById(id) > 0;
    }

    private OrderRecord readUploadSummary(StoredFile storedFile, String fileNo) {
        try {
            return orderExcelImportService.readOrderSummary(
                    fileStorageUtil.resolvePath(storedFile.getPath().toString()),
                    storedFile
            );
        } catch (RuntimeException ex) {
            OrderRecord fallback = new OrderRecord();
            fallback.setOrderNo(fileNo);
            fallback.setCustomerName(null);
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
        syncRecognitionErrorMessage(order);
        LocalDateTime now = LocalDateTime.now();
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(now);
        }
        order.setUpdatedAt(now);
    }

    private Long firstTaskId(List<OrderSheetPrintTask> printTasks) {
        if (printTasks == null || printTasks.isEmpty()) {
            return null;
        }
        return printTasks.get(0).getId();
    }

    private OrderUploadResponse buildUploadResponse(OrderRecord orderRecord, Long firstTaskId, int lineCount) {
        OrderUploadResponse response = new OrderUploadResponse();
        response.setOrderId(orderRecord.getId());
        response.setOrderNo(orderRecord.getOrderNo());
        response.setCustomerName(orderRecord.getCustomerName());
        response.setLineCount(lineCount);
        response.setTotalPairs(orderRecord.getTotalQuantity());
        response.setPrintTaskId(firstTaskId);
        response.setPrintTaskNo(orderRecord.getOrderNo());
        return response;
    }

    private void ensureOrderNoNotUploaded(String orderNo) {
        if (!hasText(orderNo)) {
            return;
        }
        OrderRecord existing = orderRecordMapper.selectOne(new LambdaQueryWrapper<OrderRecord>()
                .eq(OrderRecord::getOrderNo, orderNo)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("订单流水号 " + orderNo + " 已经上传过，请在对应记录上点“重新上传”。");
        }
    }

    private void ensureReuploadOrderNoMatches(OrderRecord existingOrder, OrderRecord parsedOrder) {
        String existingOrderNo = normalizeOrderNo(existingOrder == null ? null : existingOrder.getOrderNo());
        String parsedOrderNo = normalizeOrderNo(parsedOrder == null ? null : parsedOrder.getOrderNo());
        if (!hasText(existingOrderNo) || !hasText(parsedOrderNo) || !existingOrderNo.equals(parsedOrderNo)) {
            throw new BusinessException("重新上传文件里的订单流水号必须和当前订单一致，当前订单："
                    + (hasText(existingOrderNo) ? existingOrderNo : "空")
                    + "，上传文件："
                    + (hasText(parsedOrderNo) ? parsedOrderNo : "空"));
        }
    }

    private void applyReuploadSummary(OrderRecord target, OrderRecord parsed) {
        applyOrderSummary(target, parsed);
        target.setOrderRecognitionStatus(OrderRecognitionStatus.PENDING.getCode());
        target.setPackingRecognitionStatus(OrderRecognitionStatus.PENDING.getCode());
        target.setOrderErrorMessage(null);
        target.setPackingErrorMessage(null);
        syncRecognitionErrorMessage(target);
        target.setUpdatedAt(LocalDateTime.now());
    }

    private void invalidateSheetPrintTasks(Long orderId) {
        List<OrderSheetPrintTask> tasks = orderSheetPrintTaskMapper.selectList(new LambdaQueryWrapper<OrderSheetPrintTask>()
                .eq(OrderSheetPrintTask::getOrderId, orderId)
                .ne(OrderSheetPrintTask::getStatus, PrintTaskStatus.INVALID.getCode()));
        LocalDateTime now = LocalDateTime.now();
        for (OrderSheetPrintTask task : tasks) {
            task.setStatus(PrintTaskStatus.INVALID.getCode());
            task.setUpdatedAt(now);
            orderSheetPrintTaskMapper.updateById(task);
        }
    }

    private List<OrderSheetPrintTask> createSheetPrintTasks(OrderRecord order, StoredFile storedFile) {
        if (order == null || order.getId() == null) {
            return List.of();
        }
        List<OrderSheetPrintTask> tasks = List.of(
                buildSheetPrintTask(order, storedFile, PrintType.ORDER),
                buildSheetPrintTask(order, storedFile, PrintType.PACKING)
        );
        for (OrderSheetPrintTask task : tasks) {
            orderSheetPrintTaskMapper.insert(task);
        }
        return tasks;
    }

    private OrderSheetPrintTask buildSheetPrintTask(OrderRecord order, StoredFile storedFile, PrintType printType) {
        LocalDateTime now = LocalDateTime.now();
        OrderSheetPrintTask task = new OrderSheetPrintTask();
        task.setOrderId(order.getId());
        task.setPrintType(printType.getCode());
        task.setOriginalFileName(storedFile == null ? null : storedFile.getOriginalName());
        task.setOriginalFilePath(storedFile == null || storedFile.getPath() == null ? null : storedFile.getPath().toString());
        task.setStatus(PrintTaskStatus.PENDING.getCode());
        task.setPrintCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
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

    private StoredFile storedFileFromTask(Long orderId, PrintType printType) {
        OrderSheetPrintTask task = orderSheetPrintTaskMapper.selectOne(new LambdaQueryWrapper<OrderSheetPrintTask>()
                .eq(OrderSheetPrintTask::getOrderId, orderId)
                .eq(OrderSheetPrintTask::getPrintType, printType.getCode())
                .ne(OrderSheetPrintTask::getStatus, PrintTaskStatus.INVALID.getCode())
                .orderByDesc(OrderSheetPrintTask::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(printType.getLabel() + "原稿任务不存在，不能识别");
        }
        if (!hasText(task.getOriginalFilePath())) {
            throw new BusinessException(printType.getLabel() + "原稿路径为空，不能识别");
        }
        Path path = fileStorageUtil.resolvePath(task.getOriginalFilePath());
        String originalName = hasText(task.getOriginalFileName())
                ? task.getOriginalFileName()
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
        syncRecognitionErrorMessage(order);
        orderRecordMapper.updateById(order);
    }

    private void syncRecognitionErrorMessage(OrderRecord order) {
        order.setErrorMessage(firstText(order.getOrderErrorMessage(), order.getPackingErrorMessage()));
    }

    private String firstText(String left, String right) {
        if (hasText(left)) {
            return left;
        }
        return hasText(right) ? right : null;
    }

    private String normalizeOrderNo(String value) {
        return hasText(value) ? value.trim() : "";
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

    private static class DevelopmentNoBucket {
        private final String developmentNo;
        private int pairCount;
        private int detailCount;

        private DevelopmentNoBucket(String developmentNo) {
            this.developmentNo = developmentNo;
        }

        private void add(int pairs) {
            pairCount += pairs;
            detailCount += 1;
        }

        private String getDevelopmentNo() {
            return developmentNo;
        }

        private int getPairCount() {
            return pairCount;
        }

        private int getDetailCount() {
            return detailCount;
        }
    }
}
