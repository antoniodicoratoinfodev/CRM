package com.crm.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Preserves rejected records without allowing them to block the rest of a local data file. */
final class CorruptRecordQuarantine {
    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_TYPE = "voidreach.corrupt-records";

    private CorruptRecordQuarantine() { }

    static RejectedRecord capture(Properties source, String kind, String identifier, String prefix, RuntimeException failure) {
        Map<String, String> rawValues = new LinkedHashMap<>();
        for (String key : source.stringPropertyNames()) {
            if (key.startsWith(prefix)) rawValues.put(key, source.getProperty(key));
        }
        return new RejectedRecord(kind, identifier, safeReason(failure), rawValues);
    }

    static RejectedRecord singleProperty(String kind, String key, String rawValue, RuntimeException failure) {
        Map<String, String> rawValues = new LinkedHashMap<>();
        if (rawValue != null) rawValues.put(key, rawValue);
        return new RejectedRecord(kind, key, safeReason(failure), rawValues);
    }

    static void writeBestEffort(Path sourceFile, List<RejectedRecord> rejected) {
        writeBestEffort(sourceFile, rejected, null);
    }

    static void writeBestEffort(Path sourceFile, List<RejectedRecord> rejected, String userId) {
        if (rejected.isEmpty()) return;
        Properties quarantine = new Properties();
        quarantine.setProperty(AtomicPropertiesStore.SCHEMA_VERSION_KEY, String.valueOf(SCHEMA_VERSION));
        quarantine.setProperty(AtomicPropertiesStore.FILE_TYPE_KEY, FILE_TYPE);
        quarantine.setProperty("source.file", sourceFile.getFileName().toString());
        quarantine.setProperty("captured.at", Instant.now().toString());
        quarantine.setProperty("records.count", String.valueOf(rejected.size()));
        for (int i = 0; i < rejected.size(); i++) {
            RejectedRecord record = rejected.get(i);
            String prefix = "record." + i + ".";
            quarantine.setProperty(prefix + "kind", record.kind());
            quarantine.setProperty(prefix + "identifier", record.identifier());
            quarantine.setProperty(prefix + "reason", record.reason());
            quarantine.setProperty(prefix + "values.count", String.valueOf(record.rawValues().size()));
            int valueIndex = 0;
            for (Map.Entry<String, String> value : record.rawValues().entrySet()) {
                quarantine.setProperty(prefix + "value." + valueIndex + ".key", value.getKey());
                quarantine.setProperty(prefix + "value." + valueIndex + ".raw", value.getValue());
                valueIndex++;
            }
        }
        Path quarantineFile = sourceFile.resolveSibling(sourceFile.getFileName() + ".corrupt.properties");
        try {
            AtomicPropertiesStore.store(quarantineFile, userId == null ? quarantine :
                    com.crm.service.WorkspaceVaultService.seal(quarantine, userId), "VoidReach CRM quarantined local records");
        } catch (IOException ignored) {
            // Loading valid records is more important than failing because diagnostics cannot be persisted.
        }
    }

    static void removeCaptured(Properties source, RejectedRecord rejected) {
        new ArrayList<>(rejected.rawValues().keySet()).forEach(source::remove);
    }

    private static String safeReason(RuntimeException failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    record RejectedRecord(String kind, String identifier, String reason, Map<String, String> rawValues) { }
}
