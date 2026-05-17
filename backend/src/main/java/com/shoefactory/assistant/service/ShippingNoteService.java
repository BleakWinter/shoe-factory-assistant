package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PageResponse;
import com.shoefactory.assistant.dto.ShippingNoteCreateRequest;
import com.shoefactory.assistant.dto.ShippingNoteRecordResponse;

public interface ShippingNoteService {

    ShippingNoteRecordResponse createShippingNote(ShippingNoteCreateRequest request);

    PageResponse<ShippingNoteRecordResponse> listShippingNotes(
            String orderNo,
            String developmentNo,
            long page,
            long size
    );
}
