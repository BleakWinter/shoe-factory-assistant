package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class StyleConfigSaveRequest {

    @Size(max = 128)
    private String developmentNo;

    @Size(max = 128)
    private String boxSpec;

    @DecimalMin(value = "0.000", inclusive = true)
    @Digits(integer = 7, fraction = 3)
    private BigDecimal netWeightPerPair;

    @DecimalMin(value = "0.000", inclusive = true)
    @Digits(integer = 7, fraction = 3)
    private BigDecimal grossWeightPerPair;

    public String getDevelopmentNo() {
        return developmentNo;
    }

    public void setDevelopmentNo(String developmentNo) {
        this.developmentNo = developmentNo;
    }

    public String getBoxSpec() {
        return boxSpec;
    }

    public void setBoxSpec(String boxSpec) {
        this.boxSpec = boxSpec;
    }

    public BigDecimal getNetWeightPerPair() {
        return netWeightPerPair;
    }

    public void setNetWeightPerPair(BigDecimal netWeightPerPair) {
        this.netWeightPerPair = netWeightPerPair;
    }

    public BigDecimal getGrossWeightPerPair() {
        return grossWeightPerPair;
    }

    public void setGrossWeightPerPair(BigDecimal grossWeightPerPair) {
        this.grossWeightPerPair = grossWeightPerPair;
    }
}
