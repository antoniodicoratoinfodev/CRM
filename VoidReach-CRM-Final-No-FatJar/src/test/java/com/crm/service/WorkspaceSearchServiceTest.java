package com.crm.service;

import com.crm.model.Contact;
import com.crm.model.Note;
import com.crm.model.NoteFormat;
import com.crm.model.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceSearchServiceTest {
    @Test void searchesAcrossAccentsCaseAndCustomContactFields() {
        Contact contact = contact("José Álvarez", "Northstar");
        contact.setCustomField("Project", "Aurora launch");
        var search = new WorkspaceSearchService(List.of(contact), List.of("Project"), Map.of(), List.of());

        assertEquals(contact.getId(), search.search("  JOSE aurora  ", 20).getFirst().id());
        assertTrue(search.search("Jose unknown", 20).isEmpty());
    }

    @Test void searchesNoteContentsAndTaskDescriptionsWithTheirDestinationIds() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        Task task = new Task("Review", "Aurora proposal", 600, 60, "Blue");
        Note note = new Note("Meeting notes", NoteFormat.MARKDOWN);
        note.setContent("# Notes\nThe Aurora proposal is ready.");
        var search = new WorkspaceSearchService(List.of(), List.of(), Map.of(day, List.of(task)), List.of(note));

        var results = search.search("aurora proposal", 20);
        assertEquals(2, results.size());
        var taskResult = results.stream().filter(result -> result.kind() == WorkspaceSearchService.Kind.TASK).findFirst().orElseThrow();
        assertEquals(task.getId(), taskResult.id());
        assertEquals(day, taskResult.date());
        assertTrue(results.stream().anyMatch(result -> result.id().equals(note.getId())));
    }

    @Test void ranksTitleMatchesBeforeContentAndAppliesLimitAfterRanking() {
        List<Contact> contacts = IntStream.range(0, 45).mapToObj(i -> contact("Contact " + i, "Aurora")).toList();
        Note exact = new Note("Aurora", NoteFormat.MARKDOWN);
        var search = new WorkspaceSearchService(contacts, List.of(), Map.of(), List.of(exact));

        assertEquals(exact.getId(), search.search("aurora", 1).getFirst().id());
        assertEquals(20, search.search("aurora", 20).size());
    }

    @Test void blankAndMissingQueriesReturnNoRecords() {
        var search = new WorkspaceSearchService(List.of(contact("A", "B")), List.of(), Map.of(), List.of());
        assertTrue(search.search(null, 20).isEmpty());
        assertTrue(search.search("  ", 20).isEmpty());
        assertTrue(search.search("a", 0).isEmpty());
        assertTrue(search.search("missing", 20).isEmpty());
    }

    @Test void rebuildingTheIndexDoesNotLeakThePreviousAccountsRecords() {
        var first = new WorkspaceSearchService(List.of(contact("Private client", "")), List.of(), Map.of(), List.of());
        var second = new WorkspaceSearchService(List.of(), List.of(), Map.of(), List.of());
        assertEquals(1, first.search("private", 20).size());
        assertTrue(second.search("private", 20).isEmpty());
    }

    private Contact contact(String name, String company) {
        return new Contact(name, company, "", "", "", "", "", "");
    }
}
