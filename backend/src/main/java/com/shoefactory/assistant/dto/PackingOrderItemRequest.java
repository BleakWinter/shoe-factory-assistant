package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public class PackingOrderItemRequest {

    @NotEmpty
    private List<@NotNull @Positive Long> sourceDetailIds;
    @Size(max = 100)
    private String boxSpec;
    @Positive
    private Long innerBoxTypeId;
    @Size(max = 500)
    private String remark;
    private Map<String, Integer> expectedPackingCapacities;
    private List<Long> expectedPackingDetailIds;

    public List<@NotNull @Positive Long> getSourceDetailIds() { return sourceDetailIds; }
    public void setSourceDetailIds(List<@NotNull @Positive Long> sourceDetailIds) { this.sourceDetailIds = sourceDetailIds; }
    public String getBoxSpec() { return boxSpec; }
    public void setBoxSpec(String boxSpec) { this.boxSpec = boxSpec; }
    public Long getInnerBoxTypeId() { return innerBoxTypeId; }
    public void setInnerBoxTypeId(Long innerBoxTypeId) { this.innerBoxTypeId = innerBoxTypeId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Map<String, Integer> getExpectedPackingCapacities() { return expectedPackingCapacities; }
    public void setExpectedPackingCapacities(Map<String, Integer> expectedPackingCapacities) { this.expectedPackingCapacities = expectedPackingCapacities; }
    public List<Long> getExpectedPackingDetailIds() { return expectedPackingDetailIds; }
    public void setExpectedPackingDetailIds(List<Long> expectedPackingDetailIds) { this.expectedPackingDetailIds = expectedPackingDetailIds; }
}
