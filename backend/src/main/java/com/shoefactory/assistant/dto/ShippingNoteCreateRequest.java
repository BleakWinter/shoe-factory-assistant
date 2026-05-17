package com.shoefactory.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public class ShippingNoteCreateRequest {

    @NotNull
    private Long orderId;
    private String recipientName;
    private LocalDate shippingDate;
    @Valid
    @NotEmpty
    private List<ShippingNoteItemRequest> items;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public LocalDate getShippingDate() { return shippingDate; }
    public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
    public List<ShippingNoteItemRequest> getItems() { return items; }
    public void setItems(List<ShippingNoteItemRequest> items) { this.items = items; }
}
