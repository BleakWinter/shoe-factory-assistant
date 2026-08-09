package com.shoefactory.assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MonthlyReconciliationResponse {

    private String month;
    private String customerName;
    private Integer totalOrderQuantity = 0;
    private Integer totalShippingQuantity = 0;
    private BigDecimal goodsAmount = BigDecimal.ZERO;
    private Integer missingPriceCount = 0;
    private List<Detail> details = new ArrayList<>();

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Integer getTotalOrderQuantity() { return totalOrderQuantity; }
    public void setTotalOrderQuantity(Integer totalOrderQuantity) { this.totalOrderQuantity = totalOrderQuantity; }
    public Integer getTotalShippingQuantity() { return totalShippingQuantity; }
    public void setTotalShippingQuantity(Integer totalShippingQuantity) { this.totalShippingQuantity = totalShippingQuantity; }
    public BigDecimal getGoodsAmount() { return goodsAmount; }
    public void setGoodsAmount(BigDecimal goodsAmount) { this.goodsAmount = goodsAmount; }
    public Integer getMissingPriceCount() { return missingPriceCount; }
    public void setMissingPriceCount(Integer missingPriceCount) { this.missingPriceCount = missingPriceCount; }
    public List<Detail> getDetails() { return details; }
    public void setDetails(List<Detail> details) { this.details = details; }

    public static class Detail {
        private Long taskItemId;
        private Long shippingTaskId;
        private String taskNo;
        private LocalDate shippingDate;
        private String orderNo;
        private String developmentNo;
        private String colorMaterial;
        private Integer orderQuantity;
        private Integer shippingQuantity;
        private String productType;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private String remark;
        private Boolean missingPrice;

        public Long getTaskItemId() { return taskItemId; }
        public void setTaskItemId(Long taskItemId) { this.taskItemId = taskItemId; }
        public Long getShippingTaskId() { return shippingTaskId; }
        public void setShippingTaskId(Long shippingTaskId) { this.shippingTaskId = shippingTaskId; }
        public String getTaskNo() { return taskNo; }
        public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
        public LocalDate getShippingDate() { return shippingDate; }
        public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getDevelopmentNo() { return developmentNo; }
        public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
        public String getColorMaterial() { return colorMaterial; }
        public void setColorMaterial(String colorMaterial) { this.colorMaterial = colorMaterial; }
        public Integer getOrderQuantity() { return orderQuantity; }
        public void setOrderQuantity(Integer orderQuantity) { this.orderQuantity = orderQuantity; }
        public Integer getShippingQuantity() { return shippingQuantity; }
        public void setShippingQuantity(Integer shippingQuantity) { this.shippingQuantity = shippingQuantity; }
        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public Boolean getMissingPrice() { return missingPrice; }
        public void setMissingPrice(Boolean missingPrice) { this.missingPrice = missingPrice; }
    }
}
