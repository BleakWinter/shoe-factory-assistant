package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.OrderPackingDetailResponse;
import com.shoefactory.assistant.dto.OrderRecordDetailResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // 订单接口：上传 Excel、查看订单主表、查看明细和明细图片。
    private final OrderService orderService;
    private final PrintPreviewService printPreviewService;

    public OrderController(OrderService orderService, PrintPreviewService printPreviewService) {
        this.orderService = orderService;
        this.printPreviewService = printPreviewService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<OrderUploadResponse> upload(@RequestPart("file") MultipartFile file) {
        // 前端上传字段名固定为 file。
        return ApiResponse.ok(orderService.uploadOrderSource(file));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderRecordResponse>> listOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String developmentNo,
            @RequestParam(required = false) String recognitionStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        // 订单列表展示 order_record 主表数据。
        return ApiResponse.ok(orderService.listOrders(
                orderNo,
                customerName,
                developmentNo,
                recognitionStatus,
                page,
                size
        ));
    }

    @GetMapping("/{id}/details")
    public ApiResponse<List<OrderRecordDetailResponse>> listOrderDetails(@PathVariable Long id) {
        // 详情按钮调用这里：明细表和每条明细的处理状态一次返回。
        return ApiResponse.ok(orderService.listOrderDetails(id));
    }

    @GetMapping("/{id}/packing-details")
    public ApiResponse<List<OrderPackingDetailResponse>> listOrderPackingDetails(@PathVariable Long id) {
        return ApiResponse.ok(orderService.listOrderPackingDetails(id));
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

    @GetMapping("/{id}/pdf/{printType}")
    public ResponseEntity<UrlResource> orderPdf(
            @PathVariable Long id,
            @PathVariable String printType
    ) throws MalformedURLException {
        // 生成后的 PDF 路径保存在 order_record，这里按订单 id 和类型读取。
        Path pdfPath = printPreviewService.loadOrderPdf(id, PrintType.parse(printType));
        UrlResource resource = new UrlResource(pdfPath.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(fileSize(pdfPath))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(pdfPath.getFileName().toString(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @PostMapping("/{id}/print-previews")
    public ApiResponse<PrintPreviewResponse> generatePrintPreview(
            @PathVariable Long id,
            @Valid @RequestBody PrintPreviewCreateRequest request
    ) {
        // 早期接口：从订单 id 直接生成预览；当前主流程更多走 /api/print-tasks/{id}/preview。
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
