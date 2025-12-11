package com.sharma.bank;

import com.sharma.bank.dao.UserDAO;
import com.sharma.bank.model.User;

public class App 
{
    public static void main( String[] args )
    {
        UserDAO dao = new UserDAO();
        User user = new User("Kunj C. Sharma", "kunjcsharma69@gmail.com", "Kunj@6161");
        //for new user.
        boolean result = dao.createUser(user);
        if(result)
        {
            System.out.println("🎉 User added successfully!");
        }
        else
        {
            System.out.println("❌ Failed to add user.");
        }
        //for login user.
        boolean loginOk = dao.login("kunjcsharma69@gmail.com", "Kunj@6161");
        System.out.println("Login with correct password: " + loginOk);
        boolean loginWrong = dao.login("kunjcsharma@gmail.com", "kunj@6161");
        System.out.println("Login with WRONG password: " + loginWrong);
    }
}
