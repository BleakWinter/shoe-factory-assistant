package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.PrintPreviewCreateRequest;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.enums.PrintType;
import com.shoefactory.assistant.service.OrderService;
import com.shoefactory.assistant.service.PrintPreviewService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PrintPreviewService printPreviewService;

    public OrderController(OrderService orderService, PrintPreviewService printPreviewService) {
        this.orderService = orderService;
        this.printPreviewService = printPreviewService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OrderRecordResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(orderService.uploadOrderSource(file));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderRecordResponse>> listOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String styleNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(required = false) String recognitionStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(orderService.listOrders(
                orderNo,
                styleNo,
                customerName,
                deliveryDate,
                recognitionStatus,
                page,
                size
        ));
    }

    @PostMapping("/{id}/print-previews")
    public ApiResponse<PrintPreviewResponse> generatePrintPreview(
            @PathVariable Long id,
            @Valid @RequestBody PrintPreviewCreateRequest request
    ) {
        return ApiResponse.ok(printPreviewService.generatePreview(id, PrintType.parse(request.getPrintType())));
    }
}
