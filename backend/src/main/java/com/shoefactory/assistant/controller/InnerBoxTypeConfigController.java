package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.InnerBoxTypeSaveRequest;
import com.shoefactory.assistant.entity.InnerBoxTypeConfig;
import com.shoefactory.assistant.service.InnerBoxTypeConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/inner-box-types")
public class InnerBoxTypeConfigController {
    private final InnerBoxTypeConfigService service;
    public InnerBoxTypeConfigController(InnerBoxTypeConfigService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<InnerBoxTypeConfig>> list(@RequestParam(required = false) Boolean enabled) {
        return ApiResponse.ok(service.list(enabled));
    }

    @PostMapping
    public ApiResponse<InnerBoxTypeConfig> create(@Valid @RequestBody InnerBoxTypeSaveRequest request) {
        return ApiResponse.ok(service.save(null, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<InnerBoxTypeConfig> update(@PathVariable Long id, @Valid @RequestBody InnerBoxTypeSaveRequest request) {
        return ApiResponse.ok(service.save(id, request));
    }
}
