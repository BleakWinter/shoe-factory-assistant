package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.entity.OrderPrintTask;

import java.nio.file.Path;

public interface PrintPreviewService {

    PrintPreviewResponse generatePreview(OrderPrintTask task);

    PrintPreviewResponse regeneratePreview(OrderPrintTask task);

    Path loadTaskPdf(OrderPrintTask task);
}
