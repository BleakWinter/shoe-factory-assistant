package com.shoefactory.assistant.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.entity.ShippingNoteTask;
import com.shoefactory.assistant.entity.ShippingNoteTaskItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ShippingNoteTaskResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> SIZE_MAP_TYPE = new TypeReference<>() {
    };

    private Long id;
    private String taskNo;
    private String printNo;
    private Long orderId;
    private String orderNo;
    private String customerName;
    private String recipientName;
    private LocalDate shippingDate;
    private String developmentNos;
    private Integer itemCount;
    private Integer totalPairs;
    private Integer totalCartonCount;
    private List<ShippingNoteItemRequest> items;
    private LocalDateTime createdAt;

    public static ShippingNoteTaskResponse from(ShippingNoteTask task, List<ShippingNoteTaskItem> items) {
        ShippingNoteTaskResponse response = new ShippingNoteTaskResponse();
        response.setId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setPrintNo(task.getTaskNo());
        response.setOrderId(task.getOrderId());
        response.setOrderNo(task.getOrderNo());
        response.setCustomerName(task.getCustomerName());
        response.setRecipientName(task.getRecipientName());
        response.setShippingDate(task.getShippingDate());
        response.setDevelopmentNos(task.getDevelopmentNos());
        response.setItemCount(task.getItemCount());
        response.setTotalPairs(task.getTotalPairs());
        response.setTotalCartonCount(task.getTotalCartonCount());
        response.setItems(items == null ? List.of() : items.stream()
                .map(ShippingNoteTaskResponse::toItemResponse)
                .toList());
        response.setCreatedAt(task.getCreatedAt());
        return response;
    }

    private static ShippingNoteItemRequest toItemResponse(ShippingNoteTaskItem item) {
        ShippingNoteItemRequest response = new ShippingNoteItemRequest();
        response.setSourceDetailId(item.getSourceDetailId());
        response.setOrderNo(item.getOrderNo());
        response.setDevelopmentNo(item.getDevelopmentNo());
        response.setCustomerName(item.getCustomerName());
        response.setCustomerStyleNo(item.getCustomerStyleNo());
        response.setEnglishColor(item.getEnglishColor());
        response.setEnglishMaterial(item.getEnglishMaterial());
        response.setColorMaterial(item.getColorMaterial());
        response.setTrademark(item.getTrademark());
        response.setSizeQuantities(parseSizeQuantities(item.getSizeQuantitiesJson()));
        response.setPairCount(item.getPairCount());
        response.setCartonCount(item.getCartonCount());
        response.setTotalPairs(item.getTotalPairs());
        response.setCartonStart(item.getCartonStart());
        response.setCartonEnd(item.getCartonEnd());
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
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getPrintNo() { return printNo; }
    public void setPrintNo(String printNo) { this.printNo = printNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public LocalDate getShippingDate() { return shippingDate; }
    public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
    public String getDevelopmentNos() { return developmentNos; }
    public void setDevelopmentNos(String developmentNos) { this.developmentNos = developmentNos; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getTotalPairs() { return totalPairs; }
    public void setTotalPairs(Integer totalPairs) { this.totalPairs = totalPairs; }
    public Integer getTotalCartonCount() { return totalCartonCount; }
    public void setTotalCartonCount(Integer totalCartonCount) { this.totalCartonCount = totalCartonCount; }
    public List<ShippingNoteItemRequest> getItems() { return items; }
    public void setItems(List<ShippingNoteItemRequest> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
