package com.shoefactory.assistant.controller;

import com.shoefactory.assistant.common.ApiResponse;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;
import com.shoefactory.assistant.service.PrintTaskService;
import jakarta.validation.Valid;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/print-tasks")
public class PrintTaskController {

    private final PrintTaskService printTaskService;

    public PrintTaskController(PrintTaskService printTaskService) {
        this.printTaskService = printTaskService;
    }

    @GetMapping
    public ApiResponse<List<PrintTaskResponse>> listTasks() {
        return ApiResponse.ok(printTaskService.listTasks());
    }

    @GetMapping("/pending")
    public ApiResponse<List<PrintTaskResponse>> listPendingTasks(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ApiResponse.ok(printTaskService.listPendingTasks(limit));
    }

    @PostMapping("/{id}/preview")
    public ApiResponse<PrintPreviewResponse> generatePreview(@PathVariable Long id) {
        return ApiResponse.ok(printTaskService.generateTaskPreview(id));
    }

    @PostMapping("/{id}/preview/regenerate")
    public ApiResponse<PrintPreviewResponse> regeneratePreview(@PathVariable Long id) {
        return ApiResponse.ok(printTaskService.regenerateTaskPreview(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<UrlResource> taskPdf(@PathVariable Long id) throws MalformedURLException {
        Path pdfPath = printTaskService.loadTaskPdf(id);
        UrlResource resource = new UrlResource(pdfPath.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(fileSize(pdfPath))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(pdfPath.getFileName().toString(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    @PatchMapping("/{id}/printed")
    public ApiResponse<PrintTaskResponse> markPrinted(@PathVariable Long id) {
        return ApiResponse.ok(printTaskService.markTaskPrinted(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<PrintTaskResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody PrintTaskStatusUpdateRequest request
    ) {
        return ApiResponse.ok(printTaskService.updateTaskStatus(id, request));
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return -1;
        }
    }
}
