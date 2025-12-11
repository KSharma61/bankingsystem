package com.sharma.bank.dao;

import com.sharma.bank.model.User;
import com.sharma.bank.util.DBConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDAO {

    // ------------------------
    // 1) Create / register user
    // ------------------------
    public boolean createUser(User user) {
        String sql = "INSERT INTO users (full_name, email, password_hash) VALUES (?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Set full_name and email
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getEmail());

            // Hash plain password using BCrypt before storing
            String hashedPassword = BCrypt.hashpw(user.getPasswordHash(), BCrypt.gensalt());
            stmt.setString(3, hashedPassword);

            int rowsInserted = stmt.executeUpdate();
            return rowsInserted > 0;   // true if at least 1 row inserted

        } catch (SQLException e) {
            System.out.println("Error inserting user:");
            e.printStackTrace();
            return false;
        }
    }

    // ------------------------
    // 2) Fetch user by email
    // ------------------------
    public User getUserByEmail(String email) {
        String sql = "SELECT user_id, full_name, email, password_hash, created_at FROM users WHERE email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int userId = rs.getInt("user_id");
                    String fullName = rs.getString("full_name");
                    String emailFromDb = rs.getString("email");
                    String passwordHash = rs.getString("password_hash");
                    java.time.LocalDateTime createdAt =
                            rs.getTimestamp("created_at").toLocalDateTime();

                    return new User(userId, fullName, emailFromDb, passwordHash, createdAt);
                }
            }

        } catch (SQLException e) {
            System.out.println("Error fetching user:");
            e.printStackTrace();
        }

        return null; // user not found
    }

    // ------------------------
    // 3) Check login
    // ------------------------
    public boolean login(String email, String password) {
        User user = getUserByEmail(email);
        if (user == null) {
            return false;   // no such email
        }

        // Compare plain password with stored hash
        return BCrypt.checkpw(password, user.getPasswordHash());
    }
}
