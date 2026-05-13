package com.shoefactory.assistant.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.entity.OrderRecordDetail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OrderRecordDetailResponse {

    // 数据库里尺码数量存 JSON，返回前端时转成 Map，页面才能直接渲染尺码网格。
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };

    private Long id;
    private Long orderId;
    private Integer lineNo;
    private String lastNo;
    private String developmentNo;
    private String customerName;
    private String customerOrderNo;
    private String warehouseStoreNo;
    private LocalDate deliveryDate;
    private String poNo;
    private String customerStyleNo;
    private String imageUrl;
    private String englishColor;
    private String englishMaterial;
    private String upperMaterial;
    private String liningMaterial;
    private String accessory;
    private String insolePlatform;
    private String outsole;
    private String trademark;
    private Map<String, Integer> sizeQuantities;
    private Integer quantity;
    private Integer cartonCount;
    private String boxSpec;
    private String sourceSheetName;
    private Integer rowIndex;
    private String remark;
    private List<OrderDetailProcessResponse> processes;
    private LocalDateTime createdAt;

    public static OrderRecordDetailResponse from(OrderRecordDetail detail, List<OrderDetailProcess> processes) {
        OrderRecordDetailResponse response = new OrderRecordDetailResponse();
        response.setId(detail.getId());
        response.setOrderId(detail.getOrderId());
        response.setLineNo(detail.getLineNo());
        response.setLastNo(detail.getLastNo());
        response.setDevelopmentNo(detail.getDevelopmentNo());
        response.setCustomerName(detail.getCustomerName());
        response.setCustomerOrderNo(detail.getCustomerOrderNo());
        response.setWarehouseStoreNo(detail.getWarehouseStoreNo());
        response.setDeliveryDate(detail.getDeliveryDate());
        response.setPoNo(detail.getPoNo());
        response.setCustomerStyleNo(detail.getCustomerStyleNo());
        response.setImageUrl(detail.getStyleImagePath() == null ? null : "/api/orders/details/" + detail.getId() + "/image");
        response.setEnglishColor(detail.getEnglishColor());
        response.setEnglishMaterial(detail.getEnglishMaterial());
        response.setUpperMaterial(detail.getUpperMaterial());
        response.setLiningMaterial(detail.getLiningMaterial());
        response.setAccessory(detail.getAccessory());
        response.setInsolePlatform(detail.getInsolePlatform());
        response.setOutsole(detail.getOutsole());
        response.setTrademark(detail.getTrademark());
        response.setSizeQuantities(parseSizeQuantities(detail.getSizeQuantitiesJson()));
        response.setQuantity(detail.getQuantity());
        response.setCartonCount(detail.getCartonCount());
        response.setBoxSpec(detail.getBoxSpec());
        response.setSourceSheetName(detail.getSourceSheetName());
        response.setRowIndex(detail.getRowIndex());
        response.setRemark(detail.getRemark());
        response.setProcesses(processes == null
                ? List.of()
                : processes.stream().map(OrderDetailProcessResponse::from).toList());
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
            // 历史脏数据不应该拖垮整个列表，解析失败就返回空尺码。
            return Collections.emptyMap();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getLastNo() { return lastNo; }
    public void setLastNo(String lastNo) { this.lastNo = lastNo; }
    public String getDevelopmentNo() { return developmentNo; }
    public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerOrderNo() { return customerOrderNo; }
    public void setCustomerOrderNo(String customerOrderNo) { this.customerOrderNo = customerOrderNo; }
    public String getWarehouseStoreNo() { return warehouseStoreNo; }
    public void setWarehouseStoreNo(String warehouseStoreNo) { this.warehouseStoreNo = warehouseStoreNo; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
    public String getPoNo() { return poNo; }
    public void setPoNo(String poNo) { this.poNo = poNo; }
    public String getCustomerStyleNo() { return customerStyleNo; }
    public void setCustomerStyleNo(String customerStyleNo) { this.customerStyleNo = customerStyleNo; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getEnglishColor() { return englishColor; }
    public void setEnglishColor(String englishColor) { this.englishColor = englishColor; }
    public String getEnglishMaterial() { return englishMaterial; }
    public void setEnglishMaterial(String englishMaterial) { this.englishMaterial = englishMaterial; }
    public String getUpperMaterial() { return upperMaterial; }
    public void setUpperMaterial(String upperMaterial) { this.upperMaterial = upperMaterial; }
    public String getLiningMaterial() { return liningMaterial; }
    public void setLiningMaterial(String liningMaterial) { this.liningMaterial = liningMaterial; }
    public String getAccessory() { return accessory; }
    public void setAccessory(String accessory) { this.accessory = accessory; }
    public String getInsolePlatform() { return insolePlatform; }
    public void setInsolePlatform(String insolePlatform) { this.insolePlatform = insolePlatform; }
    public String getOutsole() { return outsole; }
    public void setOutsole(String outsole) { this.outsole = outsole; }
    public String getTrademark() { return trademark; }
    public void setTrademark(String trademark) { this.trademark = trademark; }
    public Map<String, Integer> getSizeQuantities() { return sizeQuantities; }
    public void setSizeQuantities(Map<String, Integer> sizeQuantities) { this.sizeQuantities = sizeQuantities; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getCartonCount() { return cartonCount; }
    public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
    public String getBoxSpec() { return boxSpec; }
    public void setBoxSpec(String boxSpec) { this.boxSpec = boxSpec; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public Integer getRowIndex() { return rowIndex; }
    public void setRowIndex(Integer rowIndex) { this.rowIndex = rowIndex; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<OrderDetailProcessResponse> getProcesses() { return processes; }
    public void setProcesses(List<OrderDetailProcessResponse> processes) { this.processes = processes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
