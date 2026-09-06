package com.crm.service;

import com.crm.model.CrmDataSnapshot;
import com.crm.model.UserAccount;
import com.crm.repository.CrmBackupService;
import com.crm.repository.CrmDataRepository;
import com.crm.repository.ExportOwner;
import com.crm.repository.ImportedWorkspace;
import com.crm.repository.LocalCrmDataRepository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Coordinates per-account CRM persistence and rotating backups. */
public final class CrmWorkspaceService implements AutoCloseable {
    private static final long SAVE_DEBOUNCE_MILLIS = 400;
    private final CrmDataRepository repository;
    private final CrmBackupService backupService;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voidreach-crm-io");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voidreach-crm-save-debounce");
        thread.setDaemon(true);
        return thread;
    });
    private final Object pendingSaveLock = new Object();
    private volatile UserAccount currentUser;
    private CrmDataSnapshot pendingSnapshot;
    private Consumer<SaveState> pendingListener;
    private ScheduledFuture<?> pendingSave;
    private long revision;
    private long savedRevision;
    private CompletableFuture<Void> closing;

    public CrmWorkspaceService() {
        this(new LocalCrmDataRepository(), new CrmBackupService());
    }

    CrmWorkspaceService(CrmDataRepository repository, CrmBackupService backupService) {
        this.repository = Objects.requireNonNull(repository);
        this.backupService = Objects.requireNonNull(backupService);
    }

    public CrmDataSnapshot open(UserAccount user) {
        close();
        currentUser = Objects.requireNonNull(user);
        if (user.isVaultEnabled()) WorkspaceVaultService.markEnabled(user.getId());
        try {
            return repository.loadForUser(user.getId());
        } finally {
            backupService.start(user.getId());
        }
    }

    public void save(CrmDataSnapshot snapshot) {
        if (currentUser == null) return;
        repository.saveForUser(currentUser.getId(), Objects.requireNonNull(snapshot));
    }

    /** Exports the open account's saved workspace to [target], stamped with that account's identity. */
    public void exportCurrentUser(Path target) {
        UserAccount user = currentUser;
        if (user == null) throw new IllegalStateException("No account is open.");
        repository.exportForUser(user.getId(), Objects.requireNonNull(target),
                new ExportOwner(user.getEmail(), user.getFullName()));
    }

    /** Reads a portable file without committing it, so the caller can vet the owning account first. */
    public ImportedWorkspace readImport(Path source) {
        return repository.readImport(Objects.requireNonNull(source));
    }

    public CompletableFuture<CrmDataSnapshot> openAsync(UserAccount user) {
        return CompletableFuture.supplyAsync(() -> open(user), ioExecutor);
    }

    /** Queues only the newest detached snapshot and serializes disk writes on one I/O thread. */
    public void requestSave(CrmDataSnapshot snapshot, Consumer<SaveState> listener) {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(listener);
        synchronized (pendingSaveLock) {
            if (closing != null) throw new IllegalStateException("The workspace is closing.");
            pendingSnapshot = snapshot;
            pendingListener = listener;
            revision++;
            if (pendingSave != null) pendingSave.cancel(false);
            pendingSave = debounceExecutor.schedule(this::enqueuePendingSave,
                    SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
        listener.accept(SaveState.SAVING);
    }

    private void enqueuePendingSave() {
        synchronized (pendingSaveLock) {
            pendingSave = null;
            // Submission is inside the same lock as flush: a close/export cannot overtake it.
            if (pendingSnapshot != null) ioExecutor.execute(() -> {
                try { persistLatest(); }
                catch (RuntimeException ignored) { /* Failure was reported; the dirty snapshot is retained. */ }
            });
        }
    }

    private void persistLatest() {
        CrmDataSnapshot snapshot;
        Consumer<SaveState> listener;
        long writingRevision;
        synchronized (pendingSaveLock) {
            if (pendingSnapshot == null || savedRevision == revision) return;
            snapshot = pendingSnapshot;
            listener = pendingListener;
            writingRevision = revision;
        }
        try {
            save(snapshot);
            synchronized (pendingSaveLock) {
                savedRevision = writingRevision;
                if (writingRevision == revision) listener.accept(SaveState.SAVED);
            }
        } catch (RuntimeException exception) {
            listener.accept(new SaveState(exception));
            throw exception;
        }
    }

    /** A serial I/O barrier which includes writes still waiting for the debounce timer. */
    public CompletableFuture<Void> flushAsync() {
        synchronized (pendingSaveLock) {
            if (pendingSave != null) pendingSave.cancel(false);
            pendingSave = null;
            return CompletableFuture.runAsync(this::persistLatest, ioExecutor);
        }
    }

    public CompletableFuture<Void> exportAsync(Path target) {
        return flushAsync().thenRunAsync(() -> exportCurrentUser(target), ioExecutor);
    }
    public CompletableFuture<java.util.List<Path>> listBackupsAsync() {
        return CompletableFuture.supplyAsync(() -> backupService.listBackups(currentUser.getId()), ioExecutor);
    }
    public CompletableFuture<Path> checkpointAsync() {
        return flushAsync().thenApplyAsync(ignored -> backupService.checkpoint(currentUser.getId()), ioExecutor);
    }
    public CompletableFuture<CrmDataSnapshot> readBackupAsync(Path path) {
        return CompletableFuture.supplyAsync(() -> backupService.readBackup(currentUser.getId(), path), ioExecutor);
    }
    public String backupFailure() { return backupService.lastFailure(); }

    /** Serializes account protection with saves and waits for a running backup before migration. */
    public CompletableFuture<Void> maintenanceAsync(Runnable operation) {
        return flushAsync().thenRunAsync(() -> {
            backupService.close();
            try { operation.run(); }
            finally { if (currentUser != null) backupService.start(currentUser.getId()); }
        }, ioExecutor);
    }

    /** Failed final saves leave the workspace, its retry path, and backups alive. */
    public CompletableFuture<Void> closeAsync() {
        synchronized (pendingSaveLock) {
            if (closing != null) return closing;
            CompletableFuture<Void> result = new CompletableFuture<>();
            closing = result;
            flushAsync().thenRunAsync(() -> {
                String userId = currentUser == null ? null : currentUser.getId();
                close();
                if (userId != null) WorkspaceVaultService.lock(userId);
                debounceExecutor.shutdownNow();
                ioExecutor.shutdown();
            }, ioExecutor).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    synchronized (pendingSaveLock) { closing = null; }
                    result.completeExceptionally(failure);
                } else result.complete(null);
            });
            return result;
        }
    }

    @Override
    public void close() {
        backupService.close();
        currentUser = null;
    }

    public record SaveState(Throwable failure) {
        public static final SaveState SAVING = new SaveState(null);
        public static final SaveState SAVED = new SaveState(null);

        public boolean failed() { return failure != null; }
    }
}
