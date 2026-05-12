package com.shoefactory.assistant.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.entity.OrderLine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

public class OrderLineResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };

    private Long id;
    private Long orderId;
    private String orderNo;
    private String invoiceNo;
    private String customerName;
    private LocalDate orderDate;
    private LocalDate deliveryDate;
    private String imageUrl;
    private String lastNo;
    private String styleNo;
    private String developmentNo;
    private String customerOrderNo;
    private String warehouseNo;
    private String poNo;
    private String customerStyleNo;
    private String englishColor;
    private String englishMaterial;
    private String upperMaterial;
    private String liningMaterial;
    private String accessory;
    private String insolePlatform;
    private String outsole;
    private String trademark;
    private Integer quantity;
    private Integer cartonCount;
    private Integer totalQuantity;
    private Map<String, Integer> sizeQuantities;
    private String shipmentStatus;
    private String importStatus;
    private String errorMessage;
    private String sourceSheetName;
    private Integer rowIndex;
    private LocalDateTime createdAt;

    public static OrderLineResponse from(OrderLine line) {
        OrderLineResponse response = new OrderLineResponse();
        response.setId(line.getId());
        response.setOrderId(line.getOrderId());
        response.setOrderNo(line.getOrderNo());
        response.setInvoiceNo(line.getInvoiceNo());
        response.setCustomerName(line.getCustomerName());
        response.setOrderDate(line.getOrderDate());
        response.setDeliveryDate(line.getDeliveryDate());
        response.setImageUrl(line.getImagePath() == null ? null : "/api/orders/lines/" + line.getId() + "/image");
        response.setLastNo(line.getLastNo());
        response.setStyleNo(line.getStyleNo());
        response.setDevelopmentNo(line.getDevelopmentNo());
        response.setCustomerOrderNo(line.getCustomerOrderNo());
        response.setWarehouseNo(line.getWarehouseNo());
        response.setPoNo(line.getPoNo());
        response.setCustomerStyleNo(line.getCustomerStyleNo());
        response.setEnglishColor(line.getEnglishColor());
        response.setEnglishMaterial(line.getEnglishMaterial());
        response.setUpperMaterial(line.getUpperMaterial());
        response.setLiningMaterial(line.getLiningMaterial());
        response.setAccessory(line.getAccessory());
        response.setInsolePlatform(line.getInsolePlatform());
        response.setOutsole(line.getOutsole());
        response.setTrademark(line.getTrademark());
        response.setQuantity(line.getQuantity());
        response.setCartonCount(line.getCartonCount());
        response.setTotalQuantity(line.getTotalQuantity());
        response.setSizeQuantities(parseSizeQuantities(line.getSizeQuantitiesJson()));
        response.setShipmentStatus(line.getShipmentStatus());
        response.setImportStatus(line.getImportStatus());
        response.setErrorMessage(line.getErrorMessage());
        response.setSourceSheetName(line.getSourceSheetName());
        response.setRowIndex(line.getRowIndex());
        response.setCreatedAt(line.getCreatedAt());
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
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getLastNo() { return lastNo; }
    public void setLastNo(String lastNo) { this.lastNo = lastNo; }
    public String getStyleNo() { return styleNo; }
    public void setStyleNo(String styleNo) { this.styleNo = styleNo; }
    public String getDevelopmentNo() { return developmentNo; }
    public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
    public String getCustomerOrderNo() { return customerOrderNo; }
    public void setCustomerOrderNo(String customerOrderNo) { this.customerOrderNo = customerOrderNo; }
    public String getWarehouseNo() { return warehouseNo; }
    public void setWarehouseNo(String warehouseNo) { this.warehouseNo = warehouseNo; }
    public String getPoNo() { return poNo; }
    public void setPoNo(String poNo) { this.poNo = poNo; }
    public String getCustomerStyleNo() { return customerStyleNo; }
    public void setCustomerStyleNo(String customerStyleNo) { this.customerStyleNo = customerStyleNo; }
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
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Integer getCartonCount() { return cartonCount; }
    public void setCartonCount(Integer cartonCount) { this.cartonCount = cartonCount; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
    public Map<String, Integer> getSizeQuantities() { return sizeQuantities; }
    public void setSizeQuantities(Map<String, Integer> sizeQuantities) { this.sizeQuantities = sizeQuantities; }
    public String getShipmentStatus() { return shipmentStatus; }
    public void setShipmentStatus(String shipmentStatus) { this.shipmentStatus = shipmentStatus; }
    public String getImportStatus() { return importStatus; }
    public void setImportStatus(String importStatus) { this.importStatus = importStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public Integer getRowIndex() { return rowIndex; }
    public void setRowIndex(Integer rowIndex) { this.rowIndex = rowIndex; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
