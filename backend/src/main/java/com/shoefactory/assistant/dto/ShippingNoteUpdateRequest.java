package com.shoefactory.assistant.dto;

import java.time.LocalDate;

public class ShippingNoteUpdateRequest {

    private String recipientName;
    private LocalDate shippingDate;

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public LocalDate getShippingDate() { return shippingDate; }
    public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
}