package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("order_packing_detail")
public class OrderPackingDetail {

    // 装箱单明细表：Excel “装箱单”sheet 中每一行有效装箱记录对应一条。
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Integer lineNo;
    private String styleImagePath;
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
    private String sizeQuantitiesJson;
    private Integer cartonCount;
    private Integer totalPairs;
    private String cartonStart;
    private String cartonEnd;
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
    public String getStyleImagePath() { return styleImagePath; }
    public void setStyleImagePath(String styleImagePath) { this.styleImagePath = styleImagePath; }
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
    public String getSizeQuantitiesJson() { return sizeQuantitiesJson; }
    public void setSizeQuantitiesJson(String sizeQuantitiesJson) { this.sizeQuantitiesJson = sizeQuantitiesJson; }
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
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
