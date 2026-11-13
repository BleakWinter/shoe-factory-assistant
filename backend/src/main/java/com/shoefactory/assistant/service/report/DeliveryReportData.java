package com.shoefactory.assistant.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;
import com.shoefactory.assistant.enums.OrderSourceType;
import com.shoefactory.assistant.mapper.OrderPackingDetailMapper;
import com.shoefactory.assistant.service.CustomerPriceService;
import com.shoefactory.assistant.service.LedgerDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.delivery-report", name = "enabled", havingValue = "true")
class DeliveryReportData {
    private final LedgerDataService ledger;
    private final OrderPackingDetailMapper packing;

    DeliveryReportData(LedgerDataService ledger, OrderPackingDetailMapper packing) {
        this.ledger = ledger;
        this.packing = packing;
    }

    @Transactional(readOnly = true)
    public Snapshot load(LocalDate date) {
        LedgerDataService.Data data = ledger.load(null);
        Map<Long, OrderRecord> orders = data.ordersById();
        // An existing shipping-note item covers the entire source detail in the current ledger.
        Set<Long> shipped = data.items().stream().map(ShippingNoteTaskItem::getSourceDetailId).collect(Collectors.toSet());
        Map<GroupKey, OrderGroupData> groups = new LinkedHashMap<>();
        Set<Long> excelOrderIds = new HashSet<>();
        for (var detail : data.details()) {
            LocalDate due = detail.getDeliveryDate();
            if (shipped.contains(detail.getId()) || (due != null && due.isAfter(date.plusDays(7)))) {
                continue;
            }
            int quantity = LedgerDataService.quantity(detail);
            OrderRecord order = orders.get(detail.getOrderId());
            if (quantity <= 0 || order == null) {
                continue;
            }
            String number = valueOrDefault(order.getOrderNo(), "订单 #" + order.getId());
            var key = new GroupKey(order.getCustomerId(), number.toUpperCase(Locale.ROOT), due);
            var group = groups.computeIfAbsent(key, ignored -> new OrderGroupData(number));
            String style = CustomerPriceService.normalize(detail.getDevelopmentNo());
            group.styles.merge(valueOrDefault(style, "款号待确认"), (long) quantity, Long::sum);
            if (Objects.equals(order.getSourceType(), OrderSourceType.EXCEL.getCode())) {
                group.excelOrderIds.add(order.getId());
                excelOrderIds.add(order.getId());
            }
        }

        Set<Long> packed = excelOrderIds.isEmpty() ? Set.of() : packing.selectList(
                new LambdaQueryWrapper<OrderPackingDetail>()
                        .select(OrderPackingDetail::getOrderId)
                        .in(OrderPackingDetail::getOrderId, excelOrderIds)
        ).stream().map(OrderPackingDetail::getOrderId).collect(Collectors.toSet());

        List<AlertGroup> alerts = new ArrayList<>();
        groups.forEach((key, group) -> {
            LocalDate due = key.due();
            long overdue = due == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(due, date));
            List<DeliveryReport.Style> styles = group.styles.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new DeliveryReport.Style(entry.getKey(), entry.getValue()))
                    .toList();
            alerts.add(new AlertGroup(new DeliveryReport.OrderGroup(group.number, due, overdue, styles),
                    group.excelOrderIds.stream().anyMatch(id -> !packed.contains(id))));
        });
        alerts.sort(Comparator.comparing((AlertGroup alert) -> alert.order().deliveryDate(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(alert -> alert.order().orderNo(), String.CASE_INSENSITIVE_ORDER));
        return new Snapshot(date, List.copyOf(alerts));
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    record Snapshot(LocalDate date, List<AlertGroup> groups) {
        DeliveryReport forSharing() {
            return new DeliveryReport(date, groups.stream().map(AlertGroup::order)
                    .filter(group -> group.deliveryDate() != null && !group.deliveryDate().isAfter(date.plusDays(3)))
                    .toList());
        }
    }

    record AlertGroup(DeliveryReport.OrderGroup order, boolean packingMissing) {}

    private record GroupKey(Long customerId, String orderNo, LocalDate due) {}

    private static final class OrderGroupData {
        private final String number;
        private final Map<String, Long> styles = new LinkedHashMap<>();
        private final Set<Long> excelOrderIds = new HashSet<>();

        private OrderGroupData(String number) {
            this.number = number;
        }
    }
}
