package com.crm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.UserAccount;
import com.crm.repository.CrmBackupService;
import com.crm.repository.CrmDataRepository;
import com.crm.repository.ExportOwner;
import com.crm.repository.ImportedWorkspace;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CrmWorkspaceServiceTest {
    @Test void immediateCloseFlushesDebouncedChanges() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CrmWorkspaceService service = new CrmWorkspaceService(repository, new CrmBackupService()); service.open(account());
        service.requestSave(snapshot("last keystroke"), state -> { });
        service.closeAsync().get(3, TimeUnit.SECONDS);
        assertEquals("last keystroke", repository.lastSnapshot.get().contacts().getFirst().nameProperty().get());
        assertEquals(1, repository.saveCount.get());
    }

    @Test void failedFinalSaveKeepsDirtyDataAndAllowsCloseRetry() throws Exception {
        RecordingRepository repository = new RecordingRepository(); repository.fail = true;
        CrmWorkspaceService service = new CrmWorkspaceService(repository, new CrmBackupService()); service.open(account());
        service.requestSave(snapshot("unsaved"), state -> { });
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
                () -> service.closeAsync().get(3, TimeUnit.SECONDS));
        repository.fail = false;
        service.closeAsync().get(3, TimeUnit.SECONDS);
        assertEquals("unsaved", repository.lastSnapshot.get().contacts().getFirst().nameProperty().get());
    }

    @Test void exportFlushesTheCurrentRevision() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CrmWorkspaceService service = new CrmWorkspaceService(repository, new CrmBackupService()); service.open(account());
        try {
            service.requestSave(snapshot("export latest"), state -> { }); service.exportAsync(Path.of("unused.properties")).get(3, TimeUnit.SECONDS);
            assertEquals("export latest", repository.exported.contacts().getFirst().nameProperty().get());
        } finally { service.closeAsync().get(3, TimeUnit.SECONDS); }
    }
    @Test void debouncePersistsOnlyTheNewestDetachedSnapshot() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        CrmWorkspaceService service = new CrmWorkspaceService(repository, new CrmBackupService());
        try {
            service.open(account());
            CountDownLatch saved = new CountDownLatch(1);
            service.requestSave(snapshot("first"), state -> {
                if (state == CrmWorkspaceService.SaveState.SAVED) saved.countDown();
            });
            service.requestSave(snapshot("latest"), state -> {
                if (state == CrmWorkspaceService.SaveState.SAVED) saved.countDown();
            });

            assertTrue(saved.await(2, TimeUnit.SECONDS));
            assertEquals(1, repository.saveCount.get());
            assertEquals("latest", repository.lastSnapshot.get().contacts().getFirst().nameProperty().get());
        } finally {
            service.closeAsync().get(2, TimeUnit.SECONDS);
        }
    }

    private static UserAccount account() {
        UserAccount user = new UserAccount();
        user.setId("account-1");
        return user;
    }

    private static CrmDataSnapshot snapshot(String name) {
        return CrmDataSnapshot.detachedCopyOf(List.of(new Contact("id-1", name, "", "", "", "", "", "", "")),
                Map.of(), LocalDate.of(2026, 7, 12), "Day", 1.0);
    }

    private static final class RecordingRepository implements CrmDataRepository {
        private final AtomicInteger saveCount = new AtomicInteger();
        private final AtomicReference<CrmDataSnapshot> lastSnapshot = new AtomicReference<>();
        private volatile boolean fail;
        private CrmDataSnapshot exported;

        @Override public CrmDataSnapshot loadForUser(String userId) { return snapshot("loaded"); }

        @Override public void saveForUser(String userId, CrmDataSnapshot data) {
            if (fail) throw new IllegalStateException("Simulated disk full");
            saveCount.incrementAndGet();
            lastSnapshot.set(data);
        }

        @Override public void exportForUser(String userId, Path target, ExportOwner owner) {
            exported = lastSnapshot.get();
        }

        @Override public ImportedWorkspace readImport(Path source) {
            throw new UnsupportedOperationException("not exercised by this test");
        }
    }
}
