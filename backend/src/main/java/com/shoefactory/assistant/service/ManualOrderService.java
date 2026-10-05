package com.shoefactory.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shoefactory.assistant.common.BusinessException;
import com.shoefactory.assistant.dto.*;
import com.shoefactory.assistant.entity.*;
import com.shoefactory.assistant.mapper.*;
import com.shoefactory.assistant.util.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ManualOrderService {
    private final OrderRecordMapper orders;
    private final OrderRecordDetailMapper details;
    private final ShippingNoteTaskMapper shipments;
    private final ShippingNoteTaskItemMapper shipmentItems;
    private final CustomerConfigMapper customers;
    private final CustomerPriceService prices;
    public ManualOrderService(OrderRecordMapper orders, OrderRecordDetailMapper details, ShippingNoteTaskMapper shipments,
                              ShippingNoteTaskItemMapper shipmentItems, CustomerConfigMapper customers, CustomerPriceService prices) {
        this.orders=orders; this.details=details; this.shipments=shipments;
        this.shipmentItems=shipmentItems; this.customers=customers; this.prices=prices;
    }
    public record Detail(OrderRecordDetail detail, int shippedQuantity, int remainingQuantity) {}
    public record Shipment(ShippingNoteTask shipment, List<ShippingNoteTaskItem> items) {}
    public record View(OrderRecord order, List<Detail> items, List<Shipment> shipments) {}
    @Transactional(readOnly=true)
    public View get(Long id) {
        OrderRecord order = requireManual(orders.selectById(id));
        List<OrderRecordDetail> rows=rows(id);
        Map<Long,Integer> shipped=shipped(rows);
        List<ShippingNoteTaskItem> entries=rows.isEmpty()?List.of():shipmentItems.selectList(
                new LambdaQueryWrapper<ShippingNoteTaskItem>().in(ShippingNoteTaskItem::getSourceDetailId,rows.stream().map(OrderRecordDetail::getId).toList()));
        List<Long> taskIds=entries.stream().map(ShippingNoteTaskItem::getTaskId).distinct().toList();
        List<ShippingNoteTask> tasks=taskIds.isEmpty()?List.of():shipments.selectBatchIds(taskIds);
        return new View(order,rows.stream().map(d->new Detail(d,shipped.getOrDefault(d.getId(),0),
                Math.max(0,LedgerDataService.quantity(d)-shipped.getOrDefault(d.getId(),0)))).toList(),
                tasks.stream().sorted(Comparator.comparing(ShippingNoteTask::getShippingDate).reversed())
                        .map(t->new Shipment(t,entries.stream().filter(i->t.getId().equals(i.getTaskId())).toList())).toList());
    }
    @Transactional
    public View save(Long id, ManualOrderSaveRequest request) {
        CustomerConfig customer=customers.selectForUpdate(request.customerId());
        if (customer==null) throw new BusinessException("客户不存在");
        if (!"MANUAL".equals(customer.getInputMode())) throw new BusinessException("该客户使用 Excel 录单");
        if (id==null && !Boolean.TRUE.equals(customer.getEnabled())) throw new BusinessException("该客户已停用，不能新增订单");
        if (request.orderDate().isAfter(today())) throw new BusinessException("订单日期不能晚于今天");
        if (request.deliveryDate()!=null && request.deliveryDate().isBefore(request.orderDate())) throw new BusinessException("交货日期不能早于订单日期");
        OrderRecord order=id==null?new OrderRecord():requireManual(orders.selectForUpdate(id));
        if (id!=null && !request.customerId().equals(order.getCustomerId())) throw new BusinessException("已有订单不能更换客户");
        List<OrderRecordDetail> existing=id==null?List.of():rows(id);
        if (shipped(existing).values().stream().anyMatch(n->n>0)) throw new BusinessException("已有出货记录的订单不能修改，请新增补单");
        if (orders.selectCount(new LambdaQueryWrapper<OrderRecord>().eq(OrderRecord::getCustomerId,customer.getId())
                .eq(OrderRecord::getOrderNo,request.orderNo().trim()).ne(id!=null,OrderRecord::getId,id))>0)
            throw new BusinessException("该客户订单号已存在");
        Map<Long,OrderRecordDetail> byId=existing.stream().collect(Collectors.toMap(OrderRecordDetail::getId,d->d));
        Set<Long> retained=new HashSet<>();
        for (var row:request.items()) {
            if (row.id()!=null && (!byId.containsKey(row.id()) || !retained.add(row.id()))) throw new BusinessException("订单明细ID无效或重复");
            if (CustomerPriceService.normalize(row.developmentNo()).isBlank()) throw new BusinessException("款号不能为空");
        }
        LocalDateTime now=LocalDateTime.now();
        order.setCustomerId(customer.getId()); order.setCustomerName(customer.getName());
        order.setOrderNo(request.orderNo().trim()); order.setOrderDate(request.orderDate()); order.setRemark(request.remark());
        order.setSourceType(3); order.setOrderRecognitionStatus(1); order.setPackingRecognitionStatus(2);
        order.setTotalQuantity(request.items().stream().mapToInt(ManualOrderSaveRequest.Item::quantity).sum());
        order.setTotalCartonCount(0); order.setDevelopmentNos(request.items().stream()
                .map(i->CustomerPriceService.normalize(i.developmentNo())).distinct().collect(Collectors.joining(",")));
        order.setUpdatedAt(now);
        if (id==null) { order.setCreatedAt(now); orders.insert(order); } else orders.updateById(order);
        for (OrderRecordDetail row:existing) if (!retained.contains(row.getId())) details.deleteById(row.getId());
        for (int i=0;i<request.items().size();i++) {
            var input=request.items().get(i);
            OrderRecordDetail row=input.id()==null?new OrderRecordDetail():byId.get(input.id());
            row.setOrderId(order.getId()); row.setLineNo(i+1); row.setDevelopmentNo(CustomerPriceService.normalize(input.developmentNo()));
            row.setCustomerName(customer.getName()); row.setCustomerOrderNo(order.getOrderNo());
            row.setDeliveryDate(request.deliveryDate()); row.setQuantity(input.quantity());
            row.setUpperMaterial(input.colorMaterial()); row.setRemark(input.remark());
            row.setSourceSheetName("手工录入"); row.setUpdatedAt(now);
            prices.ensure(customer.getId(),List.of(row.getDevelopmentNo()));
            if (input.id()==null) { row.setCreatedAt(now); details.insert(row); }
            else details.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<OrderRecordDetail>()
                    .eq(OrderRecordDetail::getId,row.getId()).set(OrderRecordDetail::getLineNo,row.getLineNo())
                    .set(OrderRecordDetail::getDevelopmentNo,row.getDevelopmentNo()).set(OrderRecordDetail::getQuantity,row.getQuantity())
                    .set(OrderRecordDetail::getDeliveryDate,row.getDeliveryDate())
                    .set(OrderRecordDetail::getUpperMaterial,row.getUpperMaterial()).set(OrderRecordDetail::getRemark,row.getRemark())
                    .set(OrderRecordDetail::getCustomerOrderNo,row.getCustomerOrderNo()).set(OrderRecordDetail::getUpdatedAt,now));
        }
        return get(order.getId());
    }
    @Transactional
    public View confirm(Long id, ManualShipmentRequest request) {
        OrderRecord order=requireManual(orders.selectForUpdate(id));
        Set<Long> selected=new LinkedHashSet<>(request.detailIds());
        if (selected.size()!=request.detailIds().size()) throw new BusinessException("出货明细不能重复选择");
        ShippingNoteTask repeated=shipments.selectOne(new LambdaQueryWrapper<ShippingNoteTask>().eq(ShippingNoteTask::getRequestId,request.requestId()));
        if (repeated!=null) {
            List<ShippingNoteTaskItem> repeatedItems=shipmentItems.selectList(new LambdaQueryWrapper<ShippingNoteTaskItem>().eq(ShippingNoteTaskItem::getTaskId,repeated.getId()));
            boolean same=request.shippingDate().equals(repeated.getShippingDate()) && repeatedItems.size()==selected.size()
                    && repeatedItems.stream().allMatch(i->order.getId().equals(i.getOrderId()))
                    && selected.equals(repeatedItems.stream().map(ShippingNoteTaskItem::getSourceDetailId).collect(Collectors.toSet()));
            if (!same) throw new BusinessException("本次提交编号已经用于另一笔出货，请重新打开确认窗口");
            return get(id);
        }
        if (request.shippingDate().isAfter(today()) || (order.getOrderDate()!=null && request.shippingDate().isBefore(order.getOrderDate())))
            throw new BusinessException("出货日期必须在订单日期与今天之间");
        List<OrderRecordDetail> rows=rows(id);
        Map<Long,OrderRecordDetail> byId=rows.stream().collect(Collectors.toMap(OrderRecordDetail::getId,d->d));
        Map<Long,Integer> alreadyShipped=shipped(rows);
        for (Long detailId:selected) {
            OrderRecordDetail detail=byId.get(detailId);
            if (detail==null) throw new BusinessException("出货明细不属于该订单");
            if (alreadyShipped.getOrDefault(detailId,0)>0) throw new BusinessException(detail.getDevelopmentNo()+" 已确认出货，不能重复出货");
        }
        CustomerConfig customer=customers.selectById(order.getCustomerId());
        if (customer==null) throw new BusinessException("客户不存在");
        LocalDateTime now=LocalDateTime.now();
        ShippingNoteTask task=new ShippingNoteTask();
        task.setTaskNo(FileStorageUtil.newBusinessNo("SH")); task.setRequestId(request.requestId());
        task.setCustomerId(customer.getId()); task.setSourceType(3); task.setRecipientName(customer.getRecipientName());
        task.setShippingDate(request.shippingDate()); task.setInvoiceNos(order.getOrderNo()); task.setItemCount(selected.size());
        task.setDevelopmentNos(selected.stream().map(detailId->byId.get(detailId).getDevelopmentNo()).distinct().collect(Collectors.joining(",")));
        task.setTotalPairs(selected.stream().map(byId::get).mapToInt(LedgerDataService::quantity).sum());
        task.setTotalCartonCount(0); task.setCreatedAt(now); task.setUpdatedAt(now); shipments.insert(task);
        int lineNo=0;
        for (Long detailId:selected) {
            OrderRecordDetail detail=byId.get(detailId);
            ShippingNoteTaskItem row=new ShippingNoteTaskItem();
            row.setTaskId(task.getId()); row.setLineNo(++lineNo); row.setOrderId(order.getId()); row.setSourceDetailId(detail.getId());
            row.setCreatedAt(now); row.setUpdatedAt(now); shipmentItems.insert(row);
        }
        return get(id);
    }
    private List<OrderRecordDetail> rows(Long id) {
        return details.selectList(new LambdaQueryWrapper<OrderRecordDetail>().eq(OrderRecordDetail::getOrderId,id).orderByAsc(OrderRecordDetail::getLineNo));
    }
    private Map<Long,Integer> shipped(List<OrderRecordDetail> rows) {
        if (rows.isEmpty()) return Map.of();
        Map<Long,OrderRecordDetail> byId=rows.stream().collect(Collectors.toMap(OrderRecordDetail::getId,d->d));
        List<ShippingNoteTaskItem> entries=shipmentItems.selectList(new LambdaQueryWrapper<ShippingNoteTaskItem>()
                .in(ShippingNoteTaskItem::getSourceDetailId,byId.keySet()));
        Map<Long,Integer> result=new HashMap<>();
        for (var entry:entries) result.merge(entry.getSourceDetailId(),
                LedgerDataService.quantity(byId.get(entry.getSourceDetailId())),Integer::sum);
        return result;
    }
    private OrderRecord requireManual(OrderRecord order) {
        if (order==null) throw new BusinessException("订单不存在");
        if (!Integer.valueOf(3).equals(order.getSourceType())) throw new BusinessException("此入口仅用于手工订单");
        return order;
    }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
}
