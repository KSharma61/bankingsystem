package com.sharma.bank.model;

import java.math.BigDecimal ;
import java.time.LocalDateTime;

public class Account
{
    private int accountId;          // matches accounts.account_id
    private int userId;             // FK -> users.user_id
    private String accountNumber;   // e.g., "AC-1731881234567"
    private String accountType;     // e.g., "SAVINGS", "CURRENT"
    private BigDecimal balance;     // money, exact
    private String status;          // ACTIVE, CLOSED, FROZEN
    private LocalDateTime createdAt;
    
    public Account(){}
    
    // Constructor for creating a NEW account (before DB assigns id & createdAt)
    public Account(int userId, String accountNumber, String accountType, BigDecimal balance, String status)
    {
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.status = status;
    }

    // Constructor for reading an existing account FROM the DB
    public Account(int accountId, int userId, String accountNumber, String accountType, BigDecimal balance, String status, LocalDateTime createdAt) 
    {
        this.accountId = accountId;
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.balance = balance;
        this.status = status;
        this.createdAt = createdAt;
    }

       public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}