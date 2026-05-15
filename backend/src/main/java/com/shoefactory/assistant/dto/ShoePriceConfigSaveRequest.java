package com.shoefactory.assistant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ShoePriceConfigSaveRequest {

    @Size(max = 128)
    private String developmentNo;

    @DecimalMin(value = "0.00", inclusive = true)
    @Digits(integer = 8, fraction = 2)
    private BigDecimal shoePrice;

    public String getDevelopmentNo() {
        return developmentNo;
    }

    public void setDevelopmentNo(String developmentNo) {
        this.developmentNo = developmentNo;
    }

    public BigDecimal getShoePrice() {
        return shoePrice;
    }

    public void setShoePrice(BigDecimal shoePrice) {
        this.shoePrice = shoePrice;
    }
}
