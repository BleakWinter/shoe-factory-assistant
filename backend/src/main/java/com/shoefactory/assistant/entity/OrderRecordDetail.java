package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("order_record_detail")
public class OrderRecordDetail {

    // 订单明细表：Excel “订单”sheet 中每一行有效明细对应一条记录。
    @TableId(type = IdType.AUTO)
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
    private String styleImageUrl;
    private String styleImagePath;
    private String englishColor;
    private String englishMaterial;
    private String upperMaterial;
    private String liningMaterial;
    private String accessory;
    private String insolePlatform;
    private String outsole;
    private String trademark;
    private String sizeQuantitiesJson;
    private Integer quantity;
    private Integer cartonCount;
    private String boxSpec;
    private String sourceSheetName;
    private Integer rowIndex;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
    public String getStyleImageUrl() { return styleImageUrl; }
    public void setStyleImageUrl(String styleImageUrl) { this.styleImageUrl = styleImageUrl; }
    public String getStyleImagePath() { return styleImagePath; }
    public void setStyleImagePath(String styleImagePath) { this.styleImagePath = styleImagePath; }
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
    public String getSizeQuantitiesJson() { return sizeQuantitiesJson; }
    public void setSizeQuantitiesJson(String sizeQuantitiesJson) { this.sizeQuantitiesJson = sizeQuantitiesJson; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
