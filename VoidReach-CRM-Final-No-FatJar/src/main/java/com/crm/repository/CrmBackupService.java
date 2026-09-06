package com.crm.repository;

import com.crm.model.CrmDataSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Creates three rotating CRM snapshots, encrypted when local protection is enabled. */
public final class CrmBackupService implements AutoCloseable {
    static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(2);
    static final int MAX_BACKUPS_PER_USER = 3;

    private final LocalCrmDataRepository repository;
    private final Path backupRoot;
    private final Duration interval;
    private ScheduledExecutorService executor;
    private volatile String lastFailure = "";
    public String lastFailure() { return lastFailure; }

    public synchronized List<Path> listBackups(String userId) {
        Path directory = backupRoot.resolve(safeUserDirectory(userId));
        if (!Files.isDirectory(directory)) return List.of();
        try (var entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile).filter(path -> isBackupFile(path) || isCheckpoint(path))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed()).toList();
        } catch (IOException failure) { throw new IllegalStateException("Backups could not be listed.", failure); }
    }

    public synchronized Path checkpoint(String userId) {
        Path directory = backupRoot.resolve(safeUserDirectory(userId));
        Path target = directory.resolve("checkpoint-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".properties");
        repository.writeSnapshot(target, repository.loadForUser(userId), "VoidReach manual checkpoint", userId);
        return target;
    }

    public CrmDataSnapshot readBackup(String userId, Path path) {
        Path directory = backupRoot.resolve(safeUserDirectory(userId)).toAbsolutePath().normalize();
        Path resolved = path.toAbsolutePath().normalize();
        if (!resolved.getParent().equals(directory) || !listBackups(userId).contains(path))
            throw new IllegalArgumentException("Choose a backup belonging to the active account.");
        ImportedWorkspace imported = repository.readImport(resolved);
        if (!imported.warnings().isEmpty()) throw new IllegalArgumentException("This backup contains damaged records. Choose another snapshot, or explicitly import the readable records.");
        return imported.snapshot();
    }

    public CrmBackupService() {
        this(new LocalCrmDataRepository(),
                LocalUserRepository.applicationDataDirectory().resolve("backup"), DEFAULT_INTERVAL);
    }

    CrmBackupService(LocalCrmDataRepository repository, Path backupRoot, Duration interval) {
        this.repository = Objects.requireNonNull(repository);
        this.backupRoot = Objects.requireNonNull(backupRoot);
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("The backup interval must be positive");
        }
        this.interval = interval;
    }

    public synchronized void start(String userId) {
        close();
        if (userId == null || userId.isBlank()) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "voidreach-crm-backup");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        long delayMillis = interval.toMillis();
        executor.scheduleWithFixedDelay(
                () -> createBackupSafely(userId), delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    synchronized void createBackupNow(String userId) {
        Path source = repository.dataFile(userId);
        if (!Files.isRegularFile(source)) return;

        CrmDataSnapshot snapshot = repository.loadForUser(userId);
        Path accountDirectory = backupRoot.resolve(safeUserDirectory(userId));
        try {
            Files.createDirectories(accountDirectory);
            long sequence = nextSequence(accountDirectory);
            Path target = accountDirectory.resolve(String.format("crm-data-%013d.properties", sequence));
            repository.writeSnapshot(target, snapshot, "VoidReach CRM automatic backup for one account", userId);
            pruneOldBackups(accountDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("The automatic backup could not be created", e);
        }
    }

    private void createBackupSafely(String userId) {
        try {
            createBackupNow(userId);
            lastFailure = "";
        } catch (RuntimeException failure) {
            lastFailure = "Automatic backup failed. Check disk space and folder access.";
        }
    }

    private long nextSequence(Path accountDirectory) throws IOException {
        long highest = 0;
        try (var files = Files.list(accountDirectory)) {
            for (Path file : files.filter(this::isBackupFile).toList()) {
                String name = file.getFileName().toString();
                try {
                    highest = Math.max(highest, Long.parseLong(name.substring("crm-data-".length(), name.length() - ".properties".length())));
                } catch (NumberFormatException ignored) {
                    // Unknown files in the user's backup folder are left untouched.
                }
            }
        }
        return Math.max(System.currentTimeMillis(), highest + 1);
    }

    private void pruneOldBackups(Path accountDirectory) throws IOException {
        List<Path> backups;
        try (var files = Files.list(accountDirectory)) {
            backups = files.filter(this::isBackupFile)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (int i = MAX_BACKUPS_PER_USER; i < backups.size(); i++) Files.deleteIfExists(backups.get(i));
    }

    private boolean isBackupFile(Path path) {
        String name = path.getFileName().toString();
        return Files.isRegularFile(path) && name.matches("crm-data-[0-9]+\\.properties");
    }

    private boolean isCheckpoint(Path path) { return path.getFileName().toString().matches("checkpoint-[0-9]+-[a-f0-9]{8}\\.properties"); }

    public synchronized void migrateProtection(String userId) {
        Path directory = backupRoot.resolve(safeUserDirectory(userId));
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).filter(path -> {
                String name = path.getFileName().toString();
                return name.matches("(crm-data-[0-9]+|checkpoint-[0-9]+-[a-f0-9]{8})\\.properties(\\.bak|\\.corrupt\\.properties(\\.bak)?|(?:\\.[A-Za-z0-9_-]+)+\\.tmp)?");
            }).toList()) com.crm.service.WorkspaceVaultService.encryptExisting(file, userId);
        } catch (IOException failure) { throw new IllegalStateException("Existing backups could not be encrypted.", failure); }
    }

    private String safeUserDirectory(String userId) {
        String safe = userId.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? "account" : safe;
    }

    @Override public synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
