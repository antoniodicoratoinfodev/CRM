package com.crm.repository;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.Note;
import com.crm.model.NoteFolder;
import com.crm.model.NoteFormat;
import com.crm.model.Task;
import com.crm.model.CrmTrash;
import com.crm.model.ContactInteraction;
import com.crm.service.WorkspaceVaultService;
import com.crm.repository.CorruptRecordQuarantine.RejectedRecord;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

/** One file per user: local equivalent of owner_user_id filtering in SQL. */
public class LocalCrmDataRepository implements CrmDataRepository {
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_RECORDS = 100_000;
    private static final String FILE_TYPE = "voidreach.crm-data";
    // Owner stamp added to portable exports (not to the per-account on-disk file). Both the desktop
    // and Android apps write/read these keys so an import can warn on a foreign account's file.
    private static final String OWNER_EMAIL_KEY = "account.owner.email";
    private static final String OWNER_NAME_KEY = "account.owner.name";
    private final Path dataDirectory;

    LocalCrmDataRepository(Path dataDirectory) { this.dataDirectory = dataDirectory; }
    public LocalCrmDataRepository() { this(LocalUserRepository.applicationDataDirectory().resolve("data")); }

    @Override public synchronized CrmDataSnapshot loadForUser(String userId) {
        Path file = dataFile(userId);
        Properties properties = WorkspaceVaultService.unseal(load(file), userId);
        List<RejectedRecord> rejected = new ArrayList<>();
        CrmDataSnapshot snapshot = deserialize(properties, rejected);
        CorruptRecordQuarantine.writeBestEffort(file, rejected, userId);
        return snapshot;
    }

    /** Parses an already-loaded property set into a snapshot, collecting rejects (writes no files). */
    private CrmDataSnapshot deserialize(Properties properties, List<RejectedRecord> rejected) {
        return deserialize(properties, rejected, true);
    }

    private CrmDataSnapshot deserialize(Properties properties, List<RejectedRecord> rejected, boolean includeTrash) {
        List<Contact> contacts = new ArrayList<>();
        Map<LocalDate, List<Task>> tasks = new HashMap<>();
        List<Note> notes = new ArrayList<>();
        List<NoteFolder> noteFolders = new ArrayList<>();

        List<String> customFields = readContactCustomFields(properties, rejected);

        for (int index : recordIndexes(properties, "contact.", "contacts.count", rejected)) {
            String prefix = "contact." + index + ".";
            try {
                contacts.add(readContact(properties, prefix, customFields));
            } catch (RuntimeException failure) {
                rejected.add(CorruptRecordQuarantine.capture(properties, "contact", String.valueOf(index), prefix, failure));
            }
        }

        for (int index : recordIndexes(properties, "task.", "tasks.count", rejected)) {
            String prefix = "task." + index + ".";
            try {
                LocalDate date = LocalDate.parse(requiredValue(properties, prefix + "date"));
                Map<String, String> metadata = readMetadata(properties, prefix);
                boolean extended = Boolean.parseBoolean(metadata.getOrDefault("extendedSchedule", "false"));
                int start = requiredInteger(properties, prefix + "startMin");
                int duration = requiredInteger(properties, prefix + "duration");
                if (!extended) Task.validateSchedule(start, duration);
                Task task = Task.scheduled(
                        requiredNonBlank(properties, prefix + "id"),
                        requiredValue(properties, prefix + "title"),
                        optionalValue(properties, prefix + "description", ""),
                        start, duration,
                        optionalValue(properties, prefix + "color", "Blue"),
                        Boolean.parseBoolean(optionalValue(properties, prefix + "completed", "false")));
                task.applyMetadata(metadata);
                tasks.computeIfAbsent(date, ignored -> new ArrayList<>()).add(task);
            } catch (RuntimeException failure) {
                rejected.add(CorruptRecordQuarantine.capture(properties, "task", String.valueOf(index), prefix, failure));
            }
        }

        for (int index : recordIndexes(properties, "noteFolder.", "noteFolders.count", rejected)) {
            String prefix = "noteFolder." + index + ".";
            try {
                noteFolders.add(new NoteFolder(
                        requiredNonBlank(properties, prefix + "id"),
                        requiredNonBlank(properties, prefix + "name"),
                        optionalValue(properties, prefix + "parentFolderId", "")));
            } catch (RuntimeException failure) {
                rejected.add(CorruptRecordQuarantine.capture(properties, "noteFolder", String.valueOf(index), prefix, failure));
            }
        }

        for (int index : recordIndexes(properties, "note.", "notes.count", rejected)) {
            String prefix = "note." + index + ".";
            try {
                Note note = new Note(
                        requiredNonBlank(properties, prefix + "id"),
                        optionalValue(properties, prefix + "title", ""),
                        optionalValue(properties, prefix + "content", ""),
                        NoteFormat.valueOf(optionalValue(properties, prefix + "format", "TEXT")),
                        readLinkedTaskIds(properties, prefix),
                        optionalValue(properties, prefix + "fontFamily", Note.DEFAULT_FONT_FAMILY),
                        optionalDouble(properties, prefix + "fontSize", Note.DEFAULT_FONT_SIZE),
                        optionalInteger(properties, prefix + "fontWeight", Note.DEFAULT_FONT_WEIGHT),
                        Boolean.parseBoolean(optionalValue(properties, prefix + "italic", "false")),
                        optionalValue(properties, prefix + "previewFontFamily", Note.DEFAULT_PREVIEW_FONT_FAMILY),
                        optionalDouble(properties, prefix + "previewFontSize", Note.DEFAULT_PREVIEW_FONT_SIZE),
                        optionalValue(properties, prefix + "previewTextColor", ""),
                        optionalValue(properties, prefix + "folderId", ""));
                note.setContactId(optionalValue(properties, prefix + "contactId", ""));
                notes.add(note);
            } catch (RuntimeException failure) {
                rejected.add(CorruptRecordQuarantine.capture(properties, "note", String.valueOf(index), prefix, failure));
            }
        }

        LocalDate selectedDate = preference(properties, "calendar.selectedDate", LocalDate.now(), LocalDate::parse, rejected);
        String viewMode = preference(properties, "calendar.viewMode", "Day", value -> {
            if (!List.of("Day", "Week", "Month", "Agenda").contains(value)) throw new IllegalArgumentException("Invalid calendar view mode");
            return value;
        }, rejected);
        double zoom = preference(properties, "calendar.zoom", 1.0, value -> {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0) throw new IllegalArgumentException("Invalid calendar zoom");
            return parsed;
        }, rejected);

