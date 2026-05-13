package com.shoefactory.assistant.service;

import com.shoefactory.assistant.dto.PrintPreviewResponse;
import com.shoefactory.assistant.enums.PrintType;

import java.nio.file.Path;

public interface PrintPreviewService {

    // 按打印类型生成 PDF 预览，printType 决定取“订单”还是“装箱单”sheet。
    PrintPreviewResponse generatePreview(Long orderId, PrintType printType);

    // 删除已有 PDF、清空订单表里的 PDF 路径，再重新生成预览。
    PrintPreviewResponse regeneratePreview(Long orderId, PrintType printType);

    // 读取已经生成好的 PDF 文件。
    Path loadPreviewPdf(Long previewId);

    // 按订单和打印类型读取已生成并保存到 order_record 的 PDF。
    Path loadOrderPdf(Long orderId, PrintType printType);
}
