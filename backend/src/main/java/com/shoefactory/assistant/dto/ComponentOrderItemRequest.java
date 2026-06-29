package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotNull;

public class ComponentOrderItemRequest {

    @NotNull
    private Long sourceDetailId;

    public Long getSourceDetailId() { return sourceDetailId; }
    public void setSourceDetailId(Long sourceDetailId) { this.sourceDetailId = sourceDetailId; }
}
