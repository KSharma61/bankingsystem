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
    // ---- Top navigation pages (marketing site) ----
    private enum PublicPage { HOME, ABOUT, CONTACT, LOGIN, SIGNUP }
    private PublicPage activePublicPage = PublicPage.LOGIN;

    // Remember which accounts to show on dashboard cards (optional override)
    private Integer dashboardChequingAccountId = null;
    private Integer dashboardSavingsAccountId = null;

    // When we navigate to Accounts page from a card click, we preselect this account
    private Integer accountsPageSelectedAccountId = null;

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
        //sceneRoot.getChildren().setAll(buildLoginScreen());
        showPublicPage(PublicPage.LOGIN);

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

    private Account findAccountById(Integer accountId) 
    {
        if (accountId == null || userAccounts == null) return null;
        for (Account a : userAccounts) {
            if (a.getAccountId() == accountId) return a;
        }
        return null;
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
        accountBox.setItems(FXCollections.observableArrayList(activeAccountsOnly(userAccounts)));

        accountBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? "" : a.getAccountNumber() + " (" + a.getAccountType() + ")");
            }
        });
        accountBox.setButtonCell(accountBox.getCellFactory().call(null));

        // Default selection: Chequing if exists else first account
        List<Account> active = activeAccountsOnly(userAccounts);

        Account defaultAcc = chequing != null ? chequing
                : (!active.isEmpty() ? active.get(0) : null);
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

        // NEW: Date/Time column
        TableColumn<TxRow, String> colDate = new TableColumn<>("Date/Time");
        colDate.setCellValueFactory(d -> d.getValue().dateTime);

        TableColumn<TxRow, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> d.getValue().type);

        TableColumn<TxRow, String> colAmt = new TableColumn<>("Amount");
        colAmt.setCellValueFactory(d -> d.getValue().amount);

        TableColumn<TxRow, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(d -> d.getValue().desc);

        // include colDate
        table.getColumns().setAll(colDate, colType, colAmt, colDesc);

        Runnable reloadTable = () -> {
            Account selected = accountBox.getValue();
            if (selected == null) {
                table.setItems(FXCollections.observableArrayList(
                        new TxRow("-", "INFO", "$0.00", "No account selected")
                ));
                return;
            }

            List<Transaction> txs = transactionDAO.getTransactionsByAccountId(selected.getAccountId());
            txs.sort(Comparator.comparing(Transaction::getCreatedAt).reversed());

            var rows = FXCollections.<TxRow>observableArrayList();
            for (int i = 0; i < Math.min(10, txs.size()); i++) {
                Transaction t = txs.get(i);
                rows.add(new TxRow(
                        formatTxTime(t.getCreatedAt()),
                        safe(t.getTransactionType()),
                        money(t.getAmount()),
                        safe(t.getDescription())
                ));
            }

            if (rows.isEmpty()) {
                rows.add(new TxRow("-", "INFO", "$0.00", "No transactions yet"));
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
            Account fresh = accountDAO.getAccountById(selected.getAccountId());
            if (fresh != null && "CLOSED".equalsIgnoreCase(fresh.getStatus())) {
                showSimpleAlert("Account closed", "This account is CLOSED and cannot be used for transactions.");
                refreshDashboardData();
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
            Account fresh = accountDAO.getAccountById(selected.getAccountId());
            if (fresh != null && "CLOSED".equalsIgnoreCase(fresh.getStatus())) {
                showSimpleAlert("Account closed", "This account is CLOSED and cannot be used for transactions.");
                refreshDashboardData();
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
            Account fromFresh = accountDAO.getAccountById(input.get().fromAccountId);
            Account toFresh = accountDAO.getAccountById(input.get().toAccountId);

            if ((fromFresh != null && "CLOSED".equalsIgnoreCase(fromFresh.getStatus())) ||
                (toFresh != null && "CLOSED".equalsIgnoreCase(toFresh.getStatus()))) {
                showSimpleAlert("Account closed", "One of the selected accounts is CLOSED and cannot be used.");
                refreshDashboardData();
                return;
            }

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
    //sign up button working.
    private Node buildSignupScreen() 
    {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("loginRoot");

        activePublicPage = PublicPage.SIGNUP;
        HBox top = buildTopNavBar();
        root.setTop(top);

        HBox center = new HBox(24);
        center.setPadding(new Insets(40));
        center.setAlignment(Pos.CENTER);

        VBox leftPanel = new VBox(12);
        leftPanel.getStyleClass().add("heroPanel");
        leftPanel.setPadding(new Insets(28));
        leftPanel.setAlignment(Pos.TOP_LEFT);
        leftPanel.setMinHeight(520);

        Label heroSmall = new Label("Create your account to start using LunarOne Finance");
        heroSmall.getStyleClass().add("heroSmall");

        Region heroSpacer = new Region();
        VBox.setVgrow(heroSpacer, Priority.ALWAYS);

        Label heroBig = new Label("Welcome!");
        heroBig.getStyleClass().add("heroBig");

        leftPanel.getChildren().addAll(heroSmall, heroSpacer, heroBig);

        VBox card = new VBox(12);
        card.getStyleClass().add("authCard");
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.TOP_LEFT);
        card.setMaxWidth(420);

        Label title = new Label("Sign up");
        title.getStyleClass().add("authTitle");

        TextField fullName = new TextField();
        fullName.setPromptText("Full name");
        fullName.getStyleClass().add("authField");

        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        emailField.getStyleClass().add("authField");

        PasswordField pass = new PasswordField();
        pass.setPromptText("Password");
        pass.getStyleClass().add("authField");

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm password");
        confirm.getStyleClass().add("authField");

        Label error = new Label("");
        error.getStyleClass().add("authError");
        error.setManaged(false);
        error.setVisible(false);

        Button createBtn = new Button("Create Account");
        createBtn.getStyleClass().add("authPrimaryBtn");
        createBtn.setMaxWidth(Double.MAX_VALUE);

        Button backToLogin = new Button("Back to Log in");
        backToLogin.getStyleClass().add("authSecondaryBtn");
        backToLogin.setMaxWidth(Double.MAX_VALUE);

        createBtn.setOnAction(e -> {
            hideError(error);

            String fn = fullName.getText() == null ? "" : fullName.getText().trim();
            String em = emailField.getText() == null ? "" : emailField.getText().trim();
            String pw = pass.getText() == null ? "" : pass.getText();
            String cpw = confirm.getText() == null ? "" : confirm.getText();

            if (fn.length() < 2) { showError(error, "Please enter a valid full name."); return; }
            if (!EMAIL_PATTERN.matcher(em).matches()) { showError(error, "Please enter a valid email address."); return; }
            if (pw.length() < 8) { showError(error, "Password must be at least 8 characters."); return; }
            if (!pw.equals(cpw)) { showError(error, "Passwords do not match."); return; }

            User existing = userDAO.getUserByEmail(em);
            if (existing != null) {
                showError(error, "An account with this email already exists. Please log in.");
                return;
            }

            boolean ok = userDAO.createUser(new User(fn, em, pw));
            if (!ok) {
                showError(error, "Signup failed. Please try again.");
                return;
            }

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText("Account created ✅");
            a.setContentText("Your account has been created. Please log in.");
            a.showAndWait();

            showPublicPage(PublicPage.LOGIN);
        });

        backToLogin.setOnAction(e -> showPublicPage(PublicPage.LOGIN));

        card.getChildren().addAll(title, fullName, emailField, pass, confirm, error, createBtn, new Separator(), backToLogin);

        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        center.getChildren().addAll(leftPanel, card);

        root.setCenter(center);
        return root;
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
        /*HBox top = new HBox();
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
        top.getChildren().addAll(brandBox, spacer);*/
        activePublicPage = PublicPage.LOGIN;
        HBox top = buildTopNavBar();
        root.setTop(top);

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
        signupBtn.setOnAction(e -> showPublicPage(PublicPage.SIGNUP));

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
    private List<Account> activeAccountsOnly(List<Account> list) 
    {
        if (list == null) return List.of();
        return list.stream()
                .filter(a -> a != null && a.getStatus() != null && !"CLOSED".equalsIgnoreCase(a.getStatus()))
                .toList();
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

        // ---------- Accounts table ----------
        TableView<Account> accountsTable = new TableView<>();
        accountsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        accountsTable.setFixedCellSize(40);

        TableColumn<Account, String> colNum = new TableColumn<>("Account #");
        colNum.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAccountNumber()));

        //TableColumn<Account, String> colType = new TableColumn<>("Type");
        //colType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAccountType()));

        TableColumn<Account, String> colBal = new TableColumn<>("Balance");
        colBal.setCellValueFactory(d -> new SimpleStringProperty(money(d.getValue().getBalance())));

        //TableColumn<Account, String> colStatus = new TableColumn<>("Status");
        //colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));

        accountsTable.getColumns().addAll(colNum, colBal);

        // ---------- Create account (compact row) ----------
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.setItems(FXCollections.observableArrayList("CHEQUING", "SAVINGS"));
        typeBox.setValue("CHEQUING");

        TextField initialBalance = new TextField("0.00");
        initialBalance.setPromptText("Initial balance");
        initialBalance.setPrefWidth(120);

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

            // Confirmation (NEW)
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText("Confirm account creation");
            confirm.setContentText("Are you sure you want to create a " + typeBox.getValue()
                    + " account with initial balance " + money(bal) + "?");

            ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
            ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
            confirm.getButtonTypes().setAll(yes, no);

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() == no) {
                showSimpleAlert("Declined", "Account creation cancelled.");
                return;
            }

            boolean ok = accountDAO.createAccount(acc);
            if (!ok) {
                showSimpleAlert("Failed", "Account could not be created. Check DB.");
                return;
            }

            showSimpleAlert("Success ✅", "Account created: " + accNumber);
            refreshDashboardData();
        });

        HBox createRow = new HBox(10,
                new Label("Type:"), typeBox,
                new Label("Initial Balance:"), initialBalance,
                createBtn
        );
        createRow.setAlignment(Pos.CENTER_LEFT);

        VBox createCard = new VBox(12, title, createRow);
        createCard.getStyleClass().add("panel");
        createCard.setPadding(new Insets(14)); // smaller section

        // ---------- Transactions viewer ----------
        Label txTitle = new Label("Account Transactions");
        txTitle.getStyleClass().add("panelTitle");

        ComboBox<Account> accountSelector = new ComboBox<>();
        accountSelector.setItems(FXCollections.observableArrayList(userAccounts == null ? List.of() : userAccounts));
        accountSelector.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Account a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? "" : a.getAccountNumber() + " (" + a.getAccountType() + ")");
            }
        });
        accountSelector.setButtonCell(accountSelector.getCellFactory().call(null));

        // Choose default selection:
        Account defaultAcc = null;
        if (accountsPageSelectedAccountId != null) {
            defaultAcc = findAccountById(accountsPageSelectedAccountId);
        }
        if (defaultAcc == null && userAccounts != null && !userAccounts.isEmpty()) {
            defaultAcc = userAccounts.get(0);
        }
        accountSelector.setValue(defaultAcc);

        // Transactions table
        TableView<TxRow> txTable = new TableView<>();
        txTable.getStyleClass().add("txTable");
        txTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        txTable.setFixedCellSize(40);
        VBox.setVgrow(txTable, Priority.ALWAYS);

        TableColumn<TxRow, String> tDate = new TableColumn<>("Date/Time");
        tDate.setCellValueFactory(d -> d.getValue().dateTime);

        TableColumn<TxRow, String> tType = new TableColumn<>("Type");
        tType.setCellValueFactory(d -> d.getValue().type);

        TableColumn<TxRow, String> tAmt = new TableColumn<>("Amount");
        tAmt.setCellValueFactory(d -> d.getValue().amount);

        TableColumn<TxRow, String> tDesc = new TableColumn<>("Description");
        tDesc.setCellValueFactory(d -> d.getValue().desc);

        txTable.getColumns().addAll(tDate, tType, tAmt, tDesc);

        Runnable reloadTx = () -> {
            Account selected = accountSelector.getValue();
            if (selected == null) {
                txTable.setItems(FXCollections.observableArrayList(
                        new TxRow("-","INFO", "$0.00", "No account selected")
                ));
                return;
            }

            List<Transaction> txs = transactionDAO.getTransactionsByAccountId(selected.getAccountId());
            txs.sort(Comparator.comparing(Transaction::getCreatedAt).reversed());

            var rows = FXCollections.<TxRow>observableArrayList();
            for (Transaction t : txs) {
                rows.add(new TxRow(
                        formatTxTime(t.getCreatedAt()),
                        safe(t.getTransactionType()),
                        money(t.getAmount()),
                        safe(t.getDescription())
                ));
            }
            if (rows.isEmpty()) rows.add(new TxRow("-", "INFO", "$0.00", "No transactions yet"));
            txTable.setItems(rows);
        };

        reloadTx.run();
        accountSelector.valueProperty().addListener((obs, o, n) -> reloadTx.run());

        // Sync: clicking an account row should also switch the selector
        accountsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) accountSelector.setValue(n);
        });

        // ---------- Dashboard card selection controls (NEW) ----------
        Label cardMapTitle = new Label("Dashboard Card Accounts");
        cardMapTitle.getStyleClass().add("panelTitle");

        ComboBox<Account> cheqCardBox = new ComboBox<>(accountSelector.getItems());
        ComboBox<Account> savCardBox = new ComboBox<>(accountSelector.getItems());
        cheqCardBox.setCellFactory(accountSelector.getCellFactory());
        savCardBox.setCellFactory(accountSelector.getCellFactory());
        cheqCardBox.setButtonCell(cheqCardBox.getCellFactory().call(null));
        savCardBox.setButtonCell(savCardBox.getCellFactory().call(null));

        // Load existing selected card accounts if set
        cheqCardBox.setValue(findAccountById(dashboardChequingAccountId));
        savCardBox.setValue(findAccountById(dashboardSavingsAccountId));

        Button saveCardMap = new Button("Save");
        saveCardMap.getStyleClass().add("secondaryBtn");
        saveCardMap.setOnAction(e -> {
            Account c = cheqCardBox.getValue();
            Account s = savCardBox.getValue();
            dashboardChequingAccountId = (c != null) ? c.getAccountId() : null;
            dashboardSavingsAccountId = (s != null) ? s.getAccountId() : null;

            showSimpleAlert("Saved", "Dashboard card accounts updated.");
            refreshDashboardData();
        });

        Runnable reloadAccounts = () -> {
            if (loggedInUser == null) return;

            // load all from DB
            userAccounts = accountDAO.getAccountsByUserId(loggedInUser.getUserId());

            // filter ACTIVE only for UI lists
            List<Account> active = activeAccountsOnly(userAccounts);

            accountsTable.setItems(FXCollections.observableArrayList(active));

            // IMPORTANT: keep selector list in sync too
            accountSelector.setItems(FXCollections.observableArrayList(active));
            cheqCardBox.setItems(FXCollections.observableArrayList(active));
            savCardBox.setItems(FXCollections.observableArrayList(active));

            // keep current selection valid
            Account selected = accountSelector.getValue();
            if (selected == null || "CLOSED".equalsIgnoreCase(selected.getStatus())) {
                accountSelector.setValue(active.isEmpty() ? null : active.get(0));
            }
        };
        reloadAccounts.run();
        HBox cardMapRow = new HBox(10,
                new Label("Chequing card:"), cheqCardBox,
                new Label("Savings card:"), savCardBox,
                saveCardMap
        );
        cardMapRow.setAlignment(Pos.CENTER_LEFT);

        // ---------- close account button (NEW) ----------
        Button closeAccountBtn = new Button("Close Account");
        closeAccountBtn.getStyleClass().add("secondaryBtn");

        closeAccountBtn.setOnAction(e -> {
            Account selected = accountSelector.getValue(); // or accountsTable selection
            if (selected == null) {
                showSimpleAlert("No account", "Select an account first.");
                return;
            }

            // Refresh the latest balance from DB (safe)
            userAccounts = accountDAO.getAccountsByUserId(loggedInUser.getUserId());
            Account fresh = null;
            for (Account a : userAccounts) {
                if (a.getAccountId() == selected.getAccountId()) { fresh = a; break; }
            }
            if (fresh == null) fresh = selected;

            if (fresh.getBalance() != null && fresh.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                Alert a = new Alert(Alert.AlertType.WARNING);
                a.setHeaderText("Cannot close account");
                a.setContentText(
                        "This account still has funds (" + money(fresh.getBalance()) + ").\n\n" +
                        "Please transfer or withdraw the funds first so the balance becomes $0.00, then try again."
                );
                a.showAndWait();
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setHeaderText("Confirm account closure");
            confirm.setContentText("Are you sure you want to close account:\n\n" + fresh.getAccountNumber() + " ?");
            ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
            ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
            confirm.getButtonTypes().setAll(yes, no);

            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isEmpty() || res.get() == no) {
                showSimpleAlert("Declined", "Account closure cancelled.");
                return;
            }

            boolean ok = accountDAO.closeAccount(fresh.getAccountId()); // we’ll add this method if missing
            if (!ok) {
                showSimpleAlert("Failed", "Account could not be closed. Check DB.");
                return;
            }

            showSimpleAlert("Closed ✅", "Account closed: " + fresh.getAccountNumber());
            accountsPageSelectedAccountId = null; // prevent selecting a closed one again
            reloadAccounts.run();                 // refresh accounts list + selector
            reloadTx.run();                       // refresh transactions panel
            refreshDashboardData();               // update dashboard cards/balances

            // refresh UI
            refreshDashboardData();
            // reload accounts + selector + tx
            // (call your reloadAccounts() and reloadTx() runnables if you have them)
        });

        // ---------- PDF Statement download (NEW) ----------
        Button downloadPdf = new Button("Download Statement (PDF)");
        downloadPdf.getStyleClass().add("secondaryBtn");

        downloadPdf.setOnAction(e -> {
            Account selected = accountSelector.getValue();
            if (selected == null) {
                showSimpleAlert("No account", "Select an account first.");
                return;
            }
            downloadStatementPdf(selected);
        });

        HBox txTopRow = new HBox(12,
            new Label("Account:"), accountSelector,
            new Region(),
            downloadPdf,
            closeAccountBtn
        );

        HBox.setHgrow(txTopRow.getChildren().get(2), Priority.ALWAYS);
        txTopRow.setAlignment(Pos.CENTER_LEFT);

        VBox txCard = new VBox(12, txTitle, txTopRow, txTable, cardMapTitle, cardMapRow);
        txCard.getStyleClass().add("panel");
        txCard.setPadding(new Insets(16));
        VBox.setVgrow(txCard, Priority.ALWAYS);

        // Layout: createCard on top, then accounts table + tx panel
        HBox bottom = new HBox(16);

        VBox accountsCard = new VBox(12, new Label("Accounts List"), accountsTable);
        accountsCard.getStyleClass().add("panel");
        accountsCard.setPadding(new Insets(16));
        VBox.setVgrow(accountsTable, Priority.ALWAYS);

        HBox.setHgrow(accountsCard, Priority.ALWAYS);
        HBox.setHgrow(txCard, Priority.ALWAYS);

        bottom.getChildren().addAll(accountsCard, txCard);
        VBox.setVgrow(bottom, Priority.ALWAYS);

        wrapper.getChildren().addAll(createCard, bottom);
        return wrapper;
    }

    private void downloadStatementPdf(Account account) 
    {
        try {
            // choose file location
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Save Statement PDF");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            chooser.setInitialFileName("Statement_" + account.getAccountNumber() + ".pdf");

            java.io.File file = chooser.showSaveDialog(stage);
            if (file == null) return;

            List<Transaction> txs = transactionDAO.getTransactionsByAccountId(account.getAccountId());
            txs.sort(Comparator.comparing(Transaction::getCreatedAt).reversed());

            // PDFBox
            org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            doc.addPage(page);

            org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                    new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);

            float y = 750;

            cs.beginText();
            cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 18);
            cs.newLineAtOffset(50, y);
            cs.showText("LunarOne Finance - Account Statement");
            cs.endText();

            y -= 30;

            cs.beginText();
            cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(50, y);
            cs.showText("Account: " + account.getAccountNumber() + " (" + account.getAccountType() + ")");
            cs.endText();

            y -= 18;

            cs.beginText();
            cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
            cs.newLineAtOffset(50, y);
            cs.showText("Current Balance: " + money(account.getBalance()));
            cs.endText();

            y -= 28;

            // table header
            cs.beginText();
            cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 12);
            cs.newLineAtOffset(50, y);
            cs.showText("Type");
            cs.newLineAtOffset(120, 0);
            cs.showText("Amount");
            cs.newLineAtOffset(120, 0);
            cs.showText("Description");
            cs.endText();

            y -= 18;

            cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 11);

            for (Transaction t : txs) {
                if (y < 60) break; // keep simple: one page for now

                String type = safe(t.getTransactionType());
                String amt = money(t.getAmount());
                String desc = safe(t.getDescription());
                if (desc.length() > 45) desc = desc.substring(0, 45) + "...";

                cs.beginText();
                cs.newLineAtOffset(50, y);
                cs.showText(type);
                cs.newLineAtOffset(120, 0);
                cs.showText(amt);
                cs.newLineAtOffset(120, 0);
                cs.showText(desc);
                cs.endText();

                y -= 16;
            }

            cs.close();
            doc.save(file);
            doc.close();

            showSimpleAlert("Saved ✅", "Statement saved to:\n" + file.getAbsolutePath());

        } catch (Exception ex) {
            ex.printStackTrace();
            showSimpleAlert("Error", "Could not generate PDF statement.");
        }
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

        /*Account chequing = pickAccountForCard("CHEQUING", "CHECKING", "CHEQUING ACCOUNT");
        Account savings  = pickAccountForCard("SAVINGS", "SAVING");*/

        Account chequing = (dashboardChequingAccountId != null)
        ? findAccountById(dashboardChequingAccountId)
        : pickAccountForCard("CHEQUING", "CHECKING", "CHEQUING ACCOUNT");

        Account savings = (dashboardSavingsAccountId != null)
        ? findAccountById(dashboardSavingsAccountId)
        : pickAccountForCard("SAVINGS", "SAVING");

        // Fallbacks if account_type naming isn't exactly matching
        if (chequing == null && userAccounts != null && userAccounts.size() >= 1) chequing = userAccounts.get(0);
        if (savings  == null && userAccounts != null && userAccounts.size() >= 2) savings  = userAccounts.get(1);

        BigDecimal chequingBal = chequing != null ? chequing.getBalance() : BigDecimal.ZERO;
        BigDecimal savingsBal  = savings  != null ? savings.getBalance()  : BigDecimal.ZERO;

        // Credit is dummy for now (until you add a credit/limit table/column)
        BigDecimal creditLimit = new BigDecimal("5000.00");

       HBox cardsRow = new HBox(16,
        balanceCard("Chequing", chequing, money(chequingBal), "Total Balance"),
        balanceCard("Savings",  savings,  money(savingsBal),  "Total Balance"),
        balanceCard("Credit",   null,     money(creditLimit), "Available Credit")
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

    private Pane balanceCard(String title, Account accOrNull, String amount, String sub) 
    {
        VBox card = new VBox(6);
        card.getStyleClass().add("balanceCard");
        card.setPadding(new Insets(16));
        card.setMinHeight(140);

        Label t = new Label(title);
        t.getStyleClass().add("cardTitle");

        // NEW: show account number
        Label accNum = new Label(accOrNull != null ? accOrNull.getAccountNumber() : "—");
        accNum.getStyleClass().add("cardAccountNumber");

        Label a = new Label(amount);
        a.getStyleClass().add("cardAmount");

        Label s = new Label(sub);
        s.getStyleClass().add("cardSub");

        card.getChildren().addAll(t, accNum, a, s);

        // NEW: Make card clickable ONLY if we have a real account
        if (accOrNull != null) {
            card.getStyleClass().add("clickableCard");
            card.setOnMouseClicked(e -> {
                accountsPageSelectedAccountId = accOrNull.getAccountId();
                pageTitle.setText("Accounts");
                appRoot.setCenter(buildAccountsPage());
            });
        }

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

    private VBox buildTransactionsPanelBox() 
    {
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

        // NEW: Date/Time column
        TableColumn<TxRow, String> colDate = new TableColumn<>("Date/Time");
        colDate.setCellValueFactory(d -> d.getValue().dateTime);

        TableColumn<TxRow, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> d.getValue().type);

        TableColumn<TxRow, String> colAmt = new TableColumn<>("Amount");
        colAmt.setCellValueFactory(d -> d.getValue().amount);

        TableColumn<TxRow, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(d -> d.getValue().desc);

        table.getColumns().setAll(colDate, colType, colAmt, colDesc);

        table.setItems(FXCollections.observableArrayList(
                new TxRow("2025-12-14 10:00", "DEPOSIT", "$1000.00", "Learning deposit"),
                new TxRow("2025-12-14 10:10", "WITHDRAW", "$300.00", "Test withdrawal"),
                new TxRow("2025-12-14 10:20", "TRANSFER", "$200.00", "To Savings account")
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


    private static class TxRow 
    {
        final SimpleStringProperty dateTime = new SimpleStringProperty();
        final SimpleStringProperty type = new SimpleStringProperty();
        final SimpleStringProperty amount = new SimpleStringProperty();
        final SimpleStringProperty desc = new SimpleStringProperty();

        TxRow(String dt, String t, String a, String d) {
            dateTime.set(dt);
            type.set(t);
            amount.set(a);
            desc.set(d);
        }
    }

    private String formatTxTime(Object createdAt) 
    {
        if (createdAt == null) return "-";
        try {
            // If your Transaction.getCreatedAt() returns java.sql.Timestamp:
            if (createdAt instanceof java.sql.Timestamp ts) {
                return ts.toLocalDateTime().toString().replace('T', ' ');
            }
            // If it returns java.time.LocalDateTime:
            if (createdAt instanceof java.time.LocalDateTime ldt) {
                return ldt.toString().replace('T', ' ');
            }
            return createdAt.toString();
        } catch (Exception e) {
            return String.valueOf(createdAt);
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
        fromBox.setItems(FXCollections.observableArrayList(activeAccountsOnly(userAccounts)));
        toBox.setItems(FXCollections.observableArrayList(activeAccountsOnly(userAccounts)));

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

    private void showPublicPage(PublicPage page) 
    {
        activePublicPage = page;

        switch (page) {
            case HOME -> sceneRoot.getChildren().setAll(buildPublicShell("Home"));
            case ABOUT -> sceneRoot.getChildren().setAll(buildPublicShell("About Us"));
            case CONTACT -> sceneRoot.getChildren().setAll(buildPublicShell("Contact"));
            case LOGIN -> sceneRoot.getChildren().setAll(buildLoginScreen()); // your existing screen
            case SIGNUP -> sceneRoot.getChildren().setAll(buildSignupScreen());

        }
    }

    // Builds the outer shell + top nav like PDF, with an empty white card inside
    private Node buildPublicShell(String title) 
    {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("loginRoot"); // reuse your existing light background if you want

        // Top bar with brand + nav
        HBox top = buildTopNavBar();
        root.setTop(top);

        // Center white card placeholder
        StackPane centerWrap = new StackPane();
        centerWrap.setPadding(new Insets(40));

        VBox card = new VBox(12);
        card.getStyleClass().add("authCard"); // reuse your existing card style (clean white rounded)
        card.setPadding(new Insets(28));
        card.setMaxWidth(900);
        card.setMinHeight(520);

        Label t = new Label(title);
        t.getStyleClass().add("authTitle");

        Label placeholder = new Label("(Content placeholder)");
        placeholder.getStyleClass().add("muted");

        card.getChildren().addAll(t, placeholder);
        centerWrap.getChildren().add(card);

        root.setCenter(centerWrap);
        return root;
    }

    // Builds PDF-style top nav (HOME / ABOUT / CONTACT / LOG IN) with underline on active
    private HBox buildTopNavBar() {
        activePublicPage = PublicPage.LOGIN;
        HBox top = new HBox(18);
        top.getStyleClass().add("loginTopBar");
        top.setPadding(new Insets(16, 18, 16, 18));
        top.setAlignment(Pos.CENTER_LEFT);

        VBox brandBox = new VBox();
        brandBox.getStyleClass().add("brandBox");
        Label brandTop = new Label("LunarOne");
        brandTop.getStyleClass().add("brandTop");
        Label brandBottom = new Label("Finance.");
        brandBottom.getStyleClass().add("brandBottom");
        brandBox.getChildren().addAll(brandTop, brandBottom);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Nav items (use Hyperlink for “underline” style)
        Hyperlink home = navLink("HOME", PublicPage.HOME);
        Hyperlink about = navLink("ABOUT US", PublicPage.ABOUT);
        Hyperlink contact = navLink("CONTACT", PublicPage.CONTACT);
        Hyperlink login = navLink("LOG IN", PublicPage.LOGIN);

        HBox nav = new HBox(18, home, about, contact, login);
        nav.setAlignment(Pos.CENTER_RIGHT);

        // Apply active underline/bold
        applyActiveNav(home, about, contact, login);

        top.getChildren().addAll(brandBox, spacer, nav);
        return top;
    }

    private Hyperlink navLink(String text, PublicPage target) {
        Hyperlink h = new Hyperlink(text);
        h.getStyleClass().add("topNavLink");
        h.setOnAction(e -> showPublicPage(target));
        return h;
    }

    private void applyActiveNav(Hyperlink home, Hyperlink about, Hyperlink contact, Hyperlink login) {
        home.getStyleClass().remove("topNavActive");
        about.getStyleClass().remove("topNavActive");
        contact.getStyleClass().remove("topNavActive");
        login.getStyleClass().remove("topNavActive");

        switch (activePublicPage) {
            case HOME -> home.getStyleClass().add("topNavActive");
            case ABOUT -> about.getStyleClass().add("topNavActive");
            case CONTACT -> contact.getStyleClass().add("topNavActive");
            case LOGIN -> login.getStyleClass().add("topNavActive");
        }
    }

    public static void main(String[] args) 
    {
        launch(args);
    }
}