package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.PrintPreview;
import com.shoefactory.assistant.entity.PrintTask;

import java.time.LocalDateTime;
import java.util.List;

public class PrintTaskResponse {

    private Long id;
    private String taskNo;
    private Long orderId;
    private String orderNo;
    private String customerName;
    private List<String> styleNos;
    private Integer totalPairs;
    private Long previewId;
    private String previewUrl;
    private String printType;
    private String printerName;
    private Integer copies;
    private String status;
    private Integer priority;
    private String errorMessage;
    private LocalDateTime pickedAt;
    private LocalDateTime printedAt;
    private LocalDateTime createdAt;

    public static PrintTaskResponse from(PrintTask task, OrderRecord order, PrintPreview preview) {
        PrintTaskResponse response = new PrintTaskResponse();
        response.setId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setOrderId(task.getOrderId());
        response.setPreviewId(task.getPreviewId());
        response.setPrintType(task.getPrintType());
        response.setPrinterName(task.getPrinterName());
        response.setCopies(task.getCopies());
        response.setStatus(task.getStatus());
        response.setPriority(task.getPriority());
        response.setErrorMessage(task.getErrorMessage());
        response.setPickedAt(task.getPickedAt());
        response.setPrintedAt(task.getPrintedAt());
        response.setCreatedAt(task.getCreatedAt());
        if (order != null) {
            response.setOrderNo(order.getOrderNo());
            response.setCustomerName(order.getCustomerName());
            response.setTotalPairs(order.getQuantity());
        }
        if (preview != null) {
            response.setPreviewUrl(preview.getPreviewUrl());
        }
        return response;
    }

    public static PrintTaskResponse from(PrintTask task, OrderRecord order, PrintPreview preview, List<String> styleNos) {
        PrintTaskResponse response = from(task, order, preview);
        response.setStyleNos(styleNos);
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
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

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public List<String> getStyleNos() {
        return styleNos;
    }

    public void setStyleNos(List<String> styleNos) {
        this.styleNos = styleNos;
    }

    public Integer getTotalPairs() {
        return totalPairs;
    }

    public void setTotalPairs(Integer totalPairs) {
        this.totalPairs = totalPairs;
    }

    public Long getPreviewId() {
        return previewId;
    }

    public void setPreviewId(Long previewId) {
        this.previewId = previewId;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getPrintType() {
        return printType;
    }

    public void setPrintType(String printType) {
        this.printType = printType;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getPickedAt() {
        return pickedAt;
    }

    public void setPickedAt(LocalDateTime pickedAt) {
        this.pickedAt = pickedAt;
    }

    public LocalDateTime getPrintedAt() {
        return printedAt;
    }

    public void setPrintedAt(LocalDateTime printedAt) {
        this.printedAt = printedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
