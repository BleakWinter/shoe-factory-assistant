package com.shoefactory.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("monthly_settlement")
public class MonthlySettlement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String billMonth;
    private int revision;
    private String draftJson;
    private String snapshotJson;
    private LocalDateTime confirmedAt;
    private LocalDate receivedOn;

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getBillMonth() { return billMonth; }
    public void setBillMonth(String billMonth) { this.billMonth = billMonth; }
    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }
    public String getDraftJson() { return draftJson; }
    public void setDraftJson(String draftJson) { this.draftJson = draftJson; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public void setReceivedOn(LocalDate receivedOn) { this.receivedOn = receivedOn; }
}
