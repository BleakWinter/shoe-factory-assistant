package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.MonthlySettlementData.*;
import com.shoefactory.assistant.entity.CustomerConfig;
import com.shoefactory.assistant.entity.MonthlySettlement;
import com.shoefactory.assistant.mapper.CustomerConfigMapper;
import com.shoefactory.assistant.mapper.MonthlySettlementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MonthlySettlementService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final MonthlySettlementMapper bills;
    private final CustomerConfigMapper customerLocks;
    private final CustomerConfigService customers;
    private final MonthlyReconciliationService reconciliation;
    private final CustomerPriceService prices;
    private final UnpricedShipmentService unpriced;
    private final ObjectMapper json;

    public MonthlySettlementService(MonthlySettlementMapper bills, CustomerConfigMapper customerLocks,
            CustomerConfigService customers, MonthlyReconciliationService reconciliation, CustomerPriceService prices,
            UnpricedShipmentService unpriced, ObjectMapper json) {
        this.bills = bills;
        this.customerLocks = customerLocks;
        this.customers = customers;
        this.reconciliation = reconciliation;
        this.prices = prices;
        this.unpriced = unpriced;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public View get(String month, Long customerId) {
        YearMonth period = period(month);
        CustomerConfig customer = customers.require(customerId);
        Context context = context(customerId, month);
        if (confirmed(context.bill())) return lockedView(context.bill());
        Draft draft = context.bill() == null ? defaultDraft(customer) : draft(context.bill());
        return preview(month, revision(context.bill()), draft, calculate(customerId, period, draft, context.history(), false));
    }

    @Transactional
    public View save(String month, Long customerId, SaveRequest request) {
        YearMonth period = period(month);
        lockCustomer(customerId);
        Context context = context(customerId, month);
        MonthlySettlement bill = context.bill();
        if (confirmed(bill)) throw new BusinessException("账单已确认，请刷新查看");
        checkRevision(bill, request.revision());
        Draft draft = request.draft();
        Snapshot snapshot = calculate(customerId, period, draft, context.history(), true);
        boolean isNew = bill == null;
        if (isNew) {
            bill = new MonthlySettlement();
            bill.setCustomerId(customerId);
            bill.setBillMonth(month);
        }
        bill.setRevision(bill.getRevision() + 1);
        bill.setDraftJson(write(draft));
        if (isNew) bills.insert(bill);
        else bills.updateById(bill);
        return preview(month, bill.getRevision(), draft, snapshot);
    }

    @Transactional
    public View confirm(String month, Long customerId, ConfirmRequest request) {
        YearMonth period = period(month);
        lockCustomer(customerId);
        Context context = context(customerId, month);
        MonthlySettlement bill = context.bill();
        if (confirmed(bill)) return lockedView(bill);
        if (bill == null) throw new BusinessException("请先保存结算草稿");
        checkRevision(bill, request.revision());
        Draft draft = draft(bill);
        Snapshot latest = calculate(customerId, period, draft, context.history(), true);
        if (!token(latest).equals(request.previewToken())) {
            throw new BusinessException("出货、价格或返还余额已更新，请刷新核对后再确认");
        }
        Set<String> carryKeys = Set.copyOf(draft.carryKeys());
        Snapshot locked = new Snapshot(latest.current(), latest.pending(),
                latest.carryovers().stream().filter(row -> carryKeys.contains(row.key())).toList(),
                latest.refunds().stream().filter(row -> row.amount().signum() > 0).toList(), latest.totals());
        bill.setSnapshotJson(write(locked));
        bill.setConfirmedAt(LocalDateTime.now().withNano(0));
        bill.setRevision(bill.getRevision() + 1);
        bills.updateById(bill);
        return lockedView(bill);
    }

    @Transactional
    public View recordReceipt(String month, Long customerId, ReceiptRequest request) {
        period(month);
        lockCustomer(customerId);
        MonthlySettlement bill = context(customerId, month).bill();
        if (!confirmed(bill)) throw new BusinessException("请先确认月结");
        checkRevision(bill, request.revision());
        bill.setReceivedOn(request.receivedOn());
        bill.setRevision(bill.getRevision() + 1);
        bills.update(null, new LambdaUpdateWrapper<MonthlySettlement>().eq(MonthlySettlement::getId, bill.getId())
                .set(MonthlySettlement::getReceivedOn, bill.getReceivedOn())
                .set(MonthlySettlement::getRevision, bill.getRevision()));
        return lockedView(bill);
    }

    private Snapshot calculate(Long customerId, YearMonth period, Draft draft, List<ClosedBill> history, boolean validate) {
        Set<String> billed = new HashSet<>();
        Map<String, Shipment> deferred = new LinkedHashMap<>();
        for (ClosedBill previous : history) {
            previous.snapshot().current().forEach(row -> billed.add(row.key()));
            previous.snapshot().carryovers().forEach(row -> billed.add(row.key()));
            previous.snapshot().pending().forEach(row -> deferred.putIfAbsent(row.key(), row));
        }

        var live = reconciliation.getMonthlyReconciliation(period.toString(), customerId);
        Map<String, Shipment> fresh = new LinkedHashMap<>();
        Set<String> shipmentKeys = new HashSet<>();
        int shippedPairs = 0;
        for (var row : live.getDetails()) {
            String key = row.getShippingTaskId() + ":" + row.getSourceDetailId();
            if (row.getShippingQuantity() <= 0 || !shipmentKeys.add(key)) continue;
            shippedPairs += row.getShippingQuantity();
            if (billed.contains(key) || deferred.containsKey(key)) continue;
            fresh.put(key, new Shipment(key, row.getTaskNo(), row.getShippingDate(), row.getOrderNo(),
                    row.getDevelopmentNo(), row.getColorMaterial(), row.getShippingQuantity(), row.getUnitPrice(),
                    row.getAmount(), null, null, null));
        }
        Set<String> postpone = Set.copyOf(draft.deferredKeys());
        Set<String> selected = Set.copyOf(draft.carryKeys());
        if (validate && !fresh.keySet().containsAll(postpone)) {
            throw new BusinessException("部分暂缓明细已不在本期出货，请刷新后重新选择");
        }

        List<Shipment> prior = deferred.values().stream().filter(row -> !billed.contains(row.key())
                && YearMonth.from(row.shippingDate()).isBefore(period)).toList();
        Map<String, BigDecimal> currentPrices = prior.isEmpty() ? Map.of() : prices.prices();
        List<Shipment> carried = prior.stream().map(row -> {
            BigDecimal price = currentPrices.get(CustomerPriceService.key(customerId, row.developmentNo()));
            return withPrice(row, price, null);
        }).toList();
        var missingStyles = new LinkedHashSet<String>();
        fresh.values().stream().filter(row -> row.unitPrice() == null).forEach(row -> missingStyles.add(row.developmentNo()));
        carried.stream().filter(row -> row.unitPrice() == null).forEach(row -> missingStyles.add(row.developmentNo()));
        var quotes = missingStyles.isEmpty() ? Map.<String, UnpricedShipmentService.QuotationHistory>of()
                : unpriced.priceProgress(customerId, missingStyles);

        List<Shipment> current = new ArrayList<>();
        List<Shipment> pending = new ArrayList<>();
        List<Shipment> carryovers = new ArrayList<>();
        for (Shipment row : fresh.values()) {
            Shipment priced = withPrice(row, row.unitPrice(), quotes.get(CustomerPriceService.normalize(row.developmentNo())));
            (priced.unitPrice() == null || postpone.contains(row.key()) ? pending : current).add(priced);
        }
        for (Shipment row : carried) {
            Shipment priced = withPrice(row, row.unitPrice(), quotes.get(CustomerPriceService.normalize(row.developmentNo())));
            (priced.unitPrice() == null ? pending : carryovers).add(priced);
        }
        if (validate && !carryovers.stream().map(Shipment::key).collect(Collectors.toSet()).containsAll(selected)) {
            throw new BusinessException("部分补结明细已入账或单价已变更，请刷新后重新选择");
        }
        List<Refund> refunds = refunds(period, draft.refunds(), history, validate);
        Totals totals = totals(shippedPairs, current, carryovers, refunds, draft, selected);
        return new Snapshot(current, pending, carryovers, refunds, totals);
    }

    private List<Refund> refunds(YearMonth period, List<RefundInput> inputs, List<ClosedBill> history, boolean validate) {
        Map<Long, BigDecimal> requested = new HashMap<>();
        for (RefundInput input : inputs) {
            if (requested.put(input.originId(), input.amount()) != null) throw new BusinessException("同一笔质保金只能录入一次返还");
        }
        Map<Long, BigDecimal> paid = new HashMap<>();
        for (ClosedBill previous : history) {
            previous.snapshot().refunds().forEach(row -> paid.merge(row.originId(), row.amount(), BigDecimal::add));
        }
        List<Refund> available = new ArrayList<>();
        for (ClosedBill previous : history) {
            YearMonth due = YearMonth.parse(previous.month()).plusMonths(2);
            BigDecimal remaining = previous.snapshot().totals().hold().subtract(paid.getOrDefault(previous.id(), ZERO));
            if (due.isAfter(period) || remaining.signum() <= 0) continue;
            BigDecimal amount = requested.remove(previous.id());
            if (validate && amount != null && amount.compareTo(remaining) > 0) {
                throw new BusinessException(previous.month() + " 的质保金返还不能超过剩余金额");
            }
            available.add(new Refund(previous.id(), previous.month(), due.toString(), remaining,
                    amount == null || amount.compareTo(remaining) > 0 ? ZERO : money(amount)));
        }
        if (validate && !requested.isEmpty()) throw new BusinessException("部分质保金已返还或尚未到期，请刷新后重新选择");
        return available;
    }

    private Totals totals(int shippedPairs, List<Shipment> current, List<Shipment> carryovers, List<Refund> refunds,
            Draft draft, Set<String> selected) {
        BigDecimal goods = sum(current, Shipment::amount);
        BigDecimal carry = sum(carryovers.stream().filter(row -> selected.contains(row.key())).toList(), Shipment::amount);
        BigDecimal base = draft.holdBase() == null ? goods : money(draft.holdBase());
        BigDecimal hold = money(base.multiply(draft.holdRate()).movePointLeft(2));
        BigDecimal refund = sum(refunds, Refund::amount);
        BigDecimal materials = sum(draft.materials(), item -> money(item.quantity().multiply(item.unitPrice())));
        BigDecimal credits = sum(draft.adjustments().stream().filter(item -> item.type() == AdjustmentType.CREDIT).toList(), Adjustment::amount);
        BigDecimal debits = sum(draft.adjustments().stream().filter(item -> item.type() == AdjustmentType.DEBIT).toList(), Adjustment::amount);
        BigDecimal receivable = goods.add(carry).add(refund).add(credits).subtract(hold).subtract(materials).subtract(debits);
        return new Totals(shippedPairs, goods, carry, base, hold, refund, materials, credits, debits, receivable);
    }

    private Shipment withPrice(Shipment row, BigDecimal price, UnpricedShipmentService.QuotationHistory history) {
        var quote = history == null ? null : history.latestPriced() == null ? history.latest() : history.latestPriced();
        String status = price != null ? "CONFIRMED" : history == null ? "NOT_QUOTED" : history.status().name();
        return new Shipment(row.key(), row.taskNo(), row.shippingDate(), row.orderNo(), row.developmentNo(), row.colorMaterial(),
                row.quantity(), price == null ? null : money(price), price == null ? null : money(price.multiply(BigDecimal.valueOf(row.quantity()))),
                status, quote == null ? null : quote.id(), quote == null ? null : quote.finalPrice());
    }

    private record ClosedBill(long id, String month, Snapshot snapshot) {}
    private record Context(MonthlySettlement bill, List<ClosedBill> history) {}

    private Context context(Long customerId, String month) {
        var rows = bills.selectList(new LambdaQueryWrapper<MonthlySettlement>().eq(MonthlySettlement::getCustomerId, customerId)
                .and(query -> query.eq(MonthlySettlement::getBillMonth, month).or().isNotNull(MonthlySettlement::getConfirmedAt))
                .orderByAsc(MonthlySettlement::getBillMonth));
        MonthlySettlement bill = rows.stream().filter(row -> month.equals(row.getBillMonth())).findFirst().orElse(null);
        if (confirmed(bill)) return new Context(bill, List.of());
        return new Context(bill, rows.stream().filter(MonthlySettlementService::confirmed)
                .map(row -> new ClosedBill(row.getId(), row.getBillMonth(), read(row.getSnapshotJson(), Snapshot.class))).toList());
    }

    private void lockCustomer(Long id) {
        // 同客户不同月份共享待请款和质保金余额，月结写入按客户串行。
        if (customerLocks.selectForUpdate(id) == null) throw new BusinessException("客户不存在");
    }

    private Draft defaultDraft(CustomerConfig customer) {
        return new Draft(LocalDate.now(), "DAWEI".equals(customer.getCode()) ? BigDecimal.TEN : ZERO,
                null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private View lockedView(MonthlySettlement bill) {
        return new View(bill.getBillMonth(), bill.getRevision(), bill.getConfirmedAt(), bill.getReceivedOn(), draft(bill),
                read(bill.getSnapshotJson(), Snapshot.class), null);
    }

    private View preview(String month, int revision, Draft draft, Snapshot snapshot) {
        return new View(month, revision, null, null, draft, snapshot, token(snapshot));
    }

    private Draft draft(MonthlySettlement bill) { return read(bill.getDraftJson(), Draft.class); }
    private static boolean confirmed(MonthlySettlement bill) { return bill != null && bill.getConfirmedAt() != null; }
    private static int revision(MonthlySettlement bill) { return bill == null ? 0 : bill.getRevision(); }

    private static void checkRevision(MonthlySettlement bill, int expected) {
        if (expected != revision(bill)) throw new BusinessException("账单已在其他页面更新，请刷新后重试");
    }

    private static YearMonth period(String month) {
        if (month == null || !month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")) {
            throw new BusinessException("月份格式必须为 yyyy-MM");
        }
        return YearMonth.parse(month);
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> amount) {
        return rows.stream().map(amount).reduce(ZERO, BigDecimal::add);
    }

    private String token(Snapshot snapshot) {
        return DigestUtils.md5DigestAsHex(write(snapshot).getBytes(StandardCharsets.UTF_8));
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new BusinessException("无法保存结算单", error); }
    }

    private <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException error) { throw new BusinessException("无法读取结算单", error); }
    }
}
