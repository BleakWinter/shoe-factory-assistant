package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteTaskResponse;
import com.shoefactory.assistant.dto.ShippingNoteUpdateRequest;
import com.shoefactory.assistant.service.ShippingNoteTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping-note-tasks")
public class ShippingNoteTaskController {

    private final ShippingNoteTaskService shippingNoteTaskService;

    public ShippingNoteTaskController(ShippingNoteTaskService shippingNoteTaskService) {
        this.shippingNoteTaskService = shippingNoteTaskService;
    }

    @PostMapping
    public ApiResponse<ShippingNoteTaskResponse> createShippingNoteTask(
            @Valid @RequestBody ShippingNoteCreateRequest request
    ) {
        return ApiResponse.ok(shippingNoteTaskService.createShippingNoteTask(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ShippingNoteTaskResponse>> listShippingNoteTasks(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String developmentNo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(shippingNoteTaskService.listShippingNoteTasks(orderNo, developmentNo, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShippingNoteTaskResponse> getShippingNoteTask(@PathVariable Long id) {
        return ApiResponse.ok(shippingNoteTaskService.getShippingNoteTask(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShippingNoteTaskResponse> updateShippingNoteTask(
            @PathVariable Long id,
            @Valid @RequestBody ShippingNoteUpdateRequest request
    ) {
        return ApiResponse.ok(shippingNoteTaskService.updateShippingNoteTask(id, request));
    }
}
