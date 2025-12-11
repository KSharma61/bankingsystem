package com.sharma.bank.dao;

import com.sharma.bank.model.Account;
import com.sharma.bank.util.DBConnection;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccountDAO
{

    // Simple helper to generate an account number
    // Later you can replace this with a more "bank-like" format
    public String generateAccountNumber()
    {
        long timestamp = System.currentTimeMillis();
        return "AC-"+timestamp;
    }
// 1) Create a new account in the DB
public boolean createAccount(Account account)
{
    String sql = "INSERT INTO accounts (user_id, account_number, account_type, balance, status)" + "VALUES(?,?,?,?,?)";
    try(Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql))
    {
        stmt.setInt(1, account.getUserId());
        stmt.setString(2, account.getAccountNumber());
        stmt.setString(3, account.getAccountType());
        stmt.setBigDecimal(4, account.getBalance());
        stmt.setString(5, account.getStatus());

        int rows = stmt.executeUpdate();
        return rows > 0; 
    }
    catch(SQLException e)
    {
        System.out.println("ERROR CREATING ACCOUNT:");
        e.printStackTrace();
        return false;
    }
}

// 2) Get all accounts for a specific user getAccountsByUserId
public List<Account> getAccountsByUserId(int userId)
{
    String sql = "SELECT account_id, account_number, account_type, balance, status, created_at From accounts WHERE user_id = ? ORDER BY created_at";
    List<Account> accounts = new ArrayList<>();
    try(Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql))
    {
        stmt.setInt(1, userId);
        try(ResultSet rs = stmt.executeQuery())
        {
            while(rs.next())
            {
                int accountId = rs.getInt("account_id");
                String accNumber = rs.getString("account_number");
                String accType = rs.getString("account_type");
                BigDecimal balance = rs.getBigDecimal("balance");
                String status = rs.getString("status");
                LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();

                Account acc = new Account(accountId, userId, accNumber, accType, balance, status, createdAt);
                accounts.add(acc);
            }
        }
    }
    catch (SQLException e) 
    {
        System.out.println("ERROR FETCHING ACCOUNTS:");
        e.printStackTrace();
    }
    return accounts;
}

// 3) Update account balance (used later for deposit/withdraw)
public boolean updateBalance(int accountId, BigDecimal newBalance)
{
    String sql = "UPDATE accounts SET balance = ? WHERE account_id = ?";
    try(Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql))
    {
        stmt.setBigDecimal(1, newBalance);
        stmt.setInt(2, accountId);

        int rows = stmt.executeUpdate();
        return rows > 0;
    }
    catch(SQLException e)
    {
        System.out.println("ERROR UPDATING BALANCE:");
        e.printStackTrace();
        return false;
    }

}
public Account getAccountById(int accountId) {
    String sql = "SELECT account_id, user_id, account_number, account_type, " +
                 "balance, status, created_at " +
                 "FROM accounts WHERE account_id = ?";

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setInt(1, accountId);

        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                int userId = rs.getInt("user_id");
                String accNumber = rs.getString("account_number");
                String accType = rs.getString("account_type");
                BigDecimal balance = rs.getBigDecimal("balance");
                String status = rs.getString("status");
                LocalDateTime createdAt =
                        rs.getTimestamp("created_at").toLocalDateTime();

                return new Account(
                        accountId,
                        userId,
                        accNumber,
                        accType,
                        balance,
                        status,
                        createdAt
                );
            }
        }

    } catch (SQLException e) {
        System.out.println("ERROR FETCHING ACCOUNT BY ID:");
        e.printStackTrace();
    }
    return null;
}
}