package com.shoefactory.assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderStatisticsResponse {

    private Integer totalPairs;
    private Integer shippedPairs;
    private Integer unshippedPairs;
    private Integer styleCount;
    private Integer detailCount;
    private BigDecimal totalPaymentAmount = BigDecimal.ZERO;
    private Integer unpricedShippedPairs;
    private Integer unpricedUnshippedPairs;
    private List<UnpricedShippedStyle> unpricedShippedStyles = new ArrayList<>();
    private List<UnpricedUnshippedStyle> unpricedUnshippedStyles = new ArrayList<>();
    private List<StatisticsTimePoint> orderCreatedTrend = new ArrayList<>();
    private List<StatisticsTimePoint> shippedPairsTrend = new ArrayList<>();
    private List<ShippedOrderStatistic> shippedOrderStatistics = new ArrayList<>();
    private List<StatisticsAmountPoint> paymentAmountTrend = new ArrayList<>();
    private List<UnshippedInvoiceStatistic> unshippedInvoiceStatistics = new ArrayList<>();
    private List<UnshippedStyleStatistic> unshippedStyleStatistics = new ArrayList<>();
    private List<DevelopmentNoStatisticNode> developmentNoTree = new ArrayList<>();
    private List<DevelopmentNoStatisticNode> topDevelopmentNos = new ArrayList<>();

    public Integer getTotalPairs() { return totalPairs; }
    public void setTotalPairs(Integer totalPairs) { this.totalPairs = totalPairs; }
    public Integer getShippedPairs() { return shippedPairs; }
    public void setShippedPairs(Integer shippedPairs) { this.shippedPairs = shippedPairs; }
    public Integer getUnshippedPairs() { return unshippedPairs; }
    public void setUnshippedPairs(Integer unshippedPairs) { this.unshippedPairs = unshippedPairs; }
    public Integer getStyleCount() { return styleCount; }
    public void setStyleCount(Integer styleCount) { this.styleCount = styleCount; }
    public Integer getDetailCount() { return detailCount; }
    public void setDetailCount(Integer detailCount) { this.detailCount = detailCount; }
    public BigDecimal getTotalPaymentAmount() { return totalPaymentAmount; }
    public void setTotalPaymentAmount(BigDecimal totalPaymentAmount) {
        this.totalPaymentAmount = totalPaymentAmount;
    }
    public Integer getUnpricedShippedPairs() { return unpricedShippedPairs; }
    public void setUnpricedShippedPairs(Integer unpricedShippedPairs) {
        this.unpricedShippedPairs = unpricedShippedPairs;
    }
    public List<UnpricedShippedStyle> getUnpricedShippedStyles() { return unpricedShippedStyles; }
    public void setUnpricedShippedStyles(List<UnpricedShippedStyle> unpricedShippedStyles) {
        this.unpricedShippedStyles = unpricedShippedStyles;
    }
    public Integer getUnpricedUnshippedPairs() { return unpricedUnshippedPairs; }
    public void setUnpricedUnshippedPairs(Integer unpricedUnshippedPairs) {
        this.unpricedUnshippedPairs = unpricedUnshippedPairs;
    }
    public List<UnpricedUnshippedStyle> getUnpricedUnshippedStyles() { return unpricedUnshippedStyles; }
    public void setUnpricedUnshippedStyles(List<UnpricedUnshippedStyle> unpricedUnshippedStyles) {
        this.unpricedUnshippedStyles = unpricedUnshippedStyles;
    }
    public List<StatisticsTimePoint> getOrderCreatedTrend() { return orderCreatedTrend; }
    public void setOrderCreatedTrend(List<StatisticsTimePoint> orderCreatedTrend) {
        this.orderCreatedTrend = orderCreatedTrend;
    }
    public List<StatisticsTimePoint> getShippedPairsTrend() { return shippedPairsTrend; }
    public void setShippedPairsTrend(List<StatisticsTimePoint> shippedPairsTrend) {
        this.shippedPairsTrend = shippedPairsTrend;
    }
    public List<ShippedOrderStatistic> getShippedOrderStatistics() { return shippedOrderStatistics; }
    public void setShippedOrderStatistics(List<ShippedOrderStatistic> shippedOrderStatistics) {
        this.shippedOrderStatistics = shippedOrderStatistics;
    }
    public List<StatisticsAmountPoint> getPaymentAmountTrend() { return paymentAmountTrend; }
    public void setPaymentAmountTrend(List<StatisticsAmountPoint> paymentAmountTrend) {
        this.paymentAmountTrend = paymentAmountTrend;
    }
    public List<UnshippedInvoiceStatistic> getUnshippedInvoiceStatistics() { return unshippedInvoiceStatistics; }
    public void setUnshippedInvoiceStatistics(List<UnshippedInvoiceStatistic> unshippedInvoiceStatistics) {
        this.unshippedInvoiceStatistics = unshippedInvoiceStatistics;
    }
    public List<UnshippedStyleStatistic> getUnshippedStyleStatistics() { return unshippedStyleStatistics; }
    public void setUnshippedStyleStatistics(List<UnshippedStyleStatistic> unshippedStyleStatistics) {
        this.unshippedStyleStatistics = unshippedStyleStatistics;
    }
    public List<DevelopmentNoStatisticNode> getDevelopmentNoTree() { return developmentNoTree; }
    public void setDevelopmentNoTree(List<DevelopmentNoStatisticNode> developmentNoTree) {
        this.developmentNoTree = developmentNoTree;
    }
    public List<DevelopmentNoStatisticNode> getTopDevelopmentNos() { return topDevelopmentNos; }
    public void setTopDevelopmentNos(List<DevelopmentNoStatisticNode> topDevelopmentNos) {
        this.topDevelopmentNos = topDevelopmentNos;
    }

    public static class DevelopmentNoStatisticNode {
        private String key;
        private String label;
        private String fullDevelopmentNo;
        private Integer level;
        private Integer pairCount;
        private Integer shippedPairCount;
        private Integer unshippedPairCount;
        private Integer detailCount;
        private Integer styleCount;
        private List<DevelopmentNoOrderReference> orderReferences = new ArrayList<>();
        private List<DevelopmentNoStatisticNode> children = new ArrayList<>();

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getFullDevelopmentNo() { return fullDevelopmentNo; }
        public void setFullDevelopmentNo(String fullDevelopmentNo) { this.fullDevelopmentNo = fullDevelopmentNo; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public Integer getPairCount() { return pairCount; }
        public void setPairCount(Integer pairCount) { this.pairCount = pairCount; }
        public Integer getShippedPairCount() { return shippedPairCount; }
        public void setShippedPairCount(Integer shippedPairCount) { this.shippedPairCount = shippedPairCount; }
        public Integer getUnshippedPairCount() { return unshippedPairCount; }
        public void setUnshippedPairCount(Integer unshippedPairCount) { this.unshippedPairCount = unshippedPairCount; }
        public Integer getDetailCount() { return detailCount; }
        public void setDetailCount(Integer detailCount) { this.detailCount = detailCount; }
        public Integer getStyleCount() { return styleCount; }
        public void setStyleCount(Integer styleCount) { this.styleCount = styleCount; }
        public List<DevelopmentNoOrderReference> getOrderReferences() { return orderReferences; }
        public void setOrderReferences(List<DevelopmentNoOrderReference> orderReferences) {
            this.orderReferences = orderReferences;
        }
        public List<DevelopmentNoStatisticNode> getChildren() { return children; }
        public void setChildren(List<DevelopmentNoStatisticNode> children) { this.children = children; }
    }

    public static class StatisticsTimePoint {
        private LocalDate date;
        private Integer value;

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public Integer getValue() { return value; }
        public void setValue(Integer value) { this.value = value; }
    }

    public static class StatisticsAmountPoint {
        private LocalDate date;
        private BigDecimal value;

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
    }

    public static class ShippedOrderStatistic {
        private LocalDate shippingDate;
        private String orderNo;
        private String developmentNo;
        private Integer shippedPairs;

        public LocalDate getShippingDate() { return shippingDate; }
        public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public String getDevelopmentNo() { return developmentNo; }
        public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
        public Integer getShippedPairs() { return shippedPairs; }
        public void setShippedPairs(Integer shippedPairs) { this.shippedPairs = shippedPairs; }
    }

    public static class UnpricedShippedStyle {
        private String developmentNo;
        private LocalDate shippingDate;
        private Integer shippedPairs;

        public String getDevelopmentNo() { return developmentNo; }
        public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
        public LocalDate getShippingDate() { return shippingDate; }
        public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
        public Integer getShippedPairs() { return shippedPairs; }
        public void setShippedPairs(Integer shippedPairs) { this.shippedPairs = shippedPairs; }
    }

    public static class UnpricedUnshippedStyle {
        private String developmentNo;
        private Integer unshippedPairs;

        public String getDevelopmentNo() { return developmentNo; }
        public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
        public Integer getUnshippedPairs() { return unshippedPairs; }
        public void setUnshippedPairs(Integer unshippedPairs) { this.unshippedPairs = unshippedPairs; }
    }

    public static class UnshippedInvoiceStatistic {
        private Long orderId;
        private String invoiceNo;
        private LocalDateTime createdAt;
        private Integer pairCount;
        private Integer shippedPairCount;
        private Integer unshippedPairCount;
        private Integer detailCount;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getInvoiceNo() { return invoiceNo; }
        public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public Integer getPairCount() { return pairCount; }
        public void setPairCount(Integer pairCount) { this.pairCount = pairCount; }
        public Integer getShippedPairCount() { return shippedPairCount; }
        public void setShippedPairCount(Integer shippedPairCount) { this.shippedPairCount = shippedPairCount; }
        public Integer getUnshippedPairCount() { return unshippedPairCount; }
        public void setUnshippedPairCount(Integer unshippedPairCount) { this.unshippedPairCount = unshippedPairCount; }
        public Integer getDetailCount() { return detailCount; }
        public void setDetailCount(Integer detailCount) { this.detailCount = detailCount; }
    }

    public static class UnshippedStyleStatistic {
        private Long orderId;
        private String invoiceNo;
        private String developmentNo;
        private LocalDate deliveryDate;
        private Boolean defaultDeliveryDate;
        private LocalDateTime createdAt;
        private Integer pairCount;
        private Integer shippedPairCount;
        private Integer unshippedPairCount;
        private Integer detailCount;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getInvoiceNo() { return invoiceNo; }
        public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
        public String getDevelopmentNo() { return developmentNo; }
        public void setDevelopmentNo(String developmentNo) { this.developmentNo = developmentNo; }
        public LocalDate getDeliveryDate() { return deliveryDate; }
        public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }
        public Boolean getDefaultDeliveryDate() { return defaultDeliveryDate; }
        public void setDefaultDeliveryDate(Boolean defaultDeliveryDate) { this.defaultDeliveryDate = defaultDeliveryDate; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public Integer getPairCount() { return pairCount; }
        public void setPairCount(Integer pairCount) { this.pairCount = pairCount; }
        public Integer getShippedPairCount() { return shippedPairCount; }
        public void setShippedPairCount(Integer shippedPairCount) { this.shippedPairCount = shippedPairCount; }
        public Integer getUnshippedPairCount() { return unshippedPairCount; }
        public void setUnshippedPairCount(Integer unshippedPairCount) { this.unshippedPairCount = unshippedPairCount; }
        public Integer getDetailCount() { return detailCount; }
        public void setDetailCount(Integer detailCount) { this.detailCount = detailCount; }
    }

    public static class DevelopmentNoOrderReference {
        private Long orderId;
        private String invoiceNo;
        private String orderNo;
        private Integer pairCount;
        private Integer shippedPairCount;
        private Integer unshippedPairCount;
        private Integer detailCount;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getInvoiceNo() { return invoiceNo; }
        public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Integer getPairCount() { return pairCount; }
        public void setPairCount(Integer pairCount) { this.pairCount = pairCount; }
        public Integer getShippedPairCount() { return shippedPairCount; }
        public void setShippedPairCount(Integer shippedPairCount) { this.shippedPairCount = shippedPairCount; }
        public Integer getUnshippedPairCount() { return unshippedPairCount; }
        public void setUnshippedPairCount(Integer unshippedPairCount) { this.unshippedPairCount = unshippedPairCount; }
        public Integer getDetailCount() { return detailCount; }
        public void setDetailCount(Integer detailCount) { this.detailCount = detailCount; }
    }
}
