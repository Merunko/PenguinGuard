package me.merunko.PenguinGuard;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import me.google.authorisation.GoogleAuthService;
import me.merunko.PenguinGuard.interfaces.outer.GoogleLogin;
import me.merunko.PenguinGuard.interfaces.outer.MainMenu;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class PenguinGuard extends Application {
    private Stage primaryStage;
    private StackPane rootContainer;
    private final GoogleAuthService googleAuthService = new GoogleAuthService();

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        rootContainer = new StackPane();
        Scene mainScene = new Scene(rootContainer);

        // Load CSS stylesheet
        try {
            String css = Objects.requireNonNull(getClass().getResource("/templates/styles.css")).toExternalForm();
            mainScene.getStylesheets().add(css);
        } catch (NullPointerException e) {
            System.err.println("Stylesheet not found, application will run without CSS");
        }

        // Initial setup with fade-in effect
        showLoginScreenWithFade();

        // Configure stage
        primaryStage.setTitle("PenguinGuard");
        primaryStage.setScene(mainScene);
        primaryStage.setResizable(false);

        // Set initial window size for login
        primaryStage.setWidth(450);
        primaryStage.setHeight(500);

        // Set the window icon
        try {
            InputStream iconStream = getClass().getResourceAsStream("/static/logo.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                primaryStage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("Error loading window icon: " + e.getMessage());
        }

        primaryStage.show();
    }

    private void showLoginScreenWithFade() {
        // Pass the shared googleAuthService instance to GoogleLogin
        GoogleLogin googleLogin = new GoogleLogin(this::showMainMenu, googleAuthService);
        StackPane loginContainer = new StackPane(googleLogin.createLoginInterface());
        loginContainer.setOpacity(0);

        rootContainer.getChildren().setAll(loginContainer);

        // Fade in the login screen
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), loginContainer);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.play();

        // Set login window properties
        Platform.runLater(() -> {
            primaryStage.setResizable(false);
            primaryStage.setWidth(400);
            primaryStage.setHeight(500);
        });
    }

    public void showMainMenu() {
        // Pre-initialize the MainMenu to ensure proper sizing
        MainMenu mainMenu = new MainMenu(googleAuthService);
        BorderPane mainContainer = mainMenu.getContainer();

        // Create wrapper to ensure proper centering
        StackPane wrapper = new StackPane(mainContainer);
        wrapper.setAlignment(Pos.CENTER);

        // Set initial opacity to 0 for fade-in
        wrapper.setOpacity(0);

        // First fade out the current content
        if (!rootContainer.getChildren().isEmpty()) {
            FadeTransition fadeOut = getFadeTransition(wrapper);
            fadeOut.play();
        } else {
            // If there's no current content, just show the main menu directly
            rootContainer.getChildren().setAll(wrapper);

            Platform.runLater(() -> {
                primaryStage.setResizable(true);
                primaryStage.setMinWidth(800);
                primaryStage.setMinHeight(600);
                primaryStage.setWidth(1000);
                primaryStage.setHeight(800);
                primaryStage.centerOnScreen();
            });

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), wrapper);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        }
    }

    private FadeTransition getFadeTransition(StackPane wrapper) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), rootContainer.getChildren().get(0));
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(event -> {
            // Replace content after fade out completes
            rootContainer.getChildren().setAll(wrapper);

            // Window properties
            Platform.runLater(() -> {
                primaryStage.setResizable(true);
                primaryStage.setMinWidth(800);
                primaryStage.setMinHeight(600);
                primaryStage.setWidth(1000);
                primaryStage.setHeight(800);
                primaryStage.centerOnScreen();
            });

            // Now perform fade-in
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), wrapper);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        return fadeOut;
    }

    public static void main(String[] args) {
        launch(args);
    }
}