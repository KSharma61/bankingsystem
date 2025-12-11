package com.sharma.bank;

import com.sharma.bank.dao.UserDAO;
import com.sharma.bank.model.User;
import com.sharma.bank.dao.AccountDAO;
import com.sharma.bank.dao.TransactionDAO;
import com.sharma.bank.model.Account;
import com.sharma.bank.model.Transaction;
import com.sharma.bank.service.BankingService;

import java.math.BigDecimal;
import java.util.List;

public class App {
    public static void main(String[] args) 
    {
        // -------------------------
        // USER CREATION + LOGIN
        // -------------------------

        UserDAO dao = new UserDAO();

        // Only needed first time to insert user.
        // After that, you can leave this commented out.
        /*
        User user = new User("Kunj C. Sharma", "kunjcsharma69@gmail.com", "Kunj@6161");
        boolean result = dao.createUser(user);
        if (result) {
            System.out.println("🎉 User added successfully!");
        } else {
            System.out.println("❌ Failed to add user.");
        }
        */

        // Login test (uses existing user in DB)
        boolean loginOk = dao.login("kunjcsharma69@gmail.com", "Kunj@6161");
        System.out.println("Login with correct password: " + loginOk);

        // -------------------------
        // ACCOUNT SECTION
        // -------------------------

        AccountDAO accountDAO = new AccountDAO();

        // Find the user by correct email
        User existingUser = dao.getUserByEmail("kunjcsharma69@gmail.com");

        if (existingUser == null) {
            System.out.println("❌ Cannot create account: user not found.");
            return;
        }

        // Generate account number
        String accNumber = accountDAO.generateAccountNumber();

        // Create account object
        Account acc = new Account(
                existingUser.getUserId(),
                accNumber,
                "SAVINGS",
                new BigDecimal("5000.00"),
                "ACTIVE"
        );

        // Insert into DB
        boolean accCreated = accountDAO.createAccount(acc);

        if (accCreated) {
            System.out.println("✅ Account created: " + accNumber);
        } else {
            System.out.println("❌ Failed to create account.");
        }

        // Fetch all accounts for this user
        List<Account> accounts = accountDAO.getAccountsByUserId(existingUser.getUserId());
        System.out.println("\n📄 Accounts for " + existingUser.getFullName() + ":");

        for (Account a : accounts) {
            System.out.println(
                    "- " + a.getAccountNumber()
                    + " | Type: " + a.getAccountType()
                    + " | Balance: " + a.getBalance()
                    + " | Status: " + a.getStatus()
                    + " | Created: " + a.getCreatedAt()
            );
        }

        // ---------------------------
        // DEPOSIT & WITHDRAW TESTS
        // ---------------------------

        if (accounts.isEmpty()) {
            System.out.println("User has no accounts, cannot test deposit/withdraw.");
            return;
        }

        // Pick the first account
        Account firstAccount = accounts.get(0);

        // Create service
        BankingService bankingService = new BankingService();

        // Test: deposit 1000.00 into first account
        bankingService.deposit(
                firstAccount.getAccountId(),
                new BigDecimal("1000.00"),
                "Learning deposit"
        );

        // Show transaction history after deposit
        TransactionDAO transactionDAO = new TransactionDAO();
        List<Transaction> history =
                transactionDAO.getTransactionsByAccountId(firstAccount.getAccountId());

        System.out.println("\n📜 Transaction history for account "
                + firstAccount.getAccountNumber() + " (after deposit):");
        for (Transaction t : history) {
            System.out.println(
                    "- [" + t.getTransactionType() + "] "
                    + t.getAmount()
                    + " on " + t.getCreatedAt()
                    + " | " + t.getDescription()
            );
        }

        // ---------------------------
        // WITHDRAW TEST USING SERVICE
        // ---------------------------

        bankingService.withdraw(
                firstAccount.getAccountId(),
                new BigDecimal("300.00"),
                "Test withdrawal"
        );

        // Reload and show transaction history after withdrawal
        history = transactionDAO.getTransactionsByAccountId(firstAccount.getAccountId());

        System.out.println("\n📜 Transaction history for account "
                + firstAccount.getAccountNumber() + " (after withdrawal):");
        for (Transaction t : history) {
            System.out.println(
                    "- [" + t.getTransactionType() + "] "
                    + t.getAmount()
                    + " on " + t.getCreatedAt()
                    + " | " + t.getDescription()
            );
        }

        // ---------------------------
        // TRANSFER TEST USING SERVICE
        // ---------------------------

        if (accounts.size() < 2) {
            System.out.println("\n⚠️ Need at least 2 accounts to test transfer.");
            return;
        }

        Account fromAccount = accounts.get(0);
        Account toAccount   = accounts.get(1);

        System.out.println("\n🔁 Testing transfer of 200.00 from "
                + fromAccount.getAccountNumber() + " to " + toAccount.getAccountNumber());

        bankingService.transfer(
                fromAccount.getAccountId(),
                toAccount.getAccountId(),
                new BigDecimal("200.00"),
                "Test transfer"
        );

        // Reload accounts and show updated balances
        accounts = accountDAO.getAccountsByUserId(existingUser.getUserId());
        System.out.println("\n📄 Accounts for " + existingUser.getFullName() + " (after transfer):");
        for (Account a : accounts) {
            System.out.println(
                    "- " + a.getAccountNumber()
                    + " | Balance: " + a.getBalance()
                    + " | Status: " + a.getStatus()
            );
        }
    }
}