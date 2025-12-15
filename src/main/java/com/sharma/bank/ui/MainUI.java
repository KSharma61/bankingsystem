package com.sharma.bank.ui;
import com.sharma.bank.dao.AccountDAO;
import com.sharma.bank.dao.TransactionDAO;
import com.sharma.bank.dao.UserDAO;
import com.sharma.bank.model.Account;
import com.sharma.bank.model.User;
import com.sharma.bank.model.Transaction;
import com.sharma.bank.service.BankingService;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class MainUI extends Application 
{

    private Stage stage;
    private Scene scene;

    // Root that can swap screens (Login <-> Dashboard)
    private StackPane sceneRoot;

    // App shell root
    private BorderPane appRoot;

    // Sidebar
    private VBox sidebar;
    private boolean sidebarCollapsed = false;

    // Page title
    private Label pageTitle;

    // DAO
    private final UserDAO userDAO = new UserDAO();

    private final AccountDAO accountDAO = new AccountDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    private List<Account> userAccounts;   // accounts of the logged-in user
    
    private final BankingService bankingService = new BankingService();
    // Keep logged-in user (use later to load accounts/transactions)
    private User loggedInUser;

    // Simple email validation
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @Override
    public void start(Stage stage) 
    {
        this.stage = stage;

        sceneRoot = new StackPane();
        sceneRoot.getStyleClass().add("appRoot");

        // First screen: Login
        sceneRoot.getChildren().setAll(buildLoginScreen());

        scene = new Scene(sceneRoot, 1200, 760);

        // Load CSS
        var css = getClass().getResource("/styles/main.css");
        if (css == null) {
            System.out.println("❌ CSS NOT FOUND: /styles/main.css");
        } else {
            scene.getStylesheets().add(css.toExternalForm());
            System.out.println("✅ CSS loaded: " + css);
        }

        stage.setTitle("LunarOne Finance");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }


    private Account pickAccountForCard(String... typeKeywords) 
    {
        if (userAccounts == null) return null;

        for (Account a : userAccounts) {
            if (a.getAccountType() == null) continue;
            String t = a.getAccountType().toUpperCase().trim();

            for (String k : typeKeywords) {
                if (t.contains(k.toUpperCase())) {
                    return a;
                }
            }
        }
        return null;
    }


    private VBox buildTransactionsPanelReal(Account chequing, Account savings) 
    {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("panel");
        panel.setPadding(new Insets(16));

        Label title = new Label("Recent Transactions");
        title.getStyleClass().add("panelTitle");

        // -------------------------
        // Account selector (NEW)
        // -------------------------
        ComboBox<Account> accountBox = new ComboBox<>();
        accountBox.setItems(FXCollections.observableArrayList(userAccounts == null ? List.of() : userAccounts));

        accountBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? "" : a.getAccountNumber() + " (" + a.getAccountType() + ")");
            }
        });
        accountBox.setButtonCell(accountBox.getCellFactory().call(null));

        // Default selection: Chequing if exists else first account
        Account defaultAcc = chequing != null ? chequing
                : (userAccounts != null && !userAccounts.isEmpty() ? userAccounts.get(0) : null);
        accountBox.setValue(defaultAcc);

        HBox selectorRow = new HBox(10, new Label("Account:"), accountBox);
        selectorRow.setAlignment(Pos.CENTER_LEFT);
        selectorRow.getStyleClass().add("accountSelectorRow");

        // -------------------------
        // Transactions table
        // -------------------------
        TableView<TxRow> table = new TableView<>();
        table.getStyleClass().add("txTable");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(40);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<TxRow, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> d.getValue().type);

        TableColumn<TxRow, String> colAmt = new TableColumn<>("Amount");
        colAmt.setCellValueFactory(d -> d.getValue().amount);

        TableColumn<TxRow, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(d -> d.getValue().desc);

        table.getColumns().addAll(colType, colAmt, colDesc);

        Runnable reloadTable = () -> {
            Account selected = accountBox.getValue();
            if (selected == null) {
                table.setItems(FXCollections.observableArrayList(
                        new TxRow("INFO", "$0.00", "No account selected")
                ));
                return;
            }

            List<Transaction> txs = transactionDAO.getTransactionsByAccountId(selected.getAccountId());
            txs.sort(Comparator.comparing(Transaction::getCreatedAt).reversed());

            var rows = FXCollections.<TxRow>observableArrayList();
            for (int i = 0; i < Math.min(10, txs.size()); i++) {
                Transaction t = txs.get(i);
                rows.add(new TxRow(
                        safe(t.getTransactionType()),
                        money(t.getAmount()),
                        safe(t.getDescription())
                ));
            }

            if (rows.isEmpty()) {
                rows.add(new TxRow("INFO", "$0.00", "No transactions yet"));
            }

            table.setItems(rows);
        };

        // load once
        reloadTable.run();

        // reload when user changes account
        accountBox.valueProperty().addListener((obs, o, n) -> reloadTable.run());

        // -------------------------
        // Action buttons
        // -------------------------
        Button depositBtn = new Button("Deposit");
        depositBtn.getStyleClass().add("primaryBtn");

        Button withdrawBtn = new Button("Withdraw");
        withdrawBtn.getStyleClass().add("secondaryBtn");

        Button transferBtn = new Button("Transfer");
        transferBtn.getStyleClass().add("secondaryBtn");

        depositBtn.setOnAction(e -> {
            Account selected = accountBox.getValue();
            if (selected == null) {
                showSimpleAlert("No account", "Select an account first.");
                return;
            }

            Optional<TxInput> input = showAmountDialog("Deposit", "Enter deposit amount + description");
            if (input.isEmpty()) return;

            boolean ok = bankingService.deposit(selected.getAccountId(), input.get().amount, input.get().description);
            if (ok) {
                refreshDashboardData();
                reloadTable.run();
            } else {
                showSimpleAlert("Deposit failed", "Deposit did not complete.");
            }
        });

        withdrawBtn.setOnAction(e -> {
            Account selected = accountBox.getValue();
            if (selected == null) {
                showSimpleAlert("No account", "Select an account first.");
                return;
            }

            Optional<TxInput> input = showAmountDialog("Withdraw", "Enter withdrawal amount + description");
            if (input.isEmpty()) return;

            boolean ok = bankingService.withdraw(selected.getAccountId(), input.get().amount, input.get().description);
            if (ok) {
                refreshDashboardData();
                reloadTable.run();
            } else {
                showSimpleAlert("Withdrawal failed", "Not enough funds or system error.");
            }
        });

        transferBtn.setOnAction(e -> {
            if (userAccounts == null || userAccounts.size() < 2) {
                showSimpleAlert("Transfer not possible", "You need at least 2 accounts to test transfer.");
                return;
            }

            Optional<TransferInput> input = showTransferDialog();
            if (input.isEmpty()) return;

            boolean ok = bankingService.transfer(
                    input.get().fromAccountId,
                    input.get().toAccountId,
                    input.get().amount,
                    input.get().description
            );

            if (ok) {
                refreshDashboardData();
                reloadTable.run();
            } else {
                showSimpleAlert("Transfer failed", "Transfer did not complete.");
            }
        });

        HBox actions = new HBox(10, depositBtn, withdrawBtn, transferBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        panel.getChildren().addAll(title, selectorRow, table, actions);
        return panel;
    }


    private String money(BigDecimal value) 
    {
        if (value == null) value = BigDecimal.ZERO;
        return "$" + value.setScale(2, BigDecimal.ROUND_HALF_UP);
    }


    private String safe(String s) 
    {
        return (s == null || s.isBlank()) ? "-" : s;
    }
    /* =========================
       LOGIN SCREEN (CONNECTED)
       ========================= */

    private Node buildLoginScreen() 
    {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("loginRoot");

        // Top bar (brand)
        HBox top = new HBox();
        top.getStyleClass().add("loginTopBar");
        top.setPadding(new Insets(16, 18, 16, 18));

        VBox brandBox = new VBox();
        brandBox.getStyleClass().add("brandBox");
        Label brandTop = new Label("LunarOne");
        brandTop.getStyleClass().add("brandTop");
        Label brandBottom = new Label("Finance.");
        brandBottom.getStyleClass().add("brandBottom");
        brandBox.getChildren().addAll(brandTop, brandBottom);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(brandBox, spacer);

        // Center area
        HBox center = new HBox(24);
        center.setPadding(new Insets(40));
        center.setAlignment(Pos.CENTER);
        center.getStyleClass().add("loginCenter");

        // Left panel
        VBox leftPanel = new VBox(12);
        leftPanel.getStyleClass().add("heroPanel");
        leftPanel.setPadding(new Insets(28));
        leftPanel.setAlignment(Pos.TOP_LEFT);
        leftPanel.setMinHeight(520);

        Label heroSmall = new Label("Modern banking features you can show in a portfolio");
        heroSmall.getStyleClass().add("heroSmall");

        Region heroSpacer = new Region();
        VBox.setVgrow(heroSpacer, Priority.ALWAYS);

        Label heroBig = new Label("Welcome!");
        heroBig.getStyleClass().add("heroBig");

        leftPanel.getChildren().addAll(heroSmall, heroSpacer, heroBig);

        // Login card
        VBox loginCard = new VBox(12);
        loginCard.getStyleClass().add("authCard");
        loginCard.setPadding(new Insets(24));
        loginCard.setAlignment(Pos.TOP_LEFT);
        loginCard.setMaxWidth(380);

        Label title = new Label("Log in");
        title.getStyleClass().add("authTitle");

        Label subtitle = new Label("Use your credentials to access your account.");
        subtitle.getStyleClass().add("authSub");

        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        emailField.getStyleClass().add("authField");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");
        passField.getStyleClass().add("authField");

        Label error = new Label("");
        error.getStyleClass().add("authError");
        error.setManaged(false);
        error.setVisible(false);

        Button loginBtn = new Button("Log in");
        loginBtn.getStyleClass().add("authPrimaryBtn");
        loginBtn.setMaxWidth(Double.MAX_VALUE);

        Button signupBtn = new Button("Sign up");
        signupBtn.getStyleClass().add("authSecondaryBtn");
        signupBtn.setMaxWidth(Double.MAX_VALUE);

        // ---- LOGIN ACTION (REAL DAO) ----
        loginBtn.setOnAction(e -> {
            hideError(error);

            String email = emailField.getText() == null ? "" : emailField.getText().trim();
            String pass = passField.getText() == null ? "" : passField.getText();

            // 1) Validate input
            if (email.isEmpty() || pass.isEmpty()) {
                showError(error, "Please enter email and password.");
                return;
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                showError(error, "Please enter a valid email address.");
                return;
            }

            // 2) Check user exists (so we can give clear message)
            User user;
            try {
                user = userDAO.getUserByEmail(email);
            } catch (Exception ex) {
                // DAO usually catches SQL internally, but just in case:
                showError(error, "Login failed due to a system error. Please try again.");
                return;
            }

            if (user == null) {
                showError(error, "No account found for this email. Please sign up first.");
                return;
            }

            // 3) Verify password
            boolean ok;
            try {
                ok = userDAO.login(email, pass);
            } catch (Exception ex) {
                showError(error, "Login failed due to a system error. Please try again.");
                return;
            }

            if (!ok) {
                showError(error, "Incorrect password. Please try again.");
                return;
            }

            // ✅ SUCCESS
            loggedInUser = user;

            // Load accounts for this user right away
            userAccounts = accountDAO.getAccountsByUserId(loggedInUser.getUserId());
            if (userAccounts == null || userAccounts.isEmpty()) 
            {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setHeaderText("No accounts yet");
                a.setContentText("Your login worked, but you have no bank accounts created yet.\n\nWe’ll add an account-creation UI next.");
                a.showAndWait();
            }
            showDashboard();

        });

        // Press Enter in password = login
        passField.setOnAction(e -> loginBtn.fire());

        // ---- SIGNUP ACTION (REAL DAO) ----
        signupBtn.setOnAction(e -> {
            hideError(error);

            Optional<User> created = showSignupDialog();
            if (created.isEmpty()) return;

            User newUser = created.get();

            // Check duplicate email
            if (!EMAIL_PATTERN.matcher(newUser.getEmail()).matches()) {
                showError(error, "Invalid email format. Signup cancelled.");
                return;
            }

            User existing = userDAO.getUserByEmail(newUser.getEmail());
            if (existing != null) {
                showError(error, "An account with this email already exists. Please login.");
                // pre-fill email for convenience
                emailField.setText(newUser.getEmail());
                passField.clear();
                return;
            }

            boolean createdOk = userDAO.createUser(newUser);
            if (!createdOk) {
                showError(error, "Signup failed. Please try again (or check DB connection).");
                return;
            }

            // Success: prefill email, clear password
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText("Account created ✅");
            a.setContentText("Your account has been created. Please login.");
            a.showAndWait();

            emailField.setText(newUser.getEmail());
            passField.clear();
        });

        loginCard.getChildren().addAll(
                title, subtitle,
                emailField, passField,
                error,
                loginBtn,
                new Separator(),
                signupBtn
        );

        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        center.getChildren().addAll(leftPanel, loginCard);

        root.setTop(top);
        root.setCenter(center);

        return root;
    }

    private Optional<User> showSignupDialog() {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Sign up");
        dialog.setHeaderText("Create a new account");

        ButtonType createBtnType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        TextField fullName = new TextField();
        fullName.setPromptText("Full name");

        TextField email = new TextField();
        email.setPromptText("Email");

        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm password");

        Label tip = new Label("Tip: Use a strong password (min 8 characters).");
        tip.setStyle("-fx-text-fill: rgba(0,0,0,0.55); -fx-font-weight: 600;");

        grid.add(new Label("Full Name"), 0, 0);
        grid.add(fullName, 1, 0);

        grid.add(new Label("Email"), 0, 1);
        grid.add(email, 1, 1);

        grid.add(new Label("Password"), 0, 2);
        grid.add(password, 1, 2);

        grid.add(new Label("Confirm"), 0, 3);
        grid.add(confirm, 1, 3);

        grid.add(tip, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Disable Create until something typed
        Node createBtn = dialog.getDialogPane().lookupButton(createBtnType);
        createBtn.setDisable(true);

        Runnable validator = () -> {
            boolean ok =
                    !fullName.getText().trim().isEmpty()
                    && !email.getText().trim().isEmpty()
                    && !password.getText().trim().isEmpty()
                    && !confirm.getText().trim().isEmpty();
            createBtn.setDisable(!ok);
        };

        fullName.textProperty().addListener((obs, o, n) -> validator.run());
        email.textProperty().addListener((obs, o, n) -> validator.run());
        password.textProperty().addListener((obs, o, n) -> validator.run());
        confirm.textProperty().addListener((obs, o, n) -> validator.run());

        dialog.setResultConverter(btn -> {
            if (btn != createBtnType) return null;

            String fn = fullName.getText().trim();
            String em = email.getText().trim();
            String pw = password.getText();

            // Local validations
            if (fn.length() < 2) {
                showSimpleAlert("Invalid name", "Please enter a valid full name.");
                return null;
            }
            if (!EMAIL_PATTERN.matcher(em).matches()) {
                showSimpleAlert("Invalid email", "Please enter a valid email address.");
                return null;
            }
            if (pw.length() < 8) {
                showSimpleAlert("Weak password", "Password must be at least 8 characters.");
                return null;
            }
            if (!pw.equals(confirm.getText())) {
                showSimpleAlert("Password mismatch", "Password and confirm password do not match.");
                return null;
            }

            // IMPORTANT:
            // Your UserDAO expects "passwordHash" field to carry the PLAIN password at creation time,
            // because it hashes it inside createUser().
            return new User(fn, em, pw);
        });

        return dialog.showAndWait();
    }

    private void showSimpleAlert(String header, String text) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
    }

    private void showError(Label errorLabel, String msg) {
        errorLabel.setText(msg);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void hideError(Label errorLabel) {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    /* =========================
       DASHBOARD SHELL
       ========================= */

    private void showDashboard() {
        appRoot = new BorderPane();
        appRoot.getStyleClass().add("appShell");

        appRoot.setTop(buildTopBar());
        appRoot.setLeft(buildSidebar());
        appRoot.setCenter(buildDashboard());

        sceneRoot.getChildren().setAll(appRoot);
    }

    private HBox buildTopBar() {
        Button toggleBtn = new Button("☰");
        toggleBtn.getStyleClass().add("iconBtn");
        toggleBtn.setOnAction(e -> toggleSidebar());

        VBox brandBox = new VBox();
        brandBox.getStyleClass().add("brandBox");
        Label brandTop = new Label("LunarOne");
        brandTop.getStyleClass().add("brandTop");
        Label brandBottom = new Label("Finance.");
        brandBottom.getStyleClass().add("brandBottom");
        brandBox.getChildren().addAll(brandTop, brandBottom);

        HBox left = new HBox(12, toggleBtn, brandBox);
        left.setAlignment(Pos.CENTER_LEFT);

        pageTitle = new Label("Dashboard");
        pageTitle.getStyleClass().add("pageTitle");

        // Optional: show user name once logged in
        Label userLabel = new Label(loggedInUser != null ? "Hi, " + loggedInUser.getFullName() : "");
        userLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-weight: 700;");

        HBox center = new HBox(10, pageTitle, userLabel);
        center.setAlignment(Pos.CENTER);
        HBox.setHgrow(center, Priority.ALWAYS);

        Button logout = new Button("Logout");
        logout.getStyleClass().add("ghostBtn");
        logout.setOnAction(e -> {
            loggedInUser = null;
            sceneRoot.getChildren().setAll(buildLoginScreen());
        });

        HBox right = new HBox(10, logout);
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox top = new HBox(16, left, center, right);
        top.getStyleClass().add("topBar");
        top.setPadding(new Insets(14, 18, 14, 18));
        return top;
    }

    private VBox buildSidebar() {
        sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16));
        sidebar.setPrefWidth(230);

        Label navTitle = new Label("Menu");
        navTitle.getStyleClass().add("sidebarTitle");

        Button dash = navButton("Dashboard");
        Button accounts = navButton("Accounts");
        Button cards = navButton("Cards");
        Button transfer = navButton("Transfer");
        Button cheque = navButton("Cheque Deposit");
        Button interac = navButton("Interac e-transfer");
        Button ai = navButton("AI insights");

        dash.setOnAction(e -> {
            pageTitle.setText("Dashboard");
            appRoot.setCenter(buildDashboard());
        });

        accounts.setOnAction(e -> {
            pageTitle.setText("Accounts");
            appRoot.setCenter(buildAccountsPage());
        });

        cards.setOnAction(e -> {
            pageTitle.setText("Cards (Coming soon)");
            appRoot.setCenter(buildPlaceholder("Cards", "We’ll build cards UI next."));
        });

        transfer.setOnAction(e -> {
            pageTitle.setText("Transfer (Coming soon)");
            appRoot.setCenter(buildPlaceholder("Transfer", "We’ll connect this to BankingService.transfer()."));
        });

        cheque.setOnAction(e -> {
            pageTitle.setText("Cheque Deposit (Coming soon)");
            appRoot.setCenter(buildPlaceholder("Cheque Deposit", "UI first, then backend."));
        });

        interac.setOnAction(e -> {
            pageTitle.setText("Interac e-transfer (Coming soon)");
            appRoot.setCenter(buildPlaceholder("Interac e-transfer", "UI first, then backend."));
        });

        ai.setOnAction(e -> {
            pageTitle.setText("AI insights (Coming soon)");
            appRoot.setCenter(buildPlaceholder("AI insights", "We’ll add charts + analytics later."));
        });

        Separator sep = new Separator();
        sep.getStyleClass().add("sidebarSep");

        sidebar.getChildren().addAll(
                navTitle,
                dash, accounts, cards, transfer,
                sep,
                cheque, interac, ai
        );

        return sidebar;
    }

    private Node buildAccountsPage() 
    {
        VBox wrapper = new VBox(16);
        wrapper.setPadding(new Insets(18));
        wrapper.getStyleClass().add("pageWrapper");

        Label title = new Label("Your Accounts");
        title.getStyleClass().add("panelTitle");

        // Table
        TableView<Account> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(40);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Account, String> colNum = new TableColumn<>("Account #");
        colNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAccountNumber()));

        TableColumn<Account, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAccountType()));

        TableColumn<Account, String> colBal = new TableColumn<>("Balance");
        colBal.setCellValueFactory(d -> new SimpleStringProperty(money(d.getValue().getBalance())));

        TableColumn<Account, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));

        table.getColumns().addAll(colNum, colType, colBal, colStatus);

        Runnable reload = () -> {
            if (loggedInUser == null) return;
            userAccounts = accountDAO.getAccountsByUserId(loggedInUser.getUserId());
            table.setItems(FXCollections.observableArrayList(userAccounts));
        };
        reload.run();

        // Create account controls
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.setItems(FXCollections.observableArrayList("CHEQUING", "SAVINGS"));
        typeBox.setValue("CHEQUING");

        TextField initialBalance = new TextField("0.00");
        initialBalance.setPromptText("Initial balance");

        Button createBtn = new Button("Create Account");
        createBtn.getStyleClass().add("primaryBtn");

        createBtn.setOnAction(e -> {
            if (loggedInUser == null) return;

            BigDecimal bal;
            try {
                bal = new BigDecimal(initialBalance.getText().trim());
                if (bal.compareTo(BigDecimal.ZERO) < 0) {
                    showSimpleAlert("Invalid balance", "Initial balance cannot be negative.");
                    return;
                }
            } catch (Exception ex) {
                showSimpleAlert("Invalid number", "Enter a valid balance like 0.00 or 500.00");
                return;
            }

            String accNumber = accountDAO.generateAccountNumber();

            Account acc = new Account(
                    loggedInUser.getUserId(),
                    accNumber,
                    typeBox.getValue(),
                    bal,
                    "ACTIVE"
            );

            boolean ok = accountDAO.createAccount(acc);
            if (!ok) {
                showSimpleAlert("Failed", "Account could not be created. Check DB.");
                return;
            }

            showSimpleAlert("Success ✅", "Account created: " + accNumber);
            refreshDashboardData();
            reload.run();
        });

        HBox createRow = new HBox(10,
                new Label("Type:"), typeBox,
                new Label("Initial Balance:"), initialBalance,
                createBtn
        );
        createRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, title, createRow, table);
        card.getStyleClass().add("panel");
        card.setPadding(new Insets(16));
        VBox.setVgrow(card, Priority.ALWAYS);

        wrapper.getChildren().add(card);
        return wrapper;
    }

    private Button navButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("navBtn");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;

        if (sidebarCollapsed) {
            sidebar.setPrefWidth(72);
            for (var n : sidebar.getChildren()) {
                if (n instanceof Button btn) {
                    btn.setText("");
                    btn.setMinHeight(42);
                }
                if (n instanceof Label lbl) lbl.setVisible(false);
                if (n instanceof Separator s) s.setVisible(false);
            }
        } else {
            appRoot.setLeft(buildSidebar());
        }
    }

    private Node buildPlaceholder(String title, String desc) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(18));
        box.setAlignment(Pos.TOP_LEFT);

        Label t = new Label(title);
        t.getStyleClass().add("panelTitle");

        Label d = new Label(desc);
        d.getStyleClass().add("muted");

        VBox card = new VBox(10, t, d);
        card.getStyleClass().add("panel");
        card.setPadding(new Insets(18));
        card.setMaxWidth(700);

        box.getChildren().add(card);
        return box;
    }

    /* =========================
       DASHBOARD CONTENT
       ========================= */

    private Node buildDashboard() 
    {
        VBox wrapper = new VBox(18);
        wrapper.setPadding(new Insets(18));
        wrapper.getStyleClass().add("pageWrapper");

        Account chequing = pickAccountForCard("CHEQUING", "CHECKING", "CHEQUING ACCOUNT");
        Account savings  = pickAccountForCard("SAVINGS", "SAVING");

        // Fallbacks if account_type naming isn't exactly matching
        if (chequing == null && userAccounts != null && userAccounts.size() >= 1) chequing = userAccounts.get(0);
        if (savings  == null && userAccounts != null && userAccounts.size() >= 2) savings  = userAccounts.get(1);

        BigDecimal chequingBal = chequing != null ? chequing.getBalance() : BigDecimal.ZERO;
        BigDecimal savingsBal  = savings  != null ? savings.getBalance()  : BigDecimal.ZERO;

        // Credit is dummy for now (until you add a credit/limit table/column)
        BigDecimal creditLimit = new BigDecimal("5000.00");

        HBox cardsRow = new HBox(16,
                balanceCard("Chequing", money(chequingBal), "Total Balance"),
                balanceCard("Savings",  money(savingsBal),  "Total Balance"),
                balanceCard("Credit",   money(creditLimit), "Available Credit")
        );
        for (var n : cardsRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        HBox bottomRow = new HBox(16);
        VBox statsPanel = buildStatsPanel();
        VBox txPanel = buildTransactionsPanelReal(chequing, savings); // ✅ real transactions

        HBox.setHgrow(statsPanel, Priority.ALWAYS);
        HBox.setHgrow(txPanel, Priority.ALWAYS);

        statsPanel.setPrefWidth(700);
        txPanel.setPrefWidth(500);

        bottomRow.getChildren().addAll(statsPanel, txPanel);
        VBox.setVgrow(bottomRow, Priority.ALWAYS);

        wrapper.getChildren().addAll(cardsRow, bottomRow);
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        return wrapper;
    }

    private Pane balanceCard(String title, String amount, String sub) {
        VBox card = new VBox(6);
        card.getStyleClass().add("balanceCard");
        card.setPadding(new Insets(16));
        card.setMinHeight(140);

        Label t = new Label(title);
        t.getStyleClass().add("cardTitle");

        Label a = new Label(amount);
        a.getStyleClass().add("cardAmount");

        Label s = new Label(sub);
        s.getStyleClass().add("cardSub");

        card.getChildren().addAll(t, a, s);
        return card;
    }

    private VBox buildStatsPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("panel");
        panel.setPadding(new Insets(16));

        Label title = new Label("Statistics");
        title.getStyleClass().add("panelTitle");

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.getStyleClass().add("chartBox");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setMinHeight(320);
        VBox.setVgrow(chart, Priority.ALWAYS);

        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.getData().add(new XYChart.Data<>("Mon", 120));
        s.getData().add(new XYChart.Data<>("Tue", 180));
        s.getData().add(new XYChart.Data<>("Wed", 160));
        s.getData().add(new XYChart.Data<>("Thu", 220));
        s.getData().add(new XYChart.Data<>("Fri", 200));
        s.getData().add(new XYChart.Data<>("Sat", 260));
        s.getData().add(new XYChart.Data<>("Sun", 240));
        chart.getData().add(s);

        Label hint = new Label("AI insights & analytics coming soon.");
        hint.getStyleClass().add("muted");

        panel.getChildren().addAll(title, chart, hint);
        return panel;
    }

    private VBox buildTransactionsPanelBox() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("panel");
        panel.setPadding(new Insets(16));

        Label title = new Label("Recent Transactions");
        title.getStyleClass().add("panelTitle");

        TableView<TxRow> table = new TableView<>();
        table.getStyleClass().add("txTable");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(40);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<TxRow, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> d.getValue().type);

        TableColumn<TxRow, String> colAmt = new TableColumn<>("Amount");
        colAmt.setCellValueFactory(d -> d.getValue().amount);

        TableColumn<TxRow, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(d -> d.getValue().desc);

        table.getColumns().addAll(colType, colAmt, colDesc);

        table.setItems(FXCollections.observableArrayList(
                new TxRow("DEPOSIT", "$1000.00", "Learning deposit"),
                new TxRow("WITHDRAW", "$300.00", "Test withdrawal"),
                new TxRow("TRANSFER", "$200.00", "To Savings account")
        ));

        Button deposit = new Button("Deposit");
        deposit.getStyleClass().add("primaryBtn");

        Button withdraw = new Button("Withdraw");
        withdraw.getStyleClass().add("secondaryBtn");

        Button transfer = new Button("Transfer");
        transfer.getStyleClass().add("secondaryBtn");

        HBox actions = new HBox(10, deposit, withdraw, transfer);
        actions.setAlignment(Pos.CENTER_RIGHT);

        panel.getChildren().addAll(title, table, actions);
        return panel;
    }

    private static class TxRow {
        final SimpleStringProperty type = new SimpleStringProperty();
        final SimpleStringProperty amount = new SimpleStringProperty();
        final SimpleStringProperty desc = new SimpleStringProperty();

        TxRow(String t, String a, String d) {
            type.set(t);
            amount.set(a);
            desc.set(d);
        }
    }

    private static class TxInput 
    {
        final BigDecimal amount;
        final String description;

        TxInput(BigDecimal amount, String description) 
        {
            this.amount = amount;
            this.description = description;
        }
    }

    private static class TransferInput 
    {
        final int fromAccountId;
        final int toAccountId;
        final BigDecimal amount;
        final String description;

        TransferInput(int fromId, int toId, BigDecimal amount, String desc) {
            this.fromAccountId = fromId;
            this.toAccountId = toId;
            this.amount = amount;
            this.description = desc;
        }
    }

    private Optional<TxInput> showAmountDialog(String title, String header) 
    {
        Dialog<TxInput> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        ButtonType okType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        TextField amountField = new TextField();
        amountField.setPromptText("Amount (e.g., 100.00)");

        TextField descField = new TextField();
        descField.setPromptText("Description");

        grid.add(new Label("Amount"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Description"), 0, 1);
        grid.add(descField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(okType);
        okBtn.setDisable(true);

        amountField.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.trim().isEmpty()));

        dialog.setResultConverter(btn -> {
            if (btn != okType) return null;

            try {
                BigDecimal amt = new BigDecimal(amountField.getText().trim());
                if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                    showSimpleAlert("Invalid amount", "Amount must be positive.");
                    return null;
                }
                String desc = descField.getText() == null ? "" : descField.getText().trim();
                if (desc.isBlank()) desc = title + " via UI";
                return new TxInput(amt, desc);
            } catch (Exception ex) {
                showSimpleAlert("Invalid amount", "Enter a valid number like 100.00");
                return null;
            }
        });

        return dialog.showAndWait();
    }

    private Optional<TransferInput> showTransferDialog() 
    {
        Dialog<TransferInput> dialog = new Dialog<>();
        dialog.setTitle("Transfer");
        dialog.setHeaderText("Transfer money between accounts");

        ButtonType okType = new ButtonType("Transfer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        ComboBox<Account> fromBox = new ComboBox<>();
        ComboBox<Account> toBox = new ComboBox<>();
        fromBox.setItems(FXCollections.observableArrayList(userAccounts));
        toBox.setItems(FXCollections.observableArrayList(userAccounts));

        fromBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? "" : a.getAccountNumber() + " (" + a.getAccountType() + ")");
            }
        });
        fromBox.setButtonCell(fromBox.getCellFactory().call(null));

        toBox.setCellFactory(fromBox.getCellFactory());
        toBox.setButtonCell(toBox.getCellFactory().call(null));

        TextField amountField = new TextField();
        amountField.setPromptText("Amount (e.g., 200.00)");

        TextField descField = new TextField();
        descField.setPromptText("Description");

        grid.add(new Label("From"), 0, 0);
        grid.add(fromBox, 1, 0);
        grid.add(new Label("To"), 0, 1);
        grid.add(toBox, 1, 1);
        grid.add(new Label("Amount"), 0, 2);
        grid.add(amountField, 1, 2);
        grid.add(new Label("Description"), 0, 3);
        grid.add(descField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Node okBtn = dialog.getDialogPane().lookupButton(okType);
        okBtn.setDisable(true);

        Runnable validate = () -> {
            boolean ok = fromBox.getValue() != null
                    && toBox.getValue() != null
                    && !amountField.getText().trim().isEmpty();
            okBtn.setDisable(!ok);
        };

        fromBox.valueProperty().addListener((obs,o,n) -> validate.run());
        toBox.valueProperty().addListener((obs,o,n) -> validate.run());
        amountField.textProperty().addListener((obs,o,n) -> validate.run());

        dialog.setResultConverter(btn -> {
            if (btn != okType) return null;

            Account from = fromBox.getValue();
            Account to = toBox.getValue();

            if (from == null || to == null) return null;
            if (from.getAccountId() == to.getAccountId()) {
                showSimpleAlert("Invalid transfer", "From and To cannot be the same account.");
                return null;
            }

            try {
                BigDecimal amt = new BigDecimal(amountField.getText().trim());
                if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                    showSimpleAlert("Invalid amount", "Amount must be positive.");
                    return null;
                }

                String desc = descField.getText() == null ? "" : descField.getText().trim();
                if (desc.isBlank()) desc = "Transfer via UI";

                return new TransferInput(from.getAccountId(), to.getAccountId(), amt, desc);

            } catch (Exception ex) {
                showSimpleAlert("Invalid amount", "Enter a valid number like 200.00");
                return null;
            }
        });

        return dialog.showAndWait();
    }

    private void refreshDashboardData() 
    {
        if (loggedInUser == null) return;
        userAccounts = accountDAO.getAccountsByUserId(loggedInUser.getUserId());
        // refresh center content (dashboard) without breaking layout
        if (appRoot != null && pageTitle != null && "Dashboard".equals(pageTitle.getText())) {
            appRoot.setCenter(buildDashboard());
        }
    }

    public static void main(String[] args) 
    {
        launch(args);
    }
}