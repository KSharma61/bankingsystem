package com.sharma.bank.dao;

import com.sharma.bank.model.Transaction;
import com.sharma.bank.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    // 1) Insert a new transaction into the DB
    public boolean createTransaction(Transaction transaction) {
        String sql = "INSERT INTO transactions " +
                     "(account_id, amount, transaction_type, description) " +
                     "VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, transaction.getAccountId());
            stmt.setBigDecimal(2, transaction.getAmount());
            stmt.setString(3, transaction.getTransactionType());
            stmt.setString(4, transaction.getDescription());

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.out.println("ERROR CREATING TRANSACTION:");
            e.printStackTrace();
            return false;
        }
    }

    // 2) Get all transactions for a specific account
    public List<Transaction> getTransactionsByAccountId(int accountId) {
        String sql = "SELECT transaction_id, account_id, amount, transaction_type, " +
                     "description, created_at " +
                     "FROM transactions " +
                     "WHERE account_id = ? " +
                     "ORDER BY created_at DESC";

        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, accountId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int transactionId = rs.getInt("transaction_id");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    String type = rs.getString("transaction_type");
                    String description = rs.getString("description");
                    LocalDateTime createdAt =
                            rs.getTimestamp("created_at").toLocalDateTime();

                    Transaction t = new Transaction(
                            transactionId,
                            accountId,
                            amount,
                            type,
                            description,
                            createdAt
                    );

                    transactions.add(t);
                }
            }

        } catch (SQLException e) {
            System.out.println("ERROR FETCHING TRANSACTIONS:");
            e.printStackTrace();
        }

        return transactions;
    }
}
