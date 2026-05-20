package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.entity.OrderSheetPrintTask;

import java.nio.file.Path;

public interface PrintPreviewService {

    PrintPreviewResponse generatePreview(OrderSheetPrintTask task);

    PrintPreviewResponse regeneratePreview(OrderSheetPrintTask task);

    Path loadTaskPdf(OrderSheetPrintTask task);
}
