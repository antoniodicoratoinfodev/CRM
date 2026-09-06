package com.crm.controller;

import com.crm.model.UserAccount;
import com.crm.repository.LocalUserRepository;
import com.crm.service.AvatarImageProcessor;
import com.crm.service.AvatarImageProcessor.CropRegion;
import com.crm.service.AvatarImageProcessor.CropSelection;
import com.crm.service.AvatarImageProcessor.Source;
import com.crm.service.AvatarService;
import com.crm.service.AuthService;
import com.crm.service.DialogService;
import com.crm.service.ThemeService;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Handles account actions, authentication changes, logout, and profile avatars. */
public final class AccountController {
    private final Label currentUserLabel;
    private final Button accountMenuButton;
    private final FontIcon defaultAvatarIcon;
    private final ImageView avatarImage;
    private final ThemeService themeService;
    private final DialogService dialogService;
    private final AuthService authService;
    private final AvatarService avatarService;
    private final ChangeListener<Number> avatarScaleListener = (observable, oldValue, newValue) -> refreshAvatar();
    private final WeakChangeListener<Number> weakAvatarScaleListener = new WeakChangeListener<>(avatarScaleListener);

    private Window avatarScaleWindow;
    private UserAccount currentUser;
    private Runnable logoutAction;
    private Runnable exportDataAction;
    private Runnable importDataAction;
    private long avatarLoadVersion;
    private String displayedAvatarFileName;
    private int displayedAvatarPixelSize;

    public AccountController(Label currentUserLabel, Button accountMenuButton,
                             FontIcon defaultAvatarIcon, ImageView avatarImage,
                             ThemeService themeService, DialogService dialogService) {
        this.currentUserLabel = Objects.requireNonNull(currentUserLabel);
        this.accountMenuButton = Objects.requireNonNull(accountMenuButton);
        this.defaultAvatarIcon = Objects.requireNonNull(defaultAvatarIcon);
        this.avatarImage = Objects.requireNonNull(avatarImage);
        this.themeService = Objects.requireNonNull(themeService);
        this.dialogService = Objects.requireNonNull(dialogService);
        LocalUserRepository users = new LocalUserRepository();
        this.authService = new AuthService(users);
        this.avatarService = new AvatarService(users);
    }

    public void setCurrentUser(UserAccount user, Runnable logoutAction) {
        setCurrentUser(user, logoutAction, null, 0);
    }

    public void setCurrentUser(UserAccount user, Runnable logoutAction,
                               BufferedImage preloadedAvatar, int preloadedPixelSize) {
        currentUser = Objects.requireNonNull(user);
        com.crm.view.BrandMark.select(com.crm.model.AppIcon.from(user.getPreferredIcon()));
        this.logoutAction = logoutAction;
        currentUserLabel.setText(user.getFullName());
        displayedAvatarFileName = null;
        displayedAvatarPixelSize = 0;
        if (preloadedAvatar != null && preloadedPixelSize > 0) {
            applyAvatarImage(SwingFXUtils.toFXImage(preloadedAvatar, null));
            displayedAvatarFileName = user.getAvatarFileName();
            displayedAvatarPixelSize = preloadedPixelSize;
        } else {
            applyAvatarImage(null);
        }
        javafx.application.Platform.runLater(() -> {
            if (currentUser != user) return;
            installAvatarScaleTracking();
            refreshAvatar();
        });
    }

    /** Wires the account menu's data transfer entries to the owner-aware export/import flow. */
    public void setDataTransferActions(Runnable exportDataAction, Runnable importDataAction) {
        this.exportDataAction = exportDataAction;
        this.importDataAction = importDataAction;
    }

    public void showMenu() {
        if (currentUser == null) return;
        MenuItem profile = new MenuItem("Profile and account details");
        profile.setOnAction(event -> editProfile());
        MenuItem security = new MenuItem("Security: change password");
        security.setOnAction(event -> changePassword());
        MenuItem avatar = new MenuItem("Update profile picture");
        avatar.setOnAction(event -> updateAvatar());
        MenuItem exportData = new MenuItem("Export data");
        exportData.setOnAction(event -> { if (exportDataAction != null) exportDataAction.run(); });
        MenuItem importData = new MenuItem("Import data");
        importData.setOnAction(event -> { if (importDataAction != null) importDataAction.run(); });
        MenuItem logout = new MenuItem("Sign out");
        logout.setOnAction(event -> logout());
        Menu iconChoice = new Menu("App icon"); iconChoice.setId("appIconMenu");
        ToggleGroup icons = new ToggleGroup();
        for (com.crm.model.AppIcon icon : com.crm.model.AppIcon.values()) {
            RadioMenuItem item = new RadioMenuItem(icon.toString()); item.setId("appIcon" + icon.name());
            item.setToggleGroup(icons); item.setSelected(icon.name().equals(currentUser.getPreferredIcon()));
            item.setGraphic(new ImageView(com.crm.view.BrandMark.icon(icon, 24)));
            item.setOnAction(event -> selectIcon(icon)); iconChoice.getItems().add(item);
        }
        new ContextMenu(profile, security, avatar, iconChoice, new SeparatorMenuItem(),
                exportData, importData, new SeparatorMenuItem(), logout)
                .show(accountMenuButton, Side.BOTTOM, 0, 6);
    }

