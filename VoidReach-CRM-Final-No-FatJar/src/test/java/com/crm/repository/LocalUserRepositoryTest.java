package com.crm.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.model.UserAccount;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalUserRepositoryTest {
    @TempDir Path directory;

    @Test void iconChoiceIsPerAccountAndLegacyRecordsStayReadable() throws Exception {
        LocalUserRepository repository = new LocalUserRepository(directory);
        UserAccount book = account("book", "book@example.test", "Book User"); book.setPreferredIcon("BOOK");
        UserAccount modern = account("modern", "modern@example.test", "Modern User");
        repository.save(book); repository.save(modern);
        assertEquals("BOOK", repository.findByEmail(book.getEmail()).orElseThrow().getPreferredIcon());
        assertEquals("V", repository.findByEmail(modern.getEmail()).orElseThrow().getPreferredIcon());
        assertEquals("BOOK", new UserAccount(book).getPreferredIcon());
        Path file = directory.resolve("users.properties"); Properties stored = load(file);
        stored.remove("user.book.preferredIcon"); storeDirectly(file, stored);
        assertEquals("V", repository.findByEmail(book.getEmail()).orElseThrow().getPreferredIcon());
        book.setPreferredIcon("unknown"); assertEquals("V", book.getPreferredIcon());
    }

    @Test void corruptAccountDoesNotBlockValidAccountsAndIsQuarantined() throws Exception {
        LocalUserRepository repository = new LocalUserRepository(directory);
        UserAccount corrupt = account("corrupt-id", "corrupt@example.com", "Corrupt User");
        UserAccount valid = account("valid-id", "valid@example.com", "Valid User");
        repository.save(corrupt);
        repository.save(valid);

        Path usersFile = directory.resolve("users.properties");
        Properties stored = load(usersFile);
        stored.setProperty("user.corrupt-id.createdAt", encode("not-an-instant"));
        storeDirectly(usersFile, stored);

        UserAccount loaded = repository.findByEmail("valid@example.com").orElseThrow();
        assertEquals("valid-id", loaded.getId());
        assertTrue(repository.findByEmail("corrupt@example.com").isEmpty());
        assertTrue(Files.isRegularFile(directory.resolve("users.properties.corrupt.properties")));

        repository.save(valid);
        Properties sanitized = load(usersFile);
        assertFalse(sanitized.stringPropertyNames().stream().anyMatch(key -> key.startsWith("user.corrupt-id.")));
        assertTrue(sanitized.stringPropertyNames().stream().anyMatch(key -> key.startsWith("user.valid-id.")));
    }

    @Test void preferredThemeIsStoredPerAccountAndDefaultsForLegacyAccounts() throws Exception {
        LocalUserRepository repository = new LocalUserRepository(directory);
        UserAccount darkUser = account("dark-id", "dark@example.com", "Dark User");
        UserAccount lightUser = account("light-id", "light@example.com", "Light User");
        lightUser.setPreferredTheme("LIGHT");
        repository.save(darkUser);
        repository.save(lightUser);

        assertEquals("DARK", repository.findByEmail("dark@example.com").orElseThrow().getPreferredTheme());
        assertEquals("LIGHT", repository.findByEmail("light@example.com").orElseThrow().getPreferredTheme());

        Path usersFile = directory.resolve("users.properties");
        Properties stored = load(usersFile);
        stored.remove("user.light-id.preferredTheme");
        storeDirectly(usersFile, stored);

        assertEquals("DARK", repository.findByEmail("light@example.com").orElseThrow().getPreferredTheme());
    }

    private static UserAccount account(String id, String email, String name) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setEmail(email);
        account.setFullName(name);
        account.setPasswordHash("hash");
        account.setPasswordSalt("salt");
        account.setCreatedAt(Instant.parse("2026-07-11T12:00:00Z"));
        return account;
    }

    private static Properties load(Path file) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        return properties;
    }

    private static void storeDirectly(Path file, Properties properties) throws Exception {
        try (OutputStream output = Files.newOutputStream(file)) { properties.store(output, "test corruption"); }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
