package com.crm.controller;

import com.crm.model.UserAccount;
import com.crm.repository.LocalUserRepository;
import com.crm.service.AuthService;
import com.crm.service.ThemeService;
import com.crm.service.WorkspaceVaultService;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Explicit opt-in protection and authenticated offline recovery-key management. */
final class LocalProtectionController {
    private final UserAccount user;
    private final ThemeService theme;
    private final BiConsumer<Runnable, Consumer<Throwable>> maintenance;
    private final AuthService auth = new AuthService(new LocalUserRepository());

    LocalProtectionController(UserAccount user, ThemeService theme, BiConsumer<Runnable, Consumer<Throwable>> maintenance) {
        this.user = user; this.theme = theme; this.maintenance = maintenance;
    }

    void show() {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Local protection"); theme.applyTo(dialog);
        String status = user.isVaultEnabled() ? user.isVaultMigrated() ? "Workspace and managed backups encrypted" :
                "Protection setup is incomplete — finish migration" : "Workspace stored without encryption";
        Label title = new Label(status); title.getStyleClass().add("settings-card-title"); title.setWrapText(true);
        Label scope = new Label("Optional AES-256-GCM protection covers this profile's contacts, tasks, notes, trash and managed backups. "
                + "You will enter your password at each sign-in. Account identity, avatar images and exported workspace/ICS files are not encrypted.");
        scope.setWrapText(true);
        Label recovery = new Label(user.getRecoveryVerifier().isBlank() ? "No offline recovery key is configured. Without a password or saved key, encrypted data cannot be recovered." :
                "An offline recovery key is configured. Replacing it invalidates the previous key. Recovery keys are single-use."); recovery.setWrapText(true);
        VBox content = new VBox(14, title, scope, recovery); content.getStyleClass().add("event-editor"); content.setPrefWidth(510);
        dialog.getDialogPane().setContent(content);
        ButtonType enable = new ButtonType(user.isVaultEnabled() ? "Finish setup" : "Enable encryption", ButtonBar.ButtonData.OK_DONE);
        ButtonType key = new ButtonType("New recovery key", ButtonBar.ButtonData.OTHER);
        if (!user.isVaultEnabled() || !user.isVaultMigrated()) dialog.getDialogPane().getButtonTypes().add(enable);
        dialog.getDialogPane().getButtonTypes().addAll(key, ButtonType.CLOSE);
        dialog.showAndWait().ifPresent(result -> {
            if (result == enable) {
                if (user.isVaultEnabled()) password("Finish local protection setup").ifPresent(password ->
                        maintenance.accept(() -> { auth.verifyPassword(user, password); auth.finishProtection(user); }, this::showResult));
                else prepare();
            } else if (result == key) newRecoveryKey();
        });
    }

    private void prepare() {
        password("Enable local encryption").ifPresent(password -> {
            AtomicReference<WorkspaceVaultService.Setup> setup = new AtomicReference<>();
            maintenance.accept(() -> setup.set(auth.prepareProtection(user, password)), failure -> {
                if (failure != null) { showResult(failure); return; }
                WorkspaceVaultService.Setup prepared = setup.get();
                if (!showKey(prepared.recoveryKey(), true)) { prepared.close(); return; }
                maintenance.accept(() -> {
                    try (prepared) { auth.enableProtection(user, prepared); }
                }, this::showResult);
            });
        });
    }

    private void newRecoveryKey() {
        password("Replace offline recovery key").ifPresent(password -> {
            AtomicReference<String> key = new AtomicReference<>();
            maintenance.accept(() -> key.set(auth.createRecoveryKey(user, password)), failure -> {
                if (failure != null) showResult(failure); else showKey(key.get(), false);
            });
        });
    }

    private java.util.Optional<String> password(String title) {
        Dialog<String> dialog = new Dialog<>(); dialog.setTitle(title); theme.applyTo(dialog);
        PasswordField password = new PasswordField(); password.setPromptText("Current password");
        Label message = new Label("Confirm your current password. No email is sent and no external service is contacted."); message.setWrapText(true);
        VBox content = new VBox(12, message, password); content.getStyleClass().add("event-editor"); content.setPrefWidth(430);
        dialog.getDialogPane().setContent(content); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            if (password.getText().isEmpty()) { event.consume(); message.setText("Enter your current password."); }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText() : null);
        var result = dialog.showAndWait(); password.clear(); return result;
    }

    private boolean showKey(String key, boolean beforeActivation) {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Save your offline recovery key"); theme.applyTo(dialog);
        TextArea value = new TextArea(key); value.setEditable(false); value.setWrapText(true); value.setPrefRowCount(2);
        value.setAccessibleText("Offline recovery key. Store privately outside this computer.");
        Label instructions = new Label("Keep this key privately, outside this computer (for example in a password manager). "
                + "Anyone with this key can reset your password. It is shown only now."); instructions.setWrapText(true);
        CheckBox saved = new CheckBox("I have stored this recovery key safely");
        VBox content = new VBox(14, instructions, value, saved); content.getStyleClass().add("event-editor"); content.setPrefWidth(510);
        dialog.getDialogPane().setContent(content);
        ButtonType done = new ButtonType(beforeActivation ? "Encrypt local workspace" : "Done", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(done);
        if (beforeActivation) dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(done).disableProperty().bind(saved.selectedProperty().not());
        boolean confirmed = dialog.showAndWait().filter(button -> button == done).isPresent(); value.clear(); return confirmed;
    }

    private void showResult(Throwable failure) {
        Alert alert = new Alert(failure == null ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        theme.applyTo(alert); alert.setTitle(failure == null ? "Local protection ready" : "Protection not completed"); alert.setHeaderText(null);
        while (failure != null && failure.getCause() != null && failure instanceof java.util.concurrent.CompletionException) failure = failure.getCause();
        alert.setContentText(failure == null ? "Your workspace and managed backups are encrypted. Keep your password and offline recovery key safe." :
                (failure.getMessage() == null ? "The operation failed." : failure.getMessage()) +
                "\n\nKeep your password and key. If setup was started, use Finish setup to retry. Do not assume older files are encrypted until setup succeeds.");
        alert.showAndWait();
    }
}
