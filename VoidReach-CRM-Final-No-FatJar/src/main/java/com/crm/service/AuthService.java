package com.crm.service;

import com.crm.model.UserAccount;
import com.crm.repository.UserRepository;
import com.crm.repository.LocalCrmDataRepository;
import com.crm.repository.CrmBackupService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local authentication. Recovery requires a previously saved, high-entropy offline key. */
public class AuthService {
    private static final String HASH_PREFIX = "pbkdf2-sha256:600000:";
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();
    public AuthService(UserRepository users) { this.users = users; }

    public UserAccount register(String name, String email, String password) {
        if (name == null || name.trim().length() < 2) throw new IllegalArgumentException("Enter your full name.");
        if (email == null || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Enter a valid email address.");
        validatePassword(password);
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(normalized).isPresent()) throw new IllegalArgumentException("An account already exists for this email address.");
        UserAccount user = new UserAccount(); user.setId(UUID.randomUUID().toString()); user.setFullName(name.trim());
        user.setEmail(normalized); user.setCreatedAt(Instant.now()); setPassword(user, password); users.save(user); return user;
    }

    public UserAccount login(String email, String password) {
        UserAccount user = users.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Incorrect email or password."));
        if (!matchesPassword(user, password)) throw new IllegalArgumentException("Incorrect email or password.");
        WorkspaceVaultService.unlock(user, password);
        if (user.isVaultEnabled() && !user.isVaultMigrated()) finishProtection(user);
        if (!user.getPasswordHash().startsWith(HASH_PREFIX)) { setPassword(user, password); users.save(user); }
        return user;
    }

    public void verifyPassword(UserAccount user, String password) {
        if (!matchesPassword(user, password)) throw new IllegalArgumentException("The current password is incorrect.");
        WorkspaceVaultService.unlock(user, password);
    }

    public WorkspaceVaultService.Setup prepareProtection(UserAccount user, String password) {
        verifyPassword(user, password);
        return WorkspaceVaultService.prepare(user, password);
    }

    /** Called only after the user has explicitly confirmed storing the displayed recovery key. */
    public void enableProtection(UserAccount user, WorkspaceVaultService.Setup setup) {
        UserAccount updated = new UserAccount(user);
        updated.setVaultConfig(setup.config()); updated.setRecoveryVerifier(WorkspaceVaultService.verifier(setup.recoveryKey()));
        updated.setVaultMigrated(false);
        persistCredentials(user, updated); // Both account generations contain key wraps before any data encryption.
        WorkspaceVaultService.activate(user, setup);
        finishProtection(user);
    }

    public void finishProtection(UserAccount user) {
        if (!user.isVaultEnabled()) return;
        try { persistCredentials(user, new UserAccount(user)); }
        catch (RuntimeException failure) { WorkspaceVaultService.lock(user.getId()); throw failure; }
        new LocalCrmDataRepository().migrateProtection(user.getId());
        try (CrmBackupService backups = new CrmBackupService()) { backups.migrateProtection(user.getId()); }
        UserAccount updated = new UserAccount(user); updated.setVaultMigrated(true); persistCredentials(user, updated);
    }

    public String createRecoveryKey(UserAccount user, String password) {
        verifyPassword(user, password);
        String key = WorkspaceVaultService.newRecoveryKey();
        UserAccount updated = new UserAccount(user);
        WorkspaceVaultService.replaceRecoveryKey(updated, key);
        updated.setResetCodeHash(null); updated.setResetCodeExpiresAt(null);
        persistCredentials(user, updated);
        return key;
    }

    public void resetPassword(String email, String recoveryKey, String password) {
        UserAccount user = users.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or recovery key."));
        if (!WorkspaceVaultService.matchesRecovery(user, recoveryKey)) throw new IllegalArgumentException("Invalid email or recovery key.");
        validatePassword(password);
        UserAccount updated = new UserAccount(user);
        WorkspaceVaultService.recover(updated, recoveryKey, password);
        setPassword(updated, password); updated.setRecoveryVerifier("");
        updated.setResetCodeHash(null); updated.setResetCodeExpiresAt(null);
        try { persistCredentials(user, updated); }
        finally { WorkspaceVaultService.lock(user.getId()); }
    }

    public void updateProfile(UserAccount user, String fullName) {
        if (fullName == null || fullName.trim().length() < 2) throw new IllegalArgumentException("Enter your full name.");
        UserAccount updated = new UserAccount(user); updated.setFullName(fullName.trim()); users.save(updated);
        user.setFullName(updated.getFullName());
    }

    public void changePassword(UserAccount user, String currentPassword, String newPassword) {
        verifyPassword(user, currentPassword); validatePassword(newPassword);
        UserAccount updated = new UserAccount(user); WorkspaceVaultService.changePassword(updated, newPassword);
        setPassword(updated, newPassword); updated.setResetCodeHash(null); updated.setResetCodeExpiresAt(null);
        persistCredentials(user, updated);
    }

    private void persistCredentials(UserAccount user, UserAccount updated) {
        users.save(updated);
        user.setPasswordHash(updated.getPasswordHash()); user.setPasswordSalt(updated.getPasswordSalt());
        user.setVaultConfig(updated.getVaultConfig()); user.setVaultMigrated(updated.isVaultMigrated());
        if (user.isVaultEnabled()) WorkspaceVaultService.markEnabled(user.getId());
        user.setRecoveryVerifier(updated.getRecoveryVerifier()); user.setResetCodeHash(null); user.setResetCodeExpiresAt(null);
        try { users.save(updated); }
        catch (RuntimeException failure) {
            throw new IllegalStateException("The account update is active, but its safety copy failed. Keep the new password/key and retry.", failure);
        }
    }

    private boolean matchesPassword(UserAccount user, String password) {
        if (password == null || password.length() > 4096) return false;
        String stored = user.getPasswordHash();
        boolean modern = stored.startsWith(HASH_PREFIX);
        String computed = hash(password, user.getPasswordSalt(), modern ? 600_000 : 120_000);
        return MessageDigest.isEqual((modern ? stored.substring(HASH_PREFIX.length()) : stored).getBytes(StandardCharsets.UTF_8),
                computed.getBytes(StandardCharsets.UTF_8));
    }
    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 4096)
            throw new IllegalArgumentException("Use a password between 8 and 4096 characters.");
    }
    private void setPassword(UserAccount user, String password) {
        byte[] salt = new byte[16]; random.nextBytes(salt); user.setPasswordSalt(Base64.getEncoder().encodeToString(salt));
        user.setPasswordHash(HASH_PREFIX + hash(password, user.getPasswordSalt(), 600_000));
    }
    private String hash(String value, String salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(value.toCharArray(), Base64.getDecoder().decode(salt), iterations, 256);
        try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
        catch (Exception failure) { throw new IllegalStateException("Password protection error", failure); }
        finally { spec.clearPassword(); }
    }
}
