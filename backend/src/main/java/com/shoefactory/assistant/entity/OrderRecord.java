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
    private String originalFileName;
    private String originalFilePath;
    private String boxImageUrl;
    private String boxImagePath;
    private String developmentNos;
    private Boolean orderPrinted;
    private Boolean packingPrinted;
    private String orderPdfPath;
    private String packingPdfPath;
    private LocalDateTime orderPdfGeneratedAt;
    private LocalDateTime packingPdfGeneratedAt;
    private Integer totalQuantity;
    private Integer totalCartonCount;
    private Integer sourceType;
    private Integer recognitionStatus;
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

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getOriginalFilePath() {
        return originalFilePath;
    }

    public void setOriginalFilePath(String originalFilePath) {
        this.originalFilePath = originalFilePath;
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

    public Boolean getOrderPrinted() {
        return orderPrinted;
    }

    public void setOrderPrinted(Boolean orderPrinted) {
        this.orderPrinted = orderPrinted;
    }

    public Boolean getPackingPrinted() {
        return packingPrinted;
    }

    public void setPackingPrinted(Boolean packingPrinted) {
        this.packingPrinted = packingPrinted;
    }

    public String getOrderPdfPath() {
        return orderPdfPath;
    }

    public void setOrderPdfPath(String orderPdfPath) {
        this.orderPdfPath = orderPdfPath;
    }

    public String getPackingPdfPath() {
        return packingPdfPath;
    }

    public void setPackingPdfPath(String packingPdfPath) {
        this.packingPdfPath = packingPdfPath;
    }

    public LocalDateTime getOrderPdfGeneratedAt() {
        return orderPdfGeneratedAt;
    }

    public void setOrderPdfGeneratedAt(LocalDateTime orderPdfGeneratedAt) {
        this.orderPdfGeneratedAt = orderPdfGeneratedAt;
    }

    public LocalDateTime getPackingPdfGeneratedAt() {
        return packingPdfGeneratedAt;
    }

    public void setPackingPdfGeneratedAt(LocalDateTime packingPdfGeneratedAt) {
        this.packingPdfGeneratedAt = packingPdfGeneratedAt;
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

    public Integer getRecognitionStatus() {
        return recognitionStatus;
    }

    public void setRecognitionStatus(Integer recognitionStatus) {
        this.recognitionStatus = recognitionStatus;
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
