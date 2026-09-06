package com.crm.service;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/** Owns the active application theme and applies it consistently to scenes and dialogs. */
public final class ThemeService {
    private static final String DARK_STYLESHEET = "/css/style-dark.css";
    private static final String LIGHT_STYLESHEET = "/css/style.css";
    private static final String BLUE_GRAY_STYLESHEET = "/css/style-blue-gray.css";
    private static final String GRAY_BLUE_STYLESHEET = "/css/style-gray-blue.css";

    public enum Theme {
        DARK("Dark"),
        LIGHT("Light"),
        BLUE_GRAY("Blue-gray"),
        GRAY_BLUE("Gray Blue");

        private final String displayName;

        Theme(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        private Theme next() {
            Theme[] themes = values();
            return themes[(ordinal() + 1) % themes.length];
        }
    }

    private final Supplier<Window> ownerSupplier;
    private Theme activeTheme = Theme.DARK;

    public ThemeService(Supplier<Window> ownerSupplier) {
        this.ownerSupplier = Objects.requireNonNull(ownerSupplier);
    }

    public boolean isDarkMode() {
        return activeTheme != Theme.LIGHT;
    }

    public boolean isBlueGrayTheme() {
        return activeTheme == Theme.BLUE_GRAY;
    }

    public boolean isGrayBlueTheme() {
        return activeTheme == Theme.GRAY_BLUE;
    }

    public Theme activeTheme() {
        return activeTheme;
    }

    public void toggle() {
        activeTheme = activeTheme.next();
    }

    public void setTheme(Theme theme) {
        activeTheme = Objects.requireNonNull(theme);
    }

    public void restore(String storedTheme) {
        try {
            activeTheme = Theme.valueOf(storedTheme);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            activeTheme = Theme.DARK;
        }
    }

    public void applyTo(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().setAll(stylesheets());
    }

    public void applyTo(Dialog<?> dialog) {
        if (dialog == null) return;
        Window owner = ownerSupplier.get();
        if (owner != null && dialog.getOwner() == null) dialog.initOwner(owner);
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().setAll(stylesheets());
        if (!pane.getStyleClass().contains("dialog-pane")) pane.getStyleClass().add("dialog-pane");
        applyDialogSceneFill(pane.getScene());
        pane.sceneProperty().addListener((observable, oldScene, newScene) -> applyDialogSceneFill(newScene));
    }

    private void applyDialogSceneFill(Scene scene) {
        if (scene == null) return;
        scene.setFill(switch (activeTheme) {
            case LIGHT -> Color.web("#ffffff");
            case DARK -> Color.web("#111a2c");
            case BLUE_GRAY -> Color.web("#202d42");
            case GRAY_BLUE -> Color.web("#30363d");
        });
    }

    private List<String> stylesheets() {
        Typography.load();
        String base = activeTheme == Theme.LIGHT ? LIGHT_STYLESHEET : DARK_STYLESHEET;
        String baseUrl = stylesheetUrl(base);
        List<String> stylesheets = new ArrayList<>(switch (activeTheme) {
            case BLUE_GRAY -> List.of(baseUrl, stylesheetUrl(BLUE_GRAY_STYLESHEET));
            case GRAY_BLUE -> List.of(baseUrl, stylesheetUrl(GRAY_BLUE_STYLESHEET));
            default -> List.of(baseUrl);
        });
        stylesheets.add(stylesheetUrl("/css/workspace.css"));
        stylesheets.add(stylesheetUrl("/css/typography.css"));
        return List.copyOf(stylesheets);
    }

    private String stylesheetUrl(String resource) {
        return Objects.requireNonNull(ThemeService.class.getResource(resource),
                "Theme stylesheet is missing: " + resource).toExternalForm();
    }
}
