package me.merunko.PenguinGuard.interfaces.outer;

import com.google.auth.oauth2.GoogleCredentials;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import me.google.drive.DriveService;
import me.merunko.PenguinGuard.cache.EntryCache;
import me.merunko.PenguinGuard.security.Converter;
import me.merunko.utilities.ResourceLoader;

import java.io.IOException;

public class GuardLogin {
    private final ResourceLoader resourceLoader;
    private VBox container;
    private PasswordField passwordField;
    private TextField visiblePasswordField;
    private PasswordField confirmPasswordField;
    private TextField visibleConfirmField;
    private boolean passwordVisible = false;
    private ImageView eyeIconView;
    private final GoogleCredentials credentials; // Updated from AccessToken
    private final Runnable onSuccessCallback;
    private final Runnable onBackCallback;
    private final Runnable showGoogleLoginCallback;
    private final DriveService driveService;

    public GuardLogin(Runnable onSuccess, Runnable onBack,
                      GoogleCredentials credentials, Runnable showGoogleLogin,
                      boolean guardExists) {
        this.credentials = credentials;
        this.onSuccessCallback = onSuccess;
        this.onBackCallback = onBack;
        this.showGoogleLoginCallback = showGoogleLogin;
        this.resourceLoader = new ResourceLoader();
        Converter converter = new Converter();
        this.driveService = new DriveService(credentials, converter); // Updated to use GoogleCredentials

        initializeContainer();

        if (guardExists) {
            showVerificationInterface();
        } else {
            showSetupInterface();
        }
    }

