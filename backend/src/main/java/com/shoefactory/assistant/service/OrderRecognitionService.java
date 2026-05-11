package com.shoefactory.assistant.service;

import java.nio.file.Path;

public interface OrderRecognitionService {

    OrderRecognitionResult recognizeExcelOrder(Path sourceExcel);
}
