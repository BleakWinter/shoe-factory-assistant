package com.shoefactory.assistant.controller;
import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.CustomerSaveRequest;
import com.shoefactory.assistant.entity.CustomerConfig;
import com.shoefactory.assistant.service.CustomerConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/customer-configs")
public class CustomerConfigController {
    private final CustomerConfigService service;
    public CustomerConfigController(CustomerConfigService service) { this.service = service; }
    @GetMapping public ApiResponse<List<CustomerConfig>> list() { return ApiResponse.ok(service.list()); }
    @PostMapping public ApiResponse<CustomerConfig> create(@Valid @RequestBody CustomerSaveRequest request) {
        return ApiResponse.ok(service.save(null, request));
    }
    @PutMapping("/{id}") public ApiResponse<CustomerConfig> update(@PathVariable Long id, @Valid @RequestBody CustomerSaveRequest request) {
        return ApiResponse.ok(service.save(id, request));
    }
}
