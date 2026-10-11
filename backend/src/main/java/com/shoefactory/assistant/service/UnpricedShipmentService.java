package com.shoefactory.assistant.service;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.UnpricedShipmentResponse.*;
import com.shoefactory.assistant.entity.CustomerConfig;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** 已出货但未配置客户单价的记录，数量和日期口径与月度对账一致。 */
@Service
public class UnpricedShipmentService {
    private final LedgerDataService ledger;
    private final CustomerConfigService customers;

    public UnpricedShipmentService(LedgerDataService ledger, CustomerConfigService customers) {
        this.ledger = ledger;
        this.customers = customers;
    }

    public List<MonthSummary> listMonths(Long customerId) {
        return loadDetails(loadData(customerId), null).stream()
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.shippingDate()).toString(),
                        () -> new TreeMap<String, List<Detail>>(Comparator.reverseOrder()), Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new MonthSummary(entry.getKey(), countStyles(entry.getValue()),
                        (int) entry.getValue().stream().map(row -> mainStyle(row.developmentNo())).distinct().count(),
                        entry.getValue().stream().mapToInt(Detail::shippingQuantity).sum()))
                .toList();
    }

    public MonthDetails getMonth(String month, Long customerId) {
        YearMonth period;
        try { period = YearMonth.parse(month); }
        catch (Exception ex) { throw new BusinessException("月份格式必须为 yyyy-MM"); }
        LedgerDataService.Data data = loadData(customerId);
        List<Detail> details = loadDetails(data, period);
        Map<StyleKey, String> images = styleImages(data);
        List<StyleGroup> groups = details.stream()
                .collect(Collectors.groupingBy(row -> mainStyle(row.developmentNo())))
                .entrySet().stream()
                .map(entry -> new StyleGroup(entry.getKey(), countStyles(entry.getValue()),
                        entry.getValue().stream().mapToInt(Detail::shippingQuantity).sum(), summarizeStyles(entry.getValue(), images)))
                .sorted(Comparator.comparingInt(StyleGroup::missingPriceCount).reversed()
                        .thenComparing(StyleGroup::mainStyle))
                .toList();
        return new MonthDetails(period.toString(), countStyles(details),
                details.stream().mapToInt(Detail::shippingQuantity).sum(), groups);
    }

    private record StyleKey(Long customerId, String developmentNo) {}

    private StyleKey styleKey(Detail detail) {
        return new StyleKey(detail.customerId(), detail.developmentNo());
    }

    private int countStyles(List<Detail> details) {
        // 同月、同客户的同一完整款号只计一次；不同客户需要分别维护单价。
        return (int) details.stream().map(this::styleKey).distinct().count();
    }

    private List<StyleSummary> summarizeStyles(List<Detail> details, Map<StyleKey, String> images) {
        return details.stream()
                .collect(Collectors.groupingBy(this::styleKey))
                .entrySet().stream()
                .map(entry -> new StyleSummary(entry.getKey().customerId(), entry.getValue().get(0).customerName(),
                        entry.getKey().developmentNo(), images.get(entry.getKey()),
                        entry.getValue().stream().mapToInt(Detail::shippingQuantity).sum(),
                        entry.getValue()))
                .sorted(Comparator.comparing(StyleSummary::developmentNo)
                        .thenComparing(StyleSummary::customerId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private LedgerDataService.Data loadData(Long customerId) {
        if (customerId != null) customers.require(customerId);
        return ledger.load(customerId);
    }

    private Map<StyleKey, String> styleImages(LedgerDataService.Data data) {
        var orders = data.ordersById();
        Map<StyleKey, String> images = new HashMap<>();
        // 同客户、同完整款号优先使用最新有图的订单，避免重复出货中的无图记录遮住已有图片。
        data.details().stream()
                .filter(row -> row.getStyleImagePath() != null && !row.getStyleImagePath().isBlank())
                .sorted(Comparator.comparing(OrderRecordDetail::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(OrderRecordDetail::getId, Comparator.reverseOrder()))
                .forEach(row -> {
                    var order = orders.get(row.getOrderId());
                    String style = CustomerPriceService.normalize(row.getDevelopmentNo());
                    if (order != null && order.getCustomerId() != null && !style.isBlank()) {
                        images.putIfAbsent(new StyleKey(order.getCustomerId(), style),
                                "/api/orders/details/" + row.getId() + "/image");
                    }
                });
        return images;
    }

    private List<Detail> loadDetails(LedgerDataService.Data data, YearMonth period) {
        var details = data.detailsById();
        var tasks = data.tasksById();
        var orders = data.ordersById();
        Map<Long, String> customerNames = customers.list().stream()
                .collect(Collectors.toMap(CustomerConfig::getId, CustomerConfig::getName));
        List<Detail> rows = new ArrayList<>();
        for (var item : data.items()) {
            var task = tasks.get(item.getTaskId());
            var source = details.get(item.getSourceDetailId());
            if (task == null || source == null || data.pricesByDetailId().get(source.getId()) != null) continue;
            var date = LedgerDataService.shippingDate(task);
            int quantity = LedgerDataService.quantity(source);
            if (date == null || quantity <= 0 || (period != null && !YearMonth.from(date).equals(period))) continue;
            var order = orders.get(source.getOrderId());
            Long ownerId = order == null ? task.getCustomerId() : order.getCustomerId();
            rows.add(new Detail(item.getId(), task.getId(), task.getTaskNo(), date,
                    ownerId, customerNames.getOrDefault(ownerId, "未知客户"), source.getOrderId(),
                    order == null ? source.getCustomerOrderNo() : order.getOrderNo(),
                    CustomerPriceService.normalize(source.getDevelopmentNo()), source.getUpperMaterial(),
                    quantity, source.getRemark()));
        }
        rows.sort(Comparator.comparing(Detail::shippingDate).reversed()
                .thenComparing(Detail::developmentNo)
                .thenComparing(Detail::taskItemId, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    private String mainStyle(String developmentNo) {
        if (developmentNo.isBlank()) return "未填款号";
        List<String> parts = Arrays.stream(developmentNo.split("-"))
                .map(String::trim).filter(part -> !part.isBlank()).toList();
        if (parts.isEmpty()) return "未填款号";
        // 与现有款号层级一致：取最后三段的第一段，JCD-162-15-03 → 162。
        if (parts.size() >= 3) return parts.get(parts.size() - 3);
        if (parts.size() == 2 && parts.get(0).matches("[A-Z]+")) return parts.get(1);
        return parts.get(0);
    }
}
