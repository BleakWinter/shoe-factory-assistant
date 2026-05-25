package com.shoefactory.assistant.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.entity.OrderPackingDetail;
import com.shoefactory.assistant.util.CustomerNameUtil;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

public class OrderPackingDetailResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };

    private Long id;
    private Long orderId;
    private Integer lineNo;
    private String imageUrl;
    private String companyStyleNo;
    private String customerName;
    private String customerOrderNo;
    private String warehouseStoreNo;
    private String poNo;
    private String customerStyleNo;
    private String customerColor;
    private String material;
    private String itemNumber;
    private String trademark;
    private Map<String, Integer> sizeQuantities;
    private Integer cartonCount;
    private Integer totalPairs;
    private String cartonStart;
    private String cartonEnd;
    private String sourceSheetName;
    private Integer rowIndex;
    private String remark;
    private LocalDateTime createdAt;

    public static OrderPackingDetailResponse from(OrderPackingDetail detail) {
        OrderPackingDetailResponse response = new OrderPackingDetailResponse();
        response.setId(detail.getId());
        response.setOrderId(detail.getOrderId());
        response.setLineNo(detail.getLineNo());
        response.setImageUrl(detail.getStyleImagePath() == null ? null : "/api/orders/packing-details/" + detail.getId() + "/image");
        response.setCompanyStyleNo(detail.getCompanyStyleNo());
        response.setCustomerName(CustomerNameUtil.normalizeWithoutChinese(detail.getCustomerName()));
        response.setCustomerOrderNo(detail.getCustomerOrderNo());
        response.setWarehouseStoreNo(detail.getWarehouseStoreNo());
        response.setPoNo(detail.getPoNo());
        response.setCustomerStyleNo(detail.getCustomerStyleNo());
        response.setCustomerColor(detail.getCustomerColor());
        response.setMaterial(detail.getMaterial());
        response.setItemNumber(detail.getItemNumber());
        response.setTrademark(detail.getTrademark());
        response.setSizeQuantities(parseSizeQuantities(detail.getSizeQuantitiesJson()));
        response.setCartonCount(detail.getCartonCount());
        response.setTotalPairs(detail.getTotalPairs());
        response.setCartonStart(detail.getCartonStart());
        response.setCartonEnd(detail.getCartonEnd());
        response.setSourceSheetName(detail.getSourceSheetName());
        response.setRowIndex(detail.getRowIndex());
        response.setRemark(detail.getRemark());
        response.setCreatedAt(detail.getCreatedAt());
        return response;
    }

    private static Map<String, Integer> parseSizeQuantities(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, SIZE_MAP_TYPE);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getCompanyStyleNo() { return companyStyleNo; }
    public void setCompanyStyleNo(String companyStyleNo) { this.companyStyleNo = companyStyleNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerOrderNo() { return customerOrderNo; }
    public void setCustomerOrderNo(String customerOrderNo) { this.customerOrderNo = customerOrderNo; }
    public String getWarehouseStoreNo() { return warehouseStoreNo; }
    public void setWarehouseStoreNo(String warehouseStoreNo) { this.warehouseStoreNo = warehouseStoreNo; }
    public String getPoNo() { return poNo; }
    public void setPoNo(String poNo) { this.poNo = poNo; }
    public String getCustomerStyleNo() { return customerStyleNo; }
    public void setCustomerStyleNo(String customerStyleNo) { this.customerStyleNo = customerStyleNo; }
    public String getCustomerColor() { return customerColor; }
    public void setCustomerColor(String customerColor) { this.customerColor = customerColor; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public String getItemNumber() { return itemNumber; }
    public void setItemNumber(String itemNumber) { this.itemNumber = itemNumber; }
    public String getTrademark() { return trademark; }
    public void setTrademark(String trademark) { this.trademark = trademark; }
    public Map<String, Integer> getSizeQuantities() { return sizeQuantities; }
    public void setSizeQuantities(Map<String, Integer> sizeQuantities) { this.sizeQuantities = sizeQuantities; }
    public Integer getCartonCount() { return cartonCount; }
    public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
    public Integer getTotalPairs() { return totalPairs; }
    public void setTotalPairs(Integer totalPairs) { this.totalPairs = totalPairs; }
    public String getCartonStart() { return cartonStart; }
    public void setCartonStart(String cartonStart) { this.cartonStart = cartonStart; }
    public String getCartonEnd() { return cartonEnd; }
    public void setCartonEnd(String cartonEnd) { this.cartonEnd = cartonEnd; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public Integer getRowIndex() { return rowIndex; }
    public void setRowIndex(Integer rowIndex) { this.rowIndex = rowIndex; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
