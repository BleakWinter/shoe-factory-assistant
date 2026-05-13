package com.shoefactory.assistant.enums;

public enum PrintPreviewStatus {
    // PDF 已生成，可以通过 previewUrl 打开。
    READY,
    // PDF 生成失败，error_message 会记录 LibreOffice/Excel 的错误。
    FAILED
}
