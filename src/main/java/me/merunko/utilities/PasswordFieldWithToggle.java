package me.merunko.utilities;

import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;

public class PasswordFieldWithToggle extends PasswordField {
    private final TextField visibleField = new TextField();
    private final HBox container = new HBox(5);

    // Constants for styling
    private static final String WHITE = "#FFFFFF";
    private static final String YELLOW = "#FFD700";
    private static final String DARK_GRAY = "#333333";
    private static final String TEXT_FIELD_STYLE =
            "-fx-text-fill: " + WHITE + "; " +
                    "-fx-prompt-text-fill: #AAAAAA; " +
                    "-fx-background-color: " + DARK_GRAY + "; " +
                    "-fx-background-radius: 4px; " +
                    "-fx-border-color: " + YELLOW + "; " +
                    "-fx-border-radius: 4px; " +
                    "-fx-border-width: 1px;";

    public PasswordFieldWithToggle() {
        // Configure visible text field
        visibleField.setVisible(false);
        visibleField.setManaged(false);
        visibleField.setFont(Font.font("Roboto", 14));
        visibleField.setStyle(TEXT_FIELD_STYLE);
        visibleField.setPrefWidth(400);
        visibleField.setPrefHeight(40);

        // Configure toggle checkbox
        CheckBox toggle = new CheckBox("Show Password");
        toggle.setFont(Font.font("Roboto", 12));
        toggle.setTextFill(javafx.scene.paint.Color.web(YELLOW));
        toggle.setStyle("-fx-background-color: transparent;");

        // Style the password field
        this.setFont(Font.font("Roboto", 14));
        this.setStyle(TEXT_FIELD_STYLE);
        this.setPrefWidth(400);
        this.setPrefHeight(40);

        // Setup toggle behavior
        toggle.selectedProperty().addListener((obs, oldVal, show) -> {
            if (show) {
                visibleField.setText(getText());
                setVisible(false);
                setManaged(false);
                visibleField.setVisible(true);
                visibleField.setManaged(true);
                visibleField.requestFocus();
            } else {
                setText(visibleField.getText());
                visibleField.setVisible(false);
                visibleField.setManaged(false);
                setVisible(true);
                setManaged(true);
                requestFocus();
            }
        });

        container.getChildren().addAll(this, visibleField, toggle);
        container.setAlignment(Pos.CENTER_LEFT);
    }

    public HBox getContainer() {
        return container;
    }
}