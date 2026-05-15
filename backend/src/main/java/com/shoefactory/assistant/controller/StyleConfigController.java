package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.StyleConfigResponse;
import com.shoefactory.assistant.dto.StyleConfigSaveRequest;
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
@RequestMapping("/api/style-configs")
public class StyleConfigController {

    private final StyleConfigService styleConfigService;

    public StyleConfigController(StyleConfigService styleConfigService) {
        this.styleConfigService = styleConfigService;
    }

    @GetMapping
    public ApiResponse<PageResponse<StyleConfigResponse>> listStyleConfigs(
            @RequestParam(required = false) String developmentNo,
            @RequestParam(required = false) Boolean incompleteOnly,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(styleConfigService.listStyleConfigs(
                developmentNo,
                incompleteOnly,
                page,
                size
        ));
    }

    @PostMapping
    public ApiResponse<StyleConfigResponse> createStyleConfig(
            @Valid @RequestBody StyleConfigSaveRequest request
    ) {
        return ApiResponse.ok(styleConfigService.createStyleConfig(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StyleConfigResponse> updateStyleConfig(
            @PathVariable Long id,
            @Valid @RequestBody StyleConfigSaveRequest request
    ) {
        return ApiResponse.ok(styleConfigService.updateStyleConfig(id, request));
    }

    @GetMapping("/unconfigured-development-nos")
    public ApiResponse<List<String>> listUnconfiguredDevelopmentNos() {
        return ApiResponse.ok(styleConfigService.listUnconfiguredDevelopmentNos());
    }
}
