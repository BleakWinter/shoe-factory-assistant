package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("order_detail_process")
public class OrderDetailProcess {

    // 明细处理状态表：一条明细可以对应订包装、定大底等多个处理状态。
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long orderDetailId;
    private Integer processType;
    private Integer processStatus;
    private Integer processCount;
    private LocalDateTime lastProcessAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOrderDetailId() { return orderDetailId; }
    public void setOrderDetailId(Long orderDetailId) { this.orderDetailId = orderDetailId; }
    public Integer getProcessType() { return processType; }
    public void setProcessType(Integer processType) { this.processType = processType; }
    public Integer getProcessStatus() { return processStatus; }
    public void setProcessStatus(Integer processStatus) { this.processStatus = processStatus; }
    public Integer getProcessCount() { return processCount; }
    public void setProcessCount(Integer processCount) { this.processCount = processCount; }
    public LocalDateTime getLastProcessAt() { return lastProcessAt; }
    public void setLastProcessAt(LocalDateTime lastProcessAt) { this.lastProcessAt = lastProcessAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
