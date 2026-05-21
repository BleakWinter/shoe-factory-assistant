package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("order_record")
public class OrderRecord {

    // 订单主表：一份 Excel 对应一条主记录，只放列表和打印入口需要的汇总字段。
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String customerName;
    private String boxImageUrl;
    private String boxImagePath;
    private String developmentNos;
    private Integer totalQuantity;
    private Integer totalCartonCount;
    private Integer sourceType;
    private Integer orderRecognitionStatus;
    private Integer packingRecognitionStatus;
    private String remark;
    private String errorMessage;
    private String orderErrorMessage;
    private String packingErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getBoxImageUrl() {
        return boxImageUrl;
    }

    public void setBoxImageUrl(String boxImageUrl) {
        this.boxImageUrl = boxImageUrl;
    }

    public String getBoxImagePath() {
        return boxImagePath;
    }

    public void setBoxImagePath(String boxImagePath) {
        this.boxImagePath = boxImagePath;
    }

    public String getDevelopmentNos() {
        return developmentNos;
    }

    public void setDevelopmentNos(String developmentNos) {
        this.developmentNos = developmentNos;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getTotalCartonCount() {
        return totalCartonCount;
    }

    public void setTotalCartonCount(Integer totalCartonCount) {
        this.totalCartonCount = totalCartonCount;
    }

    public Integer getSourceType() {
        return sourceType;
    }

    public void setSourceType(Integer sourceType) {
        this.sourceType = sourceType;
    }

    public Integer getOrderRecognitionStatus() {
        return orderRecognitionStatus;
    }

    public void setOrderRecognitionStatus(Integer orderRecognitionStatus) {
        this.orderRecognitionStatus = orderRecognitionStatus;
    }

    public Integer getPackingRecognitionStatus() {
        return packingRecognitionStatus;
    }

    public void setPackingRecognitionStatus(Integer packingRecognitionStatus) {
        this.packingRecognitionStatus = packingRecognitionStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getOrderErrorMessage() {
        return orderErrorMessage;
    }

    public void setOrderErrorMessage(String orderErrorMessage) {
        this.orderErrorMessage = orderErrorMessage;
    }

    public String getPackingErrorMessage() {
        return packingErrorMessage;
    }

    public void setPackingErrorMessage(String packingErrorMessage) {
        this.packingErrorMessage = packingErrorMessage;
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
