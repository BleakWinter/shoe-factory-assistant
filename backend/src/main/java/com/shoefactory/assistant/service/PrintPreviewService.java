package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.enums.PrintType;

import java.nio.file.Path;

public interface PrintPreviewService {

    PrintPreviewResponse generatePreview(Long orderId, PrintType printType);

    Path loadPreviewPdf(Long previewId);
}
