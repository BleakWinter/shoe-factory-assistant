package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("shipping_note_task_item")
public class ShippingNoteTaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer lineNo;
    private Long sourceDetailId;
    private String orderNo;
    private String developmentNo;
    private String customerName;
    private String customerStyleNo;
    private String englishColor;
    private String englishMaterial;
    private String colorMaterial;
    private String trademark;
    private String sizeQuantitiesJson;
    private Integer pairCount;
    private Integer cartonCount;
    private Integer totalPairs;
    private String cartonStart;
    private String cartonEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public Long getSourceDetailId() { return sourceDetailId; }
    public void setSourceDetailId(Long sourceDetailId) { this.sourceDetailId = sourceDetailId; }
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
    public String getSizeQuantitiesJson() { return sizeQuantitiesJson; }
    public void setSizeQuantitiesJson(String sizeQuantitiesJson) { this.sizeQuantitiesJson = sizeQuantitiesJson; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
