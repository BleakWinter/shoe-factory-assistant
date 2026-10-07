package com.shoefactory.assistant.dto;

import java.util.List;
import java.util.Map;

public class PackingOrderItemResponse {

    private Long id;
    private String key;
    private Long orderId;
    private String orderNo;
    private String developmentNo;
    private String boxSpec;
    private Long innerBoxTypeId;
    private String innerBoxTypeName;
    private String remark;
    private Integer quantity;
    private Integer cartonCount;
    private Map<String, Integer> packingCapacities;
    private List<Long> sourceDetailIds;
    private List<Long> sourcePackingDetailIds;
    private List<String> packingProblems;
    private String imageUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getDevelopmentNo() { return developmentNo; }
    public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
    public String getBoxSpec() { return boxSpec; }
    public void setBoxSpec(String boxSpec) { this.boxSpec = boxSpec; }
    public Long getInnerBoxTypeId() { return innerBoxTypeId; }
    public void setInnerBoxTypeId(Long innerBoxTypeId) { this.innerBoxTypeId = innerBoxTypeId; }
    public String getInnerBoxTypeName() { return innerBoxTypeName; }
    public void setInnerBoxTypeName(String innerBoxTypeName) { this.innerBoxTypeName = innerBoxTypeName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getCartonCount() { return cartonCount; }
    public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
    public Map<String, Integer> getPackingCapacities() { return packingCapacities; }
    public void setPackingCapacities(Map<String, Integer> packingCapacities) { this.packingCapacities = packingCapacities; }
    public List<Long> getSourceDetailIds() { return sourceDetailIds; }
    public void setSourceDetailIds(List<Long> sourceDetailIds) { this.sourceDetailIds = sourceDetailIds; }
    public List<Long> getSourcePackingDetailIds() { return sourcePackingDetailIds; }
    public void setSourcePackingDetailIds(List<Long> sourcePackingDetailIds) { this.sourcePackingDetailIds = sourcePackingDetailIds; }
    public List<String> getPackingProblems() { return packingProblems; }
    public void setPackingProblems(List<String> packingProblems) { this.packingProblems = packingProblems; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
