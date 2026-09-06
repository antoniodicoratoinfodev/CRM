package com.crm.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Recoverable records, stored with the workspace and included in backups/exports. */
public record CrmTrash(List<Contact> contacts, Map<LocalDate, List<Task>> tasks, List<Note> notes) {
    public static final CrmTrash EMPTY = new CrmTrash(List.of(), Map.of(), List.of());
    public boolean isEmpty() { return contacts.isEmpty() && tasks.isEmpty() && notes.isEmpty(); }
    public CrmTrash detached(List<String> customFields) {
        CrmDataSnapshot copy = CrmDataSnapshot.detachedCopyOf(contacts, tasks, notes, List.of(),
                LocalDate.now(), "Day", 1, customFields, false);
        return new CrmTrash(copy.contacts(), copy.tasksByDate(), copy.notes());
    }
}
