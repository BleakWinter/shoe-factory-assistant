package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public class PrintPreviewCreateRequest {

    // 早期按订单直接生成预览的请求体字段。
    @NotBlank
    private String printType;

    public String getPrintType() {
        return printType;
    }

    public void setPrintType(String printType) {
        this.printType = printType;
    }
}
