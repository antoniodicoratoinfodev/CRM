package com.crm.controller;

import com.crm.model.UserAccount;
import com.crm.repository.LocalUserRepository;
import com.crm.service.AuthService;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class LoginController {
    @FXML private VBox loginPane, registerPane, recoveryPane, resetPane;
    @FXML private TextField loginEmail, registerName, registerEmail, recoveryEmail, resetEmail, resetCode;
    @FXML private PasswordField loginPassword, registerPassword, registerPasswordConfirm, resetPassword, resetPasswordConfirm;
    @FXML private CheckBox rememberLogin;
    @FXML private Label loginMessage, registerMessage, recoveryMessage, resetMessage;
    private final AuthService auth = new AuthService(new LocalUserRepository());
    private BiConsumer<UserAccount, Boolean> authenticated;

    public void setOnAuthenticated(BiConsumer<UserAccount, Boolean> authenticated) { this.authenticated = authenticated; }
    public void requestInitialFocus() { Platform.runLater(loginEmail::requestFocus); }
    /** Clears form state before a retained login view is shown for a new session. */
    public void resetForLoginScreen() {
        loginEmail.clear();
        registerName.clear();
        registerEmail.clear();
        recoveryEmail.clear();
        resetEmail.clear();
        resetCode.clear();
        rememberLogin.setSelected(false);
        show(loginPane);
    }
    @FXML private void showLogin() { show(loginPane); }
    @FXML private void showRegister() { show(registerPane); }
    @FXML private void showRecovery() { show(recoveryPane); }
    private void show(VBox pane) {
        clearPasswordFields();
        resetCode.clear();
        if (pane != resetPane) resetEmail.clear();
        loginPane.setVisible(false); registerPane.setVisible(false); recoveryPane.setVisible(false); resetPane.setVisible(false);
        loginPane.setManaged(false); registerPane.setManaged(false); recoveryPane.setManaged(false); resetPane.setManaged(false);
        pane.setVisible(true); pane.setManaged(true); clearMessages();
        if (pane == loginPane) Platform.runLater(loginEmail::requestFocus);
        else if (pane == registerPane) Platform.runLater(registerName::requestFocus);
        else if (pane == recoveryPane) Platform.runLater(recoveryEmail::requestFocus);
        else Platform.runLater(resetCode::requestFocus);
    }
    private void clearMessages() { loginMessage.setText(""); registerMessage.setText(""); recoveryMessage.setText(""); resetMessage.setText(""); }
    private void clearPasswordFields() {
        loginPassword.clear();
        registerPassword.clear();
        registerPasswordConfirm.clear();
        resetPassword.clear();
        resetPasswordConfirm.clear();
    }

    @FXML private void handleLogin() {
        String email = loginEmail.getText();
        String password = loginPassword.getText();
        boolean remember = rememberLogin.isSelected();
        loginPassword.clear();
        runAuthTask(loginPane, "Signing in…", () -> auth.login(email, password),
                user -> authenticated.accept(user, remember),
                message -> loginMessage.setText(message));
    }

    @FXML private void handleRegister() {
        try {
            if (!registerPassword.getText().equals(registerPasswordConfirm.getText())) throw new IllegalArgumentException("The passwords do not match.");
        } catch (IllegalArgumentException e) {
            registerMessage.setText(e.getMessage());
            return;
        }
        String name = registerName.getText();
        String email = registerEmail.getText();
        String password = registerPassword.getText();
        registerPassword.clear();
        registerPasswordConfirm.clear();
        runAuthTask(registerPane, "Creating account…", () -> auth.register(name, email, password),
                user -> authenticated.accept(user, false),
                message -> registerMessage.setText(message));
    }

    @FXML private void handleRecovery() {
        String email = recoveryEmail.getText().trim();
        if (email.isBlank()) { recoveryMessage.setText("Enter your account email."); return; }
        recoveryEmail.clear(); show(resetPane); resetEmail.setText(email);
        resetMessage.setText("Use the offline recovery key previously saved from Settings. No email is sent.");
    }

    @FXML private void handleReset() {
        try {
            if (!resetPassword.getText().equals(resetPasswordConfirm.getText())) throw new IllegalArgumentException("The passwords do not match.");
        } catch (IllegalArgumentException e) {
            resetMessage.setText(e.getMessage());
            return;
        }
        String email = resetEmail.getText();
        String code = resetCode.getText();
        String password = resetPassword.getText();
        resetPassword.clear();
        resetPasswordConfirm.clear();
        resetCode.clear();
        runAuthTask(resetPane, "Updating password…", () -> {
            auth.resetPassword(email, code, password);
            return null;
        }, ignored -> {
            loginEmail.setText(email);
            resetEmail.clear();
            show(loginPane);
            loginMessage.setText("Password updated. You can now sign in.");
        }, message -> resetMessage.setText(message));
    }

    private <T> void runAuthTask(VBox pane, String status, Callable<T> operation,
                                 Consumer<T> onSuccess, Consumer<String> onFailure) {
        pane.setDisable(true);
        Label message = pane == loginPane ? loginMessage
                : pane == registerPane ? registerMessage
                : pane == recoveryPane ? recoveryMessage : resetMessage;
        message.setText(status);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return operation.call(); }
        };
        task.setOnSucceeded(event -> {
            pane.setDisable(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            pane.setDisable(false);
            Throwable failure = task.getException();
            if (failure instanceof IllegalArgumentException && failure.getMessage() != null) onFailure.accept(failure.getMessage());
            else onFailure.accept("The local data operation could not be completed. Try again without closing the app.");
        });
        Thread worker = new Thread(task, "voidreach-auth-worker");
        worker.setDaemon(true);
        worker.start();
    }
}
