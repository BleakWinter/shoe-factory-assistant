package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.dto.PrintTaskCreateRequest;
import com.shoefactory.assistant.dto.PrintTaskPreviewRequest;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;
import com.shoefactory.assistant.enums.PrintType;
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

    // 打印任务接口：页面查询任务、生成预览；pending/status 留给后续本地打印代理使用。
    private final PrintTaskService printTaskService;

    public PrintTaskController(PrintTaskService printTaskService) {
        this.printTaskService = printTaskService;
    }

    @PostMapping
    public ApiResponse<PrintTaskResponse> createTask(@Valid @RequestBody PrintTaskCreateRequest request) {
        // 早期“确认打印”接口，当前上传 Excel 时已经自动创建待打印任务。
        return ApiResponse.ok(printTaskService.createTask(request));
    }

    @GetMapping
    public ApiResponse<List<PrintTaskResponse>> listTasks() {
        // 打印列表页面调用这里。
        return ApiResponse.ok(printTaskService.listTasks());
    }

    @GetMapping("/pending")
    public ApiResponse<List<PrintTaskResponse>> listPendingTasks(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        // 预留给 print-agent 轮询待打印任务。
        return ApiResponse.ok(printTaskService.listPendingTasks(limit));
    }

    @PostMapping("/{id}/preview")
    public ApiResponse<PrintPreviewResponse> generatePreview(
            @PathVariable Long id,
            @Valid @RequestBody PrintTaskPreviewRequest request
    ) {
        // 页面点击“订单”或“装箱单”后，按所选类型生成 PDF 预览。
        return ApiResponse.ok(printTaskService.generateTaskPreview(id, PrintType.parse(request.getPrintType())));
    }

    @PostMapping("/{id}/preview/regenerate")
    public ApiResponse<PrintPreviewResponse> regeneratePreview(
            @PathVariable Long id,
            @Valid @RequestBody PrintTaskPreviewRequest request
    ) {
        // 页面点“重新生成”时，先删除旧 PDF 并清空订单上的 PDF 路径，再重新生成。
        return ApiResponse.ok(printTaskService.regenerateTaskPreview(id, PrintType.parse(request.getPrintType())));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<PrintTaskResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody PrintTaskStatusUpdateRequest request
    ) {
        // 预留给本地打印代理回写 PRINTING/SUCCESS/FAILED 等状态。
        return ApiResponse.ok(printTaskService.updateTaskStatus(id, request));
    }
}
