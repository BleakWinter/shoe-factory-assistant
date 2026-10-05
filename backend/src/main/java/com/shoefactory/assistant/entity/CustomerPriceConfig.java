package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("customer_price_config")
public class CustomerPriceConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String developmentNo;
    private BigDecimal shoePrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getDevelopmentNo() { return developmentNo; }
    public void setDevelopmentNo(String value) { developmentNo = value; }
    public BigDecimal getShoePrice() { return shoePrice; }
    public void setShoePrice(BigDecimal value) { shoePrice = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
