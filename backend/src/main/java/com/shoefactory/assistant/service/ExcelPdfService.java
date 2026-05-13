package com.shoefactory.assistant.service;

import java.nio.file.Path;

public interface ExcelPdfService {

    // 把 Excel 指定 sheet 单独导出为 PDF。
    Path convertSheetToPdf(Path sourceExcel, Path targetPdf, String sheetName);
}
