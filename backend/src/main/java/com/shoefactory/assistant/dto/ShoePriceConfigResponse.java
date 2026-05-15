package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.ShoeStyleConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ShoePriceConfigResponse {

    private Long id;
    private String developmentNo;
    private BigDecimal shoePrice;
    private Boolean complete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ShoePriceConfigResponse from(ShoeStyleConfig config) {
        ShoePriceConfigResponse response = new ShoePriceConfigResponse();
        response.setId(config.getId());
        response.setDevelopmentNo(config.getDevelopmentNo());
        response.setShoePrice(config.getShoePrice());
        response.setComplete(config.getShoePrice() != null);
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Boolean getComplete() {
        return complete;
    }

    public void setComplete(Boolean complete) {
        this.complete = complete;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
