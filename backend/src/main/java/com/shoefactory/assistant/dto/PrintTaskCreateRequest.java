package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PrintTaskCreateRequest {

    // 已经生成好的 PDF 预览 id。
    @NotNull
    private Long previewId;
    // 后续接入真实打印机时使用；当前可以为空。
    private String printerName;
    // 打印份数，接口层限制在 1 到 99。
    @Min(1)
    @Max(99)
    private Integer copies = 1;
    // 打印优先级，数字越大越靠前。
    private Integer priority = 0;

    public Long getPreviewId() {
        return previewId;
    }

    public void setPreviewId(Long previewId) {
        this.previewId = previewId;
    }

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public Integer getCopies() {
        return copies;
    }

    public void setCopies(Integer copies) {
        this.copies = copies;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }
}
