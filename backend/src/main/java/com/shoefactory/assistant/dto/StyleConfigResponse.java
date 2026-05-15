package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.ShoeStyleConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class StyleConfigResponse {

    private Long id;
    private String developmentNo;
    private String boxSpec;
    private BigDecimal netWeightPerPair;
    private BigDecimal grossWeightPerPair;
    private Boolean complete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StyleConfigResponse from(ShoeStyleConfig config) {
        StyleConfigResponse response = new StyleConfigResponse();
        response.setId(config.getId());
        response.setDevelopmentNo(config.getDevelopmentNo());
        response.setBoxSpec(config.getBoxSpec());
        response.setNetWeightPerPair(config.getNetWeightPerPair());
        response.setGrossWeightPerPair(config.getGrossWeightPerPair());
        response.setComplete(isComplete(config));
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());
        return response;
    }

    private static boolean isComplete(ShoeStyleConfig config) {
        return config.getBoxSpec() != null
                && !config.getBoxSpec().isBlank()
                && config.getNetWeightPerPair() != null
                && config.getGrossWeightPerPair() != null;
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
