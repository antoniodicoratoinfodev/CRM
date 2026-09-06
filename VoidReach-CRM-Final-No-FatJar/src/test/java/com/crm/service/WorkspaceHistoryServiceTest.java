package com.crm.service;
import com.crm.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceHistoryServiceTest {
    private CrmDataSnapshot state(List<Contact> contacts, Map<LocalDate,List<Task>> tasks, List<Note> notes) {
        return new CrmDataSnapshot(contacts, tasks, notes, LocalDate.of(2026,9,6), "Day", 1).detached();
    }
    @Test void deletedRecordsCanBeUndoneRedoneAndRetainedInTrash() {
        var contact = new Contact("person", "Elena", "Studio", "", "", "", "", "", "");
        var note = new Note("Notes", NoteFormat.MARKDOWN);
        var task = new Task("Call", "", 600, 60, "Blue");
        var original = state(List.of(contact), Map.of(LocalDate.of(2026,9,6), List.of(task)), List.of(note));
        WorkspaceHistoryService history = new WorkspaceHistoryService(); history.reset(original);
        var deleted = history.record(state(List.of(), Map.of(), List.of()));
        assertEquals(1, deleted.trash().contacts().size()); assertEquals(1, deleted.trash().notes().size());
        assertEquals(1, deleted.trash().tasks().size());
        assertEquals("Elena", history.undo().orElseThrow().contacts().getFirst().nameProperty().get());
        assertTrue(history.redo().orElseThrow().contacts().isEmpty());
    }
    @Test void navigationDoesNotConsumeUndoAndRestoredCopiesAreDetached() {
        WorkspaceHistoryService history = new WorkspaceHistoryService(); var original = state(List.of(), Map.of(), List.of()); history.reset(original);
        history.record(new CrmDataSnapshot(List.of(), Map.of(), LocalDate.now(), "Month", 2)); assertFalse(history.canUndo());
        Note note = new Note("Version A", NoteFormat.MARKDOWN); history.record(state(List.of(), Map.of(), List.of(note)));
        note.setTitle("Version B"); history.record(state(List.of(), Map.of(), List.of(note)));
        CrmDataSnapshot restored = history.undo().orElseThrow(); restored.notes().getFirst().setTitle("Outside mutation");
        assertEquals("Version B", history.redo().orElseThrow().notes().getFirst().getTitle());
        assertEquals("Version A", history.undo().orElseThrow().notes().getFirst().getTitle());
    }
}
