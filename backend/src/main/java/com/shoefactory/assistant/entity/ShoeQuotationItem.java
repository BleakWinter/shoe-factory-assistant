package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

@TableName("shoe_quotation_item")
public class ShoeQuotationItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long quotationId;
    private String category;
    private Integer sortNo;
    private String partName;
    private String materialName;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getQuotationId() { return quotationId; }
    public void setQuotationId(Long value) { quotationId = value; }
    public String getCategory() { return category; }
    public void setCategory(String value) { category = value; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer value) { sortNo = value; }
    public String getPartName() { return partName; }
    public void setPartName(String value) { partName = value; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String value) { materialName = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { unit = value; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { unitPrice = value; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal value) { amount = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
}
