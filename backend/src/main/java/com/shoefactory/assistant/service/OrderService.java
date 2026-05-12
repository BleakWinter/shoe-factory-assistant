package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.OrderLineResponse;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;

public interface OrderService {

    OrderUploadResponse uploadOrderSource(MultipartFile file);

    PageResponse<OrderLineResponse> listOrderLines(
            String orderNo,
            String styleNo,
            String customerName,
            String lastNo,
            LocalDate deliveryDate,
            long page,
            long size
    );

    Path loadOrderLineImage(Long lineId);

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
