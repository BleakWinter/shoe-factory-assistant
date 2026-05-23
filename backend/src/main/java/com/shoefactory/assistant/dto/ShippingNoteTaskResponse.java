package com.shoefactory.assistant.dto;

import com.shoefactory.assistant.entity.ShippingNoteTask;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ShippingNoteTaskResponse {

    private Long id;
    private String taskNo;
    private String printNo;
    private String recipientName;
    private LocalDate shippingDate;
    private String invoiceNos;
    private String developmentNos;
    private Integer itemCount;
    private Integer totalPairs;
    private Integer totalCartonCount;
    private List<ShippingNoteItemRequest> items;
    private LocalDateTime createdAt;

    public static ShippingNoteTaskResponse from(ShippingNoteTask task, List<ShippingNoteItemRequest> items) {
        ShippingNoteTaskResponse response = new ShippingNoteTaskResponse();
        response.setId(task.getId());
        response.setTaskNo(task.getTaskNo());
        response.setPrintNo(task.getTaskNo());
        response.setRecipientName(task.getRecipientName());
        response.setShippingDate(task.getShippingDate());
        response.setInvoiceNos(task.getInvoiceNos());
        response.setDevelopmentNos(task.getDevelopmentNos());
        response.setItemCount(task.getItemCount());
        response.setTotalPairs(task.getTotalPairs());
        response.setTotalCartonCount(task.getTotalCartonCount());
        response.setItems(items == null ? List.of() : items);
        response.setCreatedAt(task.getCreatedAt());
        return response;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getPrintNo() { return printNo; }
    public void setPrintNo(String printNo) { this.printNo = printNo; }
    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public LocalDate getShippingDate() { return shippingDate; }
    public void setShippingDate(LocalDate shippingDate) { this.shippingDate = shippingDate; }
    public String getInvoiceNos() { return invoiceNos; }
    public void setInvoiceNos(String invoiceNos) { this.invoiceNos = invoiceNos; }
    public String getDevelopmentNos() { return developmentNos; }
    public void setDevelopmentNos(String developmentNos) { this.developmentNos = developmentNos; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer itemCount) { this.itemCount = itemCount; }
    public Integer getTotalPairs() { return totalPairs; }
    public void setTotalPairs(Integer totalPairs) { this.totalPairs = totalPairs; }
    public Integer getTotalCartonCount() { return totalCartonCount; }
    public void setTotalCartonCount(Integer totalCartonCount) { this.totalCartonCount = totalCartonCount; }
    public List<ShippingNoteItemRequest> getItems() { return items; }
    public void setItems(List<ShippingNoteItemRequest> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
