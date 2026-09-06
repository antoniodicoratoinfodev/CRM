package com.crm.repository;
import com.crm.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSchemaV2Test {
    @TempDir Path directory;
    @Test void damagedImportsReportSkippedRecordsWithoutRewritingTheSource() throws Exception {
        var repository = new LocalCrmDataRepository(directory); LocalDate date = LocalDate.now();
        Task task = new Task("task", "Review", "", 600, 60, "Blue", false);
        repository.saveForUser("account", new CrmDataSnapshot(List.of(), Map.of(date, List.of(task)), List.of(), date, "Week", 1));
        Path source = directory.resolve("import.properties"); repository.exportForUser("account", source, null);
        Properties properties = new Properties(); try (var input = java.nio.file.Files.newInputStream(source)) { properties.load(input); }
        properties.setProperty("task.0.duration", "-1"); AtomicPropertiesStore.store(source, properties, "Corrupt import fixture");
        byte[] before = java.nio.file.Files.readAllBytes(source); var imported = repository.readImport(source);
        assertFalse(imported.warnings().isEmpty()); assertTrue(imported.snapshot().tasksByDate().isEmpty());
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(source));
    }
    @Test void metadataContactHistoryPreferencesAndTrashSurviveDiskRoundTrip() {
        LocalDate date = LocalDate.of(2026, 9, 6); Contact person = new Contact("person", "Elena", "", "", "", "", "", "", "");
        person.addInteraction(new ContactInteraction(date, "Call", "Discussed a proposal"));
        Task task = Task.scheduled("event", "Workshop", "", 600, 1600, "Blue", false); task.setContactId("person"); task.setFrequency(Task.Frequency.MONTHLY);
        task.setRepeatCount(5); task.setReminderMinutes(30); task.exclude(date.plusMonths(1));
        Note note = new Note("Minutes", NoteFormat.MARKDOWN); note.setContactId("person"); note.linkTask(task.getId());
        CrmTrash trash = new CrmTrash(List.of(person), Map.of(date, List.of(task)), List.of(note));
        CrmDataSnapshot snapshot = new CrmDataSnapshot(List.of(person), Map.of(date, List.of(task)), List.of(note), date, "Month", 1)
                .withExtras(CalendarPreferences.DEFAULT.values(), trash);
        LocalCrmDataRepository repository = new LocalCrmDataRepository(directory); repository.saveForUser("account", snapshot);
        CrmDataSnapshot loaded = repository.loadForUser("account");
        assertEquals(snapshot.preferences(), loaded.preferences()); assertEquals("Month", loaded.calendarViewMode());
        assertEquals(person.getInteractions(), loaded.contacts().getFirst().getInteractions());
        assertEquals(task.metadata(), loaded.tasksByDate().get(date).getFirst().metadata()); assertEquals(1600, loaded.tasksByDate().get(date).getFirst().getDuration());
        assertEquals("person", loaded.notes().getFirst().getContactId()); assertEquals(1, loaded.trash().notes().size());
        assertEquals(task.metadata(), loaded.trash().tasks().get(date).getFirst().metadata());
    }
}
