package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一个已保存的报价版本。报价内容不原地覆盖，采用时间和签字照片单独记录。 */
@TableName("shoe_quotation")
public class ShoeQuotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String versionGroup;
    private Integer versionNo;
    private Long customerId;
    private String developmentNo;
    private String lastNo;
    private String colorMaterial;
    private LocalDate quotationDate;
    private String imageKey;
    private BigDecimal laborCost;
    private Boolean includeOutsole;
    private String roundingMode;
    private Boolean manualPrice;
    private BigDecimal finalPrice;
    private BigDecimal materialAmount;
    private BigDecimal auxiliaryAmount;
    private BigDecimal outsoleAmount;
    private BigDecimal calculatedAmount;
    private String note;
    private LocalDateTime adoptedAt;
    private String signedImageKey;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getVersionGroup() { return versionGroup; }
    public void setVersionGroup(String value) { versionGroup = value; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer value) { versionNo = value; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long value) { customerId = value; }
    public String getDevelopmentNo() { return developmentNo; }
    public void setDevelopmentNo(String value) { developmentNo = value; }
    public String getLastNo() { return lastNo; }
    public void setLastNo(String value) { lastNo = value; }
    public String getColorMaterial() { return colorMaterial; }
    public void setColorMaterial(String value) { colorMaterial = value; }
    public LocalDate getQuotationDate() { return quotationDate; }
    public void setQuotationDate(LocalDate value) { quotationDate = value; }
    public String getImageKey() { return imageKey; }
    public void setImageKey(String value) { imageKey = value; }
    public BigDecimal getLaborCost() { return laborCost; }
    public void setLaborCost(BigDecimal value) { laborCost = value; }
    public Boolean getIncludeOutsole() { return includeOutsole; }
    public void setIncludeOutsole(Boolean value) { includeOutsole = value; }
    public String getRoundingMode() { return roundingMode; }
    public void setRoundingMode(String value) { roundingMode = value; }
    public Boolean getManualPrice() { return manualPrice; }
    public void setManualPrice(Boolean value) { manualPrice = value; }
    public BigDecimal getFinalPrice() { return finalPrice; }
    public void setFinalPrice(BigDecimal value) { finalPrice = value; }
    public BigDecimal getMaterialAmount() { return materialAmount; }
    public void setMaterialAmount(BigDecimal value) { materialAmount = value; }
    public BigDecimal getAuxiliaryAmount() { return auxiliaryAmount; }
    public void setAuxiliaryAmount(BigDecimal value) { auxiliaryAmount = value; }
    public BigDecimal getOutsoleAmount() { return outsoleAmount; }
    public void setOutsoleAmount(BigDecimal value) { outsoleAmount = value; }
    public BigDecimal getCalculatedAmount() { return calculatedAmount; }
    public void setCalculatedAmount(BigDecimal value) { calculatedAmount = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public LocalDateTime getAdoptedAt() { return adoptedAt; }
    public void setAdoptedAt(LocalDateTime value) { adoptedAt = value; }
    public String getSignedImageKey() { return signedImageKey; }
    public void setSignedImageKey(String value) { signedImageKey = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
