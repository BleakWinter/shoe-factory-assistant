package com.shoefactory.assistant.service.report;

import java.time.LocalDate;
import java.util.List;

record DeliveryReport(LocalDate reportDate, List<OrderGroup> orderGroups) {
    record OrderGroup(String orderNo, LocalDate deliveryDate, long overdueDays, List<Style> styles) {}

    record Style(String styleNo, long pendingPairs) {}
}
