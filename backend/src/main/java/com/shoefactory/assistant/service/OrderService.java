package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface OrderService {

    OrderRecordResponse uploadOrderSource(MultipartFile file);

    PageResponse<OrderRecordResponse> listOrders(
            String orderNo,
            String styleNo,
            String customerName,
            LocalDate deliveryDate,
            String recognitionStatus,
            long page,
            long size
    );
}
