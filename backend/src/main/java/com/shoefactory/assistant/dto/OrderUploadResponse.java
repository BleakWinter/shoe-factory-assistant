package com.shoefactory.assistant.dto;

public class OrderUploadResponse {

    private Long orderId;
    private String orderNo;
    private String customerName;
    private Integer lineCount;
    private Integer totalPairs;
    private Long printTaskId;
    private String printTaskNo;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public Integer getLineCount() {
        return lineCount;
    }

    public void setLineCount(Integer lineCount) {
        this.lineCount = lineCount;
    }

    public Integer getTotalPairs() {
        return totalPairs;
    }

    public void setTotalPairs(Integer totalPairs) {
        this.totalPairs = totalPairs;
    }

    public Long getPrintTaskId() {
        return printTaskId;
    }

    public void setPrintTaskId(Long printTaskId) {
        this.printTaskId = printTaskId;
    }

    public String getPrintTaskNo() {
        return printTaskNo;
    }

    public void setPrintTaskNo(String printTaskNo) {
        this.printTaskNo = printTaskNo;
    }
}
