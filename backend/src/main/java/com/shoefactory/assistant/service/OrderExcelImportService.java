package com.shoefactory.assistant.service;

import com.shoefactory.assistant.util.StoredFile;

import java.nio.file.Path;

public interface OrderExcelImportService {

    // 从 Excel 的“订单”sheet 解析订单主记录、明细行和内嵌图片。
    OrderImportResult importOrder(Path sourceExcel, StoredFile storedFile, String fileNo);
}
