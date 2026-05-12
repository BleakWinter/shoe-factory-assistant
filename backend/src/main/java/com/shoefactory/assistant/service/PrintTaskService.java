package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PrintTaskCreateRequest;
import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;
import com.shoefactory.assistant.enums.PrintType;

import java.util.List;

public interface PrintTaskService {

    PrintTaskResponse createTask(PrintTaskCreateRequest request);

    List<PrintTaskResponse> listTasks();

    List<PrintTaskResponse> listPendingTasks(int limit);

    PrintPreviewResponse generateTaskPreview(Long taskId, PrintType printType);

    PrintTaskResponse updateTaskStatus(Long id, PrintTaskStatusUpdateRequest request);
}
