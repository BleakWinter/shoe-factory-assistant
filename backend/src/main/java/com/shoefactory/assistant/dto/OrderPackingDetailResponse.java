package com.shoefactory.assistant.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.entity.OrderPackingDetail;

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
    private Integer pairs;
    private Integer cartonCount;
    private Integer totalPairs;
    private String cartonStart;
    private String cartonEnd;
    private String lengthValue;
    private String widthValue;
    private String heightValue;
    private String netWeight;
    private String grossWeight;
    private String measurement;
    private String totalNetWeight;
    private String totalGrossWeight;
    private String totalCbm;
    private String gender;
    private String productType;
    private String upperMaterial;
    private String soleMaterial;
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
        response.setCustomerName(detail.getCustomerName());
        response.setCustomerOrderNo(detail.getCustomerOrderNo());
        response.setWarehouseStoreNo(detail.getWarehouseStoreNo());
        response.setPoNo(detail.getPoNo());
        response.setCustomerStyleNo(detail.getCustomerStyleNo());
        response.setCustomerColor(detail.getCustomerColor());
        response.setMaterial(detail.getMaterial());
        response.setItemNumber(detail.getItemNumber());
        response.setTrademark(detail.getTrademark());
        response.setSizeQuantities(parseSizeQuantities(detail.getSizeQuantitiesJson()));
        response.setPairs(detail.getPairs());
        response.setCartonCount(detail.getCartonCount());
        response.setTotalPairs(detail.getTotalPairs());
        response.setCartonStart(detail.getCartonStart());
        response.setCartonEnd(detail.getCartonEnd());
        response.setLengthValue(detail.getLengthValue());
        response.setWidthValue(detail.getWidthValue());
        response.setHeightValue(detail.getHeightValue());
        response.setNetWeight(detail.getNetWeight());
        response.setGrossWeight(detail.getGrossWeight());
        response.setMeasurement(detail.getMeasurement());
        response.setTotalNetWeight(detail.getTotalNetWeight());
        response.setTotalGrossWeight(detail.getTotalGrossWeight());
        response.setTotalCbm(detail.getTotalCbm());
        response.setGender(detail.getGender());
        response.setProductType(detail.getProductType());
        response.setUpperMaterial(detail.getUpperMaterial());
        response.setSoleMaterial(detail.getSoleMaterial());
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
    public Integer getPairs() { return pairs; }
    public void setPairs(Integer pairs) { this.pairs = pairs; }
    public Integer getCartonCount() { return cartonCount; }
    public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
    public Integer getTotalPairs() { return totalPairs; }
    public void setTotalPairs(Integer totalPairs) { this.totalPairs = totalPairs; }
    public String getCartonStart() { return cartonStart; }
    public void setCartonStart(String cartonStart) { this.cartonStart = cartonStart; }
    public String getCartonEnd() { return cartonEnd; }
    public void setCartonEnd(String cartonEnd) { this.cartonEnd = cartonEnd; }
    public String getLengthValue() { return lengthValue; }
    public void setLengthValue(String lengthValue) { this.lengthValue = lengthValue; }
    public String getWidthValue() { return widthValue; }
    public void setWidthValue(String widthValue) { this.widthValue = widthValue; }
    public String getHeightValue() { return heightValue; }
    public void setHeightValue(String heightValue) { this.heightValue = heightValue; }
    public String getNetWeight() { return netWeight; }
    public void setNetWeight(String netWeight) { this.netWeight = netWeight; }
    public String getGrossWeight() { return grossWeight; }
    public void setGrossWeight(String grossWeight) { this.grossWeight = grossWeight; }
    public String getMeasurement() { return measurement; }
    public void setMeasurement(String measurement) { this.measurement = measurement; }
    public String getTotalNetWeight() { return totalNetWeight; }
    public void setTotalNetWeight(String totalNetWeight) { this.totalNetWeight = totalNetWeight; }
    public String getTotalGrossWeight() { return totalGrossWeight; }
    public void setTotalGrossWeight(String totalGrossWeight) { this.totalGrossWeight = totalGrossWeight; }
    public String getTotalCbm() { return totalCbm; }
    public void setTotalCbm(String totalCbm) { this.totalCbm = totalCbm; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getUpperMaterial() { return upperMaterial; }
    public void setUpperMaterial(String upperMaterial) { this.upperMaterial = upperMaterial; }
    public String getSoleMaterial() { return soleMaterial; }
    public void setSoleMaterial(String soleMaterial) { this.soleMaterial = soleMaterial; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public Integer getRowIndex() { return rowIndex; }
    public void setRowIndex(Integer rowIndex) { this.rowIndex = rowIndex; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
