package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.PackingOrderCreateRequest;
import com.shoefactory.assistant.dto.PackingOrderItemResponse;
import com.shoefactory.assistant.dto.PackingOrderPreviewRequest;
import com.shoefactory.assistant.dto.PackingOrderTaskResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.service.PackingOrderTaskService;
import jakarta.validation.Valid;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/packing-order-tasks")
public class PackingOrderTaskController {
    private final PackingOrderTaskService service;

    public PackingOrderTaskController(PackingOrderTaskService service) { this.service = service; }

    @PostMapping("/preview")
    public ApiResponse<List<PackingOrderItemResponse>> preview(@Valid @RequestBody PackingOrderPreviewRequest request) {
        return ApiResponse.ok(service.preview(request));
    }

    @PostMapping
    public ApiResponse<PackingOrderTaskResponse> create(@Valid @RequestBody PackingOrderCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<PackingOrderTaskResponse>> list(
            @RequestParam(required = false) String orderNo, @RequestParam(required = false) String developmentNo,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(service.list(orderNo, developmentNo, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<PackingOrderTaskResponse> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }

    @GetMapping("/{taskId}/items/{itemId}/image")
    public ResponseEntity<UrlResource> image(@PathVariable Long taskId, @PathVariable Long itemId) throws IOException {
        Path path = service.image(taskId, itemId);
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok().contentLength(Files.size(path))
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .body(new UrlResource(path.toUri()));
    }
}
