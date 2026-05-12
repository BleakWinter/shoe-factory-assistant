package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.OrderLineResponse;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.PrintPreviewCreateRequest;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.enums.PrintType;
import com.shoefactory.assistant.service.OrderService;
import com.shoefactory.assistant.service.PrintPreviewService;
import jakarta.validation.Valid;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public ApiResponse<OrderUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(orderService.uploadOrderSource(file));
    }

    @GetMapping("/lines")
    public ApiResponse<PageResponse<OrderLineResponse>> listOrderLines(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String styleNo,
            @RequestParam(required = false) String lastNo,
            @RequestParam(required = false) String shipmentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(orderService.listOrderLines(
                orderNo,
                styleNo,
                lastNo,
                shipmentStatus,
                deliveryDate,
                page,
                size
        ));
    }

    @GetMapping("/lines/{id}/image")
    public ResponseEntity<UrlResource> orderLineImage(@PathVariable Long id) throws MalformedURLException {
        Path imagePath = orderService.loadOrderLineImage(id);
        UrlResource resource = new UrlResource(imagePath.toUri());
        return ResponseEntity.ok()
                .contentType(detectMediaType(imagePath))
                .contentLength(fileSize(imagePath))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(imagePath.getFileName().toString(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
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

    private MediaType detectMediaType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            if (contentType != null && !contentType.isBlank()) {
                return MediaType.parseMediaType(contentType);
            }
        } catch (Exception ignored) {
            // Fall back to binary for unknown image types.
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return -1;
        }
    }
}
