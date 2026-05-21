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
    private String boxImageUrl;
    private String developmentNos;
    private List<String> developmentNoList;
    private Integer totalQuantity;
    private Integer totalCartonCount;
    private Integer sourceType;
    private String sourceTypeText;
    private Integer recognitionStatus;
    private String recognitionStatusText;
    private Integer orderRecognitionStatus;
    private String orderRecognitionStatusText;
    private Integer packingRecognitionStatus;
    private String packingRecognitionStatusText;
    private String remark;
    private String errorMessage;
    private String orderErrorMessage;
    private String packingErrorMessage;
    private LocalDateTime createdAt;

    public static OrderRecordResponse from(OrderRecord order) {
        OrderRecordResponse response = new OrderRecordResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setCustomerName(order.getCustomerName());
        response.setBoxImageUrl(order.getBoxImageUrl());
        response.setDevelopmentNos(order.getDevelopmentNos());
        response.setDevelopmentNoList(splitDevelopmentNos(order.getDevelopmentNos()));
        response.setTotalQuantity(order.getTotalQuantity());
        response.setTotalCartonCount(order.getTotalCartonCount());
        response.setSourceType(order.getSourceType());
        OrderSourceType sourceType = OrderSourceType.fromCode(order.getSourceType());
        response.setSourceTypeText(sourceType == null ? null : sourceType.getLabel());
        response.setRecognitionStatus(order.getRecognitionStatus());
        OrderRecognitionStatus status = OrderRecognitionStatus.fromCode(order.getRecognitionStatus());
        response.setRecognitionStatusText(status == null ? null : status.getLabel());
        response.setOrderRecognitionStatus(order.getOrderRecognitionStatus());
        OrderRecognitionStatus orderStatus = OrderRecognitionStatus.fromCode(order.getOrderRecognitionStatus());
        response.setOrderRecognitionStatusText(orderStatus == null ? null : orderStatus.getLabel());
        response.setPackingRecognitionStatus(order.getPackingRecognitionStatus());
        OrderRecognitionStatus packingStatus = OrderRecognitionStatus.fromCode(order.getPackingRecognitionStatus());
        response.setPackingRecognitionStatusText(packingStatus == null ? null : packingStatus.getLabel());
        response.setRemark(order.getRemark());
        response.setErrorMessage(order.getErrorMessage());
        response.setOrderErrorMessage(order.getOrderErrorMessage());
        response.setPackingErrorMessage(order.getPackingErrorMessage());
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

    public Integer getOrderRecognitionStatus() {
        return orderRecognitionStatus;
    }

    public void setOrderRecognitionStatus(Integer orderRecognitionStatus) {
        this.orderRecognitionStatus = orderRecognitionStatus;
    }

    public String getOrderRecognitionStatusText() {
        return orderRecognitionStatusText;
    }

    public void setOrderRecognitionStatusText(String orderRecognitionStatusText) {
        this.orderRecognitionStatusText = orderRecognitionStatusText;
    }

    public Integer getPackingRecognitionStatus() {
        return packingRecognitionStatus;
    }

    public void setPackingRecognitionStatus(Integer packingRecognitionStatus) {
        this.packingRecognitionStatus = packingRecognitionStatus;
    }

    public String getPackingRecognitionStatusText() {
        return packingRecognitionStatusText;
    }

    public void setPackingRecognitionStatusText(String packingRecognitionStatusText) {
        this.packingRecognitionStatusText = packingRecognitionStatusText;
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

    public String getOrderErrorMessage() {
        return orderErrorMessage;
    }

    public void setOrderErrorMessage(String orderErrorMessage) {
        this.orderErrorMessage = orderErrorMessage;
    }

    public String getPackingErrorMessage() {
        return packingErrorMessage;
    }

    public void setPackingErrorMessage(String packingErrorMessage) {
        this.packingErrorMessage = packingErrorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
