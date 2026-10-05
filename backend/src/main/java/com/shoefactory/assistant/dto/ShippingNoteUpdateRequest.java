package com.shoefactory.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public class ShippingNoteUpdateRequest {

    private String recipientName;
    private LocalDate shippingDate;
    @Size(min = 1, message = "出货单明细不能为空")
    private List<@NotNull @Valid ShippingNoteItemRequest> items;

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public LocalDate getShippingDate() { return shippingDate; }
    public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
    public List<ShippingNoteItemRequest> getItems() { return items; }
    public void setItems(List<ShippingNoteItemRequest> items) { this.items = items; }
}
