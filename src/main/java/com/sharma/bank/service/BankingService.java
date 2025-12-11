package com.sharma.bank.service;

import com.sharma.bank.dao.AccountDAO;
import com.sharma.bank.dao.TransactionDAO;
import com.sharma.bank.model.Account;
import com.sharma.bank.model.Transaction;
import com.sharma.bank.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

public class BankingService {

    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    public BankingService() {
        this.accountDAO = new AccountDAO();
        this.transactionDAO = new TransactionDAO();
    }

    // ===========================
    // DEPOSIT MONEY INTO ACCOUNT
    // ===========================
    public boolean deposit(int accountId, BigDecimal amount, String description) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("❌ Deposit amount must be positive.");
            return false;
        }

        Account account = accountDAO.getAccountById(accountId);
        if (account == null) {
            System.out.println("❌ Account not found for id: " + accountId);
            return false;
        }

        BigDecimal newBalance = account.getBalance().add(amount);

        boolean balanceUpdated = accountDAO.updateBalance(accountId, newBalance);
        if (!balanceUpdated) {
            System.out.println("❌ Failed to update balance.");
            return false;
        }

        Transaction tx = new Transaction(
                accountId,
                amount,
                "DEPOSIT",
                description
        );

        boolean txCreated = transactionDAO.createTransaction(tx);
        if (!txCreated) {
            System.out.println("⚠️ Balance updated but transaction log failed.");
            return false;
        }

        System.out.println("✅ Deposit successful. New balance: " + newBalance);
        return true;
    }

    // ===========================
    // WITHDRAW MONEY FROM ACCOUNT
    // ===========================
    public boolean withdraw(int accountId, BigDecimal amount, String description) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("❌ Withdrawal amount must be positive.");
            return false;
        }

        Account account = accountDAO.getAccountById(accountId);
        if (account == null) {
            System.out.println("❌ Account not found for id: " + accountId);
            return false;
        }

        BigDecimal currentBalance = account.getBalance();
        if (currentBalance.compareTo(amount) < 0) {
            System.out.println("❌ Insufficient funds. Current balance: " + currentBalance);
            return false;
        }

        BigDecimal newBalance = currentBalance.subtract(amount);

        boolean balanceUpdated = accountDAO.updateBalance(accountId, newBalance);
        if (!balanceUpdated) {
            System.out.println("❌ Failed to update balance.");
            return false;
        }

        Transaction tx = new Transaction(
                accountId,
                amount,
                "WITHDRAWAL",
                description
        );

        boolean txCreated = transactionDAO.createTransaction(tx);
        if (!txCreated) {
            System.out.println("⚠️ Balance updated but transaction log failed.");
            return false;
        }

        System.out.println("✅ Withdrawal successful. New balance: " + newBalance);
        return true;
    }

    // ===========================
    // TRANSFER BETWEEN ACCOUNTS
    // ===========================
    public boolean transfer(int fromAccountId, int toAccountId,
                            BigDecimal amount, String description) {

        if (fromAccountId == toAccountId) {
            System.out.println("❌ Cannot transfer to the same account.");
            return false;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("❌ Transfer amount must be positive.");
            return false;
        }

        Connection conn = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);  // start manual transaction

            // Load both accounts using the same connection
            Account fromAccount = accountDAO.getAccountById(conn, fromAccountId);
            Account toAccount   = accountDAO.getAccountById(conn, toAccountId);

            if (fromAccount == null) {
                System.out.println("❌ Source account not found: " + fromAccountId);
                conn.rollback();
                return false;
            }

            if (toAccount == null) {
                System.out.println("❌ Destination account not found: " + toAccountId);
                conn.rollback();
                return false;
            }

            BigDecimal fromBalance = fromAccount.getBalance();
            if (fromBalance.compareTo(amount) < 0) {
                System.out.println("❌ Insufficient funds in source account. Balance: " + fromBalance);
                conn.rollback();
                return false;
            }

            BigDecimal newFromBalance = fromBalance.subtract(amount);
            BigDecimal newToBalance   = toAccount.getBalance().add(amount);

            boolean fromUpdated = accountDAO.updateBalance(conn, fromAccountId, newFromBalance);
            boolean toUpdated   = accountDAO.updateBalance(conn, toAccountId, newToBalance);

            if (!fromUpdated || !toUpdated) {
                System.out.println("❌ Failed to update one or both account balances.");
                conn.rollback();
                return false;
            }

            Transaction withdrawTx = new Transaction(
                    fromAccountId,
                    amount,
                    "TRANSFER_OUT",
                    description + " (to " + toAccount.getAccountNumber() + ")"
            );

            Transaction depositTx = new Transaction(
                    toAccountId,
                    amount,
                    "TRANSFER_IN",
                    description + " (from " + fromAccount.getAccountNumber() + ")"
            );

            boolean tx1 = transactionDAO.createTransaction(conn, withdrawTx);
            boolean tx2 = transactionDAO.createTransaction(conn, depositTx);

            if (!tx1 || !tx2) {
                System.out.println("❌ Failed to log one or both transfer transactions.");
                conn.rollback();
                return false;
            }

            conn.commit();

            System.out.println("✅ Transfer successful. " + amount +
                    " moved from " + fromAccount.getAccountNumber() +
                    " to " + toAccount.getAccountNumber());
            System.out.println("   New balances -> FROM: " + newFromBalance +
                    " | TO: " + newToBalance);

            return true;

        } catch (SQLException e) {
            System.out.println("ERROR DURING TRANSFER:");
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}