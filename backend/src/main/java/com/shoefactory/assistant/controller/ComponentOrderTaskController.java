package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.ComponentOrderCreateRequest;
import com.shoefactory.assistant.dto.ComponentOrderTaskResponse;
import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.service.ComponentOrderTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/component-order-tasks")
public class ComponentOrderTaskController {

    private final ComponentOrderTaskService componentOrderTaskService;

    public ComponentOrderTaskController(ComponentOrderTaskService componentOrderTaskService) {
        this.componentOrderTaskService = componentOrderTaskService;
    }

    @PostMapping
    public ApiResponse<ComponentOrderTaskResponse> createComponentOrderTask(
            @Valid @RequestBody ComponentOrderCreateRequest request
    ) {
        return ApiResponse.ok(componentOrderTaskService.createComponentOrderTask(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ComponentOrderTaskResponse>> listComponentOrderTasks(
            @RequestParam Integer processType,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String developmentNo,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(componentOrderTaskService.listComponentOrderTasks(
                processType, orderNo, developmentNo, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ComponentOrderTaskResponse> getComponentOrderTask(@PathVariable Long id) {
        return ApiResponse.ok(componentOrderTaskService.getComponentOrderTask(id));
    }
}
