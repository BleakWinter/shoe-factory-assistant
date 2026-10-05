package com.shoefactory.assistant.controller;
import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.*;
import com.shoefactory.assistant.service.ManualOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/manual-orders")
public class ManualOrderController {
    private final ManualOrderService service;
    public ManualOrderController(ManualOrderService service) { this.service=service; }
    @GetMapping("/{id}") public ApiResponse<ManualOrderService.View> get(@PathVariable Long id) { return ApiResponse.ok(service.get(id)); }
    @PostMapping public ApiResponse<ManualOrderService.View> create(@Valid @RequestBody ManualOrderSaveRequest request) { return ApiResponse.ok(service.save(null,request)); }
    @PutMapping("/{id}") public ApiResponse<ManualOrderService.View> update(@PathVariable Long id,@Valid @RequestBody ManualOrderSaveRequest request) { return ApiResponse.ok(service.save(id,request)); }
    @PostMapping("/{id}/shipments") public ApiResponse<ManualOrderService.View> confirm(@PathVariable Long id,@Valid @RequestBody ManualShipmentRequest request) { return ApiResponse.ok(service.confirm(id,request)); }
}
