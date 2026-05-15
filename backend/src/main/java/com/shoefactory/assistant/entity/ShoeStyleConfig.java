package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("shoe_style_config")
public class ShoeStyleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String developmentNo;
    private String boxSpec;
    private BigDecimal netWeightPerPair;
    private BigDecimal grossWeightPerPair;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
