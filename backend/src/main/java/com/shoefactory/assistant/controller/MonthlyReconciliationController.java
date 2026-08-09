package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.MonthlyReconciliationResponse;
import com.shoefactory.assistant.service.MonthlyReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monthly-reconciliations")
public class MonthlyReconciliationController {

    private final MonthlyReconciliationService monthlyReconciliationService;

    public MonthlyReconciliationController(MonthlyReconciliationService monthlyReconciliationService) {
        this.monthlyReconciliationService = monthlyReconciliationService;
    }

    @GetMapping
    public ApiResponse<MonthlyReconciliationResponse> getMonthlyReconciliation(
            @RequestParam String month,
            @RequestParam String customerName
    ) {
        return ApiResponse.ok(monthlyReconciliationService.getMonthlyReconciliation(month, customerName));
    }

    @GetMapping("/customers")
    public ApiResponse<List<String>> listCustomers() {
        return ApiResponse.ok(monthlyReconciliationService.listCustomers());
    }

}
