package me.merunko.PenguinGuard.interfaces.inner;

import com.google.auth.oauth2.GoogleCredentials;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;
import me.google.drive.DriveService;
import me.merunko.PenguinGuard.Entry.Entry;
import me.merunko.PenguinGuard.cache.EntryCache;
import me.google.authorisation.GoogleAuthService;
import me.merunko.PenguinGuard.security.Converter;
import me.merunko.utilities.ResourceLoader;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EntryList {
    private static final String BLACK = "#000000";
    private static final String WHITE = "#FFFFFF";
    private static final String YELLOW = "#FFD700";
    private static final String DARK_GRAY = "#333333";
    private static final String ORANGE = "#FFA500";

    private final ScrollPane container;
    private final GoogleAuthService authService;
    private final ResourceLoader resourceLoader;
    private final DriveService driveService;

    private VBox entriesContainer;
    private boolean sortAscending = true;

    public EntryList(GoogleAuthService authService) {
        this.authService = Objects.requireNonNull(authService, "AuthService cannot be null");
        this.resourceLoader = new ResourceLoader();
        this.container = new ScrollPane();

        GoogleCredentials credentials = authService.getCredentials();
        if (credentials == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        this.driveService = new DriveService(credentials, new Converter());

        setupUI();
        loadEntriesFromCache();
    }

    private void setupUI() {
        container.setFitToWidth(true);
        container.setStyle(String.format("-fx-background: %s; -fx-border-color: %s;", BLACK, WHITE));
        container.setPadding(new Insets(10));

        // Search field with debounce
        TextField searchField = createSearchField();

        // Sort controls
        HBox sortControls = createSortControls();

        entriesContainer = new VBox(10);
        entriesContainer.setPadding(new Insets(10));

        VBox mainContainer = new VBox(10, searchField, sortControls, entriesContainer);
        container.setContent(mainContainer);
    }

    private TextField createSearchField() {
        TextField searchField = new TextField();
        searchField.setPromptText("Search by category...");
        searchField.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s;", DARK_GRAY, WHITE));

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            PauseTransition pause = new PauseTransition(Duration.millis(300));
            pause.setOnFinished(e -> filterEntries(newVal));
            pause.playFromStart();
        });
        return searchField;
    }

    private HBox createSortControls() {
        HBox sortControls = new HBox(10);
        sortControls.setAlignment(Pos.CENTER_LEFT);

        Label sortLabel = new Label("Sort by:");
        sortLabel.setTextFill(Color.web(WHITE));

        ToggleGroup sortGroup = new ToggleGroup();

        RadioButton sortAZ = createRadioButton("A-Z", true, sortGroup);
        RadioButton sortZA = createRadioButton("Z-A", false, sortGroup);

        sortControls.getChildren().addAll(sortLabel, sortAZ, sortZA);
        return sortControls;
    }

    private RadioButton createRadioButton(String text, boolean ascending, ToggleGroup group) {
        RadioButton button = new RadioButton(text);
        button.setToggleGroup(group);
        button.setSelected(ascending == sortAscending);
        button.setTextFill(Color.web(WHITE));
        button.setOnAction(e -> {
            sortAscending = ascending;
            sortEntries(ascending);
        });
        return button;
    }

    private void loadEntriesFromCache() {
        EntryCache cache = EntryCache.getInstance();
        if (cache.isCacheStale()) {
            new Thread(() -> {
                try {
                    GoogleCredentials credentials = authService.getCredentials();
                    if (credentials == null) {
                        Platform.runLater(() -> showErrorAlert("Authentication expired"));
                        return;
                    }
                    cache.refreshCache(credentials);
                    Platform.runLater(this::updateUIWithCachedEntries);
                } catch (Exception e) {
                    Platform.runLater(() -> showErrorAlert("Failed to refresh cache: " + e.getMessage()));
                }
            }).start();
        }
        updateUIWithCachedEntries();
    }

    private void updateUIWithCachedEntries() {
        List<Entry> allEntries = EntryCache.getInstance().getAllEntries();

        if (allEntries.isEmpty()) {
            showNoEntriesMessage();
        } else {
            displayGroupedEntries(allEntries);
        }
    }

    private void displayGroupedEntries(List<Entry> entries) {
        entriesContainer.getChildren().clear();

        Map<String, List<Entry>> groupedEntries = entries.stream()
                .collect(Collectors.groupingBy(Entry::category));

        List<String> sortedCategories = new ArrayList<>(groupedEntries.keySet());
        sortedCategories.sort((c1, c2) -> sortAscending ?
                c1.compareToIgnoreCase(c2) : c2.compareToIgnoreCase(c1));

        for (String category : sortedCategories) {
            List<Entry> categoryEntries = groupedEntries.get(category);
            categoryEntries.sort(Comparator.comparing(Entry::name));

            TitledPane categoryPane = createCategoryPane(category, categoryEntries);
            entriesContainer.getChildren().add(categoryPane);
        }
    }

    private TitledPane createCategoryPane(String category, List<Entry> entries) {
        TitledPane pane = new TitledPane();
        pane.setExpanded(false);
        pane.setText(category);
        pane.setStyle(String.format("-fx-text-fill: %s; -fx-font-family: 'Roboto'; -fx-font-size: 16px;", BLACK));

        VBox entriesBox = new VBox(5);
        entriesBox.setPadding(new Insets(5));

        entries.forEach(entry -> entriesBox.getChildren().add(createEntryCard(entry)));
        pane.setContent(entriesBox);

        return pane;
    }

    private VBox createEntryCard(Entry entry) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(15));
        card.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 5;", DARK_GRAY));
        card.setMaxWidth(Double.MAX_VALUE);

        Label nameLabel = new Label("Entry Name: " + entry.name());
        nameLabel.setFont(Font.font("Roboto", 16));
        nameLabel.setTextFill(Color.web(YELLOW));

        VBox detailsBox = new VBox(5);
        addOptionalField(detailsBox, "Email:", entry.email());
        addOptionalField(detailsBox, "Username:", entry.username());
        addOptionalField(detailsBox, "Other Information:", entry.otherInfo());

        HBox passwordContainer = createPasswordField(entry.password());
        HBox footer = createFooter(entry);

        card.getChildren().addAll(nameLabel, detailsBox, passwordContainer, footer);
        return card;
    }

    private void addOptionalField(VBox container, String label, String value) {
        if (value != null && !value.isEmpty()) {
            Label fieldLabel = new Label(label + " " + value);
            fieldLabel.setFont(Font.font("Roboto", 14));
            fieldLabel.setTextFill(Color.web(WHITE));
            fieldLabel.setWrapText(true);
            container.getChildren().add(fieldLabel);
        }
    }

    private HBox createPasswordField(String password) {
        HBox container = new HBox(5);
        container.setAlignment(Pos.CENTER_LEFT);

        Label passwordLabel = new Label("Password: ••••••••");
        passwordLabel.setFont(Font.font("Roboto", 14));

        Button toggleButton = new Button("Show");
        toggleButton.setOnAction(e -> togglePasswordVisibility(passwordLabel, toggleButton, password));

        Button copyButton = createCopyButton(password);

        container.getChildren().addAll(passwordLabel, toggleButton, copyButton);
        return container;
    }

    private void togglePasswordVisibility(Label label, Button button, String password) {
        if (button.getText().equals("Show")) {
            label.setText("Password: " + password);
            button.setText("Hide");
        } else {
            label.setText("Password: ••••••••");
            button.setText("Show");
        }
    }

    private Button createCopyButton(String password) {
        Button button = new Button("Copy");
        button.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(password);
            Clipboard.getSystemClipboard().setContent(content);
            showTooltip(button);
        });
        return button;
    }

    private void showTooltip(Button button) {
        Tooltip tooltip = new Tooltip("Copied to clipboard!");
        tooltip.setAutoHide(true);
        tooltip.show(button,
                button.localToScreen(button.getBoundsInLocal()).getMaxX(),
                button.localToScreen(button.getBoundsInLocal()).getMinY() - 30);
    }

    private HBox createFooter(Entry entry) {
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button deleteButton = createDeleteButton(entry);
        // Removed: deleteButtons.put(entry.id(), deleteButton);

        footer.getChildren().add(deleteButton);
        return footer;
    }

    private Button createDeleteButton(Entry entry) {
        ImageView trashIcon = resourceLoader.loadImage("trash-icon", "png", 16, 16, true, WHITE);
        Button button = new Button();
        button.setGraphic(trashIcon);
        button.setStyle("-fx-background-color: transparent;");
        button.setOnAction(e -> confirmAndDeleteEntry(entry));
        return button;
    }

    private void confirmAndDeleteEntry(Entry entry) {
        Alert confirmation = createConfirmationAlert(
                "Are you sure you want to delete '" + entry.name() + "'?"
        );

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                deleteEntryWithDelay(entry);
            }
        });
    }

    private void deleteEntryWithDelay(Entry entry) {
        PauseTransition delay = new PauseTransition(Duration.seconds(1));
        delay.setOnFinished(event -> {
            try {
                GoogleCredentials credentials = authService.getCredentials();
                if (credentials == null) {
                    Platform.runLater(() -> showErrorAlert("Authentication expired"));
                    return;
                }

                // Remove from Drive and cache
                driveService.removeEntryFromDrive(entry);
                EntryCache.getInstance().removeEntry(entry.id());

                // Refresh UI
                Platform.runLater(() -> {
                    updateUIWithCachedEntries();
                    showSuccessAlert();
                });

            } catch (Exception e) {
                Platform.runLater(() ->
                        showErrorAlert("Delete failed: " + e.getMessage()));
            }
        });
        delay.play();
    }

    private Alert createConfirmationAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        return alert;
    }

    private void styleAlert(Alert alert) {
        DialogPane pane = alert.getDialogPane();
        pane.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s;",
                DARK_GRAY, YELLOW
        ));

        Node content = pane.lookup(".content.label");
        if (content != null) {
            content.setStyle(String.format(
                    "-fx-text-fill: %s; -fx-font-size: 14px;",
                    YELLOW
            ));
        }

        ButtonBar buttonBar = (ButtonBar) pane.lookup(".button-bar");
        if (buttonBar != null) {
            buttonBar.setStyle(String.format("-fx-background-color: %s;", DARK_GRAY));
            for (Node node : buttonBar.getButtons()) {
                if (node instanceof Button button) {
                    button.setStyle(String.format(
                            "-fx-background-color: %s; -fx-text-fill: black; -fx-font-weight: bold;",
                            ORANGE
                    ));
                    button.setOnMouseEntered(e -> button.setEffect(new javafx.scene.effect.Glow(0.3)));
                    button.setOnMouseExited(e -> button.setEffect(null));
                }
            }
        }
    }

    private void showSuccessAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText("Entry deleted successfully");
        styleAlert(alert);
        alert.show();
    }

    private void showErrorAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            styleAlert(alert);
            alert.show();
        });
    }

    private void showNoEntriesMessage() {
        entriesContainer.getChildren().clear();
        Label label = new Label("No entries found");
        label.setFont(Font.font("Roboto", 16));
        label.setTextFill(Color.web(YELLOW));
        entriesContainer.getChildren().add(label);
    }

    private void sortEntries(boolean ascending) {
        this.sortAscending = ascending;
        List<Entry> entries = EntryCache.getInstance().getAllEntries();

        if (entries.isEmpty()) {
            showNoEntriesMessage();
        } else {
            displayGroupedEntries(entries);
        }
    }

    private void filterEntries(String searchText) {
        List<Entry> entries = EntryCache.getInstance().getAllEntries();

        if (searchText == null || searchText.isEmpty()) {
            displayGroupedEntries(entries);
            return;
        }

        List<Entry> filtered = entries.stream()
                .filter(e -> e.category().toLowerCase().contains(searchText.toLowerCase()))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            showNoEntriesMessage();
        } else {
            displayGroupedEntries(filtered);
        }
    }

    public ScrollPane getContainer() {
        return container;
    }
}