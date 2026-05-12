package com.shoefactory.assistant.service;

import com.shoefactory.assistant.entity.OrderLine;
import com.shoefactory.assistant.entity.OrderRecord;

import java.util.List;

public class OrderImportResult {

    private final OrderRecord order;
    private final List<OrderLine> lines;

    public OrderImportResult(OrderRecord order, List<OrderLine> lines) {
        this.order = order;
        this.lines = lines;
    }

    public OrderRecord getOrder() {
        return order;
    }

    public List<OrderLine> getLines() {
        return lines;
    }
}
