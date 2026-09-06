package com.crm.service;

import com.crm.model.UserAccount;
import com.crm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @TempDir Path directory;
    @Test void failedCredentialSafetyCopyPreventsStartingWorkspaceEncryption() {
        MemoryUsers users = new MemoryUsers(); AuthService auth = new AuthService(users);
        UserAccount user = auth.register("Test User", "test@example.test", "original-password");
        try (var setup = auth.prepareProtection(user, "original-password")) {
            users.failAt = users.saves + 2;
            assertThrows(IllegalStateException.class, () -> auth.enableProtection(user, setup));
            assertFalse(user.isVaultMigrated()); assertTrue(user.isVaultEnabled());
            assertThrows(IllegalStateException.class, () -> WorkspaceVaultService.seal(new Properties(), user.getId()));
        } finally { WorkspaceVaultService.lock(user.getId()); }
    }
    @Test void recoveryRequiresAnAuthenticatedSetupAndConsumesTheKey() {
        MemoryUsers users = new MemoryUsers(); AuthService auth = new AuthService(users);
        UserAccount user = auth.register("Test User", "test@example.test", "original-password");
        assertThrows(IllegalArgumentException.class, () -> auth.resetPassword(user.getEmail(), "123456", "new-password"));
        assertThrows(IllegalArgumentException.class, () -> auth.createRecoveryKey(user, "wrong-password"));
        String key = auth.createRecoveryKey(user, "original-password"); assertEquals(43, key.length());
        auth.resetPassword(user.getEmail(), key, "new-password");
        assertThrows(IllegalArgumentException.class, () -> auth.login(user.getEmail(), "original-password"));
        assertEquals(user.getId(), auth.login(user.getEmail(), "new-password").getId());
        assertThrows(IllegalArgumentException.class, () -> auth.resetPassword(user.getEmail(), key, "third-password"));
    }
    @Test void encryptedProfileCannotUseRememberedEmailAsAuthentication() {
        MemoryUsers users = new MemoryUsers(); AuthService auth = new AuthService(users);
        UserAccount user = auth.register("Test User", "test@example.test", "original-password");
        SessionService session = new SessionService(users, directory.resolve("session.properties")); session.remember(user);
        assertTrue(session.getRememberedUser().isPresent());
        try (var setup = WorkspaceVaultService.prepare(user, "original-password")) {
            WorkspaceVaultService.activate(user, setup); user.setVaultMigrated(true); users.save(user);
            assertTrue(session.getRememberedUser().isEmpty());
            auth.changePassword(user, "original-password", "updated-password");
            WorkspaceVaultService.lock(user.getId()); assertEquals(user.getId(), auth.login(user.getEmail(), "updated-password").getId());
        } finally { WorkspaceVaultService.lock(user.getId()); }
    }
    private static final class MemoryUsers implements UserRepository {
        final Map<String, UserAccount> records = new HashMap<>();
        int saves, failAt = -1;
        public Optional<UserAccount> findByEmail(String email) { return Optional.ofNullable(records.get(email)).map(UserAccount::new); }
        public void save(UserAccount user) { if (++saves == failAt) throw new IllegalStateException("Simulated account backup failure"); records.put(user.getEmail(), new UserAccount(user)); }
    }
}
