package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.enums.PrintTaskStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class PrintTaskResponse {

    // 打印列表现在直接以 order_record 为数据源，id 就是订单 id。
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

    public static PrintTaskResponse fromOrder(OrderRecord order) {
        PrintTaskResponse response = new PrintTaskResponse();
        if (order == null) {
            return response;
        }
        response.setId(order.getId());
        response.setTaskNo(order.getOrderNo());
        response.setOrderId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setCustomerName(order.getCustomerName());
        response.setStyleNos(splitDevelopmentNos(order.getDevelopmentNos()));
        response.setTotalPairs(order.getTotalQuantity());
        response.setStatus(resolveStatus(order));
        response.setPriority(0);
        response.setErrorMessage(order.getErrorMessage());
        response.setCreatedAt(order.getCreatedAt());
        return response;
    }

    private static String resolveStatus(OrderRecord order) {
        return Boolean.TRUE.equals(order.getOrderPrinted()) && Boolean.TRUE.equals(order.getPackingPrinted())
                ? PrintTaskStatus.SUCCESS.name()
                : PrintTaskStatus.PENDING.name();
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
