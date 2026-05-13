package com.shoefactory.assistant.service;

import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderRecordDetail;
import com.shoefactory.assistant.entity.OrderPackingDetail;

import java.util.List;

public class OrderImportResult {

    // Excel 导入的一次完整结果：一条订单主记录 + 多条订单明细。
    private final OrderRecord order;
    private final List<OrderRecordDetail> details;
    private final List<OrderPackingDetail> packingDetails;

    public OrderImportResult(OrderRecord order, List<OrderRecordDetail> details) {
        this(order, details, List.of());
    }

    public OrderImportResult(OrderRecord order, List<OrderRecordDetail> details, List<OrderPackingDetail> packingDetails) {
        this.order = order;
        this.details = details;
        this.packingDetails = packingDetails == null ? List.of() : packingDetails;
    }

    public OrderRecord getOrder() {
        return order;
    }

    public List<OrderRecordDetail> getDetails() {
        return details;
    }

    public List<OrderPackingDetail> getPackingDetails() {
        return packingDetails;
    }
}
