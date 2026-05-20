package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("order_print_task")
public class OrderPrintTask {

    // 一行表示一个可打印项，比如订单 sheet、装箱单 sheet 或后续某条明细的标签。
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long orderDetailId;
    private Integer printType;
    private Integer status;
    private String previewPdfPath;
    private LocalDateTime pdfGeneratedAt;
    private Integer printCount;
    private LocalDateTime lastPrintTime;
    private String lastPrintUser;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderDetailId() {
        return orderDetailId;
    }

    public void setOrderDetailId(Long orderDetailId) {
        this.orderDetailId = orderDetailId;
    }

    public Integer getPrintType() {
        return printType;
    }

    public void setPrintType(Integer printType) {
        this.printType = printType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getPreviewPdfPath() {
        return previewPdfPath;
    }

    public void setPreviewPdfPath(String previewPdfPath) {
        this.previewPdfPath = previewPdfPath;
    }

    public LocalDateTime getPdfGeneratedAt() {
        return pdfGeneratedAt;
    }

    public void setPdfGeneratedAt(LocalDateTime pdfGeneratedAt) {
        this.pdfGeneratedAt = pdfGeneratedAt;
    }

    public Integer getPrintCount() {
        return printCount;
    }

    public void setPrintCount(Integer printCount) {
        this.printCount = printCount;
    }

    public LocalDateTime getLastPrintTime() {
        return lastPrintTime;
    }

    public void setLastPrintTime(LocalDateTime lastPrintTime) {
        this.lastPrintTime = lastPrintTime;
    }

    public String getLastPrintUser() {
        return lastPrintUser;
    }

    public void setLastPrintUser(String lastPrintUser) {
        this.lastPrintUser = lastPrintUser;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
