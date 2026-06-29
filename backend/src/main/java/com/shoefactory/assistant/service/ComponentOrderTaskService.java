package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.ComponentOrderCreateRequest;
import com.shoefactory.assistant.dto.ComponentOrderTaskResponse;
import com.shoefactory.assistant.dto.PageResponse;

public interface ComponentOrderTaskService {

    ComponentOrderTaskResponse createComponentOrderTask(ComponentOrderCreateRequest request);

    PageResponse<ComponentOrderTaskResponse> listComponentOrderTasks(
            Integer processType,
            String orderNo,
            String developmentNo,
            long page,
            long size
    );

    ComponentOrderTaskResponse getComponentOrderTask(Long id);
}
