package com.sharma.bank;

import com.sharma.bank.ui.MainUI;
import javafx.application.Application;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        new MainUI().start(stage);
    }

    public static void main(String[] args) 
    {
        launch(args);
    }
}
