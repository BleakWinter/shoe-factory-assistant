package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.UnpricedShipmentResponse.*;
import com.shoefactory.assistant.entity.CustomerConfig;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.ShoeQuotation;
import com.shoefactory.assistant.mapper.ShoeQuotationMapper;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** 已出货但未配置客户单价的记录，数量和日期口径与月度对账一致。 */
@Service
public class UnpricedShipmentService {
    private final LedgerDataService ledger;
    private final CustomerConfigService customers;
    private final ShoeQuotationMapper quotations;

    public UnpricedShipmentService(LedgerDataService ledger, CustomerConfigService customers, ShoeQuotationMapper quotations) {
        this.ledger = ledger;
        this.customers = customers;
        this.quotations = quotations;
    }

    public List<MonthSummary> listMonths(Long customerId) {
        List<Detail> details = loadDetails(loadData(customerId), null);
        var histories = quotationHistories(details);
        return details.stream()
                .collect(Collectors.groupingBy(row -> YearMonth.from(row.shippingDate()).toString(),
                        () -> new TreeMap<String, List<Detail>>(Comparator.reverseOrder()), Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new MonthSummary(entry.getKey(), countStyles(entry.getValue()),
                        (int) entry.getValue().stream().map(row -> mainStyle(row.developmentNo())).distinct().count(),
                        entry.getValue().stream().mapToInt(Detail::shippingQuantity).sum(),
                        quotationCounts(entry.getValue(), histories)))
                .toList();
    }

    public MonthDetails getMonth(String month, Long customerId) {
        YearMonth period;
        try { period = YearMonth.parse(month); }
        catch (Exception ex) { throw new BusinessException("月份格式必须为 yyyy-MM"); }
        LedgerDataService.Data data = loadData(customerId);
        List<Detail> details = loadDetails(data, period);
        Map<StyleKey, String> images = styleImages(data);
        var histories = quotationHistories(details);
        List<StyleGroup> groups = details.stream()
                .collect(Collectors.groupingBy(row -> mainStyle(row.developmentNo())))
                .entrySet().stream()
                .map(entry -> new StyleGroup(entry.getKey(), countStyles(entry.getValue()),
                        entry.getValue().stream().mapToInt(Detail::shippingQuantity).sum(),
                        summarizeStyles(entry.getValue(), images, histories), quotationCounts(entry.getValue(), histories)))
                .sorted(Comparator.comparingInt(StyleGroup::missingPriceCount).reversed()
                        .thenComparing(StyleGroup::mainStyle))
                .toList();
        return new MonthDetails(period.toString(), countStyles(details),
                details.stream().mapToInt(Detail::shippingQuantity).sum(), groups, quotationCounts(details, histories));
    }

    private record StyleKey(Long customerId, String developmentNo) {}

    private record QuotationHistory(QuotationSummary latest, QuotationSummary latestPriced) {
        QuotationStatus status() { return latestPriced == null ? QuotationStatus.DRAFT : QuotationStatus.QUOTED; }
    }

    private Map<StyleKey, QuotationHistory> quotationHistories(List<Detail> details) {
        var keys = details.stream().map(this::styleKey)
                .filter(key -> key.customerId() != null && !key.developmentNo().isBlank())
                .collect(Collectors.toSet());
        if (keys.isEmpty()) return Map.of();
        // 一次查询全部关联版本，不限制报价日期；已报价格不因较新的未完成版本而消失。
        var quotes = quotations.selectList(new LambdaQueryWrapper<ShoeQuotation>()
                .in(ShoeQuotation::getCustomerId, keys.stream().map(StyleKey::customerId).distinct().toList())
                .in(ShoeQuotation::getDevelopmentNo, keys.stream().map(StyleKey::developmentNo).distinct().toList()));
        Map<StyleKey, QuotationHistory> histories = new HashMap<>();
        quotes.stream().sorted(Comparator.comparing(ShoeQuotation::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ShoeQuotation::getId, Comparator.reverseOrder()))
                .forEach(quote -> {
                    var key = new StyleKey(quote.getCustomerId(), CustomerPriceService.normalize(quote.getDevelopmentNo()));
                    if (!keys.contains(key)) return;
                    var summary = new QuotationSummary(quote.getId(), quote.getVersionNo(), quote.getFinalPrice(),
                            quote.getQuotationDate(), quote.getCreatedAt(), quote.getAdoptedAt());
                    var history = histories.get(key);
                    if (history == null) {
                        histories.put(key, new QuotationHistory(summary, quote.getFinalPrice() == null ? null : summary));
                    } else if (history.latestPriced() == null && quote.getFinalPrice() != null) {
                        histories.put(key, new QuotationHistory(history.latest(), summary));
                    }
                });
        return histories;
    }

    private QuotationCounts quotationCounts(List<Detail> details, Map<StyleKey, QuotationHistory> histories) {
        int unquoted = 0, drafts = 0, quoted = 0;
        for (var key : details.stream().map(this::styleKey).collect(Collectors.toSet())) {
            var history = histories.get(key);
            if (history == null) unquoted++;
            else if (history.status() == QuotationStatus.DRAFT) drafts++;
            else quoted++;
        }
        return new QuotationCounts(unquoted, drafts, quoted);
    }

    private StyleKey styleKey(Detail detail) {
        return new StyleKey(detail.customerId(), detail.developmentNo());
    }

    private int countStyles(List<Detail> details) {
        // 同月、同客户的同一完整款号只计一次；不同客户需要分别维护单价。
        return (int) details.stream().map(this::styleKey).distinct().count();
    }

    private List<StyleSummary> summarizeStyles(List<Detail> details, Map<StyleKey, String> images,
                                             Map<StyleKey, QuotationHistory> histories) {
        return details.stream()
                .collect(Collectors.groupingBy(this::styleKey))
                .entrySet().stream()
                .map(entry -> {
                    var history = histories.get(entry.getKey());
                    return new StyleSummary(entry.getKey().customerId(), entry.getValue().get(0).customerName(),
                        entry.getKey().developmentNo(), images.get(entry.getKey()),
                        entry.getValue().stream().mapToInt(Detail::shippingQuantity).sum(),
                        entry.getValue(), history == null ? QuotationStatus.NOT_QUOTED : history.status(),
                        history == null ? null : history.latest(), history == null ? null : history.latestPriced());
                })
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
