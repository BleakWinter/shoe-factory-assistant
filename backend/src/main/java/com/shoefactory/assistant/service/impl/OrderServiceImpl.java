package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import com.shoefactory.assistant.dto.OrderPackingDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.OrderStatisticsResponse;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.DevelopmentNoOrderReference;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.DevelopmentNoStatisticNode;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.ShippedOrderStatistic;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.StatisticsAmountPoint;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.StatisticsTimePoint;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.UnpricedShippedStyle;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.UnpricedUnshippedStyle;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.UnshippedInvoiceStatistic;
import com.shoefactory.assistant.dto.OrderStatisticsResponse.UnshippedStyleStatistic;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.OrderSheetPrintTask;
import com.shoefactory.assistant.entity.ComponentOrderTask;
import com.shoefactory.assistant.entity.ComponentOrderTaskItem;
import com.shoefactory.assistant.entity.ShoeStyleConfig;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;
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
import com.shoefactory.assistant.mapper.ComponentOrderTaskItemMapper;
import com.shoefactory.assistant.mapper.ComponentOrderTaskMapper;
import com.shoefactory.assistant.mapper.ShoeStyleConfigMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskItemMapper;
import com.shoefactory.assistant.service.OrderExcelImportService;
import com.shoefactory.assistant.service.OrderImportResult;
import com.shoefactory.assistant.service.OrderService;
import com.shoefactory.assistant.service.LedgerDataService;
import com.shoefactory.assistant.service.CustomerConfigService;
import com.shoefactory.assistant.service.StyleConfigService;
import com.shoefactory.assistant.util.DevelopmentNoUtil;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.PackingDetailMatchUtil;
import com.shoefactory.assistant.util.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
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
    private final ComponentOrderTaskMapper componentOrderTaskMapper;
    private final ComponentOrderTaskItemMapper componentOrderTaskItemMapper;
    private final ShoeStyleConfigMapper shoeStyleConfigMapper;
    private final ShippingNoteTaskMapper shippingNoteTaskMapper;
    private final ShippingNoteTaskItemMapper shippingNoteTaskItemMapper;
    private final FileStorageUtil fileStorageUtil;
    private final OrderExcelImportService orderExcelImportService;
    private final StyleConfigService styleConfigService;
    private final LedgerDataService ledger;
    private final CustomerConfigService customers;
    private final com.shoefactory.assistant.service.CustomerPriceService customerPrices;

    public OrderServiceImpl(
            OrderRecordMapper orderRecordMapper,
            OrderSheetPrintTaskMapper orderSheetPrintTaskMapper,
            OrderRecordDetailMapper orderRecordDetailMapper,
            OrderPackingDetailMapper orderPackingDetailMapper,
            OrderDetailProcessMapper orderDetailProcessMapper,
            ComponentOrderTaskMapper componentOrderTaskMapper,
            ComponentOrderTaskItemMapper componentOrderTaskItemMapper,
            ShoeStyleConfigMapper shoeStyleConfigMapper,
            ShippingNoteTaskMapper shippingNoteTaskMapper,
            ShippingNoteTaskItemMapper shippingNoteTaskItemMapper,
            FileStorageUtil fileStorageUtil,
            OrderExcelImportService orderExcelImportService,
            StyleConfigService styleConfigService,
            LedgerDataService ledger,
            CustomerConfigService customers,
            com.shoefactory.assistant.service.CustomerPriceService customerPrices
    ) {
        this.orderRecordMapper = orderRecordMapper;
        this.orderSheetPrintTaskMapper = orderSheetPrintTaskMapper;
        this.orderRecordDetailMapper = orderRecordDetailMapper;
        this.orderPackingDetailMapper = orderPackingDetailMapper;
        this.orderDetailProcessMapper = orderDetailProcessMapper;
        this.componentOrderTaskMapper = componentOrderTaskMapper;
        this.componentOrderTaskItemMapper = componentOrderTaskItemMapper;
        this.shoeStyleConfigMapper = shoeStyleConfigMapper;
        this.shippingNoteTaskMapper = shippingNoteTaskMapper;
        this.shippingNoteTaskItemMapper = shippingNoteTaskItemMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.orderExcelImportService = orderExcelImportService;
        this.styleConfigService = styleConfigService;
        this.ledger = ledger;
        this.customers = customers;
        this.customerPrices=customerPrices;
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
        boolean uploaded = false;
        try {
            OrderRecord orderRecord = readUploadSummary(storedFile, fileNo);
            initializeRecognitionFields(orderRecord);
            ensureOrderNoNotUploaded(orderRecord.getOrderNo());
            orderRecordMapper.insert(orderRecord);
            List<OrderSheetPrintTask> printTasks = createSheetPrintTasks(orderRecord, storedFile);
            styleConfigService.ensureConfigsForDevelopmentNos(splitDevelopmentNos(orderRecord.getDevelopmentNos()));
            customerPrices.ensure(orderRecord.getCustomerId(),splitDevelopmentNos(orderRecord.getDevelopmentNos()));
            uploaded = true;
            return buildUploadResponse(orderRecord, firstTaskId(printTasks), 0);
        } finally {
            if (!uploaded) {
                deleteStoredFileQuietly(storedFile);
            }
        }
    }

    @Override
    @Transactional
    public OrderUploadResponse appendOrderSource(Long orderId, MultipartFile file) {
        OrderRecord existingOrder = getRequiredOrder(orderId);
        requireExcelOrder(existingOrder);
        String extension = fileStorageUtil.extractExtension(file == null ? null : file.getOriginalFilename());
        FileType fileType = toFileType(extension);
        if (fileType != FileType.EXCEL) {
            throw new BusinessException("V1 仅支持上传 Excel 订单文件");
        }

        String fileNo = FileStorageUtil.newBusinessNo("SF");
        StoredFile storedFile = fileStorageUtil.saveOriginal(file, fileNo);
        boolean appended = false;
        try {
            OrderRecord appendedOrder = readUploadSummary(storedFile, fileNo);
            initializeRecognitionFields(appendedOrder);
            ensureAppendOrderNoMatches(existingOrder, appendedOrder);
            // 使用原订单的标准写法，避免前后空格造成列表不能归为同一流水号。
            appendedOrder.setOrderNo(existingOrder.getOrderNo());

            orderRecordMapper.insert(appendedOrder);
            List<OrderSheetPrintTask> printTasks = createSheetPrintTasks(appendedOrder, storedFile);
            styleConfigService.ensureConfigsForDevelopmentNos(splitDevelopmentNos(appendedOrder.getDevelopmentNos()));
            customerPrices.ensure(appendedOrder.getCustomerId(),splitDevelopmentNos(appendedOrder.getDevelopmentNos()));
            appended = true;
            return buildUploadResponse(appendedOrder, firstTaskId(printTasks), 0);
        } finally {
            if (!appended) {
                deleteStoredFileQuietly(storedFile);
            }
        }
    }

    @Override
    @Transactional
    public OrderUploadResponse reuploadOrderSource(Long orderId, MultipartFile file) {
        OrderRecord existingOrder = getRequiredOrder(orderId);
        requireExcelOrder(existingOrder);
        String extension = fileStorageUtil.extractExtension(file == null ? null : file.getOriginalFilename());
        FileType fileType = toFileType(extension);
        if (fileType != FileType.EXCEL) {
            throw new BusinessException("V1 仅支持上传 Excel 订单文件");
        }

        String fileNo = FileStorageUtil.newBusinessNo("SF");
        StoredFile storedFile = fileStorageUtil.saveOriginal(file, fileNo);
        boolean replacedTaskFiles = false;
        try {
            OrderRecord parsedOrder = readUploadSummary(storedFile, fileNo);
            initializeRecognitionFields(parsedOrder);
            ensureReuploadOrderNoMatches(existingOrder, parsedOrder);

            applyReuploadSummary(existingOrder, parsedOrder);
            updateReuploadedOrder(existingOrder);
            styleConfigService.ensureConfigsForDevelopmentNos(splitDevelopmentNos(existingOrder.getDevelopmentNos()));
            customerPrices.ensure(existingOrder.getCustomerId(),splitDevelopmentNos(existingOrder.getDevelopmentNos()));

            List<OrderSheetPrintTask> printTasks = replaceSheetPrintTaskFiles(orderId, storedFile);
            replacedTaskFiles = true;
            return buildUploadResponse(existingOrder, firstTaskId(printTasks), 0);
        } catch (RuntimeException ex) {
            if (!replacedTaskFiles) {
                deleteStoredFileQuietly(storedFile);
            }
            throw ex;
        }
    }

    @Override
    public PageResponse<OrderRecordResponse> listOrders(
            String orderNo,
            String developmentNos,
            String recognitionStatus,
            Integer unfinishedProcessType,
            Long customerId,
            Integer sourceType,
            long page,
            long size
    ) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 100);
        List<RecognitionFilter> parsedRecognitionFilters = parseRecognitionFilters(recognitionStatus);
        List<String> parsedDevelopmentNos = splitDevelopmentNos(developmentNos);
        List<OrderRecord> allOrders = orderRecordMapper.selectList(new LambdaQueryWrapper<OrderRecord>()
                .like(hasText(orderNo), OrderRecord::getOrderNo, orderNo)
                .eq(customerId != null, OrderRecord::getCustomerId, customerId)
                .eq(sourceType != null, OrderRecord::getSourceType, sourceType));
        Map<Long, List<OrderRecordDetail>> detailsByOrderId = loadDetailsByOrderId(allOrders);
        Map<Long, List<OrderPackingDetail>> packingDetailsByOrderId = loadPackingDetailsByOrderId(allOrders);
        Set<Long> completedDetailIds = loadCompletedDetailIds(allOrders, unfinishedProcessType);

        List<OrderGroupView> matchingGroups = groupOrders(allOrders).stream()
                .map(group -> new OrderGroupView(
                        group,
                        buildOrderGroupResponse(group.parts(), detailsByOrderId, packingDetailsByOrderId)
                ))
                .filter(view -> matchesDevelopmentNos(view.response(), parsedDevelopmentNos))
                .filter(view -> matchesRecognitionFilters(view.response(), parsedRecognitionFilters))
                .filter(view -> matchesUnfinishedProcess(
                        view.group(),
                        detailsByOrderId,
                        completedDetailIds,
                        unfinishedProcessType
                ))
                .sorted(Comparator.comparing(
                        (OrderGroupView view) -> view.response().getCreatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                        view -> view.response().getId(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();

        int fromIndex = (int) Math.min((safePage - 1) * safeSize, matchingGroups.size());
        int toIndex = (int) Math.min(fromIndex + safeSize, matchingGroups.size());
        PageResponse<OrderRecordResponse> response = new PageResponse<>();
        response.setRecords(matchingGroups.subList(fromIndex, toIndex).stream()
                .map(OrderGroupView::response)
                .toList());
        response.setTotal(matchingGroups.size());
        response.setPage(safePage);
        response.setSize(safeSize);
        return response;
    }

    private List<OrderGroup> groupOrders(List<OrderRecord> orders) {
        Map<String, List<OrderRecord>> grouped = new LinkedHashMap<>();
        for (OrderRecord order : orders) {
            String normalizedOrderNo = normalizeOrderNo(order.getOrderNo()).toUpperCase(Locale.ROOT);
            String key = hasText(normalizedOrderNo)
                    ? "CUSTOMER-" + order.getCustomerId() + "-ORDER-" + normalizedOrderNo
                    : "ID-" + order.getId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(order);
        }
        Comparator<OrderRecord> partComparator = Comparator
                .comparing(OrderRecord::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OrderRecord::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        return grouped.values().stream()
                .map(parts -> {
                    parts.sort(partComparator);
                    return new OrderGroup(List.copyOf(parts));
                })
                .toList();
    }

    private Set<Long> loadCompletedDetailIds(List<OrderRecord> orders, Integer processType) {
        if (processType == null) {
            return Set.of();
        }
        List<Long> orderIds = orders.stream()
                .map(OrderRecord::getId)
                .filter(id -> id != null)
                .toList();
        if (orderIds.isEmpty()) {
            return Set.of();
        }
        return orderDetailProcessMapper.selectList(new LambdaQueryWrapper<OrderDetailProcess>()
                        .select(OrderDetailProcess::getOrderDetailId)
                        .in(OrderDetailProcess::getOrderId, orderIds)
                        .eq(OrderDetailProcess::getProcessType, processType)
                        .eq(OrderDetailProcess::getProcessStatus, 1))
                .stream()
                .map(OrderDetailProcess::getOrderDetailId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }

    private boolean matchesDevelopmentNos(
            OrderRecordResponse response,
            List<String> developmentNos
    ) {
        if (developmentNos.isEmpty()) {
            return true;
        }
        List<String> availableValues = response.getDevelopmentNoList().stream()
                .map(DevelopmentNoUtil::normalizeSearchTerm)
                .filter(this::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
        // 级联选项会省略字母前缀；搜索编号不能按完整款号校验，也不能让空编号匹配所有订单。
        return developmentNos.stream()
                .map(DevelopmentNoUtil::normalizeSearchTerm)
                .filter(this::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .anyMatch(expected -> availableValues.stream()
                        // 按编号段匹配，避免 172 命中 1172、02 命中 020。
                        .anyMatch(value -> ("-" + value + "-").contains("-" + expected + "-")));
    }

    private boolean matchesRecognitionFilters(
            OrderRecordResponse response,
            List<RecognitionFilter> filters
    ) {
        if (filters.isEmpty()) {
            return true;
        }
        int pending = OrderRecognitionStatus.PENDING.getCode();
        int recognized = OrderRecognitionStatus.RECOGNIZED.getCode();
        int failed = OrderRecognitionStatus.FAILED.getCode();
        return filters.stream().anyMatch(filter -> switch (filter) {
            case ORDER_PENDING -> nullToZero(response.getOrderRecognitionStatus()) == pending;
            case PACKING_PENDING -> nullToZero(response.getPackingRecognitionStatus()) == pending;
            case ORDER_RECOGNIZED -> nullToZero(response.getOrderRecognitionStatus()) == recognized;
            case PACKING_RECOGNIZED -> nullToZero(response.getPackingRecognitionStatus()) == recognized;
            case FAILED -> nullToZero(response.getOrderRecognitionStatus()) == failed
                    || nullToZero(response.getPackingRecognitionStatus()) == failed;
        });
    }

    private boolean matchesUnfinishedProcess(
            OrderGroup group,
            Map<Long, List<OrderRecordDetail>> detailsByOrderId,
            Set<Long> completedDetailIds,
            Integer processType
    ) {
        if (processType == null) {
            return true;
        }
        return group.parts().stream()
                .flatMap(order -> detailsByOrderId.getOrDefault(order.getId(), List.of()).stream())
                .map(OrderRecordDetail::getId)
                .filter(id -> id != null)
                .anyMatch(id -> !completedDetailIds.contains(id));
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
                .map(DevelopmentNoUtil::normalize)
                .filter(this::hasText)
                .forEach(developmentNos::add);

        List<DevelopmentNoOptionResponse> options = new ArrayList<>();
        developmentNos.forEach(developmentNo ->
                appendDevelopmentNoOption(options, parseDevelopmentNoParts(developmentNo), List.of()));
        sortDevelopmentNoOptions(options);
        return options;
    }

    @Override
    public OrderStatisticsResponse getOrderStatistics() { return getOrderStatistics(null); }

    @Override
    public OrderRecordResponse getOrder(Long id) {
        return OrderRecordResponse.from(getRequiredOrder(id));
    }

    @Override
    public OrderStatisticsResponse getOrderStatistics(Long customerId) {
        LedgerDataService.Data data = ledger.load(customerId);
        List<OrderRecordDetail> details = data.details();
        Map<Long, OrderRecord> ordersById = data.ordersById();
        Map<Long, Integer> shippedPairsByDetailId = loadShippedPairsByDetailId(data);
        Map<String, DevelopmentNoBucket> buckets = new LinkedHashMap<>();
        int totalPairs = 0;
        int shippedPairs = 0;
        for (OrderRecordDetail detail : details) {
            int pairCount = detailPairCount(detail);
            int detailShippedPairs = detail.getId() == null ? 0 : nullToZero(shippedPairsByDetailId.get(detail.getId()));
            int unshippedPairs = Math.max(0, pairCount - detailShippedPairs);
            totalPairs += pairCount;
            shippedPairs += detailShippedPairs;
            String developmentNo = normalizeStatisticsDevelopmentNo(detail.getDevelopmentNo());
            DevelopmentNoOrderReference orderReference = buildDevelopmentNoOrderReference(
                    detail,
                    ordersById.get(detail.getOrderId()),
                    pairCount,
                    detailShippedPairs,
                    unshippedPairs
            );
            buckets.computeIfAbsent(developmentNo, DevelopmentNoBucket::new)
                    .add(pairCount, detailShippedPairs, unshippedPairs, orderReference);
        }

        OrderStatisticsResponse response = new OrderStatisticsResponse();
        response.setTotalPairs(totalPairs);
        response.setShippedPairs(shippedPairs);
        response.setUnshippedPairs(Math.max(0, totalPairs - shippedPairs));
        response.setStyleCount(buckets.size());
        response.setDetailCount(details.size());
        response.setOrderCreatedTrend(buildOrderCreatedTrend(data));
        response.setShippedPairsTrend(buildShippedPairsTrend(data));
        response.setShippedOrderStatistics(buildShippedOrderStatistics(data));
        PaymentStatistics paymentStatistics = buildPaymentStatistics(data);
        response.setTotalPaymentAmount(paymentStatistics.totalAmount());
        response.setUnpricedShippedPairs(paymentStatistics.unpricedShippedPairs());
        response.setUnpricedShippedStyles(paymentStatistics.unpricedShippedStyles());
        response.setPaymentAmountTrend(paymentStatistics.trend());
        UnpricedUnshippedStatistics unpricedUnshippedStatistics =
                buildUnpricedUnshippedStatistics(details, shippedPairsByDetailId, data.pricesByDetailId());
        response.setUnpricedUnshippedPairs(unpricedUnshippedStatistics.totalPairs());
        response.setUnpricedUnshippedStyles(unpricedUnshippedStatistics.styles());
        response.setUnshippedInvoiceStatistics(buildUnshippedInvoiceStatistics(details, ordersById, shippedPairsByDetailId));
        response.setUnshippedStyleStatistics(buildUnshippedStyleStatistics(details, ordersById, shippedPairsByDetailId));
        response.setDevelopmentNoTree(buildDevelopmentNoStatisticsTree(buckets.values()));
        response.setTopDevelopmentNos(buildTopDevelopmentNoStatistics(buckets.values()));
        return response;
    }

    private List<StatisticsTimePoint> buildOrderCreatedTrend(LedgerDataService.Data data) {
        Map<LocalDate, Integer> values = new LinkedHashMap<>();
        for (OrderRecord order : data.orders()) {
            LocalDate date = order.getOrderDate() != null ? order.getOrderDate() :
                    order.getCreatedAt() == null ? null : order.getCreatedAt().toLocalDate();
            if (date != null) values.merge(date, nullToZero(order.getTotalQuantity()), Integer::sum);
        }
        return buildTimePoints(values);
    }

    private List<StatisticsTimePoint> buildShippedPairsTrend(LedgerDataService.Data data) {
        Map<LocalDate, Integer> values = new LinkedHashMap<>();
        Map<Long, ShippingNoteTask> tasks = data.tasksById();
        Map<Long, OrderRecordDetail> details = data.detailsById();
        for (ShippingNoteTaskItem item : data.items()) {
            ShippingNoteTask task = tasks.get(item.getTaskId());
            OrderRecordDetail detail = details.get(item.getSourceDetailId());
            if (task == null || detail == null) continue;
            LocalDate date = LedgerDataService.shippingDate(task);
            if (date != null) values.merge(date, LedgerDataService.quantity(detail), Integer::sum);
        }
        return buildTimePoints(values);
    }

    private List<ShippedOrderStatistic> buildShippedOrderStatistics(LedgerDataService.Data data) {
        Map<Long, ShippingNoteTask> tasks = data.tasksById();
        Map<Long, OrderRecordDetail> details = data.detailsById();
        Map<Long, OrderRecord> orders = data.ordersById();
        Map<ShippedOrderStatisticKey,Integer> values = new LinkedHashMap<>();
        for (ShippingNoteTaskItem item : data.items()) {
            ShippingNoteTask task = tasks.get(item.getTaskId());
            OrderRecordDetail detail = details.get(item.getSourceDetailId());
            if (task == null || detail == null || LedgerDataService.shippingDate(task) == null) continue;
            OrderRecord order = orders.get(detail.getOrderId());
            values.merge(new ShippedOrderStatisticKey(LedgerDataService.shippingDate(task),
                    order == null ? detail.getCustomerOrderNo() : order.getOrderNo(),
                    normalizeStatisticsDevelopmentNo(detail.getDevelopmentNo())),
                    LedgerDataService.quantity(detail), Integer::sum);
        }
        return values.entrySet().stream().map(entry -> {
            ShippedOrderStatistic row = new ShippedOrderStatistic();
            row.setShippingDate(entry.getKey().shippingDate()); row.setOrderNo(entry.getKey().orderNo());
            row.setDevelopmentNo(entry.getKey().developmentNo()); row.setShippedPairs(entry.getValue());
            return row;
        }).sorted(Comparator.comparing(ShippedOrderStatistic::getShippingDate).reversed()).toList();
    }

    private record ShippedOrderStatisticKey(LocalDate shippingDate, String orderNo, String developmentNo) {}

    private PaymentStatistics buildPaymentStatistics(LedgerDataService.Data data) {
        Map<Long, ShippingNoteTask> tasks = data.tasksById();
        Map<Long, OrderRecordDetail> details = data.detailsById();
        Map<LocalDate, BigDecimal> amounts = new LinkedHashMap<>();
        Map<UnpricedShippedStyleDateKey,Integer> unpriced = new LinkedHashMap<>();
        int unpricedPairs = 0;
        for (ShippingNoteTaskItem item : data.items()) {
            ShippingNoteTask task = tasks.get(item.getTaskId());
            OrderRecordDetail detail = details.get(item.getSourceDetailId());
            if (task == null || detail == null) continue;
            LocalDate date = LedgerDataService.shippingDate(task);
            if (date == null) continue;
            int quantity = LedgerDataService.quantity(detail);
            BigDecimal price = data.pricesByDetailId().get(detail.getId());
            amounts.putIfAbsent(date,BigDecimal.ZERO);
            if (price == null) {
                unpricedPairs += quantity;
                unpriced.merge(new UnpricedShippedStyleDateKey(normalizeStatisticsDevelopmentNo(detail.getDevelopmentNo()),date),
                        quantity,Integer::sum);
            } else amounts.merge(date,price.multiply(BigDecimal.valueOf(quantity)),BigDecimal::add);
        }
        return new PaymentStatistics(amounts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add),
                unpricedPairs,buildUnpricedShippedStyles(unpriced),buildAmountTimePoints(amounts));
    }

    private List<UnpricedShippedStyle> buildUnpricedShippedStyles(
            Map<UnpricedShippedStyleDateKey, Integer> pairsByStyleAndDate
    ) {
        return pairsByStyleAndDate.entrySet()
                .stream()
                .map(entry -> {
                    UnpricedShippedStyle style = new UnpricedShippedStyle();
                    style.setDevelopmentNo(entry.getKey().developmentNo());
                    style.setShippingDate(entry.getKey().shippingDate());
                    style.setShippedPairs(entry.getValue());
                    return style;
                })
                .sorted(Comparator.comparing(
                                UnpricedShippedStyle::getShippingDate,
                                Comparator.reverseOrder())
                        .thenComparing(Comparator.comparingInt(
                                        (UnpricedShippedStyle style) -> nullToZero(style.getShippedPairs()))
                                .reversed())
                        .thenComparing(UnpricedShippedStyle::getDevelopmentNo))
                .toList();
    }

    private record UnpricedShippedStyleDateKey(
            String developmentNo,
            LocalDate shippingDate
    ) {
    }

    private UnpricedUnshippedStatistics buildUnpricedUnshippedStatistics(
            List<OrderRecordDetail> details,
            Map<Long, Integer> shippedPairsByDetailId,
            Map<Long, BigDecimal> pricesByDetailId
    ) {
        Map<String, Integer> unpricedPairsByDevelopmentNo = new LinkedHashMap<>();
        for (OrderRecordDetail detail : details) {
            String developmentNo = normalizePaymentDevelopmentNo(detail.getDevelopmentNo());
            if (pricesByDetailId.containsKey(detail.getId())) {
                continue;
            }
            int totalPairs = detailPairCount(detail);
            int shippedPairs = detail.getId() == null
                    ? 0
                    : nullToZero(shippedPairsByDetailId.get(detail.getId()));
            int unshippedPairs = Math.max(0, totalPairs - shippedPairs);
            if (unshippedPairs <= 0) {
                continue;
            }
            unpricedPairsByDevelopmentNo.merge(
                    hasText(developmentNo) ? developmentNo : "未填开发编号",
                    unshippedPairs,
                    Integer::sum
            );
        }
        List<UnpricedUnshippedStyle> styles = unpricedPairsByDevelopmentNo.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> {
                    UnpricedUnshippedStyle style = new UnpricedUnshippedStyle();
                    style.setDevelopmentNo(entry.getKey());
                    style.setUnshippedPairs(entry.getValue());
                    return style;
                })
                .toList();
        int totalPairs = unpricedPairsByDevelopmentNo.values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum();
        return new UnpricedUnshippedStatistics(totalPairs, styles);
    }

    private String normalizePaymentDevelopmentNo(String value) {
        String developmentNo = com.shoefactory.assistant.service.CustomerPriceService.normalize(value);
        return hasText(developmentNo) ? developmentNo.toUpperCase(Locale.ROOT) : "";
    }

    private List<StatisticsTimePoint> buildTimePoints(Map<LocalDate, Integer> valuesByDate) {
        return valuesByDate.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    StatisticsTimePoint point = new StatisticsTimePoint();
                    point.setDate(entry.getKey());
                    point.setValue(entry.getValue());
                    return point;
                })
                .toList();
    }

    private List<StatisticsAmountPoint> buildAmountTimePoints(Map<LocalDate, BigDecimal> valuesByDate) {
        return valuesByDate.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    StatisticsAmountPoint point = new StatisticsAmountPoint();
                    point.setDate(entry.getKey());
                    point.setValue(entry.getValue());
                    return point;
                })
                .toList();
    }

    private List<UnshippedInvoiceStatistic> buildUnshippedInvoiceStatistics(
            List<OrderRecordDetail> details,
            Map<Long, OrderRecord> ordersById,
            Map<Long, Integer> shippedPairsByDetailId
    ) {
        Map<String, UnshippedInvoiceStatistic> statisticsByOrder = new LinkedHashMap<>();
        for (OrderRecordDetail detail : details) {
            int pairCount = detailPairCount(detail);
            int shippedPairCount = detail.getId() == null ? 0 : nullToZero(shippedPairsByDetailId.get(detail.getId()));
            int unshippedPairCount = Math.max(0, pairCount - shippedPairCount);
            OrderRecord order = ordersById.get(detail.getOrderId());
            String key = detail.getOrderId() != null
                    ? "order-" + detail.getOrderId()
                    : "detail-" + (detail.getId() == null ? "unknown" : detail.getId());
            UnshippedInvoiceStatistic statistic = statisticsByOrder.computeIfAbsent(key, ignored ->
                    buildUnshippedInvoiceStatistic(detail, order));
            statistic.setPairCount(nullToZero(statistic.getPairCount()) + pairCount);
            statistic.setShippedPairCount(nullToZero(statistic.getShippedPairCount()) + shippedPairCount);
            statistic.setUnshippedPairCount(nullToZero(statistic.getUnshippedPairCount()) + unshippedPairCount);
            statistic.setDetailCount(nullToZero(statistic.getDetailCount()) + 1);
        }
        return statisticsByOrder.values()
                .stream()
                .filter(statistic -> nullToZero(statistic.getUnshippedPairCount()) > 0)
                .sorted(Comparator
                        .comparing(UnshippedInvoiceStatistic::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(statistic -> statistic.getInvoiceNo() == null ? "" : statistic.getInvoiceNo()))
                .toList();
    }

    private UnshippedInvoiceStatistic buildUnshippedInvoiceStatistic(OrderRecordDetail detail, OrderRecord order) {
        UnshippedInvoiceStatistic statistic = new UnshippedInvoiceStatistic();
        statistic.setOrderId(detail.getOrderId());
        statistic.setInvoiceNo(cleanStatisticText(order == null ? null : order.getOrderNo()));
        statistic.setCreatedAt(order == null ? null : order.getCreatedAt());
        statistic.setPairCount(0);
        statistic.setShippedPairCount(0);
        statistic.setUnshippedPairCount(0);
        statistic.setDetailCount(0);
        return statistic;
    }

    private List<UnshippedStyleStatistic> buildUnshippedStyleStatistics(
            List<OrderRecordDetail> details,
            Map<Long, OrderRecord> ordersById,
            Map<Long, Integer> shippedPairsByDetailId
    ) {
        Map<String, UnshippedStyleStatistic> statisticsByStyle = new LinkedHashMap<>();
        for (OrderRecordDetail detail : details) {
            int pairCount = detailPairCount(detail);
            int shippedPairCount = detail.getId() == null ? 0 : nullToZero(shippedPairsByDetailId.get(detail.getId()));
            int unshippedPairCount = Math.max(0, pairCount - shippedPairCount);
            OrderRecord order = ordersById.get(detail.getOrderId());
            String developmentNo = normalizeStatisticsDevelopmentNo(detail.getDevelopmentNo());
            String orderKey = detail.getOrderId() != null
                    ? "order-" + detail.getOrderId()
                    : "detail-" + (detail.getId() == null ? "unknown" : detail.getId());
            String key = orderKey + "::" + developmentNo;
            UnshippedStyleStatistic statistic = statisticsByStyle.computeIfAbsent(key, ignored ->
                    buildUnshippedStyleStatistic(detail, order, developmentNo));
            statistic.setPairCount(nullToZero(statistic.getPairCount()) + pairCount);
            statistic.setShippedPairCount(nullToZero(statistic.getShippedPairCount()) + shippedPairCount);
            statistic.setUnshippedPairCount(nullToZero(statistic.getUnshippedPairCount()) + unshippedPairCount);
            statistic.setDetailCount(nullToZero(statistic.getDetailCount()) + 1);

            mergeStyleDeliveryDate(statistic, detail, order);
        }
        return statisticsByStyle.values()
                .stream()
                .filter(statistic -> nullToZero(statistic.getUnshippedPairCount()) > 0)
                .sorted(Comparator
                        .comparing(UnshippedStyleStatistic::getDeliveryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(UnshippedStyleStatistic::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(statistic -> statistic.getInvoiceNo() == null ? "" : statistic.getInvoiceNo())
                        .thenComparing(statistic -> statistic.getDevelopmentNo() == null ? "" : statistic.getDevelopmentNo()))
                .toList();
    }

    private UnshippedStyleStatistic buildUnshippedStyleStatistic(
            OrderRecordDetail detail,
            OrderRecord order,
            String developmentNo
    ) {
        UnshippedStyleStatistic statistic = new UnshippedStyleStatistic();
        statistic.setOrderId(detail.getOrderId());
        statistic.setInvoiceNo(cleanStatisticText(order == null ? null : order.getOrderNo()));
        statistic.setDevelopmentNo(developmentNo);
        statistic.setCreatedAt(order != null && order.getCreatedAt() != null
                ? order.getCreatedAt()
                : detail.getCreatedAt());
        statistic.setDefaultDeliveryDate(false);
        statistic.setPairCount(0);
        statistic.setShippedPairCount(0);
        statistic.setUnshippedPairCount(0);
        statistic.setDetailCount(0);
        return statistic;
    }

    void mergeStyleDeliveryDate(
            UnshippedStyleStatistic statistic,
            OrderRecordDetail detail,
            OrderRecord order
    ) {
        LocalDate explicitDeliveryDate = detail.getDeliveryDate();
        if (explicitDeliveryDate != null) {
            boolean currentDateIsDefault = Boolean.TRUE.equals(statistic.getDefaultDeliveryDate());
            if (statistic.getDeliveryDate() == null
                    || currentDateIsDefault
                    || explicitDeliveryDate.isBefore(statistic.getDeliveryDate())) {
                statistic.setDeliveryDate(explicitDeliveryDate);
                statistic.setDefaultDeliveryDate(false);
            }
            return;
        }

        boolean alreadyHasExplicitDate = statistic.getDeliveryDate() != null
                && !Boolean.TRUE.equals(statistic.getDefaultDeliveryDate());
        if (alreadyHasExplicitDate) {
            return;
        }
        LocalDate defaultDeliveryDate = effectiveDeliveryDate(detail, order);
        if (defaultDeliveryDate != null
                && (statistic.getDeliveryDate() == null
                || defaultDeliveryDate.isBefore(statistic.getDeliveryDate()))) {
            statistic.setDeliveryDate(defaultDeliveryDate);
            statistic.setDefaultDeliveryDate(true);
        }
    }

    private LocalDate effectiveDeliveryDate(OrderRecordDetail detail, OrderRecord order) {
        if (detail.getDeliveryDate() != null) {
            return detail.getDeliveryDate();
        }
        LocalDateTime createdAt = order != null && order.getCreatedAt() != null
                ? order.getCreatedAt()
                : detail.getCreatedAt();
        if (order != null && order.getOrderDate()!=null) return order.getOrderDate().plusMonths(1);
        return createdAt == null ? null : createdAt.toLocalDate().plusMonths(1);
    }

    private Map<Long, Integer> loadShippedPairsByDetailId(LedgerDataService.Data data) {
        Map<Long,OrderRecordDetail> details = data.detailsById();
        Map<Long,Integer> result = new LinkedHashMap<>();
        for (ShippingNoteTaskItem item : data.items()) {
            OrderRecordDetail detail = details.get(item.getSourceDetailId());
            if (detail != null) result.merge(detail.getId(), LedgerDataService.quantity(detail),Integer::sum);
        }
        return result;
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
            node.setShippedPairCount(nullToZero(node.getShippedPairCount()) + bucket.getShippedPairCount());
            node.setUnshippedPairCount(nullToZero(node.getUnshippedPairCount()) + bucket.getUnshippedPairCount());
            node.setDetailCount(nullToZero(node.getDetailCount()) + bucket.getDetailCount());
            node.setStyleCount(nullToZero(node.getStyleCount()) + 1);
            if (index == parts.size() - 1) {
                node.setFullDevelopmentNo(bucket.getDevelopmentNo());
                node.setOrderReferences(bucket.getOrderReferences());
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
        node.setShippedPairCount(0);
        node.setUnshippedPairCount(0);
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
        node.setShippedPairCount(bucket.getShippedPairCount());
        node.setUnshippedPairCount(bucket.getUnshippedPairCount());
        node.setDetailCount(bucket.getDetailCount());
        node.setStyleCount(1);
        node.setOrderReferences(bucket.getOrderReferences());
        node.setChildren(List.of());
        return node;
    }

    private Map<Long, OrderRecord> loadStatisticsOrdersById(List<OrderRecordDetail> details) {
        List<Long> orderIds = details.stream()
                .map(OrderRecordDetail::getOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRecordMapper.selectList(new LambdaQueryWrapper<OrderRecord>()
                        .select(OrderRecord::getId, OrderRecord::getOrderNo, OrderRecord::getCreatedAt)
                        .in(OrderRecord::getId, orderIds))
                .stream()
                .collect(Collectors.toMap(
                        OrderRecord::getId,
                        order -> order,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private DevelopmentNoOrderReference buildDevelopmentNoOrderReference(
            OrderRecordDetail detail,
            OrderRecord order,
            int pairCount,
            int shippedPairCount,
            int unshippedPairCount
    ) {
        DevelopmentNoOrderReference reference = new DevelopmentNoOrderReference();
        reference.setOrderId(detail.getOrderId());
        reference.setInvoiceNo(cleanStatisticText(order == null ? null : order.getOrderNo()));
        reference.setOrderNo(cleanStatisticText(detail.getCustomerOrderNo()));
        reference.setPairCount(pairCount);
        reference.setShippedPairCount(shippedPairCount);
        reference.setUnshippedPairCount(unshippedPairCount);
        reference.setDetailCount(1);
        return reference;
    }

    private String cleanStatisticText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private int detailPairCount(OrderRecordDetail detail) {
        return resolveOrderDetailQuantity(detail);
    }

    private record PaymentStatistics(
            BigDecimal totalAmount,
            int unpricedShippedPairs,
            List<UnpricedShippedStyle> unpricedShippedStyles,
            List<StatisticsAmountPoint> trend
    ) {
        private static PaymentStatistics empty() {
            return new PaymentStatistics(BigDecimal.ZERO, 0, List.of(), List.of());
        }
    }

    private record UnpricedUnshippedStatistics(
            int totalPairs,
            List<UnpricedUnshippedStyle> styles
    ) {
    }

    private String normalizeStatisticsDevelopmentNo(String value) {
        String developmentNo = com.shoefactory.assistant.service.CustomerPriceService.normalize(value);
        return hasText(developmentNo) ? developmentNo : "未填款号";
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
        List<OrderRecord> orderGroup = loadOrderGroup(orderId);
        orderGroup.forEach(this::requireExcelOrder);
        orderGroup.forEach(this::recognizeSingleOrder);
        return buildOrderGroupResponse(orderGroup);
    }

    private void recognizeSingleOrder(OrderRecord order) {
        Long orderId = order.getId();
        List<OrderRecordDetail> existingDetails = loadDetails(orderId);
        OrderImportResult importResult;
        try {
            StoredFile storedFile = storedFileFromTask(orderId, PrintType.ORDER);
            importResult = orderExcelImportService.importOrderDetails(
                    storedFile.getPath(),
                    storedFile,
                    FileStorageUtil.newBusinessNo("SF")
            );
            if (importResult.getDetails().isEmpty()) {
                throw new BusinessException("订单 sheet 未解析到明细行");
            }
            validateRecognizedDetailCount(
                    "订单",
                    existingDetails.size(),
                    importResult.getDetails().size()
            );
        } catch (RuntimeException ex) {
            markRecognitionFailed(order, true, ex);
            return;
        }

        saveRecognizedOrderDetails(orderId, existingDetails, importResult.getDetails());
        applyOrderSummary(order, importResult.getOrder());
        styleConfigService.ensureConfigsForDevelopmentNos(importResult.getDetails().stream()
                .map(OrderRecordDetail::getDevelopmentNo)
                .toList());
        fillTotalsFromDetails(order, importResult.getDetails());
        customerPrices.ensure(order.getCustomerId(),importResult.getDetails().stream()
                .map(OrderRecordDetail::getDevelopmentNo).toList());
        fillTotalsFromPackingDetails(order, loadPackingDetails(orderId));
        order.setOrderRecognitionStatus(OrderRecognitionStatus.RECOGNIZED.getCode());
        order.setOrderErrorMessage(null);
        order.setUpdatedAt(LocalDateTime.now());
        syncRecognitionErrorMessage(order);
        orderRecordMapper.updateById(order);
    }

    @Override
    @Transactional
    public OrderRecordResponse recognizePacking(Long orderId) {
        List<OrderRecord> orderGroup = loadOrderGroup(orderId);
        orderGroup.forEach(this::requireExcelOrder);
        orderGroup.forEach(this::recognizeSinglePacking);
        return buildOrderGroupResponse(orderGroup);
    }

    private void recognizeSinglePacking(OrderRecord order) {
        Long orderId = order.getId();
        List<OrderRecordDetail> orderDetails = loadDetails(orderId);
        List<OrderPackingDetail> existingDetails = loadPackingDetails(orderId);
        List<OrderPackingDetail> packingDetails;
        try {
            ensureOrderDetailsHaveCartonRanges(orderDetails);
            StoredFile storedFile = storedFileFromTask(orderId, PrintType.PACKING);
            packingDetails = orderExcelImportService.importPackingDetails(
                    storedFile.getPath(),
                    order,
                    FileStorageUtil.newBusinessNo("SF")
            );
            if (packingDetails.isEmpty()) {
                throw new BusinessException("装箱单 sheet 未解析到明细行");
            }
            validateRecognizedDetailCount(
                    "装箱单",
                    existingDetails.size(),
                    packingDetails.size()
            );
        } catch (RuntimeException ex) {
            markRecognitionFailed(order, false, ex);
            return;
        }

        saveRecognizedPackingDetails(orderId, existingDetails, packingDetails);
        fillTotalsFromPackingDetails(order, packingDetails);
        order.setPackingRecognitionStatus(OrderRecognitionStatus.RECOGNIZED.getCode());
        order.setPackingErrorMessage(null);
        order.setUpdatedAt(LocalDateTime.now());
        syncRecognitionErrorMessage(order);
        orderRecordMapper.updateById(order);
    }

    @Override
    public List<OrderRecordDetailResponse> listOrderDetails(Long orderId) {
        List<OrderRecord> orderGroup = loadOrderGroup(orderId);
        List<Long> orderIds = orderGroup.stream().map(OrderRecord::getId).toList();
        Map<Long, Integer> partNoByOrderId = buildPartNoByOrderId(orderGroup);
        List<OrderRecordDetail> details = loadDetails(orderIds);
        if (details.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<OrderDetailProcess>> processMap = orderDetailProcessMapper.selectList(new LambdaQueryWrapper<OrderDetailProcess>()
                        .in(OrderDetailProcess::getOrderId, orderIds)
                        .orderByAsc(OrderDetailProcess::getProcessType))
                .stream()
                .collect(Collectors.groupingBy(OrderDetailProcess::getOrderDetailId));
        Map<String, String> boxSpecByDevelopmentNo = loadBoxSpecsByDevelopmentNo(details);
        return details.stream()
                .map(detail -> {
                    OrderRecordDetailResponse response = OrderRecordDetailResponse.from(
                        detail,
                        processMap.getOrDefault(detail.getId(), List.of()),
                        boxSpecByDevelopmentNo.get(normalizeDevelopmentNo(detail.getDevelopmentNo())),
                        orderGroup.get(0).getOrderNo()
                    );
                    response.setPartNo(partNoByOrderId.get(detail.getOrderId()));
                    return response;
                })
                .toList();
    }

    @Override
    public List<OrderPackingDetailResponse> listOrderPackingDetails(Long orderId) {
        List<OrderRecord> orderGroup = loadOrderGroup(orderId);
        List<Long> orderIds = orderGroup.stream().map(OrderRecord::getId).toList();
        Map<Long, Integer> partNoByOrderId = buildPartNoByOrderId(orderGroup);
        List<OrderPackingDetail> packingDetails = loadPackingDetails(orderIds);
        return packingDetails.stream()
                .map(detail -> {
                    OrderPackingDetailResponse response = OrderPackingDetailResponse.from(detail);
                    response.setPartNo(partNoByOrderId.get(detail.getOrderId()));
                    return response;
                })
                .toList();
    }

    @Override
    public List<OrderPackingDetailResponse> listMatchingPackingDetails(Long detailId) {
        OrderRecordDetail detail = orderRecordDetailMapper.selectById(detailId);
        if (detail == null) {
            throw new BusinessException("订单明细不存在: " + detailId);
        }
        List<OrderPackingDetail> packingDetails = loadPackingDetails(detail.getOrderId());
        if (packingDetails.isEmpty()) {
            return List.of();
        }
        return packingDetails.stream()
                .filter(packingDetail -> PackingDetailMatchUtil.isMatchingPackingDetail(detail, packingDetail))
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
    @Transactional
    public Boolean removeOrderDetailById(Long id) {
        OrderRecordDetail detail = orderRecordDetailMapper.selectById(id);
        if (detail == null) {
            return false;
        }

        Long orderId = detail.getOrderId();
        OrderRecord sourceOrder = orderRecordMapper.selectForUpdate(orderId);
        if (sourceOrder != null && Integer.valueOf(3).equals(sourceOrder.getSourceType())) {
            throw new BusinessException("手工订单请通过订单编辑维护明细，已出货订单不能删除明细");
        }
        orderDetailProcessMapper.delete(new LambdaQueryWrapper<OrderDetailProcess>()
                .eq(OrderDetailProcess::getOrderDetailId, id));

        Set<Long> affectedShippingTaskIds = deleteShippingNoteTaskItemsByDetailId(id);
        // 暂时停用 component_order_task_item 联动删除。
        // prod 里还没确认这张表是否存在，先避免删订单明细时访问缺失表而报错。
        // Set<Long> affectedComponentTaskIds = deleteComponentOrderTaskItemsByDetailId(id);

        boolean deleted = orderRecordDetailMapper.deleteById(id) > 0;
        if (!deleted) {
            return false;
        }

        refreshShippingNoteTasks(affectedShippingTaskIds);
        // 暂时停用 component_order_task 联动刷新，和上面的明细联动删除保持一致。
        // refreshComponentOrderTasks(affectedComponentTaskIds);
        refreshOrderSummaryAfterDetailRemoval(orderId);
        return true;
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
        var customer = customers.excelCustomer();
        if (!Boolean.TRUE.equals(customer.getEnabled())) throw new BusinessException("达为已停用，不能上传新订单");
        order.setCustomerId(customer.getId());
        order.setOrderDate(LocalDate.now());

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
                .eq(OrderRecord::getCustomerId, customers.excelCustomer().getId())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("订单流水号 " + orderNo
                    + " 已存在，请找到对应订单，根据实际情况选择“追加一份”或“重新上传”。");
        }
    }

    private void ensureReuploadOrderNoMatches(OrderRecord existingOrder, OrderRecord parsedOrder) {
        String existingOrderNo = normalizeOrderNo(existingOrder == null ? null : existingOrder.getOrderNo());
        String parsedOrderNo = normalizeOrderNo(parsedOrder == null ? null : parsedOrder.getOrderNo());
        if (hasText(existingOrderNo) && hasText(parsedOrderNo) && existingOrderNo.equals(parsedOrderNo)) {
            return;
        }
        if (hasText(parsedOrderNo) && isGeneratedUploadFallback(existingOrder)) {
            return;
        }
        throw new BusinessException("重新上传文件里的订单流水号必须和当前订单一致，当前订单："
                + (hasText(existingOrderNo) ? existingOrderNo : "空")
                + "，上传文件："
                + (hasText(parsedOrderNo) ? parsedOrderNo : "空"));
    }

    private void ensureAppendOrderNoMatches(OrderRecord existingOrder, OrderRecord appendedOrder) {
        String existingOrderNo = normalizeOrderNo(existingOrder == null ? null : existingOrder.getOrderNo());
        String appendedOrderNo = normalizeOrderNo(appendedOrder == null ? null : appendedOrder.getOrderNo());
        if (hasText(existingOrderNo) && hasText(appendedOrderNo) && existingOrderNo.equals(appendedOrderNo)) {
            return;
        }
        throw new BusinessException("追加文件里的订单流水号必须和当前订单一致，当前订单："
                + (hasText(existingOrderNo) ? existingOrderNo : "空")
                + "，上传文件："
                + (hasText(appendedOrderNo) ? appendedOrderNo : "空"));
    }

    private boolean isGeneratedUploadFallback(OrderRecord order) {
        if (order == null || nullToZero(order.getTotalQuantity()) > 0 || hasText(order.getDevelopmentNos())) {
            return false;
        }
        return normalizeOrderNo(order.getOrderNo()).matches("SF\\d{17}[A-Z0-9]{8}");
    }

    private void applyReuploadSummary(OrderRecord target, OrderRecord parsed) {
        applyOrderSummary(target, parsed);
        target.setUpdatedAt(LocalDateTime.now());
    }

    private void updateReuploadedOrder(OrderRecord order) {
        orderRecordMapper.update(null, new LambdaUpdateWrapper<OrderRecord>()
                .eq(OrderRecord::getId, order.getId())
                .set(OrderRecord::getOrderNo, order.getOrderNo())
                .set(OrderRecord::getCustomerName, order.getCustomerName())
                .set(OrderRecord::getDevelopmentNos, order.getDevelopmentNos())
                .set(OrderRecord::getTotalQuantity, order.getTotalQuantity())
                .set(OrderRecord::getTotalCartonCount, order.getTotalCartonCount())
                .set(OrderRecord::getSourceType, order.getSourceType())
                .set(OrderRecord::getUpdatedAt, order.getUpdatedAt()));
    }

    private void deleteStoredFileQuietly(StoredFile storedFile) {
        if (storedFile == null || storedFile.getPath() == null) {
            return;
        }
        try {
            Files.deleteIfExists(fileStorageUtil.resolvePath(storedFile.getPath().toString()));
        } catch (IOException ex) {
            log.warn("删除未完成重新上传的新原稿失败: {}", storedFile.getPath(), ex);
        } catch (RuntimeException ex) {
            log.warn("跳过无法删除的未完成重新上传新原稿: {}", storedFile.getPath(), ex);
        }
    }

    private List<OrderSheetPrintTask> replaceSheetPrintTaskFiles(Long orderId, StoredFile storedFile) {
        List<OrderSheetPrintTask> tasks = orderSheetPrintTaskMapper.selectList(new LambdaQueryWrapper<OrderSheetPrintTask>()
                .eq(OrderSheetPrintTask::getOrderId, orderId)
                .ne(OrderSheetPrintTask::getStatus, PrintTaskStatus.INVALID.getCode())
                .orderByAsc(OrderSheetPrintTask::getPrintType)
                .orderByAsc(OrderSheetPrintTask::getId));
        if (tasks.isEmpty()) {
            throw new BusinessException("订单打印任务不存在，不能重新上传");
        }

        Set<String> replacedOriginalPaths = tasks.stream()
                .map(OrderSheetPrintTask::getOriginalFilePath)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> replacedPreviewPdfPaths = tasks.stream()
                .map(OrderSheetPrintTask::getPreviewPdfPath)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LocalDateTime now = LocalDateTime.now();
        for (OrderSheetPrintTask task : tasks) {
            task.setOriginalFileName(storedFile == null ? null : storedFile.getOriginalName());
            task.setOriginalFilePath(storedFile == null || storedFile.getPath() == null ? null : storedFile.getPath().toString());
            task.setStatus(PrintTaskStatus.PENDING.getCode());
            task.setPreviewPdfPath(null);
            task.setPdfGeneratedAt(null);
            task.setPrintCount(0);
            task.setLastPrintTime(null);
            task.setLastPrintUser(null);
            task.setErrorMessage(null);
            task.setUpdatedAt(now);
            orderSheetPrintTaskMapper.update(null, new LambdaUpdateWrapper<OrderSheetPrintTask>()
                    .eq(OrderSheetPrintTask::getId, task.getId())
                    .set(OrderSheetPrintTask::getOriginalFileName, task.getOriginalFileName())
                    .set(OrderSheetPrintTask::getOriginalFilePath, task.getOriginalFilePath())
                    .set(OrderSheetPrintTask::getStatus, task.getStatus())
                    .set(OrderSheetPrintTask::getPreviewPdfPath, null)
                    .set(OrderSheetPrintTask::getPdfGeneratedAt, null)
                    .set(OrderSheetPrintTask::getPrintCount, task.getPrintCount())
                    .set(OrderSheetPrintTask::getLastPrintTime, null)
                    .set(OrderSheetPrintTask::getLastPrintUser, null)
                    .set(OrderSheetPrintTask::getErrorMessage, null)
                    .set(OrderSheetPrintTask::getUpdatedAt, now));
        }
        deleteReplacedFiles(replacedPreviewPdfPaths, storedFile);
        deleteReplacedFiles(replacedOriginalPaths, storedFile);
        return tasks;
    }

    private void deleteReplacedFiles(Collection<String> pathValues, StoredFile replacement) {
        if (pathValues == null || pathValues.isEmpty()) {
            return;
        }
        Path replacementPath = replacement == null || replacement.getPath() == null
                ? null
                : replacement.getPath().toAbsolutePath().normalize();
        for (String pathValue : pathValues) {
            try {
                Path path = fileStorageUtil.resolvePath(pathValue);
                if (replacementPath != null && path.equals(replacementPath)) {
                    continue;
                }
                Files.deleteIfExists(path);
            } catch (IOException ex) {
                log.warn("删除被替换的订单文件失败: {}", pathValue, ex);
            } catch (RuntimeException ex) {
                log.warn("跳过无法删除的订单历史文件: {}", pathValue, ex);
            }
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

    private List<OrderRecord> loadOrderGroup(Long orderId) {
        OrderRecord selectedOrder = getRequiredOrder(orderId);
        if (!hasText(selectedOrder.getOrderNo())) {
            return List.of(selectedOrder);
        }
        List<OrderRecord> orderGroup = new ArrayList<>(orderRecordMapper.selectList(
                new LambdaQueryWrapper<OrderRecord>()
                        .eq(OrderRecord::getOrderNo, selectedOrder.getOrderNo())
                        .eq(selectedOrder.getCustomerId()!=null, OrderRecord::getCustomerId, selectedOrder.getCustomerId())
                        .isNull(selectedOrder.getCustomerId()==null, OrderRecord::getCustomerId)
        ));
        if (orderGroup.isEmpty()) {
            return List.of(selectedOrder);
        }
        orderGroup.sort(Comparator
                .comparing(OrderRecord::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OrderRecord::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return orderGroup;
    }

    private Map<Long, Integer> buildPartNoByOrderId(List<OrderRecord> orderGroup) {
        Map<Long, Integer> partNoByOrderId = new LinkedHashMap<>();
        for (int index = 0; index < orderGroup.size(); index++) {
            partNoByOrderId.put(orderGroup.get(index).getId(), index + 1);
        }
        return partNoByOrderId;
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

    private void validateRecognizedDetailCount(
            String detailType,
            int existingCount,
            int recognizedCount
    ) {
        if (existingCount == 0) {
            return;
        }
        if (existingCount != recognizedCount) {
            throw new BusinessException(detailType + "明细行数不一致，原有 "
                    + existingCount + " 行，本次识别 "
                    + recognizedCount + " 行，已保留原明细");
        }
    }

    private void ensureOrderDetailsHaveCartonRanges(List<OrderRecordDetail> orderDetails) {
        if (orderDetails.isEmpty()) {
            throw new BusinessException("请先识别订单明细并填写开始箱号、结束箱号，再识别装箱单");
        }
        List<String> missingDetails = new ArrayList<>();
        for (int index = 0; index < orderDetails.size(); index++) {
            OrderRecordDetail detail = orderDetails.get(index);
            if (hasText(detail.getCartonStart()) && hasText(detail.getCartonEnd())) {
                continue;
            }
            String label = hasText(detail.getDevelopmentNo())
                    ? detail.getDevelopmentNo()
                    : "第 " + (index + 1) + " 条";
            missingDetails.add(label);
            if (missingDetails.size() == 10) {
                break;
            }
        }
        if (!missingDetails.isEmpty()) {
            throw new BusinessException("订单明细缺少开始箱号或结束箱号，不能识别装箱单："
                    + String.join("、", missingDetails));
        }
    }

    private void saveRecognizedOrderDetails(
            Long orderId,
            List<OrderRecordDetail> existingDetails,
            List<OrderRecordDetail> recognizedDetails
    ) {
        for (int index = 0; index < recognizedDetails.size(); index++) {
            OrderRecordDetail recognizedDetail = recognizedDetails.get(index);
            recognizedDetail.setOrderId(orderId);
            if (existingDetails.isEmpty()) {
                orderRecordDetailMapper.insert(recognizedDetail);
                continue;
            }
            OrderRecordDetail existingDetail = existingDetails.get(index);
            recognizedDetail.setId(existingDetail.getId());
            recognizedDetail.setCreatedAt(existingDetail.getCreatedAt());
            if (orderRecordDetailMapper.updateById(recognizedDetail) != 1) {
                throw new BusinessException("订单第 " + (index + 1) + " 条明细更新失败");
            }
        }
    }

    private void saveRecognizedPackingDetails(
            Long orderId,
            List<OrderPackingDetail> existingDetails,
            List<OrderPackingDetail> recognizedDetails
    ) {
        for (int index = 0; index < recognizedDetails.size(); index++) {
            OrderPackingDetail recognizedDetail = recognizedDetails.get(index);
            recognizedDetail.setOrderId(orderId);
            if (existingDetails.isEmpty()) {
                orderPackingDetailMapper.insert(recognizedDetail);
                continue;
            }
            OrderPackingDetail existingDetail = existingDetails.get(index);
            recognizedDetail.setId(existingDetail.getId());
            recognizedDetail.setCreatedAt(existingDetail.getCreatedAt());
            if (orderPackingDetailMapper.updateById(recognizedDetail) != 1) {
                throw new BusinessException("装箱单第 " + (index + 1) + " 条明细更新失败");
            }
        }
    }

    private Set<Long> deleteShippingNoteTaskItemsByDetailId(Long detailId) {
        List<ShippingNoteTaskItem> taskItems = shippingNoteTaskItemMapper.selectList(
                new LambdaQueryWrapper<ShippingNoteTaskItem>()
                        .select(ShippingNoteTaskItem::getTaskId)
                        .eq(ShippingNoteTaskItem::getSourceDetailId, detailId)
        );
        if (taskItems.isEmpty()) {
            return Collections.emptySet();
        }
        shippingNoteTaskItemMapper.delete(new LambdaQueryWrapper<ShippingNoteTaskItem>()
                .eq(ShippingNoteTaskItem::getSourceDetailId, detailId));
        return taskItems.stream()
                .map(ShippingNoteTaskItem::getTaskId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> deleteComponentOrderTaskItemsByDetailId(Long detailId) {
        List<ComponentOrderTaskItem> taskItems = componentOrderTaskItemMapper.selectList(
                new LambdaQueryWrapper<ComponentOrderTaskItem>()
                        .select(ComponentOrderTaskItem::getTaskId)
                        .eq(ComponentOrderTaskItem::getSourceDetailId, detailId)
        );
        if (taskItems.isEmpty()) {
            return Collections.emptySet();
        }
        componentOrderTaskItemMapper.delete(new LambdaQueryWrapper<ComponentOrderTaskItem>()
                .eq(ComponentOrderTaskItem::getSourceDetailId, detailId));
        return taskItems.stream()
                .map(ComponentOrderTaskItem::getTaskId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void refreshShippingNoteTasks(Set<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        for (Long taskId : taskIds) {
            List<ShippingNoteTaskItem> taskItems = shippingNoteTaskItemMapper.selectList(
                    new LambdaQueryWrapper<ShippingNoteTaskItem>()
                            .eq(ShippingNoteTaskItem::getTaskId, taskId)
                            .orderByAsc(ShippingNoteTaskItem::getLineNo)
                            .orderByAsc(ShippingNoteTaskItem::getId)
            );
            if (taskItems.isEmpty()) {
                shippingNoteTaskMapper.deleteById(taskId);
                continue;
            }

            List<OrderRecordDetail> details = loadExistingOrderDetails(taskItems.stream()
                    .map(ShippingNoteTaskItem::getSourceDetailId)
                    .toList());
            if (details.isEmpty()) {
                shippingNoteTaskItemMapper.delete(new LambdaQueryWrapper<ShippingNoteTaskItem>()
                        .eq(ShippingNoteTaskItem::getTaskId, taskId));
                shippingNoteTaskMapper.deleteById(taskId);
                continue;
            }

            ShippingNoteTask task = shippingNoteTaskMapper.selectById(taskId);
            if (task == null) {
                continue;
            }
            Map<Long, OrderRecord> orderMap = loadOrdersByIds(details.stream()
                    .map(OrderRecordDetail::getOrderId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList());
            task.setInvoiceNos(joinTaskOrderNos(details, orderMap));
            task.setDevelopmentNos(joinTaskDevelopmentNos(details));
            task.setItemCount(details.size());
            task.setTotalPairs(details.stream().mapToInt(this::resolveOrderDetailQuantity).sum());
            task.setTotalCartonCount(details.stream().mapToInt(item -> nullToZero(item.getCartonCount())).sum());
            task.setUpdatedAt(LocalDateTime.now());
            shippingNoteTaskMapper.updateById(task);
        }
    }

    private void refreshComponentOrderTasks(Set<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        for (Long taskId : taskIds) {
            List<ComponentOrderTaskItem> taskItems = componentOrderTaskItemMapper.selectList(
                    new LambdaQueryWrapper<ComponentOrderTaskItem>()
                            .eq(ComponentOrderTaskItem::getTaskId, taskId)
                            .orderByAsc(ComponentOrderTaskItem::getLineNo)
                            .orderByAsc(ComponentOrderTaskItem::getId)
            );
            if (taskItems.isEmpty()) {
                componentOrderTaskMapper.deleteById(taskId);
                continue;
            }

            List<OrderRecordDetail> details = loadExistingOrderDetails(taskItems.stream()
                    .map(ComponentOrderTaskItem::getSourceDetailId)
                    .toList());
            if (details.isEmpty()) {
                componentOrderTaskItemMapper.delete(new LambdaQueryWrapper<ComponentOrderTaskItem>()
                        .eq(ComponentOrderTaskItem::getTaskId, taskId));
                componentOrderTaskMapper.deleteById(taskId);
                continue;
            }

            ComponentOrderTask task = componentOrderTaskMapper.selectById(taskId);
            if (task == null) {
                continue;
            }
            Map<Long, OrderRecord> orderMap = loadOrdersByIds(details.stream()
                    .map(OrderRecordDetail::getOrderId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList());
            task.setOrderNos(joinTaskOrderNos(details, orderMap));
            task.setDevelopmentNos(joinTaskDevelopmentNos(details));
            task.setItemCount(details.size());
            task.setTotalPairs(details.stream().mapToInt(this::resolveOrderDetailQuantity).sum());
            task.setTotalCartonCount(details.stream().mapToInt(item -> nullToZero(item.getCartonCount())).sum());
            task.setUpdatedAt(LocalDateTime.now());
            componentOrderTaskMapper.updateById(task);
        }
    }

    private void refreshOrderSummaryAfterDetailRemoval(Long orderId) {
        if (orderId == null) {
            return;
        }
        OrderRecord order = orderRecordMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        List<OrderRecordDetail> details = loadDetails(orderId);
        order.setDevelopmentNos(joinTaskDevelopmentNos(details));
        order.setTotalQuantity(0);
        order.setTotalCartonCount(0);
        fillTotalsFromDetails(order, details);
        fillTotalsFromPackingDetails(order, loadPackingDetails(orderId));
        order.setUpdatedAt(LocalDateTime.now());
        orderRecordMapper.updateById(order);
    }

    private List<OrderRecordDetail> loadExistingOrderDetails(List<Long> detailIds) {
        List<Long> normalizedIds = detailIds == null ? List.of() : detailIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, OrderRecordDetail> detailMap = orderRecordDetailMapper.selectBatchIds(normalizedIds).stream()
                .filter(detail -> detail.getId() != null)
                .collect(Collectors.toMap(OrderRecordDetail::getId, detail -> detail));
        return normalizedIds.stream()
                .map(detailMap::get)
                .filter(detail -> detail != null)
                .toList();
    }

    private Map<Long, OrderRecord> loadOrdersByIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRecordMapper.selectBatchIds(orderIds).stream()
                .filter(order -> order.getId() != null)
                .collect(Collectors.toMap(OrderRecord::getId, order -> order));
    }

    private String joinTaskOrderNos(List<OrderRecordDetail> details, Map<Long, OrderRecord> orderMap) {
        Set<String> values = new LinkedHashSet<>();
        for (OrderRecordDetail detail : details) {
            OrderRecord order = detail.getOrderId() == null ? null : orderMap.get(detail.getOrderId());
            String orderNo = firstText(order == null ? null : order.getOrderNo(), detail.getCustomerOrderNo());
            if (hasText(orderNo)) {
                values.add(orderNo);
            }
        }
        return String.join(",", values);
    }

    private String joinTaskDevelopmentNos(List<OrderRecordDetail> details) {
        Set<String> values = new LinkedHashSet<>();
        for (OrderRecordDetail detail : details) {
            String developmentNo = detail.getDevelopmentNo();
            if (hasText(developmentNo)) {
                values.add(developmentNo.trim());
            }
        }
        return String.join(",", values);
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

    private void requireExcelOrder(OrderRecord order) {
        if (Integer.valueOf(3).equals(order.getSourceType())) throw new BusinessException("手工订单不需要 Excel 上传或识别");
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
        String developmentNo = DevelopmentNoUtil.normalize(value);
        return hasText(developmentNo) ? developmentNo : "";
    }

    private List<OrderRecordDetail> loadDetails(Long orderId) {
        return orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                .eq(OrderRecordDetail::getOrderId, orderId)
                .orderByAsc(OrderRecordDetail::getRowIndex)
                .orderByAsc(OrderRecordDetail::getLineNo));
    }

    private List<OrderRecordDetail> loadDetails(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderRecordDetailMapper.selectList(new LambdaQueryWrapper<OrderRecordDetail>()
                .in(OrderRecordDetail::getOrderId, orderIds)
                .orderByAsc(OrderRecordDetail::getOrderId)
                .orderByAsc(OrderRecordDetail::getRowIndex)
                .orderByAsc(OrderRecordDetail::getLineNo));
    }

    private List<OrderPackingDetail> loadPackingDetails(Long orderId) {
        return orderPackingDetailMapper.selectList(new LambdaQueryWrapper<OrderPackingDetail>()
                .eq(OrderPackingDetail::getOrderId, orderId)
                .orderByAsc(OrderPackingDetail::getRowIndex)
                .orderByAsc(OrderPackingDetail::getLineNo));
    }

    private List<OrderPackingDetail> loadPackingDetails(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderPackingDetailMapper.selectList(new LambdaQueryWrapper<OrderPackingDetail>()
                .in(OrderPackingDetail::getOrderId, orderIds)
                .orderByAsc(OrderPackingDetail::getOrderId)
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
        normalizeRecognitionStatusForEmptyDetails(order, details, packingDetails);
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

    private OrderRecordResponse buildOrderGroupResponse(List<OrderRecord> orderGroup) {
        return buildOrderGroupResponse(
                orderGroup,
                loadDetailsByOrderId(orderGroup),
                loadPackingDetailsByOrderId(orderGroup)
        );
    }

    private OrderRecordResponse buildOrderGroupResponse(
            List<OrderRecord> orderGroup,
            Map<Long, List<OrderRecordDetail>> detailsByOrderId,
            Map<Long, List<OrderPackingDetail>> packingDetailsByOrderId
    ) {
        if (orderGroup == null || orderGroup.isEmpty()) {
            throw new BusinessException("订单分组不能为空");
        }
        List<OrderRecordResponse> partResponses = orderGroup.stream()
                .map(order -> buildOrderResponse(
                        order,
                        detailsByOrderId.getOrDefault(order.getId(), List.of()),
                        packingDetailsByOrderId.getOrDefault(order.getId(), List.of())
                ))
                .toList();
        OrderRecordResponse response = OrderRecordResponse.from(orderGroup.get(0));
        response.setPartCount(orderGroup.size());
        response.setPartOrderIds(orderGroup.stream()
                .map(OrderRecord::getId)
                .filter(id -> id != null)
                .toList());
        response.setTotalQuantity(partResponses.stream()
                .mapToInt(part -> nullToZero(part.getTotalQuantity()))
                .sum());
        response.setTotalCartonCount(partResponses.stream()
                .mapToInt(part -> nullToZero(part.getTotalCartonCount()))
                .sum());

        LinkedHashSet<String> developmentNos = partResponses.stream()
                .flatMap(part -> part.getDevelopmentNoList().stream())
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        response.setDevelopmentNoList(List.copyOf(developmentNos));
        response.setDevelopmentNos(String.join(", ", developmentNos));
        response.setCustomerName(orderGroup.stream()
                .map(OrderRecord::getCustomerName)
                .filter(this::hasText)
                .findFirst()
                .orElse(null));
        response.setCreatedAt(orderGroup.stream()
                .map(OrderRecord::getCreatedAt)
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null));

        int orderStatus = aggregateRecognitionStatus(partResponses, false);
        int packingStatus = aggregateRecognitionStatus(partResponses, true);
        response.setOrderRecognitionStatus(orderStatus);
        response.setOrderRecognitionStatusText(OrderRecognitionStatus.fromCode(orderStatus).getLabel());
        response.setPackingRecognitionStatus(packingStatus);
        response.setPackingRecognitionStatusText(OrderRecognitionStatus.fromCode(packingStatus).getLabel());
        response.setOrderErrorMessage(joinGroupErrors(partResponses, false));
        response.setPackingErrorMessage(joinGroupErrors(partResponses, true));
        response.setErrorMessage(firstText(response.getOrderErrorMessage(), response.getPackingErrorMessage()));
        return response;
    }

    private int aggregateRecognitionStatus(
            List<OrderRecordResponse> partResponses,
            boolean packing
    ) {
        List<Integer> statuses = partResponses.stream()
                .map(part -> packing
                        ? part.getPackingRecognitionStatus()
                        : part.getOrderRecognitionStatus())
                .toList();
        if (statuses.contains(OrderRecognitionStatus.FAILED.getCode())) {
            return OrderRecognitionStatus.FAILED.getCode();
        }
        if (statuses.contains(OrderRecognitionStatus.PENDING_MANUAL.getCode())) {
            return OrderRecognitionStatus.PENDING_MANUAL.getCode();
        }
        if (!statuses.isEmpty() && statuses.stream()
                .allMatch(status -> status != null && status == OrderRecognitionStatus.RECOGNIZED.getCode())) {
            return OrderRecognitionStatus.RECOGNIZED.getCode();
        }
        return OrderRecognitionStatus.PENDING.getCode();
    }

    private String joinGroupErrors(
            List<OrderRecordResponse> partResponses,
            boolean packing
    ) {
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < partResponses.size(); index++) {
            OrderRecordResponse part = partResponses.get(index);
            String error = packing ? part.getPackingErrorMessage() : part.getOrderErrorMessage();
            if (!hasText(error)) {
                continue;
            }
            errors.add(partResponses.size() > 1
                    ? "第" + (index + 1) + "份：" + error
                    : error);
        }
        return errors.isEmpty() ? null : String.join("；", errors);
    }

    private void normalizeRecognitionStatusForEmptyDetails(
            OrderRecord order,
            List<OrderRecordDetail> details,
            List<OrderPackingDetail> packingDetails
    ) {
        int recognized = OrderRecognitionStatus.RECOGNIZED.getCode();
        int failed = OrderRecognitionStatus.FAILED.getCode();
        if (order.getOrderRecognitionStatus() != null
                && order.getOrderRecognitionStatus() == recognized
                && (details == null || details.isEmpty())) {
            order.setOrderRecognitionStatus(failed);
            if (!hasText(order.getOrderErrorMessage())) {
                order.setOrderErrorMessage("订单 sheet 未解析到明细行");
            }
        }
        if (order.getPackingRecognitionStatus() != null
                && order.getPackingRecognitionStatus() == recognized
                && (packingDetails == null || packingDetails.isEmpty())) {
            order.setPackingRecognitionStatus(failed);
            if (!hasText(order.getPackingErrorMessage())) {
                order.setPackingErrorMessage("装箱单 sheet 未解析到明细行");
            }
        }
        syncRecognitionErrorMessage(order);
    }

    private DetailTotals summarizeDetails(List<OrderRecordDetail> details) {
        int quantity = 0;
        int cartonCount = 0;
        for (OrderRecordDetail detail : details) {
            quantity += resolveOrderDetailQuantity(detail);
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
            quantity += resolvePackingDetailQuantity(detail);
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

    private int resolveOrderDetailQuantity(OrderRecordDetail detail) {
        return LedgerDataService.quantity(detail);
    }

    private int resolvePackingDetailQuantity(OrderPackingDetail detail) {
        if (detail == null) {
            return 0;
        }
        if (detail.getTotalPairs() != null && detail.getTotalPairs() > 0) {
            return detail.getTotalPairs();
        }
        int perCartonPairs = sumSizeQuantities(detail.getSizeQuantitiesJson());
        if (perCartonPairs > 0 && detail.getCartonCount() != null && detail.getCartonCount() > 0) {
            return perCartonPairs * detail.getCartonCount();
        }
        return perCartonPairs;
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

    private record OrderGroup(List<OrderRecord> parts) {
    }

    private record OrderGroupView(OrderGroup group, OrderRecordResponse response) {
    }

    private static class DevelopmentNoBucket {
        private final String developmentNo;
        private final Map<String, DevelopmentNoOrderReference> orderReferences = new LinkedHashMap<>();
        private int pairCount;
        private int shippedPairCount;
        private int unshippedPairCount;
        private int detailCount;

        private DevelopmentNoBucket(String developmentNo) {
            this.developmentNo = developmentNo;
        }

        private void add(
                int pairs,
                int shippedPairs,
                int unshippedPairs,
                DevelopmentNoOrderReference orderReference
        ) {
            pairCount += pairs;
            shippedPairCount += shippedPairs;
            unshippedPairCount += unshippedPairs;
            detailCount += 1;
            addOrderReference(orderReference);
        }

        private void addOrderReference(DevelopmentNoOrderReference source) {
            if (source == null) {
                return;
            }
            String key = String.join("|",
                    String.valueOf(source.getOrderId()),
                    sortText(source.getInvoiceNo()),
                    sortText(source.getOrderNo()));
            DevelopmentNoOrderReference target = orderReferences.computeIfAbsent(key, ignored -> {
                DevelopmentNoOrderReference reference = new DevelopmentNoOrderReference();
                reference.setOrderId(source.getOrderId());
                reference.setInvoiceNo(source.getInvoiceNo());
                reference.setOrderNo(source.getOrderNo());
                reference.setPairCount(0);
                reference.setShippedPairCount(0);
                reference.setUnshippedPairCount(0);
                reference.setDetailCount(0);
                return reference;
            });
            target.setPairCount(nullToZero(target.getPairCount()) + nullToZero(source.getPairCount()));
            target.setShippedPairCount(nullToZero(target.getShippedPairCount()) + nullToZero(source.getShippedPairCount()));
            target.setUnshippedPairCount(nullToZero(target.getUnshippedPairCount()) + nullToZero(source.getUnshippedPairCount()));
            target.setDetailCount(nullToZero(target.getDetailCount()) + nullToZero(source.getDetailCount()));
        }

        private String getDevelopmentNo() {
            return developmentNo;
        }

        private int getPairCount() {
            return pairCount;
        }

        private int getShippedPairCount() {
            return shippedPairCount;
        }

        private int getUnshippedPairCount() {
            return unshippedPairCount;
        }

        private int getDetailCount() {
            return detailCount;
        }

        private List<DevelopmentNoOrderReference> getOrderReferences() {
            return orderReferences.values()
                    .stream()
                    .sorted(Comparator.comparing((DevelopmentNoOrderReference reference) -> sortText(reference.getInvoiceNo()))
                            .thenComparing(reference -> sortText(reference.getOrderNo())))
                    .toList();
        }

        private static int nullToZero(Integer value) {
            return value == null ? 0 : value;
        }

        private static String sortText(String value) {
            return value == null ? "" : value;
        }
    }
}
