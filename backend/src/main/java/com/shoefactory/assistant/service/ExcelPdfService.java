package com.shoefactory.assistant.service;

import java.nio.file.Path;

public interface ExcelPdfService {

    Path convertSheetToPdf(Path sourceExcel, Path targetPdf, String sheetName);
}
