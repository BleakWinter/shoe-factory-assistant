package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("order_sheet_print_task")
public class OrderSheetPrintTask {

    // 只表示订单 Excel 里的整单级 sheet 打印：订单、装箱单。
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Integer printType;
    private String originalFileName;
    private String originalFilePath;
    private Integer status;
    private String previewPdfPath;
    private LocalDateTime pdfGeneratedAt;
    private Integer printCount;
    private LocalDateTime lastPrintTime;
    private String lastPrintUser;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Integer getPrintType() {
        return printType;
    }

    public void setPrintType(Integer printType) {
        this.printType = printType;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getOriginalFilePath() {
        return originalFilePath;
    }

    public void setOriginalFilePath(String originalFilePath) {
        this.originalFilePath = originalFilePath;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getPreviewPdfPath() {
        return previewPdfPath;
    }

    public void setPreviewPdfPath(String previewPdfPath) {
        this.previewPdfPath = previewPdfPath;
    }

    public LocalDateTime getPdfGeneratedAt() {
        return pdfGeneratedAt;
    }

    public void setPdfGeneratedAt(LocalDateTime pdfGeneratedAt) {
        this.pdfGeneratedAt = pdfGeneratedAt;
    }

    public Integer getPrintCount() {
        return printCount;
    }

    public void setPrintCount(Integer printCount) {
        this.printCount = printCount;
    }

    public LocalDateTime getLastPrintTime() {
        return lastPrintTime;
    }

    public void setLastPrintTime(LocalDateTime lastPrintTime) {
        this.lastPrintTime = lastPrintTime;
    }

    public String getLastPrintUser() {
        return lastPrintUser;
    }

    public void setLastPrintUser(String lastPrintUser) {
        this.lastPrintUser = lastPrintUser;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
