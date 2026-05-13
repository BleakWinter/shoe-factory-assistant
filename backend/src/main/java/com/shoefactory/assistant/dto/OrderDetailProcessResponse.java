package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.OrderDetailProcess;
import com.shoefactory.assistant.enums.OrderDetailProcessStatus;
import com.shoefactory.assistant.enums.OrderDetailProcessType;

import java.time.LocalDateTime;

public class OrderDetailProcessResponse {

    // 明细处理状态响应，直接把数字编码翻译成中文，前端不用硬编码。
    private Long id;
    private Long orderId;
    private Long orderDetailId;
    private Integer processType;
    private String processTypeText;
    private Integer processStatus;
    private String processStatusText;
    private Integer processCount;
    private LocalDateTime lastProcessAt;
    private String remark;
    private LocalDateTime createdAt;

    public static OrderDetailProcessResponse from(OrderDetailProcess process) {
        OrderDetailProcessResponse response = new OrderDetailProcessResponse();
        response.setId(process.getId());
        response.setOrderId(process.getOrderId());
        response.setOrderDetailId(process.getOrderDetailId());
        response.setProcessType(process.getProcessType());
        OrderDetailProcessType type = OrderDetailProcessType.fromCode(process.getProcessType());
        response.setProcessTypeText(type == null ? null : type.getLabel());
        response.setProcessStatus(process.getProcessStatus());
        OrderDetailProcessStatus status = OrderDetailProcessStatus.fromCode(process.getProcessStatus());
        response.setProcessStatusText(status == null ? null : status.getLabel());
        response.setProcessCount(process.getProcessCount());
        response.setLastProcessAt(process.getLastProcessAt());
        response.setRemark(process.getRemark());
        response.setCreatedAt(process.getCreatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOrderDetailId() { return orderDetailId; }
    public void setOrderDetailId(Long orderDetailId) { this.orderDetailId = orderDetailId; }
    public Integer getProcessType() { return processType; }
    public void setProcessType(Integer processType) { this.processType = processType; }
    public String getProcessTypeText() { return processTypeText; }
    public void setProcessTypeText(String processTypeText) { this.processTypeText = processTypeText; }
    public Integer getProcessStatus() { return processStatus; }
    public void setProcessStatus(Integer processStatus) { this.processStatus = processStatus; }
    public String getProcessStatusText() { return processStatusText; }
    public void setProcessStatusText(String processStatusText) { this.processStatusText = processStatusText; }
    public Integer getProcessCount() { return processCount; }
    public void setProcessCount(Integer processCount) { this.processCount = processCount; }
    public LocalDateTime getLastProcessAt() { return lastProcessAt; }
    public void setLastProcessAt(LocalDateTime lastProcessAt) { this.lastProcessAt = lastProcessAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
