package com.shoefactory.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public class PackingOrderCreateRequest {

    private LocalDate orderDate;
    @Valid
    @NotEmpty
    private List<@NotNull PackingOrderItemRequest> items;

    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public List<@NotNull PackingOrderItemRequest> getItems() { return items; }
    public void setItems(List<@NotNull PackingOrderItemRequest> items) { this.items = items; }
}
