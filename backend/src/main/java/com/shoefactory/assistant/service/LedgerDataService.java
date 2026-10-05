package com.shoefactory.assistant.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.entity.*;
import com.shoefactory.assistant.mapper.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** 统计、出货和对账共同使用的数量与价格口径。 */
@Service
public class LedgerDataService {
    private final OrderRecordMapper orders;
    private final OrderRecordDetailMapper details;
    private final ShippingNoteTaskMapper tasks;
    private final ShippingNoteTaskItemMapper items;
    private final CustomerPriceService prices;
    public LedgerDataService(OrderRecordMapper orders, OrderRecordDetailMapper details,
                             ShippingNoteTaskMapper tasks, ShippingNoteTaskItemMapper items, CustomerPriceService prices) {
        this.orders=orders; this.details=details; this.tasks=tasks; this.items=items; this.prices=prices;
    }
    public record Data(List<OrderRecord> orders, List<OrderRecordDetail> details,
                       List<ShippingNoteTask> tasks, List<ShippingNoteTaskItem> items,
                       Map<Long,BigDecimal> pricesByDetailId) {
        public Map<Long,OrderRecord> ordersById() { return orders.stream().collect(Collectors.toMap(OrderRecord::getId,o->o)); }
        public Map<Long,OrderRecordDetail> detailsById() { return details.stream().collect(Collectors.toMap(OrderRecordDetail::getId,d->d)); }
        public Map<Long,ShippingNoteTask> tasksById() { return tasks.stream().collect(Collectors.toMap(ShippingNoteTask::getId,t->t)); }
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Data load(Long customerId) {
        List<OrderRecord> selectedOrders = orders.selectList(new LambdaQueryWrapper<OrderRecord>()
                .eq(customerId!=null, OrderRecord::getCustomerId, customerId));
        List<OrderRecordDetail> selectedDetails = selectedOrders.isEmpty() ? List.of() : details.selectList(
                new LambdaQueryWrapper<OrderRecordDetail>().in(OrderRecordDetail::getOrderId,
                        selectedOrders.stream().map(OrderRecord::getId).toList()));
        List<ShippingNoteTask> selectedTasks = tasks.selectList(new LambdaQueryWrapper<ShippingNoteTask>()
                .eq(customerId!=null,ShippingNoteTask::getCustomerId,customerId));
        List<ShippingNoteTaskItem> selectedItems = selectedTasks.isEmpty() ? List.of() : items.selectList(
                new LambdaQueryWrapper<ShippingNoteTaskItem>().in(ShippingNoteTaskItem::getTaskId,
                        selectedTasks.stream().map(ShippingNoteTask::getId).toList()));
        Map<Long,OrderRecord> byId = selectedOrders.stream().collect(Collectors.toMap(OrderRecord::getId,o->o));
        Map<String,BigDecimal> configs = prices.prices();
        Map<Long,BigDecimal> byDetail = new HashMap<>();
        for (OrderRecordDetail detail : selectedDetails) {
            OrderRecord order = byId.get(detail.getOrderId());
            BigDecimal price = order == null ? null :
                    configs.get(CustomerPriceService.key(order.getCustomerId(),detail.getDevelopmentNo()));
            if (price != null) byDetail.put(detail.getId(),price);
        }
        return new Data(selectedOrders,selectedDetails,selectedTasks,selectedItems,byDetail);
    }
    public static int quantity(OrderRecordDetail detail) {
        if (detail == null) return 0;
        // 达为订单中的尺码 JSON 可能是每箱配码，quantity 才是该行总双数。
        if (detail.getQuantity()!=null && detail.getQuantity()>0) return detail.getQuantity();
        if (detail.getSizeQuantitiesJson()!=null && !detail.getSizeQuantitiesJson().isBlank()) {
            try {
                Map<String,Integer> sizes = new ObjectMapper().readValue(detail.getSizeQuantitiesJson(),new TypeReference<>(){});
                int count=sizes.values().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
                if (count>0) return detail.getCartonCount()!=null && detail.getCartonCount()>0 ? count*detail.getCartonCount() : count;
            } catch (Exception ignored) {}
        }
        return Math.max(0,detail.getQuantity()==null?0:detail.getQuantity());
    }
    public static LocalDate shippingDate(ShippingNoteTask task) {
        return task.getShippingDate()!=null ? task.getShippingDate() :
                task.getCreatedAt()==null?null:task.getCreatedAt().toLocalDate();
    }
}
