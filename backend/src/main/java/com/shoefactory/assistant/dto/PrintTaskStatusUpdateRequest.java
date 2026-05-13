package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotBlank;

public class PrintTaskStatusUpdateRequest {

    // PENDING/PRINTING/SUCCESS/FAILED/CANCELED。
    @NotBlank
    private String status;
    // 失败或取消时可写原因。
    private String errorMessage;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
