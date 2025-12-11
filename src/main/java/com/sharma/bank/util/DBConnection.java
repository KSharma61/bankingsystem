package com.sharma.bank.util;

import java.sql.Connection; //Represents an open channel from Java → PostgreSQL.
import java.sql.DriverManager; //It acts like a gateway to the DB.
import java.sql.SQLException;

public class DBConnection {

    // 1. Connection details for your PostgreSQL database
    private static final String URL = "jdbc:postgresql://localhost:5432/bank_app";
    /*
        This is the address Java uses to locate the PostgreSQL database.
        Breakdown:
        jdbc: → using Java Database Connectivity
        postgresql → which driver to use
        localhost → database server is on your own computer
        5432 → default PostgreSQL port
        bank_app → name of the database you want to connect to
     */
    private static final String USER = "bank_user";
    private static final String PASSWORD = "bank_pass";

    // 2. This method returns a live connection object
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // 3. Simple test method (optional)
    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            if (conn != null) {
                System.out.println("✅ Connected to database successfully!");
            } else { 
                System.out.println("❌ Failed to connect.");
            }
        } catch (SQLException e) {
            System.out.println("Database connection error:");
            e.printStackTrace();
        }
    }
}
