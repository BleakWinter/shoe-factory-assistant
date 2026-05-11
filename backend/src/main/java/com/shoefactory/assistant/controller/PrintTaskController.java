package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.PrintTaskCreateRequest;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;
import com.shoefactory.assistant.service.PrintTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/print-tasks")
public class PrintTaskController {

    private final PrintTaskService printTaskService;

    public PrintTaskController(PrintTaskService printTaskService) {
        this.printTaskService = printTaskService;
    }

    @PostMapping
    public ApiResponse<PrintTaskResponse> createTask(@Valid @RequestBody PrintTaskCreateRequest request) {
        return ApiResponse.ok(printTaskService.createTask(request));
    }

    @GetMapping("/pending")
    public ApiResponse<List<PrintTaskResponse>> listPendingTasks(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(printTaskService.listPendingTasks(limit));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<PrintTaskResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody PrintTaskStatusUpdateRequest request
    ) {
        return ApiResponse.ok(printTaskService.updateTaskStatus(id, request));
    }
}
