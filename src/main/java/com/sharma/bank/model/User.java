package com.sharma.bank.model;

import java.time.LocalDateTime;

public class User 
{
    private int userId;
    private String fullName;
    private String email;
    private String passwordHash;
    private LocalDateTime createdAt;

    public User(){}
    public User(String fullName, String email, String passwordHash)
    {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
    }
     public User(int userId, String fullName, String email, String passwordHash, LocalDateTime createdAt) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }
        // Getters and setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
}
