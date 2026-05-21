package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.entity.OrderSheetPrintTask;
import com.shoefactory.assistant.enums.PrintTaskStatus;
import com.shoefactory.assistant.enums.PrintType;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class PrintTaskResponse {

    private Long id;
    private String taskNo;
    private Long orderId;
    private String orderNo;
    private String customerName;
    private String originalFileName;
    private List<String> styleNos;
    private Integer totalPairs;
    private String printType;
    private String printTypeText;
    private String status;
    private String statusText;
    private String previewUrl;
    private Integer printCount;
    private LocalDateTime pdfGeneratedAt;
    private LocalDateTime lastPrintTime;
    private String errorMessage;
    private LocalDateTime createdAt;

    public static PrintTaskResponse fromTask(OrderSheetPrintTask task, OrderRecord order) {
        PrintTaskResponse response = new PrintTaskResponse();
        if (task == null) {
            return response;
        }
        PrintType type = PrintType.fromCode(task.getPrintType());
        PrintTaskStatus status = PrintTaskStatus.fromCode(task.getStatus());
        response.setId(task.getId());
        response.setTaskNo(buildTaskNo(task, order, type));
        response.setOrderId(task.getOrderId());
        response.setOriginalFileName(task.getOriginalFileName());
        response.setPrintType(type.name());
        response.setPrintTypeText(type.getLabel());
        response.setStatus(status.name());
        response.setStatusText(status.getLabel());
        response.setPrintCount(task.getPrintCount() == null ? 0 : task.getPrintCount());
        response.setPdfGeneratedAt(task.getPdfGeneratedAt());
        response.setLastPrintTime(task.getLastPrintTime());
        response.setErrorMessage(task.getErrorMessage());
        response.setCreatedAt(task.getCreatedAt());
        if (task.getPreviewPdfPath() != null && !task.getPreviewPdfPath().isBlank()) {
            response.setPreviewUrl("/api/print-tasks/" + task.getId() + "/pdf");
        }
        if (order != null) {
            response.setOrderNo(order.getOrderNo());
            response.setCustomerName(order.getCustomerName());
            response.setStyleNos(splitDevelopmentNos(order.getDevelopmentNos()));
            response.setTotalPairs(order.getTotalQuantity());
        }
        return response;
    }

    private static String buildTaskNo(OrderSheetPrintTask task, OrderRecord order, PrintType type) {
        String orderNo = order == null ? null : order.getOrderNo();
        String base = orderNo == null || orderNo.isBlank() ? String.valueOf(task.getOrderId()) : orderNo;
        return base + "-" + type.getCode();
    }

    private static List<String> splitDevelopmentNos(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
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

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
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

    public String getPrintType() {
        return printType;
    }

    public void setPrintType(String printType) {
        this.printType = printType;
    }

    public String getPrintTypeText() {
        return printTypeText;
    }

    public void setPrintTypeText(String printTypeText) {
        this.printTypeText = printTypeText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public Integer getPrintCount() {
        return printCount;
    }

    public void setPrintCount(Integer printCount) {
        this.printCount = printCount;
    }

    public LocalDateTime getPdfGeneratedAt() {
        return pdfGeneratedAt;
    }

    public void setPdfGeneratedAt(LocalDateTime pdfGeneratedAt) {
        this.pdfGeneratedAt = pdfGeneratedAt;
    }

    public LocalDateTime getLastPrintTime() {
        return lastPrintTime;
    }

    public void setLastPrintTime(LocalDateTime lastPrintTime) {
        this.lastPrintTime = lastPrintTime;
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
