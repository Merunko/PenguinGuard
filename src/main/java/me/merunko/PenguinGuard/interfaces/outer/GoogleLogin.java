package me.merunko.PenguinGuard.interfaces.outer;

import com.google.auth.oauth2.AccessToken;

import com.google.auth.oauth2.GoogleCredentials;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import me.google.authorisation.AuthStorage;
import me.google.authorisation.GoogleAuthService;
import me.google.drive.DriveService;
import me.merunko.PenguinGuard.security.Converter;
import me.merunko.PenguinGuard.user.UserData;

import me.merunko.utilities.ResourceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.scene.paint.Color.GRAY;

public class GoogleLogin {
    private static final Logger logger = LoggerFactory.getLogger(GoogleLogin.class);
    private static final ResourceLoader resourceLoader = new ResourceLoader();
    private final GoogleAuthService authService;
    private boolean isLoggedIn = false;
    private String firstName = "";
    private VBox loginContainer;
    private final Runnable onLoginSuccess;
    private DriveService driveService;
    private GoogleCredentials credentials; // Store credentials at class level

    // Color scheme constants
    private static final String BLACK = "#000000";
    private static final String WHITE = "#FFFFFF";
    private static final String YELLOW = "#FFD700";
    private static final String DARK_ORANGE = "#E69500";

    public GoogleLogin(Runnable onLoginSuccess, GoogleAuthService authService) {
        this.authService = authService;
        this.authService.setAuthCompleteListener(this::handleAuthComplete);
        this.onLoginSuccess = onLoginSuccess;
        initializeSession();
    }

