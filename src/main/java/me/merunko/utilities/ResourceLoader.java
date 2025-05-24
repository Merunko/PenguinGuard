package me.merunko.utilities;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;

public class ResourceLoader {

    public ImageView loadImage(String imageName, String imageType, int width, int height, boolean disableFX, String fxHEX) {
        try (InputStream stream = getClass().getResourceAsStream("/static/" + imageName + "." + imageType)) {
            if (stream != null) {
                ImageView imageView = new ImageView(new Image(stream));
                imageView.setFitWidth(width);
                imageView.setFitHeight(height);
                if (!disableFX) {
                    imageView.setStyle("-fx-effect: dropshadow(three-pass-box," + fxHEX + ", 10, 0.5, 0, 0);");
                }
                return imageView;
            }
        } catch (Exception e) {
            System.err.println("Error loading image: " + imageName + "." + imageType);
        }
        Rectangle placeholder = new Rectangle(80, 80, Color.web("#FFD700")); // Yellow placeholder
        placeholder.setArcWidth(20);
        placeholder.setArcHeight(20);
        return new ImageView(placeholder.snapshot(null, null));
    }

    public ImageView createEyeIcon(boolean open) {
        try {
            String iconPath = open ? "/static/eye-open.png" : "/static/eye-closed.png";
            InputStream stream = getClass().getResourceAsStream(iconPath);
            if (stream != null) {
                ImageView imageView = new ImageView(new Image(stream));
                imageView.setFitWidth(20);
                imageView.setFitHeight(20);
                return imageView;
            }
        } catch (Exception e) {
            System.err.println("Error loading eye icon");
        }

        Label eyeLabel = new Label(open ? "👁" : "🔒");
        eyeLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #FFD700;");
        return new ImageView(eyeLabel.snapshot(null, null));
    }

}
