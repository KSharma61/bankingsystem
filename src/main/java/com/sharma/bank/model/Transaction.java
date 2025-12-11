package com.sharma.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    private int transactionId;
    private int accountId;
    private BigDecimal amount;
    private String transactionType;   // "DEPOSIT", "WITHDRAWAL"
    private String description;
    private LocalDateTime createdAt;

    public Transaction() {}

    // Constructor for creating a NEW transaction (before DB gives us ID + createdAt)
    public Transaction(int accountId,
                       BigDecimal amount,
                       String transactionType,
                       String description) {
        this.accountId = accountId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.description = description;
    }

    // Constructor for reading an existing transaction FROM DB
    public Transaction(int transactionId,
                       int accountId,
                       BigDecimal amount,
                       String transactionType,
                       String description,
                       LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.description = description;
        this.createdAt = createdAt;
    }

    // Getters and setters

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}