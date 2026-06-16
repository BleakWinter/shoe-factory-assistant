package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteTaskResponse;
import com.shoefactory.assistant.dto.ShippingNoteUpdateRequest;

public interface ShippingNoteTaskService {

    ShippingNoteTaskResponse createShippingNoteTask(ShippingNoteCreateRequest request);

    PageResponse<ShippingNoteTaskResponse> listShippingNoteTasks(
            String orderNo,
            String developmentNo,
            long page,
            long size
    );

    ShippingNoteTaskResponse getShippingNoteTask(Long id);

    ShippingNoteTaskResponse updateShippingNoteTask(Long id, ShippingNoteUpdateRequest request);
}
