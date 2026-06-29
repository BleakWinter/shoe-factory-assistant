package com.shoefactory.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class ComponentOrderCreateRequest {

    @NotNull
    @Min(1)
    @Max(4)
    private Integer processType;
    @Valid
    @NotEmpty
    private List<ComponentOrderItemRequest> items;

    public Integer getProcessType() { return processType; }
    public void setProcessType(Integer processType) { this.processType = processType; }
    public List<ComponentOrderItemRequest> getItems() { return items; }
    public void setItems(List<ComponentOrderItemRequest> items) { this.items = items; }
}
