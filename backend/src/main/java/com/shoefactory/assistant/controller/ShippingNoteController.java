package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteRecordResponse;
import com.shoefactory.assistant.service.ShippingNoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping-notes")
public class ShippingNoteController {

    private final ShippingNoteService shippingNoteService;

    public ShippingNoteController(ShippingNoteService shippingNoteService) {
        this.shippingNoteService = shippingNoteService;
    }

    @PostMapping
    public ApiResponse<ShippingNoteRecordResponse> createShippingNote(
            @Valid @RequestBody ShippingNoteCreateRequest request
    ) {
        return ApiResponse.ok(shippingNoteService.createShippingNote(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ShippingNoteRecordResponse>> listShippingNotes(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String developmentNo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(shippingNoteService.listShippingNotes(orderNo, developmentNo, page, size));
    }
}
