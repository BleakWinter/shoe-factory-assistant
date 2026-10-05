package com.shoefactory.assistant.controller;
import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.*;
import com.shoefactory.assistant.service.CustomerPriceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/price-configs")
public class ShoePriceConfigController {
    private final CustomerPriceService service;
    public ShoePriceConfigController(CustomerPriceService service) { this.service=service; }
    @GetMapping public ApiResponse<PageResponse<CustomerPriceService.PriceResponse>> list(
            @RequestParam(required=false) Long customerId,
            @RequestParam(required=false) String developmentNos,
            @RequestParam(required=false) Boolean incompleteOnly,
            @RequestParam(defaultValue="1") long page, @RequestParam(defaultValue="20") long size) {
        return ApiResponse.ok(service.list(customerId,developmentNos,incompleteOnly,page,size));
    }
    @PostMapping public ApiResponse<CustomerPriceService.PriceResponse> create(@Valid @RequestBody CustomerPriceSaveRequest request) {
        return ApiResponse.ok(service.save(null,request));
    }
    @PutMapping("/{id}") public ApiResponse<CustomerPriceService.PriceResponse> update(
            @PathVariable Long id,@Valid @RequestBody CustomerPriceSaveRequest request) {
        return ApiResponse.ok(service.save(id,request));
    }
    @GetMapping("/unconfigured-development-nos") public ApiResponse<java.util.List<String>> unpriced(
            @RequestParam(required=false) Long customerId) {
        return ApiResponse.ok(service.unpricedStyles(customerId));
    }
    @GetMapping("/development-options") public ApiResponse<java.util.List<DevelopmentNoOptionResponse>> options(
            @RequestParam(required=false) Long customerId) {
        return ApiResponse.ok(service.developmentOptions(customerId));
    }
}
