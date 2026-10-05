package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteGeneratedSummaryResponse;
import com.shoefactory.assistant.dto.ShippingNoteItemRequest;
import com.shoefactory.assistant.dto.ShippingNoteTaskResponse;
import com.shoefactory.assistant.dto.ShippingNoteUpdateRequest;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskItemMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskMapper;
import com.shoefactory.assistant.service.ShippingNoteTaskService;
import com.shoefactory.assistant.util.CustomerNameUtil;
import com.shoefactory.assistant.util.FileStorageUtil;
import com.shoefactory.assistant.util.PackingDetailMatchUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ShippingNoteTaskServiceImpl implements ShippingNoteTaskService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern CARTON_PATTERN = Pattern.compile("^(.*?)(\\d+)(.*?)$");

    private final ShippingNoteTaskMapper shippingNoteTaskMapper;
    private final ShippingNoteTaskItemMapper shippingNoteTaskItemMapper;
    private final OrderRecordDetailMapper orderRecordDetailMapper;
    private final OrderPackingDetailMapper orderPackingDetailMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final com.shoefactory.assistant.service.CustomerConfigService customers;

    public ShippingNoteTaskServiceImpl(
            ShippingNoteTaskMapper shippingNoteTaskMapper,
            ShippingNoteTaskItemMapper shippingNoteTaskItemMapper,
            OrderRecordDetailMapper orderRecordDetailMapper,
            OrderPackingDetailMapper orderPackingDetailMapper,
            OrderRecordMapper orderRecordMapper,
            com.shoefactory.assistant.service.CustomerConfigService customers
    ) {
        this.shippingNoteTaskMapper = shippingNoteTaskMapper;
        this.shippingNoteTaskItemMapper = shippingNoteTaskItemMapper;
        this.orderRecordDetailMapper = orderRecordDetailMapper;
        this.orderPackingDetailMapper = orderPackingDetailMapper;
        this.orderRecordMapper = orderRecordMapper;
        this.customers = customers;
    }

    @Override
    @Transactional
    public ShippingNoteTaskResponse createShippingNoteTask(ShippingNoteCreateRequest request) {
        List<ShippingNoteItemRequest> items = sanitizeItems(request.getItems());
        if (items.isEmpty()) {
            throw new BusinessException("出货单明细不能为空");
        }

        Map<Long, OrderRecordDetail> orderDetailMap = loadOrderDetailsByRequestItems(items);
        List<OrderRecordDetail> selectedOrderDetails = items.stream()
                .map(item -> orderDetailMap.get(item.getSourceDetailId()))
                .toList();
        Map<Long, OrderRecord> orderMap = loadOrdersByOrderDetails(selectedOrderDetails);
        Long customerId = resolveCustomerId(orderMap);
        Map<Long, List<OrderPackingDetail>> packingDetailsByOrderId = loadPackingDetailsByOrderDetails(selectedOrderDetails);
        ensureOrderDetailsHaveMatchingPackingDetails(selectedOrderDetails, packingDetailsByOrderId);

        LocalDateTime now = LocalDateTime.now();
        ShippingNoteTask task = new ShippingNoteTask();
        task.setTaskNo(FileStorageUtil.newBusinessNo("SH"));
        task.setCustomerId(customerId);
        task.setSourceType(1);
        task.setRecipientName(firstText(request.getRecipientName(),
                customerId == null ? "达为鞋业" : customers.require(customerId).getRecipientName()));
        task.setShippingDate(request.getShippingDate() == null ? LocalDate.now() : request.getShippingDate());
        task.setInvoiceNos(joinInvoiceNos(selectedOrderDetails, orderMap));
        task.setDevelopmentNos(joinDevelopmentNos(selectedOrderDetails));
        task.setItemCount(selectedOrderDetails.size());
        task.setTotalPairs(sumOrderPairs(selectedOrderDetails));
        task.setTotalCartonCount(sumOrderCartons(selectedOrderDetails));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        shippingNoteTaskMapper.insert(task);

        for (int index = 0; index < selectedOrderDetails.size(); index++) {
            ShippingNoteTaskItem item = buildTaskItem(task.getId(), index + 1, selectedOrderDetails.get(index), now);
            shippingNoteTaskItemMapper.insert(item);
        }

        return getShippingNoteTask(task.getId());
    }

    @Override
    public PageResponse<ShippingNoteTaskResponse> listShippingNoteTasks(
            String orderNo,
            String developmentNo,
            LocalDate startDate,
            LocalDate endDate,
            long page,
            long size
    ) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }
        Page<ShippingNoteTask> pageRequest = new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100));
        LambdaQueryWrapper<ShippingNoteTask> wrapper = new LambdaQueryWrapper<ShippingNoteTask>()
                .ne(ShippingNoteTask::getSourceType, 3)
                .like(hasText(orderNo), ShippingNoteTask::getInvoiceNos, orderNo)
                .like(hasText(developmentNo), ShippingNoteTask::getDevelopmentNos, developmentNo)
                .ge(startDate != null, ShippingNoteTask::getShippingDate, startDate)
                .le(endDate != null, ShippingNoteTask::getShippingDate, endDate)
                .orderByDesc(ShippingNoteTask::getCreatedAt);
        Page<ShippingNoteTask> resultPage = shippingNoteTaskMapper.selectPage(pageRequest, wrapper);
        List<ShippingNoteTaskResponse> records = resultPage.getRecords().stream()
                .map(task -> ShippingNoteTaskResponse.from(task, List.of()))
                .toList();
        return PageResponse.from(resultPage, records);
    }

    @Override
    public ShippingNoteGeneratedSummaryResponse getGeneratedSummary(List<Long> orderIds) {
        List<Long> normalizedOrderIds = normalizeOrderIds(orderIds);
        if (normalizedOrderIds.isEmpty()) {
            return ShippingNoteGeneratedSummaryResponse.empty();
        }

        List<OrderRecordDetail> orderDetails = orderRecordDetailMapper.selectList(
                new LambdaQueryWrapper<OrderRecordDetail>()
                        .select(OrderRecordDetail::getId, OrderRecordDetail::getOrderId)
                        .in(OrderRecordDetail::getOrderId, normalizedOrderIds)
        );
        if (orderDetails.isEmpty()) {
            return ShippingNoteGeneratedSummaryResponse.empty();
        }

        Map<Long, Set<Long>> detailIdsByOrderId = orderDetails.stream()
                .filter(detail -> detail.getId() != null && detail.getOrderId() != null)
                .collect(Collectors.groupingBy(
                        OrderRecordDetail::getOrderId,
                        Collectors.mapping(OrderRecordDetail::getId, Collectors.toSet())
                ));
        Set<Long> allDetailIds = detailIdsByOrderId.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        if (allDetailIds.isEmpty()) {
            return ShippingNoteGeneratedSummaryResponse.empty();
        }

        List<ShippingNoteTaskItem> generatedItems = shippingNoteTaskItemMapper.selectList(
                new LambdaQueryWrapper<ShippingNoteTaskItem>()
                        .select(ShippingNoteTaskItem::getSourceDetailId)
                        .in(ShippingNoteTaskItem::getSourceDetailId, allDetailIds)
        );
        Set<Long> generatedDetailIds = generatedItems.stream()
                .map(ShippingNoteTaskItem::getSourceDetailId)
                .filter(allDetailIds::contains)
                .collect(Collectors.toSet());
        List<Long> fullyGeneratedOrderIds = normalizedOrderIds.stream()
                .filter(orderId -> {
                    Set<Long> orderDetailIds = detailIdsByOrderId.getOrDefault(orderId, Set.of());
                    return !orderDetailIds.isEmpty() && generatedDetailIds.containsAll(orderDetailIds);
                })
                .toList();

        return ShippingNoteGeneratedSummaryResponse.of(generatedDetailIds, fullyGeneratedOrderIds);
    }

    @Override
    public ShippingNoteTaskResponse getShippingNoteTask(Long id) {
        ShippingNoteTask task = shippingNoteTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("出货单任务不存在: " + id);
        }
        if (Integer.valueOf(3).equals(task.getSourceType())) throw new BusinessException("手工出货记录请从订单查看");
        return buildFullResponse(task);
    }

    @Override
    @Transactional
    public ShippingNoteTaskResponse updateShippingNoteTask(Long id, ShippingNoteUpdateRequest request) {
        ShippingNoteTask task = shippingNoteTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("出货单任务不存在: " + id);
        }
        if (Integer.valueOf(3).equals(task.getSourceType())) throw new BusinessException("手工出货记录不能从打印任务修改");
        boolean changed = false;
        if (hasText(request.getRecipientName()) && !request.getRecipientName().equals(task.getRecipientName())) {
            task.setRecipientName(request.getRecipientName().trim());
            changed = true;
        }
        if (request.getShippingDate() != null && !request.getShippingDate().equals(task.getShippingDate())) {
            task.setShippingDate(request.getShippingDate());
            changed = true;
        }
        if (changed) {
            task.setUpdatedAt(LocalDateTime.now());
            shippingNoteTaskMapper.updateById(task);
        }
        return buildFullResponse(task);
    }

    private ShippingNoteTaskResponse buildFullResponse(ShippingNoteTask task) {
        Long taskId = task.getId();
        List<ShippingNoteTaskItem> taskItems = shippingNoteTaskItemMapper.selectList(
                new LambdaQueryWrapper<ShippingNoteTaskItem>()
                        .eq(ShippingNoteTaskItem::getTaskId, taskId)
                        .orderByAsc(ShippingNoteTaskItem::getLineNo)
                        .orderByAsc(ShippingNoteTaskItem::getId)
        );
        if (taskItems.isEmpty()) {
            return ShippingNoteTaskResponse.from(task, List.of());
        }

        Map<Long, OrderRecordDetail> orderDetailMap = loadOrderDetailsByTaskItems(taskItems);
        Map<Long, OrderRecord> orderMap = loadOrdersByOrderDetails(orderDetailMap.values());
        Map<Long, List<OrderPackingDetail>> packingDetailsByOrderId = loadPackingDetailsByOrderDetails(orderDetailMap.values());
        List<ShippingNoteItemRequest> responseItems = taskItems.stream()
                .map(item -> toOrderItemResponse(
                        item,
                        orderDetailMap.get(item.getSourceDetailId()),
                        orderMap,
                        packingDetailsByOrderId
                ))
                .toList();
        return ShippingNoteTaskResponse.from(task, responseItems);
    }

    private ShippingNoteTaskItem buildTaskItem(
            Long taskId,
            int lineNo,
            OrderRecordDetail orderDetail,
            LocalDateTime now
    ) {
        ShippingNoteTaskItem item = new ShippingNoteTaskItem();
        item.setTaskId(taskId);
        item.setLineNo(lineNo);
        item.setOrderId(orderDetail.getOrderId());
        item.setSourceDetailId(orderDetail.getId());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }

    private ShippingNoteItemRequest toOrderItemResponse(
            ShippingNoteTaskItem taskItem,
            OrderRecordDetail orderDetail,
            Map<Long, OrderRecord> orderMap,
            Map<Long, List<OrderPackingDetail>> packingDetailsByOrderId
    ) {
        if (orderDetail == null) {
            throw new BusinessException("订单明细不存在: " + taskItem.getSourceDetailId());
        }
        Long orderId = orderDetail.getOrderId() == null ? taskItem.getOrderId() : orderDetail.getOrderId();
        OrderRecord order = orderId == null ? null : orderMap.get(orderId);
        Map<String, Integer> sizeQuantities = parseSizeQuantities(orderDetail.getSizeQuantitiesJson());
        int pairCount = com.shoefactory.assistant.service.LedgerDataService.quantity(orderDetail);
        List<ShippingNoteItemRequest> packingItems = getMatchingPackingDetails(orderDetail, packingDetailsByOrderId)
                .stream()
                .map(packingDetail -> toPackingItemResponse(packingDetail, order, orderDetail))
                .toList();

        ShippingNoteItemRequest response = new ShippingNoteItemRequest();
        response.setSourceDetailId(orderDetail.getId());
        response.setOrderId(orderId);
        response.setOrderNo(firstText(order == null ? null : order.getOrderNo(), orderDetail.getCustomerOrderNo()));
        response.setDevelopmentNo(cleanText(orderDetail.getDevelopmentNo()));
        response.setCustomerName(firstText(orderDetail.getCustomerName(), order == null ? null : order.getCustomerName()));
        response.setSizeQuantities(sizeQuantities);
        response.setPairCount(pairCount);
        response.setCartonCount(nullToZero(orderDetail.getCartonCount()));
        response.setTotalPairs(pairCount);
        response.setCartonStart(cleanText(orderDetail.getCartonStart()));
        response.setCartonEnd(cleanText(orderDetail.getCartonEnd()));
        response.setPackingItems(packingItems);
        return response;
    }

    private ShippingNoteItemRequest toPackingItemResponse(
            OrderPackingDetail packingDetail,
            OrderRecord order,
            OrderRecordDetail orderDetail
    ) {
        Map<String, Integer> sizeQuantities = parseSizeQuantities(packingDetail.getSizeQuantitiesJson());
        int sizeTotal = sumSizeQuantities(sizeQuantities);
        int totalPairs = getPackingTotalPairs(packingDetail, sizeTotal);
        String combinedColorMaterial = joinText(packingDetail.getCustomerColor(), packingDetail.getMaterial());

        ShippingNoteItemRequest response = new ShippingNoteItemRequest();
        response.setSourceDetailId(packingDetail.getId());
        response.setOrderId(packingDetail.getOrderId());
        response.setOrderNo(firstText(order == null ? null : order.getOrderNo(), packingDetail.getCustomerOrderNo()));
        response.setDevelopmentNo(cleanText(packingDetail.getCompanyStyleNo()));
        response.setCustomerName(firstText(
                CustomerNameUtil.normalizeWithoutChinese(packingDetail.getCustomerName()),
                order == null ? null : order.getCustomerName()));
        response.setCustomerStyleNo(cleanText(packingDetail.getCustomerStyleNo()));
        response.setEnglishColor(firstText(packingDetail.getCustomerColor(), orderDetail.getEnglishColor()));
        response.setEnglishMaterial(firstText(packingDetail.getMaterial(), orderDetail.getEnglishMaterial()));
        response.setColorMaterial(beforeFirstComma(firstText(
                orderDetail.getUpperMaterial(),
                combinedColorMaterial,
                packingDetail.getMaterial(),
                packingDetail.getCustomerColor()
        )));
        response.setTrademark(firstText(packingDetail.getTrademark(), orderDetail.getTrademark()));
        response.setSizeQuantities(sizeQuantities);
        response.setPairCount(sizeTotal > 0 ? sizeTotal : totalPairs);
        response.setCartonCount(nullToZero(packingDetail.getCartonCount()));
        response.setTotalPairs(totalPairs);
        response.setCartonStart(cleanText(packingDetail.getCartonStart()));
        response.setCartonEnd(cleanText(packingDetail.getCartonEnd()));
        return response;
    }

    private Map<Long, OrderRecordDetail> loadOrderDetailsByRequestItems(List<ShippingNoteItemRequest> items) {
        List<Long> sourceDetailIds = items.stream()
                .map(ShippingNoteItemRequest::getSourceDetailId)
                .distinct()
                .toList();
        return loadOrderDetailsByIds(sourceDetailIds);
    }

    private Long resolveCustomerId(Map<Long, OrderRecord> orders) {
        if (orders.values().stream().anyMatch(order -> Integer.valueOf(3).equals(order.getSourceType()))) {
            throw new BusinessException("手工订单请在订单列表确认出货");
        }
        Set<Long> ids = orders.values().stream().map(OrderRecord::getCustomerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.size() > 1) throw new BusinessException("同一出货单不能混合不同客户的订单");
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    private Map<Long, OrderRecordDetail> loadOrderDetailsByTaskItems(List<ShippingNoteTaskItem> items) {
        List<Long> sourceDetailIds = items.stream()
                .map(ShippingNoteTaskItem::getSourceDetailId)
                .distinct()
                .toList();
        return loadOrderDetailsByIds(sourceDetailIds);
    }

    private Map<Long, OrderRecordDetail> loadOrderDetailsByIds(List<Long> sourceDetailIds) {
        if (sourceDetailIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, OrderRecordDetail> details = orderRecordDetailMapper.selectBatchIds(sourceDetailIds)
                .stream()
                .collect(Collectors.toMap(OrderRecordDetail::getId, Function.identity()));
        for (Long sourceDetailId : sourceDetailIds) {
            if (!details.containsKey(sourceDetailId)) {
                throw new BusinessException("订单明细不存在: " + sourceDetailId);
            }
        }
        return details;
    }

    private Map<Long, OrderRecord> loadOrdersByOrderDetails(Collection<OrderRecordDetail> orderDetails) {
        List<Long> orderIds = orderDetails.stream()
                .map(OrderRecordDetail::getOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRecordMapper.selectBatchIds(orderIds)
                .stream()
                .collect(Collectors.toMap(OrderRecord::getId, Function.identity()));
    }

    private Map<Long, List<OrderPackingDetail>> loadPackingDetailsByOrderDetails(Collection<OrderRecordDetail> orderDetails) {
        List<Long> orderIds = orderDetails.stream()
                .map(OrderRecordDetail::getOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<OrderPackingDetail>> detailsByOrderId = orderPackingDetailMapper.selectList(new LambdaQueryWrapper<OrderPackingDetail>()
                        .in(OrderPackingDetail::getOrderId, orderIds)
                        .orderByAsc(OrderPackingDetail::getRowIndex)
                        .orderByAsc(OrderPackingDetail::getLineNo)
                        .orderByAsc(OrderPackingDetail::getId))
                .stream()
                .collect(Collectors.groupingBy(OrderPackingDetail::getOrderId));
        return detailsByOrderId;
    }

    private void ensureOrderDetailsHaveMatchingPackingDetails(
            List<OrderRecordDetail> orderDetails,
            Map<Long, List<OrderPackingDetail>> packingDetailsByOrderId
    ) {
        List<String> missingDetails = orderDetails.stream()
                .filter(detail -> getMatchingPackingDetails(detail, packingDetailsByOrderId).isEmpty())
                .map(this::describeOrderDetail)
                .distinct()
                .limit(5)
                .toList();
        if (!missingDetails.isEmpty()) {
            throw new BusinessException("订单明细没有对应的装箱单，不能创建出货单: " + String.join("、", missingDetails));
        }
    }

    private List<OrderPackingDetail> getMatchingPackingDetails(
            OrderRecordDetail orderDetail,
            Map<Long, List<OrderPackingDetail>> packingDetailsByOrderId
    ) {
        if (orderDetail == null || orderDetail.getOrderId() == null) {
            return List.of();
        }
        return packingDetailsByOrderId
                .getOrDefault(orderDetail.getOrderId(), List.of())
                .stream()
                .filter(packingDetail -> PackingDetailMatchUtil.isMatchingPackingDetail(orderDetail, packingDetail))
                .toList();
    }

    private String describeOrderDetail(OrderRecordDetail detail) {
        String name = firstText(detail.getDevelopmentNo(), detail.getCustomerStyleNo());
        return hasText(name) ? name : "明细ID " + detail.getId();
    }

    private List<ShippingNoteItemRequest> sanitizeItems(List<ShippingNoteItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null && item.getSourceDetailId() != null)
                .toList();
    }

    private List<Long> normalizeOrderIds(Collection<Long> orderIds) {
        if (orderIds == null) {
            return List.of();
        }
        return orderIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private String joinDevelopmentNos(List<OrderRecordDetail> items) {
        Set<String> values = new LinkedHashSet<>();
        for (OrderRecordDetail item : items) {
            String developmentNo = cleanText(item.getDevelopmentNo());
            if (hasText(developmentNo)) {
                values.add(developmentNo);
            }
        }
        return String.join(",", values);
    }

    private String joinInvoiceNos(List<OrderRecordDetail> items, Map<Long, OrderRecord> orderMap) {
        Set<String> values = new LinkedHashSet<>();
        for (OrderRecordDetail item : items) {
            OrderRecord order = item.getOrderId() == null ? null : orderMap.get(item.getOrderId());
            String orderNo = firstText(order == null ? null : order.getOrderNo(), item.getCustomerOrderNo());
            if (hasText(orderNo)) {
                values.add(orderNo);
            }
        }
        return String.join(",", values);
    }

    private int sumOrderPairs(List<OrderRecordDetail> items) {
        int total = 0;
        for (OrderRecordDetail item : items) {
            total += com.shoefactory.assistant.service.LedgerDataService.quantity(item);
        }
        return total;
    }

    private int sumOrderCartons(List<OrderRecordDetail> items) {
        int total = 0;
        for (OrderRecordDetail item : items) {
            total += nullToZero(item.getCartonCount());
        }
        return total;
    }

    private int getPackingTotalPairs(OrderPackingDetail item, int sizeTotal) {
        if (item.getTotalPairs() != null && item.getTotalPairs() > 0) {
            return item.getTotalPairs();
        }
        int cartonCount = nullToZero(item.getCartonCount());
        if (sizeTotal > 0 && cartonCount > 0) {
            return sizeTotal * cartonCount;
        }
        return sizeTotal;
    }

    private boolean isMatchingPackingDetail(OrderRecordDetail detail, OrderPackingDetail packingDetail) {
        if (isPackingRangeInsideOrderRange(detail, packingDetail)) {
            return sameProductWhenPresent(detail, packingDetail);
        }
        if (hasCartonRange(detail)) {
            return false;
        }
        if (sameRequired(detail.getCartonStart(), packingDetail.getCartonStart())
                && sameRequired(detail.getCartonEnd(), packingDetail.getCartonEnd())) {
            return sameProductWhenPresent(detail, packingDetail);
        }
        if (sameRequired(detail.getDevelopmentNo(), packingDetail.getCompanyStyleNo())) {
            return sameProductWhenPresent(detail, packingDetail);
        }
        if (sameRequired(detail.getCustomerStyleNo(), packingDetail.getCustomerStyleNo())) {
            if (sameRequired(detail.getCustomerOrderNo(), packingDetail.getCustomerOrderNo())) {
                return sameOptional(detail.getPoNo(), packingDetail.getPoNo())
                        && sameOptional(detail.getEnglishColor(), packingDetail.getCustomerColor());
            }
            if (sameRequired(detail.getPoNo(), packingDetail.getPoNo())) {
                return sameOptional(detail.getEnglishColor(), packingDetail.getCustomerColor());
            }
        }
        return false;
    }

    private boolean sameProductWhenPresent(OrderRecordDetail detail, OrderPackingDetail packingDetail) {
        return sameOptional(detail.getDevelopmentNo(), packingDetail.getCompanyStyleNo())
                && sameOptional(detail.getCustomerStyleNo(), packingDetail.getCustomerStyleNo())
                && sameOptional(detail.getCustomerOrderNo(), packingDetail.getCustomerOrderNo())
                && sameOptional(detail.getPoNo(), packingDetail.getPoNo())
                && sameOptional(detail.getEnglishColor(), packingDetail.getCustomerColor());
    }

    private boolean isPackingRangeInsideOrderRange(OrderRecordDetail detail, OrderPackingDetail packingDetail) {
        CartonRange detailRange = getCartonRange(detail.getCartonStart(), detail.getCartonEnd());
        CartonRange packingRange = getCartonRange(packingDetail.getCartonStart(), packingDetail.getCartonEnd());
        if (detailRange == null || packingRange == null) {
            return false;
        }
        return detailRange.prefix().equals(packingRange.prefix())
                && detailRange.suffix().equals(packingRange.suffix())
                && detailRange.start() <= packingRange.start()
                && packingRange.end() <= detailRange.end();
    }

    private boolean hasCartonRange(OrderRecordDetail detail) {
        return getCartonRange(detail.getCartonStart(), detail.getCartonEnd()) != null;
    }

    private CartonRange getCartonRange(String start, String end) {
        ParsedCarton startCarton = parseCarton(start);
        ParsedCarton endCarton = parseCarton(end);
        if (endCarton == null) {
            endCarton = startCarton;
        }
        if (startCarton == null || endCarton == null
                || !startCarton.prefix().equals(endCarton.prefix())
                || !startCarton.suffix().equals(endCarton.suffix())) {
            return null;
        }
        return new CartonRange(
                startCarton.prefix(),
                Math.min(startCarton.number(), endCarton.number()),
                Math.max(startCarton.number(), endCarton.number()),
                startCarton.suffix()
        );
    }

    private ParsedCarton parseCarton(String value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        Matcher matcher = CARTON_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        return new ParsedCarton(
                normalizeMatchText(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                normalizeMatchText(matcher.group(3))
        );
    }

    private boolean sameRequired(String left, String right) {
        String leftValue = normalizeMatchText(left);
        String rightValue = normalizeMatchText(right);
        return hasText(leftValue) && hasText(rightValue) && leftValue.equals(rightValue);
    }

    private boolean sameOptional(String left, String right) {
        String leftValue = normalizeMatchText(left);
        String rightValue = normalizeMatchText(right);
        return !hasText(leftValue) || !hasText(rightValue) || leftValue.equals(rightValue);
    }

    private String normalizeMatchText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private Map<String, Integer> parseSizeQuantities(String json) {
        if (!hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, SIZE_MAP_TYPE);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private int sumSizeQuantities(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.values().stream()
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String joinText(String... values) {
        if (values == null) {
            return null;
        }
        String joined = java.util.Arrays.stream(values)
                .filter(this::hasText)
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return hasText(joined) ? joined : null;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String cleanText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String beforeFirstComma(String value) {
        if (!hasText(value)) {
            return null;
        }
        String text = value.trim();
        int commaIndex = firstCommaIndex(text);
        return commaIndex >= 0 ? cleanText(text.substring(0, commaIndex)) : text;
    }

    private int firstCommaIndex(String value) {
        int englishCommaIndex = value.indexOf(',');
        int chineseCommaIndex = value.indexOf('，');
        if (englishCommaIndex < 0) {
            return chineseCommaIndex;
        }
        if (chineseCommaIndex < 0) {
            return englishCommaIndex;
        }
        return Math.min(englishCommaIndex, chineseCommaIndex);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ParsedCarton(String prefix, int number, String suffix) {
    }

    private record CartonRange(String prefix, int start, int end, String suffix) {
    }
}
