package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@TableName("customer_config")
public class CustomerConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String recipientName;
    private String inputMode;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String value) { recipientName = value; }
    public String getInputMode() { return inputMode; }
    public void setInputMode(String value) { inputMode = value; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean value) { enabled = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
