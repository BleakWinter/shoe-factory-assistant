package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PrintTaskCreateRequest;
import com.shoefactory.assistant.dto.PrintTaskResponse;
import com.shoefactory.assistant.dto.PrintTaskStatusUpdateRequest;

import java.util.List;

public interface PrintTaskService {

    PrintTaskResponse createTask(PrintTaskCreateRequest request);

    List<PrintTaskResponse> listPendingTasks(int limit);

    PrintTaskResponse updateTaskStatus(Long id, PrintTaskStatusUpdateRequest request);
}
