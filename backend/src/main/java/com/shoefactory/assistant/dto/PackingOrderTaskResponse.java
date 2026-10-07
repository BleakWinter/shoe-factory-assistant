package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.PackingOrderTask;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class PackingOrderTaskResponse {

    private Long id;
    private String taskNo;
    private LocalDate orderDate;
    private String orderNos;
    private String developmentNos;
    private Integer itemCount;
    private Integer totalPairs;
    private Integer totalCartonCount;
    private List<PackingOrderItemResponse> items;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
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
    public List<PackingOrderItemResponse> getItems() { return items; }
    public void setItems(List<PackingOrderItemResponse> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public static PackingOrderTaskResponse from(PackingOrderTask task, List<PackingOrderItemResponse> items) {
        PackingOrderTaskResponse response = new PackingOrderTaskResponse();
        response.setId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setOrderDate(task.getOrderDate());
        response.setOrderNos(task.getOrderNos());
        response.setDevelopmentNos(task.getDevelopmentNos());
        response.setItemCount(task.getItemCount());
        response.setTotalPairs(task.getTotalPairs());
        response.setTotalCartonCount(task.getTotalCartonCount());
        response.setCreatedAt(task.getCreatedAt());
        response.setItems(items);
        return response;
    }
}
