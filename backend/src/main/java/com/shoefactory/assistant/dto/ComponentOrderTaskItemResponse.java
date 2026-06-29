package com.shoefactory.assistant.dto;

import java.util.Map;

public class ComponentOrderTaskItemResponse {

    private Long sourceDetailId;
    private Long orderId;
    private String orderNo;
    private String developmentNo;
    private String lastNo;
    private Map<String, Integer> sizeQuantities;
    private Integer quantity;
    private Integer cartonCount;
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
    public String getLastNo() { return lastNo; }
    public void setLastNo(String lastNo) { this.lastNo = lastNo; }
    public Map<String, Integer> getSizeQuantities() { return sizeQuantities; }
    public void setSizeQuantities(Map<String, Integer> sizeQuantities) { this.sizeQuantities = sizeQuantities; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getCartonCount() { return cartonCount; }
    public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
    public String getCartonStart() { return cartonStart; }
    public void setCartonStart(String cartonStart) { this.cartonStart = cartonStart; }
    public String getCartonEnd() { return cartonEnd; }
    public void setCartonEnd(String cartonEnd) { this.cartonEnd = cartonEnd; }
}
