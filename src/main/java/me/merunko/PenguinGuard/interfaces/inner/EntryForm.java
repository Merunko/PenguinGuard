package me.merunko.PenguinGuard.interfaces.inner;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import me.google.drive.DriveService;
import me.merunko.PenguinGuard.Entry.Entry;
import me.merunko.PenguinGuard.cache.EntryCache;
import me.google.authorisation.GoogleAuthService;
import me.merunko.PenguinGuard.security.Converter;
import me.merunko.PenguinGuard.security.Encryption;
import me.merunko.utilities.PasswordFieldWithToggle;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public class EntryForm {
    private final GoogleAuthService authService;
    private final Converter converter;

    // Constants
    private static final String BLACK = "#000000";
    private static final String WHITE = "#FFFFFF";
    private static final String YELLOW = "#FFD700";
    private static final String DARK_ORANGE = "#E69500";
    private static final String ORANGE = "#FF8C00";
    private static final String DARK_GRAY = "#333333";
    private static final String DELIMITER = "|||";

    private final GridPane form;
    private final TextField categoryField;
    private final TextField nameField;
    private final TextField emailField;
    private final TextField userField;
    private final TextArea otherArea;
    private final PasswordFieldWithToggle passField;

    public EntryForm(GoogleAuthService authService) {
        this.authService = Objects.requireNonNull(authService, "AuthService cannot be null");
        this.converter = new Converter();
        this.form = createFormGrid();

        // Initialize fields
        categoryField = createStyledTextField();
        nameField = createStyledTextField();
        emailField = createStyledTextField();
        userField = createStyledTextField();
        otherArea = createStyledTextArea();
        passField = new PasswordFieldWithToggle();
        applyTextFieldStyle(passField);

        setupForm();
    }

    private GridPane createFormGrid() {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.TOP_LEFT);
        grid.setHgap(20);
        grid.setVgap(20);
        grid.setPadding(new Insets(40));
        grid.setBackground(new Background(new BackgroundFill(Color.web(BLACK), CornerRadii.EMPTY, Insets.EMPTY)));
        return grid;
    }

    private TextField createStyledTextField() {
        TextField field = new TextField();
        applyTextFieldStyle(field);
        return field;
    }

    private void applyTextFieldStyle(TextField field) {
        field.setFont(Font.font("Roboto", 14));
        field.setStyle("-fx-text-fill: " + WHITE + "; " +
                "-fx-prompt-text-fill: #AAAAAA; " +
                "-fx-background-color: " + DARK_GRAY + "; " +
                "-fx-background-radius: 4px; " +
                "-fx-border-color: " + YELLOW + "; " +
                "-fx-border-radius: 4px; " +
                "-fx-border-width: 1px;");
        field.setPrefWidth(400);
        field.setPrefHeight(40);
    }

    private TextArea createStyledTextArea() {
        TextArea area = new TextArea();
        area.setFont(Font.font("Roboto", 14));
        area.setStyle("-fx-text-fill: " + WHITE + "; " +
                "-fx-prompt-text-fill: #AAAAAA; " +
                "-fx-background-color: " + DARK_GRAY + "; " +
                "-fx-background-radius: 4px; " +
                "-fx-border-color: " + YELLOW + "; " +
                "-fx-border-radius: 4px; " +
                "-fx-border-width: 1px; " +
                "-fx-control-inner-background: " + DARK_GRAY + "; " +
                "-fx-focus-color: " + YELLOW + "; " +
                "-fx-faint-focus-color: " + YELLOW + "22;");
        area.setPrefRowCount(5);
        area.setPrefWidth(400);
        area.setWrapText(true);
        return area;
    }

    private void setupForm() {
        // Title
        Label titleLabel = new Label("Add New Entry");
        titleLabel.setFont(Font.font("Roboto", 24));
        titleLabel.setTextFill(Color.web(YELLOW));
        form.add(titleLabel, 0, 0, 2, 1);

        // Form fields
        addFormField("Category*:", categoryField, 1);
        addFormField("Entry Name*:", nameField, 2);
        addFormField("Email:", emailField, 3);
        addFormField("Username:", userField, 4);
        addFormField("Other Information:", otherArea, 5);

        Label passwordLabel = new Label("Password*:");
        passwordLabel.setFont(Font.font("Roboto", 16));
        passwordLabel.setTextFill(Color.web(YELLOW));
        form.add(passwordLabel, 0, 6);
        form.add(passField.getContainer(), 1, 6);

        // Submit button
        Button submitBtn = createButton(this::saveEntry);
        HBox buttonContainer = new HBox(submitBtn);
        buttonContainer.setAlignment(Pos.CENTER_RIGHT);
        form.add(buttonContainer, 1, 8);
    }

    private void addFormField(String labelText, Control field, int row) {
        Label label = new Label(labelText);
        label.setFont(Font.font("Roboto", 16));
        label.setTextFill(Color.web(YELLOW));
        form.add(label, 0, row);
        form.add(field, 1, row);
    }

    private Button createButton(Runnable action) {
        Button button = new Button("Save Entry");
        button.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: black; -fx-font-family: 'Roboto'; " +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 4px;",
                ORANGE
        ));
        button.setPrefSize(200, 50);
        button.setOnMouseEntered(e -> {
            button.setEffect(new javafx.scene.effect.Glow(0.3));
            button.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: black; -fx-font-family: 'Roboto'; " +
                            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 4px; " +
                            "-fx-effect: dropshadow(gaussian, %s, 8, 0, 0, 2);",
                    DARK_ORANGE, YELLOW
            ));
        });
        button.setOnMouseExited(e -> {
            button.setEffect(null);
            button.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: black; -fx-font-family: 'Roboto'; " +
                            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 4px;",
                    ORANGE
            ));
        });
        button.setOnAction(e -> action.run());
        return button;
    }

    private void saveEntry() {
        try {
            validateRequiredFields();
            validateOptionalFields();

            String accessToken = authService.getAccessToken();
            if (accessToken == null) {
                showAlert("Authentication Error", "Not authenticated with Google Drive. Please sign in first.", Alert.AlertType.ERROR);
                return;
            }

            GoogleCredentials credentials = GoogleCredentials.newBuilder()
                    .setAccessToken(new AccessToken(accessToken, null))
                    .build();

            DriveService driveService = new DriveService(credentials, converter);
            byte[] entryBytes = createEntryBytes();

            try {
                driveService.saveEntryToDrive(entryBytes);
            } catch (DriveService.DriveOperationException e) {
                showAlert("Drive Save Error", "Entry saved to cache but failed to save to Drive: " + e.getMessage(), Alert.AlertType.ERROR);
                return;
            }

            cacheEntry();
            showAlert("Entry Saved", "The new entry was successfully saved", Alert.AlertType.INFORMATION);
            clearForm();

        } catch (IllegalArgumentException e) {
            showAlert("Validation Error", e.getMessage(), Alert.AlertType.ERROR);
        } catch (Exception e) {
            showAlert("Error", "Failed to save entry: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void validateRequiredFields() {
        if (categoryField.getText().trim().isEmpty() ||
                nameField.getText().trim().isEmpty() ||
                passField.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Category, Entry Name, and Password are required fields.");
        }
    }

    private void validateOptionalFields() {
        if (emailField.getText().trim().isEmpty() &&
                userField.getText().trim().isEmpty() &&
                otherArea.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("At least one of Email, Username, or Other Information must be provided.");
        }
    }

    private byte[] createEntryBytes() {
        // Trim all inputs
        String category = categoryField.getText().trim();
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String username = userField.getText().trim();
        String otherInfo = otherArea.getText().trim();
        String password = passField.getText().trim();

        // Validate no field contains the delimiter
        if (Stream.of(category, name, email, username, otherInfo, password)
                .anyMatch(s -> s.contains(DELIMITER))) {
            throw new IllegalArgumentException("Field values cannot contain '" + DELIMITER + "'");
        }

        String id = UUID.randomUUID().toString();
        String entryString = String.join(DELIMITER,
                id,
                Encryption.encrypt(category),
                Encryption.encrypt(name),
                email.isEmpty() ? "" : Encryption.encrypt(email),
                username.isEmpty() ? "" : Encryption.encrypt(username),
                otherInfo.isEmpty() ? "" : Encryption.encrypt(otherInfo),
                Encryption.encrypt(password)  // Include encrypted password here
        );

        return entryString.getBytes(StandardCharsets.UTF_8);
    }

    private void cacheEntry() {
        String password = passField.getText().trim();

        Entry newEntry = new Entry(
                UUID.randomUUID().toString(),
                categoryField.getText().trim(),
                nameField.getText().trim(),
                emailField.getText().trim(),
                userField.getText().trim(),
                otherArea.getText().trim(),
                password
        );

        EntryCache.getInstance().addEntry(newEntry);
    }

    private void showAlert(String title, String message, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-background-color: " + DARK_GRAY + "; -fx-text-fill: " + YELLOW + ";");
        dialogPane.setHeaderText(null);
        dialogPane.lookup(".content.label").setStyle("-fx-text-fill: " + YELLOW + "; -fx-font-size: 14px;");

        ButtonBar buttonBar = (ButtonBar) dialogPane.lookup(".button-bar");
        buttonBar.setStyle("-fx-background-color: " + DARK_GRAY + ";");
        for (Node node : buttonBar.getButtons()) {
            if (node instanceof Button button) {
                button.setStyle("-fx-background-color: " + ORANGE + "; -fx-text-fill: black; -fx-font-weight: bold;");
                button.setOnMouseEntered(e -> button.setEffect(new javafx.scene.effect.Glow(0.3)));
                button.setOnMouseExited(e -> button.setEffect(null));
            }
        }

        alert.showAndWait();
    }

    private void clearForm() {
        categoryField.clear();
        nameField.clear();
        emailField.clear();
        userField.clear();
        otherArea.clear();
        passField.clear();
    }

    public GridPane getForm() {
        return form;
    }
}
