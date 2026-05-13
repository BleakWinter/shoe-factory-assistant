package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public class PrintTaskPreviewRequest {

    // ORDER 表示订单 sheet，PACKING 表示装箱单 sheet。
    @NotBlank
    private String printType;

    public String getPrintType() {
        return printType;
    }

    public void setPrintType(String printType) {
        this.printType = printType;
    }
}