    private void initializeSession() {
        try {
            authService.initializeWithRefreshToken();
            if (authService.getAccessToken() != null) {
                // We have a valid session
                this.isLoggedIn = true;
                AuthStorage.SessionData session = AuthStorage.loadSession();
                if (session != null) {
                    this.firstName = session.firstName();
                    this.credentials = GoogleCredentials.newBuilder()
                            .setAccessToken(new AccessToken(authService.getAccessToken(), null))
                            .build();
                    this.driveService = new DriveService(credentials, new Converter());
                }
                Platform.runLater(this::updateUI);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize session", e);
        }
    }

    public VBox createLoginInterface() {
        // Main container
        loginContainer = new VBox(20);
        loginContainer.setAlignment(Pos.CENTER);
        loginContainer.setPadding(new Insets(20));
        loginContainer.setBackground(new Background(new BackgroundFill(Color.web(String.valueOf(GRAY)), CornerRadii.EMPTY, Insets.EMPTY)));

        updateUI(); // Initial UI setup

        return loginContainer;
    }

    private void updateUI() {
        // Clear existing children
        loginContainer.getChildren().clear();

        // Add this check at the beginning
        if (loginContainer == null) {
            return;
        }

        // App logo with safe loading
        ImageView logo = resourceLoader.loadImage("logo", "png", 100, 120, false, "#FFFFFF");

        // Welcome text - dynamic based on login status
        Label welcomeLabel;
        Label subLabel;

        if (isLoggedIn) {
            welcomeLabel = new Label("Welcome Back, " + firstName);
            subLabel = new Label("You're logged in");
        } else {
            welcomeLabel = new Label("PenguinGuard");
            subLabel = new Label("Sign in to continue");
        }

        welcomeLabel.setFont(Font.font("Roboto", 24));
        welcomeLabel.setTextFill(Color.web(YELLOW));
        subLabel.setFont(Font.font("Roboto", 14));
        subLabel.setTextFill(Color.web(WHITE));

        // Dynamic button based on login status
        Button authButton = isLoggedIn ? createButton("Logout", YELLOW, BLACK, true) : createGoogleLoginButton();

        // Continue to PenguinGuard button (only shown when logged in)
        Button continueButton = createButton("Continue to PenguinGuard", "#FF8C00", "black", false);
        continueButton.setVisible(isLoggedIn);
        continueButton.setOnAction(event -> handleContinueAction());

        // Exit button
        Button exitButton = createButton("Exit", "#333333", "#FFD700", false);
        exitButton.setOnAction(event -> System.exit(0));

        // Add all components to the layout
        if (isLoggedIn) {
            loginContainer.getChildren().addAll(logo, welcomeLabel, subLabel, authButton, continueButton, exitButton);
        } else {
            loginContainer.getChildren().addAll(logo, welcomeLabel, subLabel, authButton, exitButton);
        }
    }

    private void handleAuthComplete(UserData userData) {
        if (userData != null) {
            this.isLoggedIn = true;
            this.firstName = userData.firstName();

            String refreshToken = authService.getRefreshToken();

            if (refreshToken != null && !refreshToken.isBlank()) {
                AuthStorage.saveSession(
                        userData.email(),
                        userData.firstName(),
                        refreshToken
                );

                try {
                    // Create proper GoogleCredentials with refresh capability
                    this.credentials = GoogleCredentials.newBuilder()
                            .setAccessToken(new AccessToken(
                                    authService.getAccessToken(),
                                    null))
                            .build();

                    this.driveService = new DriveService(credentials, new Converter());
                } catch (Exception e) {
                    logger.error("Failed to initialize DriveService", e);
                    showErrorAlert("Failed to initialize Google Drive access");
                }
            } else {
                logger.warn("Refresh token is null or empty. Skipping session save.");
            }

            Platform.runLater(this::updateUI);
        }
    }

    private Button createGoogleLoginButton() {
        ImageView googleIcon = resourceLoader.loadImage("google-icon", "png", 30, 30, false, "#000000");
        Label btnText = new Label("Continue with Google");
        btnText.setStyle("-fx-text-fill: " + BLACK + "; -fx-font-family: 'Roboto'; -fx-font-size: 14px;");

        HBox btnContent = new HBox(10, googleIcon, btnText);
        btnContent.setAlignment(Pos.CENTER);

        final Button button = new Button();
        button.setGraphic(btnContent);
        button.setStyle("-fx-background-color: " + YELLOW + "; -fx-border-color: " + DARK_ORANGE + "; -fx-border-radius: 4px;");
        button.setPrefSize(280, 42);

        button.setOnMouseEntered(event -> setButtonStyle(button, true));
        button.setOnMouseExited(event -> setButtonStyle(button, false));
        button.setOnAction(event -> handleLoginAction());

        return button;
    }

    private Button createButton(String text, String bgColor, String textColor, boolean logoutFunction) {
        Button button = new Button(text);
        button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-family: 'Roboto'; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 4px;",
                bgColor, textColor
        ));
        button.setPrefSize(280, 42);
        button.setOnMouseEntered(e -> button.setEffect(new javafx.scene.effect.Glow(0.3)));
        button.setOnMouseExited(e -> button.setEffect(null));
        if (logoutFunction) {
            button.setOnAction(event -> handleLogoutAction());
        }
        return button;
    }

    private void setButtonStyle(Button button, boolean hover) {
        String style = hover ?
                "-fx-background-color: " + DARK_ORANGE + ";" :
                "-fx-background-color: " + YELLOW + ";";
        button.setStyle(style + "-fx-border-color: " + DARK_ORANGE + "; -fx-border-radius: 4px;");
    }

    private void handleLoginAction() {
        try {
            authService.authenticate();
        } catch (Exception e) {
            showErrorAlert(e.getMessage());
        }
    }

    private void handleLogoutAction() {
        try {
            // 1. Revoke tokens
            authService.revokeTokens();

            // 2. Clear all user data and stop servers
            authService.clearUserData();

            // 3. Update application state
            this.isLoggedIn = false;
            this.firstName = "";
            AuthStorage.clearSession();

            // 4. Refresh UI
            updateUI();
            logger.info("User logged out successfully");

        } catch (Exception e) {
            String errorMsg = "Logout Error: " + e.getMessage();
            logger.error(errorMsg, e);
            Platform.runLater(() -> showErrorAlert(errorMsg));
        }
    }

    private void handleContinueAction() {
        try {
            if (driveService == null) {
                if (authService.getRefreshToken() == null) {
                    showErrorAlert("Not authenticated properly. Please login again.");
                    return;
                }

                this.credentials = GoogleCredentials.newBuilder()
                        .setAccessToken(new AccessToken(authService.getAccessToken(), null))
                        .build();

                this.driveService = new DriveService(credentials, new Converter());
            }

            boolean guardExists = driveService.doesGuardExist();
            GuardLogin guardInterface = getGuardLogin(guardExists);

            Platform.runLater(() -> {
                loginContainer.getChildren().setAll(guardInterface.getContainer());
                if (guardExists) {
                    guardInterface.showVerificationInterface();
                } else {
                    guardInterface.showSetupInterface();
                }
            });

        } catch (Exception e) {
            String errorMsg = "Failed to check PenguinGuard status: " + e.getMessage();
            logger.error(errorMsg, e);
            Platform.runLater(() -> showErrorAlert(errorMsg));
        }
    }

    private GuardLogin getGuardLogin(boolean guardExists) {
        return new GuardLogin(
                onLoginSuccess,
                () -> Platform.runLater(this::updateUI),
                this.credentials, // Now passing GoogleCredentials
                () -> Platform.runLater(this::updateUI),
                guardExists
        );
    }

    private void showErrorAlert(String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Login Error");
        alert.setHeaderText("Failed to authenticate with Google");
        alert.setContentText(content);
        alert.showAndWait();
    }
}
