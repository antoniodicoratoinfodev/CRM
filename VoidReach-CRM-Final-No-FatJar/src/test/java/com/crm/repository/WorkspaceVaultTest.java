package com.crm.repository;

import com.crm.model.*;
import com.crm.service.WorkspaceVaultService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceVaultTest {
    @TempDir Path directory;
    @Test void completeOptInFlowSurvivesAccountReloadAndPasswordRecovery() throws Exception {
        String previousHome = System.getProperty("user.home"), id = null;
        System.setProperty("user.home", directory.toString());
        try {
            var users = new LocalUserRepository(); var auth = new com.crm.service.AuthService(users);
            UserAccount user = auth.register("Vault Tester", "vault@example.test", "original-password"); id = user.getId();
            var repository = new LocalCrmDataRepository(); repository.saveForUser(id, snapshot());
            try (var backups = new CrmBackupService()) { backups.checkpoint(id); }
            String recovery;
            try (var setup = auth.prepareProtection(user, "original-password")) {
                recovery = setup.recoveryKey(); auth.enableProtection(user, setup);
            }
            assertTrue(user.isVaultMigrated()); assertTrue(WorkspaceVaultService.encrypted(properties(repository.dataFile(id))));
            WorkspaceVaultService.lock(id);
            UserAccount reloaded = auth.login(user.getEmail(), "original-password"); assertTrue(reloaded.isVaultMigrated());
            assertEquals(1, repository.loadForUser(id).contacts().size());
            auth.resetPassword(user.getEmail(), recovery, "recovered-password");
            reloaded = auth.login(user.getEmail(), "recovered-password"); assertTrue(reloaded.getRecoveryVerifier().isBlank());
            assertEquals("Private client", repository.loadForUser(id).contacts().getFirst().nameProperty().get());
        } finally { if (id != null) WorkspaceVaultService.lock(id); System.setProperty("user.home", previousHome); }
    }
    private UserAccount account() { UserAccount user = new UserAccount(); user.setId(UUID.randomUUID().toString()); return user; }
    private CrmDataSnapshot snapshot() { return CrmDataSnapshot.detachedCopyOf(List.of(new Contact("c1", "Private client", "", "", "", "", "", "", "")), Map.of(), LocalDate.now(), "Week", 1); }
    private Properties properties(Path path) throws Exception { Properties p = new Properties(); try (var input = Files.newInputStream(path)) { p.load(input); } return p; }

    @Test void migrationPreservesPrimaryPreviousRevisionAndManagedBackups() throws Exception {
        UserAccount user = account(); var repository = new LocalCrmDataRepository(directory.resolve("data"));
        var backups = new CrmBackupService(repository, directory.resolve("backup"), Duration.ofMinutes(2));
        repository.saveForUser(user.getId(), snapshot()); repository.saveForUser(user.getId(), snapshot());
        backups.createBackupNow(user.getId()); Path checkpoint = backups.checkpoint(user.getId());
        try (var setup = WorkspaceVaultService.prepare(user, "long-test-password")) {
            WorkspaceVaultService.activate(user, setup); repository.migrateProtection(user.getId()); backups.migrateProtection(user.getId());
            Path file = repository.dataFile(user.getId());
            for (Path path : List.of(file, AtomicPropertiesStore.backupPath(file), checkpoint, backups.listBackups(user.getId()).getLast())) {
                Properties p = properties(path); assertTrue(WorkspaceVaultService.encrypted(p)); assertFalse(p.containsKey("contact.0.name"));
            }
            assertEquals("Private client", repository.loadForUser(user.getId()).contacts().getFirst().nameProperty().get());
            WorkspaceVaultService.lock(user.getId());
            assertThrows(IllegalStateException.class, () -> repository.loadForUser(user.getId()));
            assertThrows(IllegalStateException.class, () -> repository.saveForUser(user.getId(), snapshot()));
            WorkspaceVaultService.unlock(user, "long-test-password");
            assertEquals(1, backups.readBackup(user.getId(), checkpoint).contacts().size());
            Path portable = directory.resolve("export.properties"); repository.exportForUser(user.getId(), portable, null);
            assertFalse(WorkspaceVaultService.encrypted(properties(portable)));
        } finally { WorkspaceVaultService.lock(user.getId()); backups.close(); }
    }

    @Test void authenticatedEncryptionRejectsWrongOwnerPasswordAndModifiedPayload() {
        UserAccount user = account();
        try (var setup = WorkspaceVaultService.prepare(user, "original-password")) {
            WorkspaceVaultService.activate(user, setup);
            Properties plaintext = new Properties(); plaintext.setProperty("secret", "private");
            Properties encrypted = WorkspaceVaultService.seal(plaintext, user.getId());
            assertEquals(plaintext, WorkspaceVaultService.unseal(encrypted, user.getId()));
            assertThrows(IllegalArgumentException.class, () -> WorkspaceVaultService.unseal(encrypted, "another-profile"));
            assertThrows(IllegalArgumentException.class, () -> WorkspaceVaultService.unlock(user, "wrong-password"));
            byte[] bytes = Base64.getDecoder().decode(encrypted.getProperty("vault.payload")); bytes[0] ^= 1;
            encrypted.setProperty("vault.payload", Base64.getEncoder().encodeToString(bytes));
            assertThrows(IllegalArgumentException.class, () -> WorkspaceVaultService.unseal(encrypted, user.getId()));
            encrypted.remove("vault.version");
            assertThrows(IllegalArgumentException.class, () -> WorkspaceVaultService.unseal(encrypted, user.getId()));
        } finally { WorkspaceVaultService.lock(user.getId()); }
    }

    @Test void passwordChangeAndSingleUseRecoveryKeepTheSameDataKey() {
        UserAccount user = account();
        try (var setup = WorkspaceVaultService.prepare(user, "original-password")) {
            WorkspaceVaultService.activate(user, setup);
            Properties plaintext = new Properties(); plaintext.setProperty("secret", "unchanged");
            Properties encrypted = WorkspaceVaultService.seal(plaintext, user.getId());
            WorkspaceVaultService.changePassword(user, "changed-password"); WorkspaceVaultService.lock(user.getId());
            assertThrows(IllegalArgumentException.class, () -> WorkspaceVaultService.unlock(user, "original-password"));
            WorkspaceVaultService.unlock(user, "changed-password"); assertEquals(plaintext, WorkspaceVaultService.unseal(encrypted, user.getId()));
            WorkspaceVaultService.lock(user.getId()); WorkspaceVaultService.recover(user, setup.recoveryKey(), "recovered-password");
            assertEquals(plaintext, WorkspaceVaultService.unseal(encrypted, user.getId()));
            assertThrows(IllegalArgumentException.class, () -> WorkspaceVaultService.recover(user, setup.recoveryKey(), "another-password"));
            WorkspaceVaultService.lock(user.getId()); WorkspaceVaultService.unlock(user, "recovered-password");
        } finally { WorkspaceVaultService.lock(user.getId()); }
    }
}