    private void selectIcon(com.crm.model.AppIcon icon) {
        String previous = currentUser.getPreferredIcon();
        currentUser.setPreferredIcon(icon.name());
        try { new LocalUserRepository().save(currentUser); }
        catch (IllegalStateException failure) {
            currentUser.setPreferredIcon(previous);
            dialogService.showError("Icon not saved", "The icon preference could not be saved. Check disk access and try again.");
            return;
        }
        com.crm.view.BrandMark.select(icon);
    }

    public void editProfile() {
        if (currentUser != null) showProfileDialog();
    }

    public void changePassword() {
        if (currentUser != null) showChangePasswordDialog();
    }

    public void updateAvatar() {
        if (currentUser != null) chooseAvatar();
    }

    public void logout() {
        detachScaleTracking();
        if (logoutAction != null) logoutAction.run();
    }

    private void chooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a profile picture");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG and JPG images", "*.png", "*.jpg", "*.jpeg"));
        java.io.File selected = chooser.showOpenDialog(accountMenuButton.getScene().getWindow());
        if (selected == null) return;
        Path selectedPath = selected.toPath();
        try {
            if (Files.size(selectedPath) > AvatarImageProcessor.MAX_UPLOAD_BYTES) {
                dialogService.showError("Image too large", "The profile picture cannot exceed 10 MB.");
                return;
            }
        } catch (IOException exception) {
            dialogService.showError("Invalid image", "The selected file could not be read.");
            return;
        }
        runBackgroundTask("Profile picture", "Preparing image...", "Invalid image",
                () -> avatarService.prepareSource(selectedPath), this::showAvatarCropDialog);
    }

    private void showAvatarCropDialog(Source source) {
        final double previewSize = 380;
        final double cropSize = 250;
        final Image previewImage = SwingFXUtils.toFXImage(source.preview(), null);
        Canvas canvas = new Canvas(previewSize, previewSize);

        class CropRenderer {
            double centerX = source.width() / 2.0;
            double centerY = source.height() / 2.0;
            double zoom = 1.0;
            double dragX;
            double dragY;

            CropSelection selection() { return new CropSelection(centerX, centerY, zoom); }

            void clampSelection() {
                CropSelection clamped = AvatarImageProcessor.clampSelection(source.width(), source.height(), selection());
                centerX = clamped.centerX();
                centerY = clamped.centerY();
            }

            void pan(double deltaX, double deltaY) {
                double sourceSide = Math.min(source.width(), source.height()) / zoom;
                centerX -= deltaX * sourceSide / cropSize;
                centerY -= deltaY * sourceSide / cropSize;
                clampSelection();
            }

            void draw() {
                GraphicsContext graphics = canvas.getGraphicsContext2D();
                graphics.setImageSmoothing(true);
                graphics.clearRect(0, 0, previewSize, previewSize);
                graphics.setFill(Color.web("#0f172a"));
                graphics.fillRect(0, 0, previewSize, previewSize);
                double backgroundScale = Math.min(previewSize / previewImage.getWidth(), previewSize / previewImage.getHeight());
                double backgroundWidth = previewImage.getWidth() * backgroundScale;
                double backgroundHeight = previewImage.getHeight() * backgroundScale;
                graphics.setGlobalAlpha(0.24);
                graphics.drawImage(previewImage, (previewSize - backgroundWidth) / 2,
                        (previewSize - backgroundHeight) / 2, backgroundWidth, backgroundHeight);
                graphics.setGlobalAlpha(1);
                graphics.setFill(Color.rgb(15, 23, 42, 0.55));
                graphics.fillRect(0, 0, previewSize, previewSize);
                CropRegion region = AvatarImageProcessor.resolveCropRegion(source.width(), source.height(), selection());
                double scaleX = previewImage.getWidth() / source.width();
                double scaleY = previewImage.getHeight() / source.height();
                double sourceX = region.x() * scaleX;
                double sourceY = region.y() * scaleY;
                double sourceWidth = Math.min(region.size() * scaleX, previewImage.getWidth() - sourceX);
                double sourceHeight = Math.min(region.size() * scaleY, previewImage.getHeight() - sourceY);
                double cropX = (previewSize - cropSize) / 2;
                double cropY = (previewSize - cropSize) / 2;
                graphics.save();
                graphics.beginPath();
                graphics.arc(previewSize / 2, previewSize / 2, cropSize / 2, cropSize / 2, 0, 360);
                graphics.closePath();
                graphics.clip();
                graphics.drawImage(previewImage, sourceX, sourceY, sourceWidth, sourceHeight,
                        cropX, cropY, cropSize, cropSize);
                graphics.restore();
                graphics.setStroke(Color.web("#60a5fa"));
                graphics.setLineWidth(3);
                graphics.strokeOval(cropX, cropY, cropSize, cropSize);
            }
        }

        CropRenderer renderer = new CropRenderer();
        renderer.draw();
        canvas.setOnMousePressed(event -> { renderer.dragX = event.getX(); renderer.dragY = event.getY(); });
        canvas.setOnMouseDragged(event -> {
            renderer.pan(event.getX() - renderer.dragX, event.getY() - renderer.dragY);
            renderer.dragX = event.getX();
            renderer.dragY = event.getY();
            renderer.draw();
        });
        Slider zoom = new Slider(1, AvatarImageProcessor.maxZoomFor(source.width(), source.height()), 1);
        zoom.setShowTickLabels(true);
        zoom.setMajorTickUnit(1);
        zoom.setBlockIncrement(0.1);
        zoom.valueProperty().addListener((observable, oldValue, newValue) -> {
            renderer.zoom = newValue.doubleValue();
            renderer.clampSelection();
            renderer.draw();
        });
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Crop profile picture");
        themeService.applyTo(dialog);
        ButtonType save = new ButtonType("Use this image", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        Label help = new Label("Drag the image to adjust the crop. Use the slider to zoom.");
        help.setWrapText(true);
        help.getStyleClass().add("auth-subtitle");
        VBox content = new VBox(14, help, canvas, new Label("Zoom"), zoom);
        content.setAlignment(Pos.CENTER);
        content.setPrefWidth(previewSize);
        dialog.getDialogPane().setContent(content);
        if (dialog.showAndWait().filter(result -> result == save).isPresent()) {
            CropSelection crop = renderer.selection();
            runBackgroundTask("Profile picture", "Optimizing profile picture...", "Image not updated", () -> {
                avatarService.updateAvatar(currentUser, source, crop);
                return null;
            }, ignored -> refreshAvatar());
        }
    }

    private void refreshAvatar() {
        UserAccount user = currentUser;
        if (user == null) return;
        int pixelSize = navbarAvatarPixelSize();
        if (Objects.equals(displayedAvatarFileName, user.getAvatarFileName())
                && displayedAvatarPixelSize == pixelSize && avatarImage.isVisible()) return;
        long requestVersion = ++avatarLoadVersion;
        javafx.concurrent.Task<BufferedImage> task = new javafx.concurrent.Task<>() {
            @Override protected BufferedImage call() { return avatarService.loadAvatarRendition(user, pixelSize); }
        };
        task.setOnSucceeded(event -> {
            if (currentUser != user || requestVersion != avatarLoadVersion) return;
            BufferedImage rendition = task.getValue();
            Image image = rendition == null ? null : SwingFXUtils.toFXImage(rendition, null);
            applyAvatarImage(image);
            displayedAvatarFileName = image == null ? null : user.getAvatarFileName();
            displayedAvatarPixelSize = image == null ? 0 : pixelSize;
        });
        task.setOnFailed(event -> {
            if (currentUser == user && requestVersion == avatarLoadVersion) {
                applyAvatarImage(null);
                displayedAvatarFileName = null;
                displayedAvatarPixelSize = 0;
            }
        });
        Thread worker = new Thread(task, "avatar-load-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void applyAvatarImage(Image image) {
        boolean available = image != null && !image.isError() && image.getWidth() > 0 && image.getHeight() > 0;
        if (available) avatarImage.setImage(image);
        avatarImage.setVisible(available);
        avatarImage.setManaged(available);
        defaultAvatarIcon.setVisible(!available);
        defaultAvatarIcon.setManaged(!available);
    }

    private int navbarAvatarPixelSize() {
        double outputScale = 1.0;
        if (avatarImage.getScene() != null && avatarImage.getScene().getWindow() != null) {
            Window window = avatarImage.getScene().getWindow();
            outputScale = Math.max(window.getOutputScaleX(), window.getOutputScaleY());
        }
        double logicalSize = Math.max(avatarImage.getFitWidth(), avatarImage.getFitHeight());
        return Math.max(1, (int) Math.ceil(logicalSize * outputScale));
    }

    private void installAvatarScaleTracking() {
        Window window = avatarImage.getScene() == null ? null : avatarImage.getScene().getWindow();
        if (window == null || window == avatarScaleWindow) return;
        detachScaleTracking();
        avatarScaleWindow = window;
        window.outputScaleXProperty().addListener(weakAvatarScaleListener);
        window.outputScaleYProperty().addListener(weakAvatarScaleListener);
    }

    private void detachScaleTracking() {
        if (avatarScaleWindow == null) return;
        avatarScaleWindow.outputScaleXProperty().removeListener(weakAvatarScaleListener);
        avatarScaleWindow.outputScaleYProperty().removeListener(weakAvatarScaleListener);
        avatarScaleWindow = null;
    }

    private <T> void runBackgroundTask(String dialogTitle, String statusText, String errorTitle,
                                   Callable<T> operation, Consumer<T> onSuccess) {
        javafx.concurrent.Task<T> task = new javafx.concurrent.Task<>() {
            @Override protected T call() throws Exception { return operation.call(); }
        };
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(32, 32);
        HBox content = new HBox(12, progressIndicator, new Label(statusText));
        content.setAlignment(Pos.CENTER_LEFT);
        Dialog<ButtonType> progressDialog = new Dialog<>();
        progressDialog.setTitle(dialogTitle);
        themeService.applyTo(progressDialog);
        progressDialog.getDialogPane().setContent(content);
        progressDialog.getDialogPane().setPrefWidth(360);
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        progressDialog.setOnCloseRequest(event -> {
            if (task.isRunning()) task.cancel(true);
        });
        task.setOnSucceeded(event -> { progressDialog.close(); onSuccess.accept(task.getValue()); });
        task.setOnFailed(event -> {
            progressDialog.close();
            dialogService.showError(errorTitle, avatarFailureMessage(task.getException()));
        });
        task.setOnCancelled(event -> progressDialog.close());
        Thread worker = new Thread(task, "avatar-image-worker");
        worker.setDaemon(true);
        progressDialog.show();
        worker.start();
    }

    private String avatarFailureMessage(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) return current.getMessage();
        }
        return "The selected image could not be processed.";
    }

    private void showProfileDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Profile and account");
        themeService.applyTo(dialog);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        TextField name = new TextField(currentUser.getFullName());
        TextField email = new TextField(currentUser.getEmail());
        email.setEditable(false);
        grid.addRow(0, new Label("Name:"), name);
        grid.addRow(1, new Label("Email:"), email);
        Label error = new Label(); error.getStyleClass().add("form-error"); error.setWrapText(true); grid.add(error, 0, 2, 2, 1);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (name.getText().trim().length() < 2) { event.consume(); error.setText("Enter your full name."); }
        });
        dialog.getDialogPane().setContent(grid);
        if (dialog.showAndWait().filter(result -> result == save).isPresent()) {
            String fullName = name.getText();
            runBackgroundTask("Profile and account", "Saving profile…", "Profile not saved", () -> {
                authService.updateProfile(currentUser, fullName);
                return currentUser.getFullName();
            }, currentUserLabel::setText);
        }
    }

    private void showChangePasswordDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change password");
        themeService.applyTo(dialog);
        ButtonType save = new ButtonType("Update password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        PasswordField current = new PasswordField();
        PasswordField next = new PasswordField();
        PasswordField confirm = new PasswordField();
        current.setPromptText("Current password");
        next.setPromptText("At least 8 characters");
        confirm.setPromptText("Repeat the new password");
        grid.addRow(0, new Label("Current password:"), current);
        grid.addRow(1, new Label("New password:"), next);
        grid.addRow(2, new Label("Confirm password:"), confirm);
        Label error = new Label(); error.getStyleClass().add("form-error"); error.setWrapText(true); grid.add(error, 0, 3, 2, 1);
        dialog.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (current.getText().isEmpty() || next.getText().length() < 8 || !next.getText().equals(confirm.getText())) {
                event.consume(); error.setText("Enter the current password and matching new passwords of at least 8 characters.");
            }
        });
        dialog.getDialogPane().setContent(grid);
        if (dialog.showAndWait().filter(result -> result == save).isEmpty()) return;
        if (!next.getText().equals(confirm.getText())) {
            dialogService.showError("Password not updated", "The passwords do not match.");
            return;
        }
        String currentPassword = current.getText();
        String nextPassword = next.getText();
        current.clear();
        next.clear();
        confirm.clear();
        runBackgroundTask("Account security", "Updating password…", "Password not updated", () -> {
            authService.changePassword(currentUser, currentPassword, nextPassword);
            return null;
        }, ignored -> dialogService.showInfo("Password updated", "Your account password has been updated."));
    }
}
