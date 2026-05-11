package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.SourceFile;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class OrderRecordResponse {

    private Long id;
    private String orderNo;
    private String customerName;
    private String styleNo;
    private String color;
    private Integer quantity;
    private Integer cartonCount;
    private LocalDate deliveryDate;
    private String recognitionStatus;
    private String errorMessage;
    private Long sourceFileId;
    private String sourceFileName;
    private String sourceFileType;
    private String sourceSheetName;
    private LocalDateTime createdAt;

    public static OrderRecordResponse from(OrderRecord order, SourceFile sourceFile) {
        OrderRecordResponse response = new OrderRecordResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setCustomerName(order.getCustomerName());
        response.setStyleNo(order.getStyleNo());
        response.setColor(order.getColor());
        response.setQuantity(order.getQuantity());
        response.setCartonCount(order.getCartonCount());
        response.setDeliveryDate(order.getDeliveryDate());
        response.setRecognitionStatus(order.getRecognitionStatus());
        response.setErrorMessage(order.getErrorMessage());
        response.setSourceFileId(order.getSourceFileId());
        response.setSourceSheetName(order.getSourceSheetName());
        response.setCreatedAt(order.getCreatedAt());
        if (sourceFile != null) {
            response.setSourceFileName(sourceFile.getOriginalName());
            response.setSourceFileType(sourceFile.getFileType());
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getStyleNo() {
        return styleNo;
    }

    public void setStyleNo(String styleNo) {
        this.styleNo = styleNo;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getCartonCount() {
        return cartonCount;
    }

    public void setCartonCount(Integer cartonCount) {
        this.cartonCount = cartonCount;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getRecognitionStatus() {
        return recognitionStatus;
    }

    public void setRecognitionStatus(String recognitionStatus) {
        this.recognitionStatus = recognitionStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(Long sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getSourceFileType() {
        return sourceFileType;
    }

    public void setSourceFileType(String sourceFileType) {
        this.sourceFileType = sourceFileType;
    }

    public String getSourceSheetName() {
        return sourceSheetName;
    }

    public void setSourceSheetName(String sourceSheetName) {
        this.sourceSheetName = sourceSheetName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
