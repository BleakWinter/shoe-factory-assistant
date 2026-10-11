package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.MonthlyReconciliationResponse;
import com.shoefactory.assistant.dto.UnpricedShipmentResponse;
import com.shoefactory.assistant.service.MonthlyReconciliationService;
import com.shoefactory.assistant.service.UnpricedShipmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monthly-reconciliations")
public class MonthlyReconciliationController {

    private final MonthlyReconciliationService monthlyReconciliationService;
    private final UnpricedShipmentService unpricedShipmentService;

    public MonthlyReconciliationController(MonthlyReconciliationService monthlyReconciliationService,
                                           UnpricedShipmentService unpricedShipmentService) {
        this.monthlyReconciliationService = monthlyReconciliationService;
        this.unpricedShipmentService = unpricedShipmentService;
    }

    @GetMapping
    public ApiResponse<MonthlyReconciliationResponse> getMonthlyReconciliation(
            @RequestParam String month,
            @RequestParam(required=false) String customerName,
            @RequestParam(required=false) Long customerId
    ) {
        return ApiResponse.ok(customerId == null ?
                monthlyReconciliationService.getMonthlyReconciliation(month, customerName) :
                monthlyReconciliationService.getMonthlyReconciliation(month, customerId));
    }

    @GetMapping("/customers")
    public ApiResponse<List<String>> listCustomers() {
        return ApiResponse.ok(monthlyReconciliationService.listCustomers());
    }

    @GetMapping("/unpriced-months")
    public ApiResponse<List<UnpricedShipmentResponse.MonthSummary>> listUnpricedMonths(
            @RequestParam(required = false) Long customerId) {
        return ApiResponse.ok(unpricedShipmentService.listMonths(customerId));
    }

    @GetMapping("/unpriced-details")
    public ApiResponse<UnpricedShipmentResponse.MonthDetails> getUnpricedMonth(
            @RequestParam String month, @RequestParam(required = false) Long customerId) {
        return ApiResponse.ok(unpricedShipmentService.getMonth(month, customerId));
    }

}
