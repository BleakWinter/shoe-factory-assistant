package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("order_line")
public class OrderLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long sourceFileId;
    private String orderNo;
    private String invoiceNo;
    private String customerName;
    private LocalDate orderDate;
    private LocalDate deliveryDate;
    private String imagePath;
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
    private String sizeQuantitiesJson;
    private String shipmentStatus;
    private String importStatus;
    private String errorMessage;
    private String sourceSheetName;
    private Integer rowIndex;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getSourceFileId() {
        return sourceFileId;
    }

    public void setSourceFileId(Long sourceFileId) {
        this.sourceFileId = sourceFileId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getLastNo() {
        return lastNo;
    }

    public void setLastNo(String lastNo) {
        this.lastNo = lastNo;
    }

    public String getStyleNo() {
        return styleNo;
    }

    public void setStyleNo(String styleNo) {
        this.styleNo = styleNo;
    }

    public String getDevelopmentNo() {
        return developmentNo;
    }

    public void setDevelopmentNo(String developmentNo) {
        this.developmentNo = developmentNo;
    }

    public String getCustomerOrderNo() {
        return customerOrderNo;
    }

    public void setCustomerOrderNo(String customerOrderNo) {
        this.customerOrderNo = customerOrderNo;
    }

    public String getWarehouseNo() {
        return warehouseNo;
    }

    public void setWarehouseNo(String warehouseNo) {
        this.warehouseNo = warehouseNo;
    }

    public String getPoNo() {
        return poNo;
    }

    public void setPoNo(String poNo) {
        this.poNo = poNo;
    }

    public String getCustomerStyleNo() {
        return customerStyleNo;
    }

    public void setCustomerStyleNo(String customerStyleNo) {
        this.customerStyleNo = customerStyleNo;
    }

    public String getEnglishColor() {
        return englishColor;
    }

    public void setEnglishColor(String englishColor) {
        this.englishColor = englishColor;
    }

    public String getEnglishMaterial() {
        return englishMaterial;
    }

    public void setEnglishMaterial(String englishMaterial) {
        this.englishMaterial = englishMaterial;
    }

    public String getUpperMaterial() {
        return upperMaterial;
    }

    public void setUpperMaterial(String upperMaterial) {
        this.upperMaterial = upperMaterial;
    }

    public String getLiningMaterial() {
        return liningMaterial;
    }

    public void setLiningMaterial(String liningMaterial) {
        this.liningMaterial = liningMaterial;
    }

    public String getAccessory() {
        return accessory;
    }

    public void setAccessory(String accessory) {
        this.accessory = accessory;
    }

    public String getInsolePlatform() {
        return insolePlatform;
    }

    public void setInsolePlatform(String insolePlatform) {
        this.insolePlatform = insolePlatform;
    }

    public String getOutsole() {
        return outsole;
    }

    public void setOutsole(String outsole) {
        this.outsole = outsole;
    }

    public String getTrademark() {
        return trademark;
    }

    public void setTrademark(String trademark) {
        this.trademark = trademark;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getCartonCount() {
        return cartonCount;
    }

    public void setCartonCount(Integer cartonCount) {
        this.cartonCount = cartonCount;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public String getSizeQuantitiesJson() {
        return sizeQuantitiesJson;
    }

    public void setSizeQuantitiesJson(String sizeQuantitiesJson) {
        this.sizeQuantitiesJson = sizeQuantitiesJson;
    }

    public String getShipmentStatus() {
        return shipmentStatus;
    }

    public void setShipmentStatus(String shipmentStatus) {
        this.shipmentStatus = shipmentStatus;
    }

    public String getImportStatus() {
        return importStatus;
    }

    public void setImportStatus(String importStatus) {
        this.importStatus = importStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSourceSheetName() {
        return sourceSheetName;
    }

    public void setSourceSheetName(String sourceSheetName) {
        this.sourceSheetName = sourceSheetName;
    }

    public Integer getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(Integer rowIndex) {
        this.rowIndex = rowIndex;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
