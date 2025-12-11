package com.sharma.bank.service;

import com.sharma.bank.dao.AccountDAO;
import com.sharma.bank.dao.TransactionDAO;
import com.sharma.bank.model.Account;
import com.sharma.bank.model.Transaction;

import java.math.BigDecimal;

public class BankingService {

    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    public BankingService() {
        this.accountDAO = new AccountDAO();
        this.transactionDAO = new TransactionDAO();
    }

    // Deposit money into an account
    public boolean deposit(int accountId, BigDecimal amount, String description) {

        // 1) Basic validation
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.out.println("❌ Deposit amount must be positive.");
            return false;
        }

        // 2) Load account from DB
        Account account = accountDAO.getAccountById(accountId);
        if (account == null) {
            System.out.println("❌ Account not found for id: " + accountId);
            return false;
        }

        // 3) Calculate new balance
        BigDecimal newBalance = account.getBalance().add(amount);

        // 4) Update accounts table
        boolean balanceUpdated = accountDAO.updateBalance(accountId, newBalance);
        if (!balanceUpdated) {
            System.out.println("❌ Failed to update balance.");
            return false;
        }

        // 5) Log the transaction in transactions table
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
}
