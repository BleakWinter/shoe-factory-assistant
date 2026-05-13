package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.OrderRecord;
import com.shoefactory.assistant.enums.OrderRecognitionStatus;
import com.shoefactory.assistant.enums.OrderSourceType;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class OrderRecordResponse {

    // 订单主表响应，对应页面上的订单列表和打印列表。
    private Long id;
    private String orderNo;
    private String customerName;
    private String originalFileName;
    private String boxImageUrl;
    private String developmentNos;
    private List<String> developmentNoList;
    private Boolean orderPrinted;
    private Boolean packingPrinted;
    private String orderPdfPath;
    private String packingPdfPath;
    private LocalDateTime orderPdfGeneratedAt;
    private LocalDateTime packingPdfGeneratedAt;
    private Integer totalQuantity;
    private Integer totalCartonCount;
    private Integer sourceType;
    private String sourceTypeText;
    private Integer recognitionStatus;
    private String recognitionStatusText;
    private String remark;
    private String errorMessage;
    private LocalDateTime createdAt;

    public static OrderRecordResponse from(OrderRecord order) {
        OrderRecordResponse response = new OrderRecordResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setCustomerName(order.getCustomerName());
        response.setOriginalFileName(order.getOriginalFileName());
        response.setBoxImageUrl(order.getBoxImageUrl());
        response.setDevelopmentNos(order.getDevelopmentNos());
        response.setDevelopmentNoList(splitDevelopmentNos(order.getDevelopmentNos()));
        response.setOrderPrinted(order.getOrderPrinted());
        response.setPackingPrinted(order.getPackingPrinted());
        response.setOrderPdfPath(order.getOrderPdfPath());
        response.setPackingPdfPath(order.getPackingPdfPath());
        response.setOrderPdfGeneratedAt(order.getOrderPdfGeneratedAt());
        response.setPackingPdfGeneratedAt(order.getPackingPdfGeneratedAt());
        response.setTotalQuantity(order.getTotalQuantity());
        response.setTotalCartonCount(order.getTotalCartonCount());
        response.setSourceType(order.getSourceType());
        OrderSourceType sourceType = OrderSourceType.fromCode(order.getSourceType());
        response.setSourceTypeText(sourceType == null ? null : sourceType.getLabel());
        response.setRecognitionStatus(order.getRecognitionStatus());
        OrderRecognitionStatus status = OrderRecognitionStatus.fromCode(order.getRecognitionStatus());
        response.setRecognitionStatusText(status == null ? null : status.getLabel());
        response.setRemark(order.getRemark());
        response.setErrorMessage(order.getErrorMessage());
        response.setCreatedAt(order.getCreatedAt());
        return response;
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

    public String getBoxImageUrl() {
        return boxImageUrl;
    }

    public void setBoxImageUrl(String boxImageUrl) {
        this.boxImageUrl = boxImageUrl;
    }

    public String getDevelopmentNos() {
        return developmentNos;
    }

    public void setDevelopmentNos(String developmentNos) {
        this.developmentNos = developmentNos;
    }

    public List<String> getDevelopmentNoList() {
        return developmentNoList;
    }

    public void setDevelopmentNoList(List<String> developmentNoList) {
        this.developmentNoList = developmentNoList;
    }

    public Boolean getOrderPrinted() {
        return orderPrinted;
    }

    public void setOrderPrinted(Boolean orderPrinted) {
        this.orderPrinted = orderPrinted;
    }

    public Boolean getPackingPrinted() {
        return packingPrinted;
    }

    public void setPackingPrinted(Boolean packingPrinted) {
        this.packingPrinted = packingPrinted;
    }

    public String getOrderPdfPath() {
        return orderPdfPath;
    }

    public void setOrderPdfPath(String orderPdfPath) {
        this.orderPdfPath = orderPdfPath;
    }

    public String getPackingPdfPath() {
        return packingPdfPath;
    }

    public void setPackingPdfPath(String packingPdfPath) {
        this.packingPdfPath = packingPdfPath;
    }

    public LocalDateTime getOrderPdfGeneratedAt() {
        return orderPdfGeneratedAt;
    }

    public void setOrderPdfGeneratedAt(LocalDateTime orderPdfGeneratedAt) {
        this.orderPdfGeneratedAt = orderPdfGeneratedAt;
    }

    public LocalDateTime getPackingPdfGeneratedAt() {
        return packingPdfGeneratedAt;
    }

    public void setPackingPdfGeneratedAt(LocalDateTime packingPdfGeneratedAt) {
        this.packingPdfGeneratedAt = packingPdfGeneratedAt;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getTotalCartonCount() {
        return totalCartonCount;
    }

    public void setTotalCartonCount(Integer totalCartonCount) {
        this.totalCartonCount = totalCartonCount;
    }

    public Integer getSourceType() {
        return sourceType;
    }

    public void setSourceType(Integer sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceTypeText() {
        return sourceTypeText;
    }

    public void setSourceTypeText(String sourceTypeText) {
        this.sourceTypeText = sourceTypeText;
    }

    public Integer getRecognitionStatus() {
        return recognitionStatus;
    }

    public void setRecognitionStatus(Integer recognitionStatus) {
        this.recognitionStatus = recognitionStatus;
    }

    public String getRecognitionStatusText() {
        return recognitionStatusText;
    }

    public void setRecognitionStatusText(String recognitionStatusText) {
        this.recognitionStatusText = recognitionStatusText;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
