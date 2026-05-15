package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.DevelopmentNoOptionResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShoePriceConfigResponse;
import com.shoefactory.assistant.dto.ShoePriceConfigSaveRequest;
import com.shoefactory.assistant.service.StyleConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/price-configs")
public class ShoePriceConfigController {

    private final StyleConfigService styleConfigService;

    public ShoePriceConfigController(StyleConfigService styleConfigService) {
        this.styleConfigService = styleConfigService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ShoePriceConfigResponse>> listShoePriceConfigs(
            @RequestParam(required = false) String developmentNos,
            @RequestParam(required = false) Boolean incompleteOnly,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(styleConfigService.listShoePriceConfigs(
                developmentNos,
                incompleteOnly,
                page,
                size
        ));
    }

    @PostMapping
    public ApiResponse<ShoePriceConfigResponse> createShoePriceConfig(
            @Valid @RequestBody ShoePriceConfigSaveRequest request
    ) {
        return ApiResponse.ok(styleConfigService.createShoePriceConfig(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShoePriceConfigResponse> updateShoePriceConfig(
            @PathVariable Long id,
            @Valid @RequestBody ShoePriceConfigSaveRequest request
    ) {
        return ApiResponse.ok(styleConfigService.updateShoePriceConfig(id, request));
    }

    @GetMapping("/unconfigured-development-nos")
    public ApiResponse<List<String>> listUnpricedDevelopmentNos() {
        return ApiResponse.ok(styleConfigService.listUnpricedDevelopmentNos());
    }

    @GetMapping("/development-options")
    public ApiResponse<List<DevelopmentNoOptionResponse>> listDevelopmentNoOptions() {
        return ApiResponse.ok(styleConfigService.listDevelopmentNoOptions());
    }
}
