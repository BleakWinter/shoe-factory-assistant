package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import com.shoefactory.assistant.dto.OrderPackingDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordResponse;
import com.shoefactory.assistant.dto.OrderStatisticsResponse;
import com.shoefactory.assistant.dto.OrderUploadResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.service.OrderService;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // 订单接口：上传 Excel、查看订单主表、查看明细和明细图片。
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OrderUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        // 前端上传字段名固定为 file。
        return ApiResponse.ok(orderService.uploadOrderSource(file));
    }

    @PostMapping(value = "/{id}/reupload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OrderUploadResponse> reupload(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(orderService.reuploadOrderSource(id, file));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderRecordResponse>> listOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String developmentNos,
            @RequestParam(required = false) String recognitionStatus,
            @RequestParam(required = false) Integer unfinishedProcessType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        // 订单列表展示 order_record 主表数据。
        return ApiResponse.ok(orderService.listOrders(
                orderNo,
                developmentNos,
                recognitionStatus,
                unfinishedProcessType,
                page,
                size
        ));
    }

    @GetMapping("/development-options")
    public ApiResponse<List<DevelopmentNoOptionResponse>> listDevelopmentNoOptions() {
        return ApiResponse.ok(orderService.listDevelopmentNoOptions());
    }

    @GetMapping("/statistics")
    public ApiResponse<OrderStatisticsResponse> getOrderStatistics() {
        return ApiResponse.ok(orderService.getOrderStatistics());
    }

    @PostMapping("/{id}/recognize-order")
    public ApiResponse<OrderRecordResponse> recognizeOrder(@PathVariable Long id) {
        return ApiResponse.ok(orderService.recognizeOrder(id));
    }

    @PostMapping("/{id}/recognize-packing")
    public ApiResponse<OrderRecordResponse> recognizePacking(@PathVariable Long id) {
        return ApiResponse.ok(orderService.recognizePacking(id));
    }

    @GetMapping("/{id}/details")
    public ApiResponse<List<OrderRecordDetailResponse>> listOrderDetails(@PathVariable Long id) {
        // 详情按钮调用这里：明细表和每条明细的处理状态一次返回。
        return ApiResponse.ok(orderService.listOrderDetails(id));
    }

    @DeleteMapping("/{id}/detail")
    public ApiResponse<Boolean> removeOrderDetail(@PathVariable Long id) {
        // 详情按钮调用这里：明细表和每条明细的处理状态一次返回。
        return ApiResponse.ok(orderService.removeOrderDetailById(id));
    }

    @GetMapping("/{id}/packing-details")
    public ApiResponse<List<OrderPackingDetailResponse>> listOrderPackingDetails(@PathVariable Long id) {
        return ApiResponse.ok(orderService.listOrderPackingDetails(id));
    }

    @GetMapping("/details/{id}/packing-details")
    public ApiResponse<List<OrderPackingDetailResponse>> listMatchingPackingDetails(@PathVariable Long id) {
        return ApiResponse.ok(orderService.listMatchingPackingDetails(id));
    }

    @GetMapping("/details/{id}/image")
    public ResponseEntity<UrlResource> orderDetailImage(@PathVariable Long id) throws MalformedURLException {
        // 图片是 Excel 内嵌图提取后的本地文件，这里以内联资源返回给浏览器预览。
        Path imagePath = orderService.loadOrderDetailImage(id);
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

    @GetMapping("/packing-details/{id}/image")
    public ResponseEntity<UrlResource> orderPackingDetailImage(@PathVariable Long id) throws MalformedURLException {
        Path imagePath = orderService.loadOrderPackingDetailImage(id);
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
