package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteGeneratedSummaryResponse;
import com.shoefactory.assistant.dto.ShippingNoteTaskResponse;
import com.shoefactory.assistant.dto.ShippingNoteUpdateRequest;

import java.util.List;

public interface ShippingNoteTaskService {

    ShippingNoteTaskResponse createShippingNoteTask(ShippingNoteCreateRequest request);

    PageResponse<ShippingNoteTaskResponse> listShippingNoteTasks(
            String orderNo,
            String developmentNo,
            long page,
            long size
    );

    ShippingNoteGeneratedSummaryResponse getGeneratedSummary(List<Long> orderIds);

    ShippingNoteTaskResponse getShippingNoteTask(Long id);

    ShippingNoteTaskResponse updateShippingNoteTask(Long id, ShippingNoteUpdateRequest request);
}