        boolean quickEdit = preference(properties, "contacts.quickEdit", false, value -> {
            if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("Invalid quick edit flag");
            return Boolean.parseBoolean(value);
        }, rejected);

        CrmTrash trash = CrmTrash.EMPTY;
        if (includeTrash && properties.containsKey("trash.contacts.count")) {
            Properties archived = new Properties();
            properties.stringPropertyNames().stream().filter(key -> key.startsWith("trash."))
                    .forEach(key -> archived.setProperty(key.substring(6), properties.getProperty(key)));
            CrmDataSnapshot deleted = deserialize(archived, rejected, false);
            trash = new CrmTrash(deleted.contacts(), deleted.tasksByDate(), deleted.notes());
        }
        return new CrmDataSnapshot(contacts, tasks, notes, noteFolders, selectedDate, viewMode, zoom,
                customFields, quickEdit, readMetadata(properties, "workspace."), trash);
    }

    private List<String> readContactCustomFields(Properties properties, List<RejectedRecord> rejected) {
        List<String> fields = new ArrayList<>();
        for (int index : recordIndexes(properties, "contactCustomField.", "contactCustomFields.count", rejected)) {
            String key = "contactCustomField." + index + ".name";
            try {
                String name = requiredNonBlank(properties, key);
                if (!fields.contains(name)) fields.add(name);
            } catch (RuntimeException failure) {
                rejected.add(CorruptRecordQuarantine.singleProperty("contactCustomField", key, properties.getProperty(key), failure));
            }
        }
        return fields;
    }

    @Override public synchronized void saveForUser(String userId, CrmDataSnapshot data) {
        Path target = dataFile(userId);
        if (Files.isRegularFile(target) && WorkspaceVaultService.encrypted(load(target))) WorkspaceVaultService.markEnabled(userId);
        writeSnapshot(target, data, "VoidReach CRM data for one account", userId);
    }

    synchronized void writeSnapshot(Path target, CrmDataSnapshot data, String comment) {
        writeSnapshot(target, data, comment, null);
    }

    synchronized void writeSnapshot(Path target, CrmDataSnapshot data, String comment, String userId) {
        Properties properties = userId == null ? serialize(data) : WorkspaceVaultService.seal(serialize(data), userId);
        try {
            AtomicPropertiesStore.store(target, properties, comment);
        } catch (IOException e) {
            throw new IllegalStateException("Local data could not be saved", e);
        }
    }

    @Override public synchronized void exportForUser(String userId, Path target, ExportOwner owner) {
        Properties properties = serialize(loadForUser(userId));
        if (owner != null) {
            put(properties, OWNER_EMAIL_KEY, owner.email());
            put(properties, OWNER_NAME_KEY, owner.name());
        }
        try {
            AtomicPropertiesStore.store(target.toAbsolutePath(), properties, "VoidReach CRM portable data (not encrypted)");
        } catch (IOException e) {
            throw new IllegalStateException("Data could not be exported", e);
        }
    }

    @Override public synchronized ImportedWorkspace readImport(Path source) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(source)) {
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException("The selected file could not be read", e);
        }
        properties = WorkspaceVaultService.unseal(properties, null);
        validateImport(properties);
        String email = ownerStamp(properties, OWNER_EMAIL_KEY);
        ExportOwner owner = email == null ? null
                : new ExportOwner(email, Objects.requireNonNullElse(ownerStamp(properties, OWNER_NAME_KEY), ""));
        List<RejectedRecord> rejected = new ArrayList<>();
        CrmDataSnapshot snapshot = deserialize(properties, rejected);
        return new ImportedWorkspace(owner, snapshot, rejected.stream().map(record -> record.kind() + " " + record.identifier() + ": " + record.reason()).toList());
    }

    private void validateImport(Properties properties) {
        String version = properties.getProperty(AtomicPropertiesStore.SCHEMA_VERSION_KEY);
        if (version != null) {
            int schema;
            try {
                schema = Integer.parseInt(version);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid schema version: " + version);
            }
            if (schema < 1 || schema > SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported schema version: " + schema);
        }
        String type = properties.getProperty(AtomicPropertiesStore.FILE_TYPE_KEY);
        if (type != null && !FILE_TYPE.equals(type)) throw new IllegalArgumentException("This file is not a VoidReach CRM data file.");
        boolean recognizable = properties.containsKey("contacts.count") || properties.containsKey("tasks.count")
                || properties.containsKey("notes.count") || properties.containsKey("noteFolders.count")
                || properties.containsKey("calendar.selectedDate");
        if (!recognizable) throw new IllegalArgumentException("This file is not a VoidReach CRM data file.");
    }

    /** Owner value tolerating both Base64 (how we write it) and a plain hand-edited value; null when absent. */
    private String ownerStamp(Properties properties, String key) {
        String raw = properties.getProperty(key);
        if (raw == null) return null;
        String decoded;
        try {
            decoded = decode(raw, key);
        } catch (RuntimeException failure) {
            decoded = raw;
        }
        decoded = decoded.trim();
        return decoded.isEmpty() ? null : decoded;
    }

    private Properties serialize(CrmDataSnapshot data) {
        Properties properties = new Properties();
        properties.setProperty(AtomicPropertiesStore.SCHEMA_VERSION_KEY, String.valueOf(SCHEMA_VERSION));
        properties.setProperty(AtomicPropertiesStore.FILE_TYPE_KEY, FILE_TYPE);
        List<String> customFields = data.contactCustomFields();
        properties.setProperty("contactCustomFields.count", String.valueOf(customFields.size()));
        for (int i = 0; i < customFields.size(); i++) {
            put(properties, "contactCustomField." + i + ".name", customFields.get(i));
        }
        properties.setProperty("contacts.count", String.valueOf(data.contacts().size()));
        for (int i = 0; i < data.contacts().size(); i++) {
            Contact contact = data.contacts().get(i);
            String prefix = "contact." + i + ".";
            put(properties, prefix + "id", contact.getId());
            put(properties, prefix + "name", contact.nameProperty().get());
            put(properties, prefix + "company", contact.companyProperty().get());
            put(properties, prefix + "title", contact.titleProperty().get());
            put(properties, prefix + "email", contact.emailProperty().get());
            put(properties, prefix + "phone", contact.phoneProperty().get());
            put(properties, prefix + "lastInteraction", contact.lastInteractionProperty().get());
            put(properties, prefix + "tags", contact.tagsProperty().get());
            put(properties, prefix + "description", contact.descriptionProperty().get());
            properties.setProperty(prefix + "interactions.count", String.valueOf(contact.getInteractions().size()));
            for (int j = 0; j < contact.getInteractions().size(); j++) {
                ContactInteraction interaction = contact.getInteractions().get(j);
                String item = prefix + "interaction." + j + ".";
                put(properties, item + "id", interaction.id()); put(properties, item + "date", interaction.date().toString());
                put(properties, item + "kind", interaction.kind()); put(properties, item + "summary", interaction.summary());
            }
            for (int fieldIndex = 0; fieldIndex < customFields.size(); fieldIndex++) {
                put(properties, prefix + "custom." + fieldIndex, contact.customFieldValue(customFields.get(fieldIndex)));
            }
        }

        List<Map.Entry<LocalDate, Task>> allTasks = new ArrayList<>();
        data.tasksByDate().forEach((date, tasks) -> tasks.forEach(task -> allTasks.add(Map.entry(date, task))));
        properties.setProperty("tasks.count", String.valueOf(allTasks.size()));
        for (int i = 0; i < allTasks.size(); i++) {
            LocalDate date = allTasks.get(i).getKey();
            Task task = allTasks.get(i).getValue();
            String prefix = "task." + i + ".";
            put(properties, prefix + "id", task.getId());
            put(properties, prefix + "date", date.toString());
            put(properties, prefix + "title", task.getTitle());
            put(properties, prefix + "description", task.getDescription());
            put(properties, prefix + "startMin", String.valueOf(task.getStartMin()));
            put(properties, prefix + "duration", String.valueOf(task.getDuration()));
            put(properties, prefix + "color", task.getColor());
            put(properties, prefix + "completed", String.valueOf(task.isCompleted()));
            writeMetadata(properties, prefix, task.metadata());
        }
        properties.setProperty("notes.count", String.valueOf(data.notes().size()));
        for (int i = 0; i < data.notes().size(); i++) {
            Note note = data.notes().get(i);
            String prefix = "note." + i + ".";
            put(properties, prefix + "id", note.getId());
            put(properties, prefix + "title", note.getTitle());
            put(properties, prefix + "content", note.getContent());
            put(properties, prefix + "format", note.getFormat().name());
            put(properties, prefix + "linkedTaskId", note.getLinkedTaskId());
            properties.setProperty(prefix + "linkedTaskIds.count", String.valueOf(note.getLinkedTaskIds().size()));
            for (int linkIndex = 0; linkIndex < note.getLinkedTaskIds().size(); linkIndex++) {
                put(properties, prefix + "linkedTaskIds." + linkIndex, note.getLinkedTaskIds().get(linkIndex));
            }
            put(properties, prefix + "fontFamily", note.getFontFamily());
            put(properties, prefix + "fontSize", String.valueOf(note.getFontSize()));
            put(properties, prefix + "fontWeight", String.valueOf(note.getFontWeight()));
            put(properties, prefix + "italic", String.valueOf(note.isItalic()));
            put(properties, prefix + "previewFontFamily", note.getPreviewFontFamily());
            put(properties, prefix + "previewFontSize", String.valueOf(note.getPreviewFontSize()));
            put(properties, prefix + "previewTextColor", note.getPreviewTextColor());
            put(properties, prefix + "folderId", note.getFolderId());
            put(properties, prefix + "contactId", note.getContactId());
        }
        properties.setProperty("noteFolders.count", String.valueOf(data.noteFolders().size()));
        for (int i = 0; i < data.noteFolders().size(); i++) {
            NoteFolder folder = data.noteFolders().get(i);
            String prefix = "noteFolder." + i + ".";
            put(properties, prefix + "id", folder.getId());
            put(properties, prefix + "name", folder.getName());
            put(properties, prefix + "parentFolderId", folder.getParentFolderId());
        }
        put(properties, "calendar.selectedDate", data.selectedDate().toString());
        put(properties, "calendar.viewMode", data.calendarViewMode());
        put(properties, "calendar.zoom", String.valueOf(data.calendarZoom()));
        put(properties, "contacts.quickEdit", String.valueOf(data.contactsQuickEdit()));
        writeMetadata(properties, "workspace.", data.preferences());
        if (!data.trash().isEmpty()) {
            CrmDataSnapshot archived = new CrmDataSnapshot(data.trash().contacts(), data.trash().tasks(),
                    data.trash().notes(), List.of(), data.selectedDate(), "Day", 1, data.contactCustomFields());
            Properties deleted = serialize(archived);
            deleted.stringPropertyNames().forEach(key -> properties.setProperty("trash." + key, deleted.getProperty(key)));
        }
        return properties;
    }

    private Contact readContact(Properties properties, String prefix, List<String> customFields) {
        Contact contact = new Contact(
                requiredNonBlank(properties, prefix + "id"),
                requiredValue(properties, prefix + "name"),
                optionalValue(properties, prefix + "company", ""),
                optionalValue(properties, prefix + "title", ""),
                optionalValue(properties, prefix + "email", ""),
                optionalValue(properties, prefix + "phone", ""),
                optionalValue(properties, prefix + "lastInteraction", ""),
                optionalValue(properties, prefix + "tags", ""),
                optionalValue(properties, prefix + "description", ""));
        for (int fieldIndex = 0; fieldIndex < customFields.size(); fieldIndex++) {
            contact.setCustomField(customFields.get(fieldIndex),
                    optionalValue(properties, prefix + "custom." + fieldIndex, ""));
        }
        int interactionCount = properties.containsKey(prefix + "interactions.count")
                ? flexibleInteger(properties.getProperty(prefix + "interactions.count"), prefix + "interactions.count") : 0;
        if (interactionCount < 0 || interactionCount > MAX_RECORDS) throw new IllegalArgumentException("Invalid interaction count");
        List<ContactInteraction> interactions = new ArrayList<>();
        for (int i = 0; i < interactionCount; i++) {
            String item = prefix + "interaction." + i + ".";
            interactions.add(new ContactInteraction(requiredNonBlank(properties, item + "id"),
                    LocalDate.parse(requiredNonBlank(properties, item + "date")),
                    optionalValue(properties, item + "kind", "Note"), requiredNonBlank(properties, item + "summary")));
        }
        contact.setInteractions(interactions);
        return contact;
    }

    private void writeMetadata(Properties properties, String prefix, Map<String, String> values) {
        properties.setProperty(prefix + "metadata.count", String.valueOf(values.size()));
        int index = 0;
        for (var item : new java.util.TreeMap<>(values).entrySet()) {
            put(properties, prefix + "metadata." + index + ".key", item.getKey());
            put(properties, prefix + "metadata." + index++ + ".value", item.getValue());
        }
    }

    private Map<String, String> readMetadata(Properties properties, String prefix) {
        String count = properties.getProperty(prefix + "metadata.count", "0");
        int size = flexibleInteger(count, prefix + "metadata.count");
        if (size < 0 || size > 1000) throw new IllegalArgumentException("Invalid metadata count");
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < size; i++) result.put(requiredNonBlank(properties, prefix + "metadata." + i + ".key"),
                optionalValue(properties, prefix + "metadata." + i + ".value", ""));
        return result;
    }

    private List<String> readLinkedTaskIds(Properties properties, String prefix) {
        String countValue = properties.getProperty(prefix + "linkedTaskIds.count");
        if (countValue == null) {
            String legacyId = optionalValue(properties, prefix + "linkedTaskId", "").trim();
            return legacyId.isEmpty() ? List.of() : List.of(legacyId);
        }
        int count = flexibleInteger(countValue, prefix + "linkedTaskIds.count");
        if (count < 0 || count > MAX_RECORDS) throw new IllegalArgumentException("Linked task count is out of range");
        List<String> taskIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String taskId = requiredNonBlank(properties, prefix + "linkedTaskIds." + index);
            taskIds.add(taskId);
        }
        return taskIds;
    }

    private SortedSet<Integer> recordIndexes(Properties properties, String prefix, String countKey, List<RejectedRecord> rejected) {
        SortedSet<Integer> indexes = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            int separator = key.indexOf('.', prefix.length());
            if (separator < 0) continue;
            try {
                int index = Integer.parseInt(key.substring(prefix.length(), separator));
                if (index >= 0 && index < MAX_RECORDS) indexes.add(index);
            } catch (NumberFormatException ignored) {
                // The malformed key cannot identify a record and will be represented by the count diagnostic below.
            }
        }
        String rawCount = properties.getProperty(countKey);
        if (rawCount == null && indexes.isEmpty()) return indexes;
        try {
            int count = flexibleInteger(rawCount, countKey);
            if (count < 0 || count > MAX_RECORDS) throw new IllegalArgumentException("Count is out of range");
            for (int i = 0; i < count; i++) indexes.add(i);
        } catch (RuntimeException failure) {
            rejected.add(CorruptRecordQuarantine.singleProperty("metadata", countKey, rawCount, failure));
        }
        return indexes;
    }

    private <T> T preference(Properties properties, String key, T fallback, Parser<T> parser, List<RejectedRecord> rejected) {
        if (!properties.containsKey(key)) return fallback;
        try {
            return parser.parse(requiredValue(properties, key));
        } catch (RuntimeException failure) {
            rejected.add(CorruptRecordQuarantine.singleProperty("preference", key, properties.getProperty(key), failure));
            return fallback;
        }
    }

    private Properties load(Path file) {
        try {
            return AtomicPropertiesStore.load(file, FILE_TYPE, SCHEMA_VERSION,
                    properties -> properties.containsKey("contacts.count")
                            || properties.containsKey("tasks.count")
                            || properties.containsKey("notes.count")
                            || properties.containsKey("noteFolders.count")
                            || properties.containsKey("calendar.selectedDate"));
        } catch (IOException e) {
            throw new IllegalStateException("Local data could not be read", e);
        }
    }

    Path dataFile(String userId) {
        if (userId == null || !userId.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid account identifier.");
        return dataDirectory.resolve(userId + ".properties");
    }

    /** Keeps legacy revisions and quarantine records, replacing only their storage envelope. */
    public synchronized void migrateProtection(String userId) {
        Path file = dataFile(userId);
        for (String suffix : List.of("", ".bak", ".corrupt.properties", ".corrupt.properties.bak"))
            WorkspaceVaultService.encryptExisting(file.resolveSibling(file.getFileName() + suffix), userId);
        if (Files.isDirectory(dataDirectory)) try (var entries = Files.list(dataDirectory)) {
            for (Path temporary : entries.filter(path -> path.getFileName().toString().startsWith(userId + ".properties.")
                    && path.getFileName().toString().endsWith(".tmp")).toList()) WorkspaceVaultService.encryptExisting(temporary, userId);
        } catch (IOException failure) { throw new IllegalStateException("Old workspace temporary files could not be protected.", failure); }
    }

    private int requiredInteger(Properties properties, String key) { return flexibleInteger(properties.getProperty(key), key); }

    private int optionalInteger(Properties properties, String key, int fallback) {
        return properties.containsKey(key) ? flexibleInteger(properties.getProperty(key), key) : fallback;
    }

    private double optionalDouble(Properties properties, String key, double fallback) {
        if (!properties.containsKey(key)) return fallback;
        String value = requiredValue(properties, key);
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) throw new IllegalArgumentException("Invalid number: " + key);
        return parsed;
    }

    private int flexibleInteger(String rawValue, String key) {
        if (rawValue == null) throw new IllegalArgumentException("Missing property: " + key);
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException plainFailure) {
            try {
                return Integer.parseInt(decode(rawValue, key));
            } catch (RuntimeException encodedFailure) {
                encodedFailure.addSuppressed(plainFailure);
                throw encodedFailure;
            }
        }
    }

    private String requiredNonBlank(Properties properties, String key) {
        String value = requiredValue(properties, key);
        if (value.isBlank()) throw new IllegalArgumentException("Empty property: " + key);
        return value;
    }

    private String requiredValue(Properties properties, String key) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null) throw new IllegalArgumentException("Missing property: " + key);
        return decode(rawValue, key);
    }

    private String optionalValue(Properties properties, String key, String fallback) {
        String rawValue = properties.getProperty(key);
        return rawValue == null ? fallback : decode(rawValue, key);
    }

    private String decode(String rawValue, String key) {
        try {
            return new String(Base64.getDecoder().decode(rawValue), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64: " + key, e);
        }
    }

    private void put(Properties properties, String key, String value) {
        properties.setProperty(key, Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    }

    @FunctionalInterface
    private interface Parser<T> { T parse(String value); }
}
