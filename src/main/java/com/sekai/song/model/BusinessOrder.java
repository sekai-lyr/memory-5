package com.sekai.song.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class BusinessOrder {
    private Long id;
    private String customerName;
    private String contact;
    private String servicePlan;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private String paymentStatus;
    private String status;
    private LocalDate dueDate;
    private String note;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getServicePlan() {
        return servicePlan;
    }

    public void setServicePlan(String servicePlan) {
        this.servicePlan = servicePlan;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public BigDecimal getRemainingAmount() {
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        BigDecimal remaining = total.subtract(paid);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }

    public String getStatusLabel() {
        return switch (status == null ? "" : status) {
            case "QUOTED" -> "已报价";
            case "IN_PROGRESS" -> "交付中";
            case "DELIVERED" -> "已交付";
            case "CLOSED" -> "已结清";
            case "CANCELED" -> "已取消";
            default -> "未设置";
        };
    }

    public String getPaymentStatusLabel() {
        return switch (paymentStatus == null ? "" : paymentStatus) {
            case "UNPAID" -> "未收款";
            case "DEPOSIT" -> "已收定金";
            case "PAID" -> "已结清";
            case "REFUNDED" -> "已退款";
            default -> "未设置";
        };
    }
}
