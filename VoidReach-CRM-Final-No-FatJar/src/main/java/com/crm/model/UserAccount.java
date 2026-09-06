package com.crm.model;

import java.time.Instant;

/** Record-shaped domain model: maps directly to a future users SQL table. */
public class UserAccount {
    public UserAccount() { }
    public UserAccount(UserAccount source) {
        id = source.id; fullName = source.fullName; email = source.email;
        passwordHash = source.passwordHash; passwordSalt = source.passwordSalt; createdAt = source.createdAt;
        resetCodeHash = source.resetCodeHash; resetCodeExpiresAt = source.resetCodeExpiresAt;
        avatarFileName = source.avatarFileName; preferredTheme = source.preferredTheme;
        preferredIcon = source.preferredIcon;
        vaultConfig = source.vaultConfig; recoveryVerifier = source.recoveryVerifier; vaultMigrated = source.vaultMigrated;
    }
    private String id;
    private String fullName;
    private String email;
    private String passwordHash;
    private String passwordSalt;
    private Instant createdAt;
    private String resetCodeHash;
    private Instant resetCodeExpiresAt;
    private String avatarFileName;
    private String preferredTheme = "DARK";
    private String preferredIcon = "V";
    public String getPreferredIcon() { return preferredIcon; }
    public void setPreferredIcon(String value) { preferredIcon = AppIcon.from(value).name(); }
    private String vaultConfig = "";
    private String recoveryVerifier = "";
    private boolean vaultMigrated;
    public String getVaultConfig() { return vaultConfig; }
    public void setVaultConfig(String value) { vaultConfig = value == null ? "" : value; }
    public boolean isVaultEnabled() { return !vaultConfig.isBlank(); }
    public String getRecoveryVerifier() { return recoveryVerifier; }
    public void setRecoveryVerifier(String value) { recoveryVerifier = value == null ? "" : value; }
    public boolean isVaultMigrated() { return vaultMigrated; }
    public void setVaultMigrated(boolean value) { vaultMigrated = value; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getResetCodeHash() { return resetCodeHash; }
    public void setResetCodeHash(String resetCodeHash) { this.resetCodeHash = resetCodeHash; }
    public Instant getResetCodeExpiresAt() { return resetCodeExpiresAt; }
    public void setResetCodeExpiresAt(Instant resetCodeExpiresAt) { this.resetCodeExpiresAt = resetCodeExpiresAt; }
    public String getAvatarFileName() { return avatarFileName; }
    public void setAvatarFileName(String avatarFileName) { this.avatarFileName = avatarFileName; }
    public String getPreferredTheme() { return preferredTheme; }
    public void setPreferredTheme(String preferredTheme) { this.preferredTheme = preferredTheme; }
}
