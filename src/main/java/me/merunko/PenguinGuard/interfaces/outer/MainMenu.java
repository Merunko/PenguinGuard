package me.merunko.PenguinGuard.interfaces.outer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import me.google.authorisation.GoogleAuthService;
import me.merunko.PenguinGuard.interfaces.inner.EntryForm;
import me.merunko.PenguinGuard.interfaces.inner.EntryList;
import me.merunko.utilities.ResourceLoader;

public class MainMenu {
    // Color scheme constants
    private static final String BLACK = "#000000";
    private static final String WHITE = "#FFFFFF";
    private static final String YELLOW = "#FFD700";
    private static final String DARK_GRAY = "#333333";
    private static final String GRAY = "#808080";
    private static final double SIDEBAR_WIDTH = 100.0; // Define sidebar width
    private static final ResourceLoader resourceLoader = new ResourceLoader();
    private final BorderPane container;
    private final StackPane contentPanel;
    private final GoogleAuthService authService;

    public MainMenu(GoogleAuthService googleAuthService) {
        this.authService = googleAuthService;
        container = new BorderPane();
        container.setBackground(new Background(new BackgroundFill(Color.web(BLACK), CornerRadii.EMPTY, Insets.EMPTY)));

        // Set explicit sizes
        container.setMinSize(800, 600);
        container.setPrefSize(1000, 800);
        container.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // Create panels
        VBox sidebar = createSidebar();
        contentPanel = new StackPane();
        contentPanel.setPadding(new Insets(40));
        contentPanel.setBackground(new Background(new BackgroundFill(Color.web(GRAY), CornerRadii.EMPTY, Insets.EMPTY)));

        // Setup layout
        container.setLeft(sidebar);
        container.setCenter(contentPanel);

        showWelcomeContent();
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(20));
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setMinWidth(SIDEBAR_WIDTH);
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setMaxWidth(SIDEBAR_WIDTH);
        sidebar.setBackground(new Background(new BackgroundFill(Color.web(DARK_GRAY), CornerRadii.EMPTY, Insets.EMPTY)));

        // Logo
        ImageView logo = resourceLoader.loadImage("logo", "png", 60, 60, false, "$FFFFFFF");
        logo.setStyle("-fx-effect: dropshadow(three-pass-box, " + WHITE + ", 5, 0.5, 0, 0);");
        VBox.setMargin(logo, new Insets(20, 0, 20, 0));
        sidebar.getChildren().add(logo);

        // Menu Buttons (Icons)
        Button addEntryBtn = createSidebarButton("add");
        addEntryBtn.setOnAction(e -> showAddEntryForm());
        VBox.setMargin(addEntryBtn, new Insets(5, 0, 0, 0));
        sidebar.getChildren().add(addEntryBtn);

        Button viewEntriesBtn = createSidebarButton("view");
        viewEntriesBtn.setOnAction(e -> showViewEntries());
        VBox.setMargin(viewEntriesBtn, new Insets(5, 0, 0, 0));
        sidebar.getChildren().add(viewEntriesBtn);

        Button settingsBtn = createSidebarButton("settings");
        settingsBtn.setOnAction(e -> showSettings());
        VBox.setMargin(settingsBtn, new Insets(5, 0, 0, 0));
        sidebar.getChildren().add(settingsBtn);

        Pane spacer = new Pane();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        Button logoutBtn = createSidebarButton("exit");
        logoutBtn.setOnAction(e -> handleLogout());
        VBox.setMargin(logoutBtn, new Insets(0, 0, 20, 0));
        sidebar.getChildren().add(logoutBtn);

        return sidebar;
    }

    private Button createSidebarButton(String imageName) {
        ImageView icon = resourceLoader.loadImage(imageName, "png", 30, 30, true, "#FFFFFF");
        Button button = new Button();
        button.setGraphic(icon);
        button.setStyle("-fx-background-color: " + GRAY + "; -fx-cursor: hand; -fx-padding: 10px;"); // Added padding
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: " + WHITE + "; -fx-cursor: hand; -fx-padding: 10px;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: " + GRAY + "; -fx-cursor: hand; -fx-padding: 10px;"));
        button.setPrefSize(SIDEBAR_WIDTH, 50); // Adjust size as needed
        return button;

    }

    private void showWelcomeContent() {
        VBox welcomeContent = new VBox(40);
        welcomeContent.setAlignment(Pos.CENTER);

        ImageView logo = resourceLoader.loadImage("logo", "png", 200, 200, false, "#FFFFFF");
        logo.setStyle("-fx-effect: dropshadow(three-pass-box, " + WHITE + ", 10, 0.5, 0, 0);");

        Label titleLabel = new Label("PenguinGuard Manager");
        titleLabel.setFont(Font.font("Roboto", 32));
        titleLabel.setTextFill(Color.web(YELLOW));

        Label subtitleLabel = new Label("Secure Password Management");
        subtitleLabel.setFont(Font.font("Roboto", 18));
        subtitleLabel.setTextFill(Color.web(WHITE));

        welcomeContent.getChildren().addAll(logo, titleLabel, subtitleLabel);
        contentPanel.getChildren().setAll(welcomeContent);
    }

    private void showAddEntryForm() {
        EntryForm entryForm = new EntryForm(this.authService);
        contentPanel.getChildren().setAll(entryForm.getForm());
    }

    private void showViewEntries() {
        if (authService.getAccessToken() == null) {
            Label authPrompt = new Label("Please authenticate first");
            authPrompt.setFont(Font.font("Roboto", 24));
            authPrompt.setTextFill(Color.web(YELLOW));
            contentPanel.getChildren().setAll(authPrompt);
            return;
        }

        EntryList entryList = new EntryList(authService);
        contentPanel.getChildren().setAll(entryList.getContainer());
    }

    private void showSettings() {
        // TODO: Implement settings functionality with matching theme
        Label placeholder = new Label("Settings - Coming Soon");
        placeholder.setFont(Font.font("Roboto", 24));
        placeholder.setTextFill(Color.web(YELLOW));
        contentPanel.getChildren().setAll(placeholder);
    }

    private void handleLogout() {
        System.exit(0);
    }

    public BorderPane getContainer() {
        return container;
    }
}