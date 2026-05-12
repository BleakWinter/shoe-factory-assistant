package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public class PrintTaskPreviewRequest {

    @NotBlank
    private String printType;

    public String getPrintType() {
        return printType;
    }

    public void setPrintType(String printType) {
        this.printType = printType;
    }
}
