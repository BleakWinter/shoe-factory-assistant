package com.shoefactory.assistant.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoefactory.assistant.entity.ShippingNoteRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class ShippingNoteRecordResponse {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<ShippingNoteItemRequest>> ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private Long id;
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

    public static ShippingNoteRecordResponse from(ShippingNoteRecord record) {
        ShippingNoteRecordResponse response = new ShippingNoteRecordResponse();
        response.setId(record.getId());
        response.setPrintNo(record.getPrintNo());
        response.setOrderId(record.getOrderId());
        response.setOrderNo(record.getOrderNo());
        response.setCustomerName(record.getCustomerName());
        response.setRecipientName(record.getRecipientName());
        response.setShippingDate(record.getShippingDate());
        response.setDevelopmentNos(record.getDevelopmentNos());
        response.setItemCount(record.getItemCount());
        response.setTotalPairs(record.getTotalPairs());
        response.setTotalCartonCount(record.getTotalCartonCount());
        response.setItems(parseItems(record.getDataJson()));
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private static List<ShippingNoteItemRequest> parseItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json, ITEM_LIST_TYPE);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
