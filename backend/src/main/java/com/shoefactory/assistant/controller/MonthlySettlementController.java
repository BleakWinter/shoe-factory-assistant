package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.MonthlySettlementData.*;
import com.shoefactory.assistant.service.MonthlySettlementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monthly-reconciliations/settlement")
public class MonthlySettlementController {
    private final MonthlySettlementService service;

    public MonthlySettlementController(MonthlySettlementService service) { this.service = service; }

    @GetMapping
    public ApiResponse<View> get(@RequestParam String month, @RequestParam Long customerId) {
        return ApiResponse.ok(service.get(month, customerId));
    }

    @PutMapping
    public ApiResponse<View> save(@RequestParam String month, @RequestParam Long customerId, @Valid @RequestBody SaveRequest request) {
        return ApiResponse.ok(service.save(month, customerId, request));
    }

    @PostMapping("/confirm")
    public ApiResponse<View> confirm(@RequestParam String month, @RequestParam Long customerId, @Valid @RequestBody ConfirmRequest request) {
        return ApiResponse.ok(service.confirm(month, customerId, request));
    }

    @PatchMapping("/receipt")
    public ApiResponse<View> recordReceipt(@RequestParam String month, @RequestParam Long customerId, @Valid @RequestBody ReceiptRequest request) {
        return ApiResponse.ok(service.recordReceipt(month, customerId, request));
    }
}