    private void initializeContainer() {
        container = new VBox(20);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(40));
        container.setBackground(new Background(new BackgroundFill(Color.GRAY, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    protected void showSetupInterface() {
        container.getChildren().clear();

        // Add logo
        ImageView logo = resourceLoader.loadImage("logo", "png", 120, 100, false, "#FFFFFF");

        Label titleLabel = createLabel("PenguinGuard", 24, "#FFD700"); // Yellow
        Label instructionLabel = createLabel("Set PenguinGuard password", 16, "#FFFFFF"); // White
        instructionLabel.setWrapText(true);

        // Password fields setup
        setupPasswordFields();

        // Toggle button setup
        setupToggleButton();

        Button submitButton = createButton("Continue", "#FF8C00", "black"); // Orange
        Button backButton = createButton("Back", "#333333", "#FFD700"); // Dark gray with yellow text

        backButton.setOnAction(event -> onBackCallback.run());

        HBox buttonBox = new HBox(10, backButton, submitButton);
        buttonBox.setAlignment(Pos.CENTER);

        submitButton.setOnAction(event -> handleSetupSubmit());

        container.getChildren().addAll(
                logo,
                titleLabel,
                instructionLabel,
                createPasswordFieldsContainer(),
                createToggleButtonContainer(),
                buttonBox
        );
    }

    protected void showVerificationInterface() {
        container.getChildren().clear();

        ImageView logo = resourceLoader.loadImage("logo", "png", 100, 120, false, "#FFFFFF");

        Label titleLabel = createLabel("Verification", 24, "#FFD700"); // Yellow
        Label instructionLabel = createLabel("Enter your password to continue", 16, "#FFFFFF"); // White
        instructionLabel.setWrapText(true);

        // Setup password field with visibility toggle
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        passwordField.setPrefSize(280, 42);
        passwordField.setStyle("-fx-font-family: 'Roboto'; -fx-font-size: 14px; -fx-text-fill: white; -fx-prompt-text-fill: #AAAAAA; -fx-background-color: #333333;");

        visiblePasswordField = new TextField();
        visiblePasswordField.setPromptText("Enter password");
        visiblePasswordField.setPrefSize(280, 42);
        visiblePasswordField.setStyle("-fx-font-family: 'Roboto'; -fx-font-size: 14px; -fx-text-fill: white; -fx-prompt-text-fill: #AAAAAA; -fx-background-color: #333333;");
        visiblePasswordField.setVisible(false);

        StackPane passwordFieldContainer = new StackPane();
        passwordFieldContainer.getChildren().addAll(passwordField, visiblePasswordField);

        // Create eye icon toggle button
        eyeIconView = resourceLoader.createEyeIcon(false);
        Button toggleVisibilityButton = new Button();
        toggleVisibilityButton.setGraphic(eyeIconView);
        toggleVisibilityButton.setStyle("-fx-background-color: transparent; -fx-padding: 10;");
        toggleVisibilityButton.setOnAction(e -> toggleVerificationPasswordVisibility());

        HBox toggleButtonContainer = new HBox(toggleVisibilityButton);
        toggleButtonContainer.setAlignment(Pos.CENTER);

        Button submitButton = createButton("Continue", "#FF8C00", "black"); // Orange
        Button backButton = createButton("Back", "#333333", "#FFD700"); // Dark gray with yellow text
        backButton.setOnAction(event -> showGoogleLoginCallback.run());

        HBox buttonBox = new HBox(10, backButton, submitButton);
        buttonBox.setAlignment(Pos.CENTER);

        submitButton.setOnAction(event -> {
            String password = getCurrentPassword();
            if (password == null || password.trim().isEmpty()) {
                showAlert("Password cannot be empty");
                return;
            }
            verifyPassword();
        });

        container.getChildren().addAll(
                logo,
                titleLabel,
                instructionLabel,
                passwordFieldContainer,
                toggleButtonContainer,
                buttonBox
        );

        Platform.runLater(() -> {
            if (passwordField.isVisible()) {
                passwordField.requestFocus();
            } else {
                visiblePasswordField.requestFocus();
            }
        });
    }

    private void toggleVerificationPasswordVisibility() {
        passwordVisible = !passwordVisible;

        if (passwordVisible) {
            visiblePasswordField.setText(passwordField.getText());
            passwordField.setVisible(false);
            visiblePasswordField.setVisible(true);
            eyeIconView.setImage(resourceLoader.createEyeIcon(true).getImage());
        } else {
            passwordField.setText(visiblePasswordField.getText());
            passwordField.setVisible(true);
            visiblePasswordField.setVisible(false);
            eyeIconView.setImage(resourceLoader.createEyeIcon(false).getImage());
        }
    }

    private void setupPasswordFields() {
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        passwordField.setPrefSize(280, 42);
        passwordField.setStyle("-fx-font-family: 'Roboto'; -fx-font-size: 14px; -fx-text-fill: white; -fx-prompt-text-fill: #AAAAAA; -fx-background-color: #333333;");

        visiblePasswordField = new TextField();
        visiblePasswordField.setPromptText("Enter password");
        visiblePasswordField.setPrefSize(280, 42);
        visiblePasswordField.setStyle("-fx-font-family: 'Roboto'; -fx-font-size: 14px; -fx-text-fill: white; -fx-prompt-text-fill: #AAAAAA; -fx-background-color: #333333;");
        visiblePasswordField.setVisible(false);

        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm password");
        confirmPasswordField.setPrefSize(280, 42);
        confirmPasswordField.setStyle("-fx-font-family: 'Roboto'; -fx-font-size: 14px; -fx-text-fill: white; -fx-prompt-text-fill: #AAAAAA; -fx-background-color: #333333;");

        visibleConfirmField = new TextField();
        visibleConfirmField.setPromptText("Confirm password");
        visibleConfirmField.setPrefSize(280, 42);
        visibleConfirmField.setStyle("-fx-font-family: 'Roboto'; -fx-font-size: 14px; -fx-text-fill: white; -fx-prompt-text-fill: #AAAAAA; -fx-background-color: #333333;");
        visibleConfirmField.setVisible(false);
    }

    private StackPane createPasswordFieldsContainer() {
        StackPane passwordFieldContainer = new StackPane();
        passwordFieldContainer.getChildren().addAll(passwordField, visiblePasswordField);

        StackPane confirmFieldContainer = new StackPane();
        confirmFieldContainer.getChildren().addAll(confirmPasswordField, visibleConfirmField);

        VBox fieldsContainer = new VBox(10, passwordFieldContainer, confirmFieldContainer);
        return new StackPane(fieldsContainer);
    }

    private void setupToggleButton() {
        eyeIconView = resourceLoader.createEyeIcon(false);
    }

    private HBox createToggleButtonContainer() {
        Button toggleVisibilityButton = new Button();
        toggleVisibilityButton.setGraphic(eyeIconView);
        toggleVisibilityButton.setStyle("-fx-background-color: transparent; -fx-padding: 10;");
        toggleVisibilityButton.setOnAction(e -> togglePasswordVisibility());

        HBox toggleButtonContainer = new HBox(toggleVisibilityButton);
        toggleButtonContainer.setAlignment(Pos.CENTER);
        return toggleButtonContainer;
    }

    private void handleSetupSubmit() {
        if (validatePasswords()) {
            try {
                String password = getCurrentPassword();

                boolean guardExists = driveService.doesGuardExist();

                if (guardExists) {
                    Platform.runLater(() -> {
                        showAlert("PenguinGuard already exists. Please verify your password.");
                        showVerificationInterface();
                    });
                } else {
                    boolean created = driveService.createGuardFolderAndFile(password);
                    if (created) {
                        Platform.runLater(this::showVerificationInterface);
                    } else {
                        Platform.runLater(() -> showAlert("Failed to create PenguinGuard"));
                    }
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error: " + e.getMessage()));
            }
        }
    }

    private void verifyPassword() {
        try {
            String enteredPassword = getCurrentPassword();
            boolean verified = driveService.verifyPassword(enteredPassword);

            if (verified) {
                // Initialize cache with GoogleCredentials instead of AccessToken
                try {
                    EntryCache.getInstance().initializeCache(credentials); // Updated
                    Platform.runLater(onSuccessCallback);
                } catch (IOException e) {
                    Platform.runLater(() -> showAlert("Failed to initialize cache: " + e.getMessage()));
                }
            } else {
                Platform.runLater(() -> {
                    showAlert("Incorrect password");
                    passwordField.clear();
                    visiblePasswordField.clear();
                    passwordField.setVisible(true);
                    visiblePasswordField.setVisible(false);
                    passwordVisible = false;
                    eyeIconView.setImage(resourceLoader.createEyeIcon(false).getImage());
                });
            }
        } catch (Exception e) {
            Platform.runLater(() -> showAlert("Verification failed: " + e.getMessage()));
        }
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;

        if (passwordVisible) {
            visiblePasswordField.setText(passwordField.getText());
            visibleConfirmField.setText(confirmPasswordField.getText());

            passwordField.setVisible(false);
            visiblePasswordField.setVisible(true);
            confirmPasswordField.setVisible(false);
            visibleConfirmField.setVisible(true);

            eyeIconView.setImage(resourceLoader.createEyeIcon(true).getImage());
        } else {
            passwordField.setText(visiblePasswordField.getText());
            confirmPasswordField.setText(visibleConfirmField.getText());

            passwordField.setVisible(true);
            visiblePasswordField.setVisible(false);
            confirmPasswordField.setVisible(true);
            visibleConfirmField.setVisible(false);

            eyeIconView.setImage(resourceLoader.createEyeIcon(false).getImage());
        }
    }

    private boolean validatePasswords() {
        String password = passwordVisible ? visiblePasswordField.getText() : passwordField.getText();
        String confirmPassword = passwordVisible ? visibleConfirmField.getText() : confirmPasswordField.getText();

        if (password.isEmpty() || confirmPassword.isEmpty()) {
            showAlert("Password fields cannot be empty");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            showAlert("Passwords do not match");
            return false;
        }

        if (password.length() < 8) {
            showAlert("Password must be at least 8 characters");
            return false;
        }

        return true;
    }

    private String getCurrentPassword() {
        if (visiblePasswordField.isVisible()) {
            return visiblePasswordField.getText();
        }
        return passwordField.getText();
    }

    private Label createLabel(String text, double size, String color) {
        Label label = new Label(text);
        label.setFont(Font.font("Roboto", size));
        label.setTextFill(Color.web(color));
        return label;
    }

    private Button createButton(String text, String bgColor, String textColor) {
        Button button = new Button(text);
        button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-font-family: 'Roboto'; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 4px;",
                bgColor, textColor
        ));
        button.setPrefSize(280, 42);
        button.setOnMouseEntered(e -> button.setEffect(new javafx.scene.effect.Glow(0.3)));
        button.setOnMouseExited(e -> button.setEffect(null));
        return button;
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Style the alert dialog
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #000000; -fx-text-fill: white;");
        dialogPane.setHeaderText(null);
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        // Style buttons
        ButtonBar buttonBar = (ButtonBar) dialogPane.lookup(".button-bar");
        buttonBar.setStyle("-fx-background-color: #000000;");
        for (Node node : buttonBar.getButtons()) {
            if (node instanceof Button button) {
                button.setStyle("-fx-background-color: #FF8C00; -fx-text-fill: black; -fx-font-weight: bold;");
                button.setOnMouseEntered(e -> button.setEffect(new javafx.scene.effect.Glow(0.3)));
                button.setOnMouseExited(e -> button.setEffect(null));
            }
        }

        alert.showAndWait();
    }

    public VBox getContainer() {
        return container;
    }
}