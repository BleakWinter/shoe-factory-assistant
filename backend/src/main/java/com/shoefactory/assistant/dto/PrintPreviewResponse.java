package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.PrintPreview;

import java.time.LocalDateTime;

public class PrintPreviewResponse {

    private Long id;
    private String previewNo;
    private Long orderId;
    private String orderNo;
    private String printType;
    private String previewUrl;
    private Long pdfSize;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;

    public static PrintPreviewResponse from(PrintPreview preview, OrderRecord order) {
        PrintPreviewResponse response = new PrintPreviewResponse();
        response.setId(preview.getId());
        response.setPreviewNo(preview.getPreviewNo());
        response.setOrderId(preview.getOrderId());
        response.setPrintType(preview.getPrintType());
        response.setPreviewUrl(preview.getPreviewUrl());
        response.setPdfSize(preview.getPdfSize());
        response.setStatus(preview.getStatus());
        response.setErrorMessage(preview.getErrorMessage());
        response.setCreatedAt(preview.getCreatedAt());
        if (order != null) {
            response.setOrderNo(order.getOrderNo());
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPreviewNo() {
        return previewNo;
    }

    public void setPreviewNo(String previewNo) {
        this.previewNo = previewNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPrintType() {
        return printType;
    }

    public void setPrintType(String printType) {
        this.printType = printType;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public Long getPdfSize() {
        return pdfSize;
    }

    public void setPdfSize(Long pdfSize) {
        this.pdfSize = pdfSize;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
