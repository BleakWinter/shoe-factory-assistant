package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.ComponentOrderTask;
import com.shoefactory.assistant.enums.OrderDetailProcessType;

import java.time.LocalDateTime;
import java.util.List;

public class ComponentOrderTaskResponse {

    private Long id;
    private String taskNo;
    private Integer processType;
    private String processTypeText;
    private String orderNos;
    private String developmentNos;
    private Integer itemCount;
    private Integer totalPairs;
    private Integer totalCartonCount;
    private List<ComponentOrderTaskItemResponse> items;
    private LocalDateTime createdAt;

    public static ComponentOrderTaskResponse from(
            ComponentOrderTask task,
            List<ComponentOrderTaskItemResponse> items
    ) {
        ComponentOrderTaskResponse response = new ComponentOrderTaskResponse();
        response.setId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setProcessType(task.getProcessType());
        OrderDetailProcessType processType = OrderDetailProcessType.fromCode(task.getProcessType());
        response.setProcessTypeText(processType == null ? null : processType.getLabel());
        response.setOrderNos(task.getOrderNos());
        response.setDevelopmentNos(task.getDevelopmentNos());
        response.setItemCount(task.getItemCount());
        response.setTotalPairs(task.getTotalPairs());
        response.setTotalCartonCount(task.getTotalCartonCount());
        response.setItems(items == null ? List.of() : items);
        response.setCreatedAt(task.getCreatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public Integer getProcessType() { return processType; }
    public void setProcessType(Integer processType) { this.processType = processType; }
    public String getProcessTypeText() { return processTypeText; }
    public void setProcessTypeText(String processTypeText) { this.processTypeText = processTypeText; }
    public String getOrderNos() { return orderNos; }
    public void setOrderNos(String orderNos) { this.orderNos = orderNos; }
    public String getDevelopmentNos() { return developmentNos; }
    public void setDevelopmentNos(String developmentNos) { this.developmentNos = developmentNos; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getTotalPairs() { return totalPairs; }
    public void setTotalPairs(Integer totalPairs) { this.totalPairs = totalPairs; }
    public Integer getTotalCartonCount() { return totalCartonCount; }
    public void setTotalCartonCount(Integer totalCartonCount) { this.totalCartonCount = totalCartonCount; }
    public List<ComponentOrderTaskItemResponse> getItems() { return items; }
    public void setItems(List<ComponentOrderTaskItemResponse> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
