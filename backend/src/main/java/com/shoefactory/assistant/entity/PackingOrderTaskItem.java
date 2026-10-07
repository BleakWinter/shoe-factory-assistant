package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("packing_order_task_item")
public class PackingOrderTaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer lineNo;
    private Long orderId;
    private String orderNo;
    private String developmentNo;
    private String boxSpec;
    private Long innerBoxTypeId;
    private String innerBoxTypeName;
    private String remark;
    private Integer quantity;
    private Integer cartonCount;
    private String packingCapacitiesJson;
    private String sourceDetailIdsJson;
    private String sourcePackingDetailIdsJson;
    private String styleImagePath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
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
    public String getPackingCapacitiesJson() { return packingCapacitiesJson; }
    public void setPackingCapacitiesJson(String packingCapacitiesJson) { this.packingCapacitiesJson = packingCapacitiesJson; }
    public String getSourceDetailIdsJson() { return sourceDetailIdsJson; }
    public void setSourceDetailIdsJson(String sourceDetailIdsJson) { this.sourceDetailIdsJson = sourceDetailIdsJson; }
    public String getSourcePackingDetailIdsJson() { return sourcePackingDetailIdsJson; }
    public void setSourcePackingDetailIdsJson(String sourcePackingDetailIdsJson) { this.sourcePackingDetailIdsJson = sourcePackingDetailIdsJson; }
    public String getStyleImagePath() { return styleImagePath; }
    public void setStyleImagePath(String styleImagePath) { this.styleImagePath = styleImagePath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
