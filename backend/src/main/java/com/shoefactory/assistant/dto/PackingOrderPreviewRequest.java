package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class PackingOrderPreviewRequest {

    @NotEmpty
    private List<@NotNull @Positive Long> sourceDetailIds;

    public List<@NotNull @Positive Long> getSourceDetailIds() { return sourceDetailIds; }
    public void setSourceDetailIds(List<@NotNull @Positive Long> sourceDetailIds) { this.sourceDetailIds = sourceDetailIds; }
}
