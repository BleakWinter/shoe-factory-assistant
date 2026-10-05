package com.shoefactory.assistant.service.impl;

import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.MonthlyReconciliationResponse;
import com.shoefactory.assistant.entity.*;
import com.shoefactory.assistant.service.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class MonthlyReconciliationServiceImpl implements MonthlyReconciliationService {
    private final LedgerDataService ledger;
    private final CustomerConfigService customers;
    public MonthlyReconciliationServiceImpl(LedgerDataService ledger, CustomerConfigService customers) {
        this.ledger=ledger; this.customers=customers;
    }
    @Override
    public MonthlyReconciliationResponse getMonthlyReconciliation(String month, String customerName) {
        CustomerConfig customer=customers.list().stream()
                .filter(c->Objects.equals(c.getRecipientName(),customerName) || Objects.equals(c.getName(),customerName))
                .findFirst().orElseThrow(()->new BusinessException("客户不存在，请从客户列表选择"));
        return getMonthlyReconciliation(month,customer.getId());
    }
    @Override
    public MonthlyReconciliationResponse getMonthlyReconciliation(String month, Long customerId) {
        YearMonth period;
        try { period=YearMonth.parse(month); }
        catch (Exception ex) { throw new BusinessException("月份格式必须为 yyyy-MM"); }
        CustomerConfig customer=customers.require(customerId);
        LedgerDataService.Data data=ledger.load(customer.getId());
        Map<Long,OrderRecordDetail> details=data.detailsById();
        Map<Long,ShippingNoteTask> tasks=data.tasksById();
        Map<Long,OrderRecord> orders=data.ordersById();
        List<MonthlyReconciliationResponse.Detail> rows=new ArrayList<>();
        Set<Long> countedDetails=new HashSet<>();
        int totalOrderQuantity=0;
        for (ShippingNoteTaskItem item:data.items()) {
            ShippingNoteTask task=tasks.get(item.getTaskId());
            OrderRecordDetail source=details.get(item.getSourceDetailId());
            LocalDate date=task==null?null:LedgerDataService.shippingDate(task);
            if (date==null || source==null || !YearMonth.from(date).equals(period)) continue;
            OrderRecord order=orders.get(source.getOrderId());
            int quantity=LedgerDataService.quantity(source);
            BigDecimal price=data.pricesByDetailId().get(source.getId());
            MonthlyReconciliationResponse.Detail row=new MonthlyReconciliationResponse.Detail();
            row.setTaskItemId(item.getId()); row.setShippingTaskId(task.getId()); row.setTaskNo(task.getTaskNo());
            row.setShippingDate(date); row.setOrderNo(order==null?source.getCustomerOrderNo():order.getOrderNo());
            row.setDevelopmentNo(source.getDevelopmentNo()); row.setColorMaterial(source.getUpperMaterial());
            row.setOrderQuantity(LedgerDataService.quantity(source)); row.setShippingQuantity(quantity);
            row.setProductType("成品"); row.setUnitPrice(price==null?null:money(price));
            row.setAmount(price==null?null:money(price.multiply(BigDecimal.valueOf(quantity))));
            row.setRemark(source.getRemark()); row.setMissingPrice(price==null); rows.add(row);
            // 保留旧出货记录兼容：同一订单明细的关联订单双数只汇总一次。
            if (countedDetails.add(source.getId())) totalOrderQuantity+=LedgerDataService.quantity(source);
        }
        rows.sort(Comparator.comparing(MonthlyReconciliationResponse.Detail::getShippingDate)
                .thenComparing(MonthlyReconciliationResponse.Detail::getTaskItemId,Comparator.nullsLast(Comparator.naturalOrder())));
        MonthlyReconciliationResponse response=new MonthlyReconciliationResponse();
        response.setMonth(period.toString()); response.setCustomerName(customer.getRecipientName()); response.setDetails(rows);
        response.setTotalOrderQuantity(totalOrderQuantity);
        response.setTotalShippingQuantity(rows.stream().mapToInt(MonthlyReconciliationResponse.Detail::getShippingQuantity).sum());
        response.setGoodsAmount(money(rows.stream().map(MonthlyReconciliationResponse.Detail::getAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add)));
        response.setMissingPriceCount((int)rows.stream().filter(r->Boolean.TRUE.equals(r.getMissingPrice())).count());
        return response;
    }
    @Override
    public List<String> listCustomers() { return customers.list().stream().map(CustomerConfig::getRecipientName).toList(); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2,RoundingMode.HALF_UP); }
}
