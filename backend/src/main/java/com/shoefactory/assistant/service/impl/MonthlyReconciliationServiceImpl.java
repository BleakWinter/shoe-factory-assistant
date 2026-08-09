package com.shoefactory.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.MonthlyReconciliationResponse;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;
import com.shoefactory.assistant.entity.ShoeStyleConfig;
import com.shoefactory.assistant.mapper.OrderRecordDetailMapper;
import com.shoefactory.assistant.mapper.OrderRecordMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskItemMapper;
import com.shoefactory.assistant.mapper.ShippingNoteTaskMapper;
import com.shoefactory.assistant.mapper.ShoeStyleConfigMapper;
import com.shoefactory.assistant.service.MonthlyReconciliationService;
import com.shoefactory.assistant.util.DevelopmentNoUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MonthlyReconciliationServiceImpl implements MonthlyReconciliationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };
    private final ShippingNoteTaskMapper shippingNoteTaskMapper;
    private final ShippingNoteTaskItemMapper shippingNoteTaskItemMapper;
    private final OrderRecordDetailMapper orderRecordDetailMapper;
    private final OrderRecordMapper orderRecordMapper;
    private final ShoeStyleConfigMapper shoeStyleConfigMapper;

    public MonthlyReconciliationServiceImpl(
            ShippingNoteTaskMapper shippingNoteTaskMapper,
            ShippingNoteTaskItemMapper shippingNoteTaskItemMapper,
            OrderRecordDetailMapper orderRecordDetailMapper,
            OrderRecordMapper orderRecordMapper,
            ShoeStyleConfigMapper shoeStyleConfigMapper
    ) {
        this.shippingNoteTaskMapper = shippingNoteTaskMapper;
        this.shippingNoteTaskItemMapper = shippingNoteTaskItemMapper;
        this.orderRecordDetailMapper = orderRecordDetailMapper;
        this.orderRecordMapper = orderRecordMapper;
        this.shoeStyleConfigMapper = shoeStyleConfigMapper;
    }

    @Override
    public MonthlyReconciliationResponse getMonthlyReconciliation(String month, String customerName) {
        YearMonth reconciliationMonth = parseMonth(month);
        String normalizedCustomerName = requireText(customerName, "客户不能为空");
        LocalDate monthStart = reconciliationMonth.atDay(1);
        LocalDate nextMonthStart = reconciliationMonth.plusMonths(1).atDay(1);

        List<ShippingNoteTask> tasks = shippingNoteTaskMapper.selectList(
                new LambdaQueryWrapper<ShippingNoteTask>()
                        .eq(ShippingNoteTask::getRecipientName, normalizedCustomerName)
                        .ge(ShippingNoteTask::getShippingDate, monthStart)
                        .lt(ShippingNoteTask::getShippingDate, nextMonthStart)
                        .orderByAsc(ShippingNoteTask::getShippingDate)
                        .orderByAsc(ShippingNoteTask::getTaskNo)
                        .orderByAsc(ShippingNoteTask::getId)
        );

        List<MonthlyReconciliationResponse.Detail> details = buildDetails(tasks);
        int totalOrderQuantity = details.stream()
                .map(MonthlyReconciliationResponse.Detail::getOrderQuantity)
                .mapToInt(this::nullToZero)
                .sum();
        int totalShippingQuantity = details.stream()
                .map(MonthlyReconciliationResponse.Detail::getShippingQuantity)
                .mapToInt(this::nullToZero)
                .sum();
        BigDecimal goodsAmount = details.stream()
                .map(MonthlyReconciliationResponse.Detail::getAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        MonthlyReconciliationResponse response = new MonthlyReconciliationResponse();
        response.setMonth(reconciliationMonth.toString());
        response.setCustomerName(normalizedCustomerName);
        response.setDetails(details);
        response.setTotalOrderQuantity(totalOrderQuantity);
        response.setTotalShippingQuantity(totalShippingQuantity);
        response.setGoodsAmount(money(goodsAmount));
        response.setMissingPriceCount((int) details.stream()
                .filter(item -> Boolean.TRUE.equals(item.getMissingPrice()))
                .count());
        return response;
    }

    @Override
    public List<String> listCustomers() {
        Set<String> customers = new LinkedHashSet<>();
        shippingNoteTaskMapper.selectList(new LambdaQueryWrapper<ShippingNoteTask>()
                        .select(ShippingNoteTask::getRecipientName)
                        .isNotNull(ShippingNoteTask::getRecipientName))
                .stream()
                .map(ShippingNoteTask::getRecipientName)
                .filter(this::hasText)
                .map(String::trim)
                .forEach(customers::add);
        if (customers.isEmpty()) {
            customers.add("达为鞋业");
        }
        return customers.stream()
                .sorted(Comparator.comparing(value -> value, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<MonthlyReconciliationResponse.Detail> buildDetails(List<ShippingNoteTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, ShippingNoteTask> taskById = tasks.stream()
                .filter(task -> task.getId() != null)
                .collect(Collectors.toMap(
                        ShippingNoteTask::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        if (taskById.isEmpty()) {
            return List.of();
        }

        List<ShippingNoteTaskItem> taskItems = shippingNoteTaskItemMapper.selectList(
                new LambdaQueryWrapper<ShippingNoteTaskItem>()
                        .in(ShippingNoteTaskItem::getTaskId, taskById.keySet())
                        .orderByAsc(ShippingNoteTaskItem::getTaskId)
                        .orderByAsc(ShippingNoteTaskItem::getLineNo)
                        .orderByAsc(ShippingNoteTaskItem::getId)
        );
        List<Long> detailIds = taskItems.stream()
                .map(ShippingNoteTaskItem::getSourceDetailId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (detailIds.isEmpty()) {
            return List.of();
        }

        Map<Long, OrderRecordDetail> detailById = orderRecordDetailMapper.selectBatchIds(detailIds)
                .stream()
                .filter(detail -> detail.getId() != null)
                .collect(Collectors.toMap(
                        OrderRecordDetail::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, OrderRecord> orderById = loadOrders(detailById.values());
        Map<String, BigDecimal> priceByDevelopmentNo = loadShoePrices(detailById.values());

        List<MonthlyReconciliationResponse.Detail> result = new ArrayList<>();
        for (ShippingNoteTaskItem taskItem : taskItems) {
            ShippingNoteTask task = taskById.get(taskItem.getTaskId());
            OrderRecordDetail orderDetail = detailById.get(taskItem.getSourceDetailId());
            if (task == null || orderDetail == null) {
                continue;
            }
            OrderRecord order = orderDetail.getOrderId() == null
                    ? null
                    : orderById.get(orderDetail.getOrderId());
            int orderQuantity = resolveOrderDetailQuantity(orderDetail);
            int shippingQuantity = resolveOrderDetailQuantity(orderDetail);
            BigDecimal unitPrice = priceByDevelopmentNo.get(normalizeDevelopmentNo(orderDetail.getDevelopmentNo()));

            MonthlyReconciliationResponse.Detail detail = new MonthlyReconciliationResponse.Detail();
            detail.setTaskItemId(taskItem.getId());
            detail.setShippingTaskId(task.getId());
            detail.setTaskNo(cleanText(task.getTaskNo()));
            detail.setShippingDate(task.getShippingDate());
            detail.setOrderNo(firstText(
                    order == null ? null : order.getOrderNo(),
                    orderDetail.getCustomerOrderNo()
            ));
            detail.setDevelopmentNo(cleanText(orderDetail.getDevelopmentNo()));
            detail.setColorMaterial(beforeFirstComma(firstText(
                    orderDetail.getUpperMaterial(),
                    joinText(orderDetail.getEnglishColor(), orderDetail.getEnglishMaterial())
            )));
            detail.setOrderQuantity(orderQuantity);
            detail.setShippingQuantity(shippingQuantity);
            detail.setProductType("成品");
            detail.setUnitPrice(unitPrice == null ? null : money(unitPrice));
            detail.setAmount(unitPrice == null
                    ? null
                    : money(unitPrice.multiply(BigDecimal.valueOf(shippingQuantity))));
            detail.setRemark(cleanText(orderDetail.getRemark()));
            detail.setMissingPrice(unitPrice == null);
            result.add(detail);
        }
        result.sort(Comparator
                .comparing(
                        MonthlyReconciliationResponse.Detail::getShippingDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(
                        MonthlyReconciliationResponse.Detail::getOrderNo,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                )
                .thenComparing(
                        MonthlyReconciliationResponse.Detail::getDevelopmentNo,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                )
                .thenComparing(
                        MonthlyReconciliationResponse.Detail::getTaskItemId,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ));
        return result;
    }

    private Map<Long, OrderRecord> loadOrders(Collection<OrderRecordDetail> details) {
        List<Long> orderIds = details.stream()
                .map(OrderRecordDetail::getOrderId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRecordMapper.selectBatchIds(orderIds)
                .stream()
                .filter(order -> order.getId() != null)
                .collect(Collectors.toMap(
                        OrderRecord::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, BigDecimal> loadShoePrices(Collection<OrderRecordDetail> details) {
        List<String> developmentNos = details.stream()
                .map(OrderRecordDetail::getDevelopmentNo)
                .map(DevelopmentNoUtil::normalize)
                .filter(this::hasText)
                .distinct()
                .toList();
        if (developmentNos.isEmpty()) {
            return Collections.emptyMap();
        }
        return shoeStyleConfigMapper.selectList(new LambdaQueryWrapper<ShoeStyleConfig>()
                        .select(ShoeStyleConfig::getDevelopmentNo, ShoeStyleConfig::getShoePrice)
                        .in(ShoeStyleConfig::getDevelopmentNo, developmentNos))
                .stream()
                .filter(config -> hasText(config.getDevelopmentNo()))
                .filter(config -> config.getShoePrice() != null)
                .collect(Collectors.toMap(
                        config -> normalizeDevelopmentNo(config.getDevelopmentNo()),
                        ShoeStyleConfig::getShoePrice,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(requireText(month, "月份不能为空"));
        } catch (DateTimeParseException ex) {
            throw new BusinessException("月份格式必须为 yyyy-MM");
        }
    }

    private int resolveOrderDetailQuantity(OrderRecordDetail detail) {
        if (detail.getQuantity() != null && detail.getQuantity() > 0) {
            return detail.getQuantity();
        }
        int perCartonPairs = parseSizeQuantities(detail.getSizeQuantitiesJson()).values()
                .stream()
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .sum();
        if (perCartonPairs > 0 && detail.getCartonCount() != null && detail.getCartonCount() > 0) {
            return perCartonPairs * detail.getCartonCount();
        }
        return perCartonPairs;
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

    private String normalizeDevelopmentNo(String value) {
        String normalized = DevelopmentNoUtil.normalize(value);
        return hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : "";
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String beforeFirstComma(String value) {
        if (!hasText(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        int chineseComma = value.indexOf('，');
        int index;
        if (comma < 0) {
            index = chineseComma;
        } else if (chineseComma < 0) {
            index = comma;
        } else {
            index = Math.min(comma, chineseComma);
        }
        return index < 0 ? value.trim() : value.substring(0, index).trim();
    }

    private String joinText(String... values) {
        return java.util.Arrays.stream(values)
                .filter(this::hasText)
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    private String firstText(String... values) {
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

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
