package com.shoefactory.assistant.service;

import com.shoefactory.assistant.entity.SourceFile;

import java.nio.file.Path;

public interface OrderExcelImportService {

    OrderImportResult importOrder(Path sourceExcel, SourceFile sourceFile);
}
