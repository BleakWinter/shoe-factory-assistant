package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class ShippingNoteItemRequest {

    @NotNull
    private Long sourceDetailId;
    private Long orderId;
    private String orderNo;
    private String developmentNo;
    private String customerName;
    private String customerStyleNo;
    private String englishColor;
    private String englishMaterial;
    private String colorMaterial;
    private String trademark;
    private Map<String, Integer> sizeQuantities;
    private Integer pairCount;
    private Integer cartonCount;
    private Integer totalPairs;
    private String cartonStart;
    private String cartonEnd;

    public Long getSourceDetailId() { return sourceDetailId; }
    public void setSourceDetailId(Long sourceDetailId) { this.sourceDetailId = sourceDetailId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getDevelopmentNo() { return developmentNo; }
    public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerStyleNo() { return customerStyleNo; }
    public void setCustomerStyleNo(String customerStyleNo) { this.customerStyleNo = customerStyleNo; }
    public String getEnglishColor() { return englishColor; }
    public void setEnglishColor(String englishColor) { this.englishColor = englishColor; }
    public String getEnglishMaterial() { return englishMaterial; }
    public void setEnglishMaterial(String englishMaterial) { this.englishMaterial = englishMaterial; }
    public String getColorMaterial() { return colorMaterial; }
    public void setColorMaterial(String colorMaterial) { this.colorMaterial = colorMaterial; }
    public String getTrademark() { return trademark; }
    public void setTrademark(String trademark) { this.trademark = trademark; }
    public Map<String, Integer> getSizeQuantities() { return sizeQuantities; }
    public void setSizeQuantities(Map<String, Integer> sizeQuantities) { this.sizeQuantities = sizeQuantities; }
    public Integer getPairCount() { return pairCount; }
    public void setPairCount(Integer pairCount) { this.pairCount = pairCount; }
    public Integer getCartonCount() { return cartonCount; }
    public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
    public Integer getTotalPairs() { return totalPairs; }
    public void setTotalPairs(Integer totalPairs) { this.totalPairs = totalPairs; }
    public String getCartonStart() { return cartonStart; }
    public void setCartonStart(String cartonStart) { this.cartonStart = cartonStart; }
    public String getCartonEnd() { return cartonEnd; }
    public void setCartonEnd(String cartonEnd) { this.cartonEnd = cartonEnd; }
}
