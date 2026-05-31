package com.shoefactory.assistant.dto;

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
    private List<StatisticsTimePoint> orderCreatedTrend = new ArrayList<>();
    private List<StatisticsTimePoint> shippedPairsTrend = new ArrayList<>();
    private List<UnshippedInvoiceStatistic> unshippedInvoiceStatistics = new ArrayList<>();
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
    public List<StatisticsTimePoint> getOrderCreatedTrend() { return orderCreatedTrend; }
    public void setOrderCreatedTrend(List<StatisticsTimePoint> orderCreatedTrend) {
        this.orderCreatedTrend = orderCreatedTrend;
    }
    public List<StatisticsTimePoint> getShippedPairsTrend() { return shippedPairsTrend; }
    public void setShippedPairsTrend(List<StatisticsTimePoint> shippedPairsTrend) {
        this.shippedPairsTrend = shippedPairsTrend;
    }
    public List<UnshippedInvoiceStatistic> getUnshippedInvoiceStatistics() { return unshippedInvoiceStatistics; }
    public void setUnshippedInvoiceStatistics(List<UnshippedInvoiceStatistic> unshippedInvoiceStatistics) {
        this.unshippedInvoiceStatistics = unshippedInvoiceStatistics;
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
