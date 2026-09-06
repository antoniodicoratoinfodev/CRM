package com.crm.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.animation.*;
import javafx.util.Duration;

public class SplashScreenController {

    @FXML private javafx.scene.Node logoView;
    @FXML private Label statusLabel;
    @FXML private Label percentLabel;
    @FXML private Rectangle progressFill;

    private final double MAX_BAR_WIDTH = 320.0;

    @FXML
    public void initialize() {
        // Animazione logo: pulsazione e rotazione leggera
        ScaleTransition scale = new ScaleTransition(Duration.millis(2500), logoView);
        scale.setFromX(1.0); scale.setFromY(1.0);
        scale.setToX(1.1); scale.setToY(1.1);
        scale.setAutoReverse(true);
        scale.setCycleCount(Animation.INDEFINITE);
        scale.play();

        // Apparizione iniziale
        FadeTransition fadeIn = new FadeTransition(Duration.millis(800), logoView);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    public void setStatus(String status) {
        if (statusLabel != null) statusLabel.setText(status.toUpperCase());
    }

    public void setProgress(double progress) {
        // Progress is between 0.0 and 1.0.
        if (progressFill != null) {
            double targetWidth = progress * MAX_BAR_WIDTH;
            
            // Animazione fluida della barra
            Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(300), 
                new KeyValue(progressFill.widthProperty(), targetWidth, Interpolator.EASE_BOTH))
            );
            timeline.play();
            
            if (percentLabel != null) {
                percentLabel.setText((int)(progress * 100) + "%");
            }
        }
    }
}
