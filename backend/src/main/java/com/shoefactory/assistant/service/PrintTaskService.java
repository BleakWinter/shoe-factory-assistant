package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;

import java.nio.file.Path;
import java.util.List;

public interface PrintTaskService {

    List<PrintTaskResponse> listTasks();

    List<PrintTaskResponse> listPendingTasks(int limit);

    PrintPreviewResponse generateTaskPreview(Long taskId);

    PrintPreviewResponse regenerateTaskPreview(Long taskId);

    PrintTaskResponse markTaskPrinted(Long id);

    PrintTaskResponse updateTaskStatus(Long id, PrintTaskStatusUpdateRequest request);

    Path loadTaskPdf(Long id);
}
