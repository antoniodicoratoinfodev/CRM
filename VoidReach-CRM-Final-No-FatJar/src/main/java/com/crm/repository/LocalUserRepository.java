package com.crm.repository;

import com.crm.model.UserAccount;
import com.crm.repository.CorruptRecordQuarantine.RejectedRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

/** Local stand-in for a users table. It deliberately persists columns, not UI state. */
public class LocalUserRepository implements UserRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_TYPE = "voidreach.users";
    private final Path file;

    public LocalUserRepository() { this(applicationDataDirectory()); }
    LocalUserRepository(Path dataDirectory) { this.file = dataDirectory.resolve("users.properties"); }

    public static Path applicationDataDirectory() { return Path.of(System.getProperty("user.home"), ".voidreach-crm"); }

    @Override public synchronized Optional<UserAccount> findByEmail(String email) {
        if (email == null) return Optional.empty();
        Properties properties = load();
        ScanResult scan = scanUsers(properties, false);
        CorruptRecordQuarantine.writeBestEffort(file, scan.rejected());
        String normalized = email.trim().toLowerCase();
        return scan.validUsers().stream()
                .filter(user -> normalized.equals(user.getEmail().trim().toLowerCase()))
                .findFirst();
    }

    @Override public synchronized void save(UserAccount user) {
        Properties properties = load();
        ScanResult scan = scanUsers(properties, true);
        CorruptRecordQuarantine.writeBestEffort(file, scan.rejected());

        String prefix = "user." + user.getId() + ".";
        properties.setProperty(AtomicPropertiesStore.SCHEMA_VERSION_KEY, String.valueOf(SCHEMA_VERSION));
        properties.setProperty(AtomicPropertiesStore.FILE_TYPE_KEY, FILE_TYPE);
        put(properties, prefix + "fullName", user.getFullName());
        put(properties, prefix + "email", user.getEmail());
        put(properties, prefix + "passwordHash", user.getPasswordHash());
        put(properties, prefix + "passwordSalt", user.getPasswordSalt());
        put(properties, prefix + "createdAt", user.getCreatedAt().toString());
        put(properties, prefix + "resetCodeHash", user.getResetCodeHash());
        put(properties, prefix + "resetCodeExpiresAt", user.getResetCodeExpiresAt() == null ? null : user.getResetCodeExpiresAt().toString());
        put(properties, prefix + "avatarFileName", user.getAvatarFileName());
        put(properties, prefix + "preferredTheme", user.getPreferredTheme());
        put(properties, prefix + "preferredIcon", user.getPreferredIcon());
        put(properties, prefix + "vaultConfig", user.getVaultConfig());
        put(properties, prefix + "recoveryVerifier", user.getRecoveryVerifier());
        put(properties, prefix + "vaultMigrated", String.valueOf(user.isVaultMigrated()));
        try {
            AtomicPropertiesStore.store(file, properties, "VoidReach CRM local users");
        } catch (IOException e) {
            throw new IllegalStateException("Local accounts could not be saved", e);
        }
    }

    private ScanResult scanUsers(Properties properties, boolean removeRejected) {
        List<UserAccount> validUsers = new ArrayList<>();
        List<RejectedRecord> rejected = new ArrayList<>();
        for (String id : userIds(properties)) {
            String prefix = "user." + id + ".";
            try {
                validUsers.add(read(properties, id));
            } catch (RuntimeException failure) {
                RejectedRecord record = CorruptRecordQuarantine.capture(properties, "user", id, prefix, failure);
                rejected.add(record);
                if (removeRejected) CorruptRecordQuarantine.removeCaptured(properties, record);
            }
        }
        return new ScanResult(validUsers, rejected);
    }

    private SortedSet<String> userIds(Properties properties) {
        SortedSet<String> ids = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("user.")) continue;
            int fieldSeparator = key.lastIndexOf('.');
            if (fieldSeparator <= "user.".length()) continue;
            ids.add(key.substring("user.".length(), fieldSeparator));
        }
        return ids;
    }

    private UserAccount read(Properties properties, String id) {
        String prefix = "user." + id + ".";
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setFullName(requiredNonBlank(properties, prefix + "fullName"));
        user.setEmail(requiredNonBlank(properties, prefix + "email"));
        user.setPasswordHash(requiredNonBlank(properties, prefix + "passwordHash"));
        user.setPasswordSalt(requiredNonBlank(properties, prefix + "passwordSalt"));
        user.setCreatedAt(Instant.parse(requiredNonBlank(properties, prefix + "createdAt")));
        user.setResetCodeHash(optionalValue(properties, prefix + "resetCodeHash"));
        String expires = optionalValue(properties, prefix + "resetCodeExpiresAt");
        if (expires != null && !expires.isBlank()) user.setResetCodeExpiresAt(Instant.parse(expires));
        user.setAvatarFileName(optionalValue(properties, prefix + "avatarFileName"));
        String preferredTheme = optionalValue(properties, prefix + "preferredTheme");
        if (preferredTheme != null && !preferredTheme.isBlank()) user.setPreferredTheme(preferredTheme);
        user.setPreferredIcon(optionalValue(properties, prefix + "preferredIcon"));
        user.setVaultConfig(optionalValue(properties, prefix + "vaultConfig"));
        user.setRecoveryVerifier(optionalValue(properties, prefix + "recoveryVerifier"));
        user.setVaultMigrated(Boolean.parseBoolean(optionalValue(properties, prefix + "vaultMigrated")));
        return user;
    }

    private Properties load() {
        try {
            return AtomicPropertiesStore.load(file, FILE_TYPE, SCHEMA_VERSION,
                    properties -> properties.stringPropertyNames().stream()
                            .anyMatch(key -> key.startsWith("user.") && key.endsWith(".email")));
        } catch (IOException e) {
            throw new IllegalStateException("Local accounts could not be read", e);
        }
    }

    private String requiredNonBlank(Properties properties, String key) {
        String value = optionalValue(properties, key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Invalid required property: " + key);
        return value;
    }

    private String optionalValue(Properties properties, String key) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null) return null;
        try {
            return new String(Base64.getDecoder().decode(rawValue), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64: " + key, e);
        }
    }

    private void put(Properties properties, String key, String value) {
        properties.setProperty(key, Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    }

    private record ScanResult(List<UserAccount> validUsers, List<RejectedRecord> rejected) { }
}
