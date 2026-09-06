package com.crm.service;

import com.crm.model.UserAccount;
import com.crm.repository.AtomicPropertiesStore;
import com.crm.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/** Stores only the selected account email, never a password or password hash. */
public class SessionService {
    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_TYPE = "voidreach.session";
    private final UserRepository users;
    private final Path file;
    public SessionService(UserRepository users) { this(users, Path.of(System.getProperty("user.home"), ".voidreach-crm", "session.properties")); }
    SessionService(UserRepository users, Path file) { this.users = users; this.file = file; }

    public Optional<UserAccount> getRememberedUser() {
        if (!Files.exists(file)) return Optional.empty();
        Properties properties;
        try { properties = AtomicPropertiesStore.load(file, FILE_TYPE, SCHEMA_VERSION, p -> p.containsKey("email")); }
        catch (IOException e) { return Optional.empty(); }
        // An email is a convenience, not an authentication token or a decryption key.
        return Optional.ofNullable(properties.getProperty("email")).flatMap(users::findByEmail)
                .filter(user -> !user.isVaultEnabled());
    }
    public void remember(UserAccount user) {
        Properties properties = new Properties(); properties.setProperty(AtomicPropertiesStore.SCHEMA_VERSION_KEY, String.valueOf(SCHEMA_VERSION)); properties.setProperty(AtomicPropertiesStore.FILE_TYPE_KEY, FILE_TYPE); properties.setProperty("email", user.getEmail());
        try { AtomicPropertiesStore.store(file, properties, "VoidReach CRM remembered session"); }
        catch (IOException e) { throw new IllegalStateException("The session could not be saved", e); }
    }
    public void forget() {
        try { Files.deleteIfExists(file); }
        catch (IOException e) { throw new IllegalStateException("The session could not be closed", e); }
    }
}
