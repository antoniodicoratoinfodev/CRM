package com.crm.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregate for one owner. SQL version maps to contacts, calendar_tasks and user_preferences tables. */
public record CrmDataSnapshot(
        List<Contact> contacts,
        Map<LocalDate, List<Task>> tasksByDate,
        List<Note> notes,
        List<NoteFolder> noteFolders,
        LocalDate selectedDate,
        String calendarViewMode,
        double calendarZoom,
        List<String> contactCustomFields,
        boolean contactsQuickEdit,
        Map<String, String> preferences,
        CrmTrash trash) {

    public CrmDataSnapshot {
        preferences = preferences == null ? Map.of() : Map.copyOf(preferences);
        trash = trash == null ? CrmTrash.EMPTY : trash;
    }

    public CrmDataSnapshot(List<Contact> contacts, Map<LocalDate, List<Task>> tasksByDate,
                           List<Note> notes, List<NoteFolder> noteFolders, LocalDate selectedDate,
                           String calendarViewMode, double calendarZoom, List<String> contactCustomFields,
                           boolean contactsQuickEdit) {
        this(contacts, tasksByDate, notes, noteFolders, selectedDate, calendarViewMode, calendarZoom,
                contactCustomFields, contactsQuickEdit, Map.of(), CrmTrash.EMPTY);
    }

    public CrmDataSnapshot withExtras(Map<String, String> preferences, CrmTrash trash) {
        return new CrmDataSnapshot(contacts, tasksByDate, notes, noteFolders, selectedDate, calendarViewMode,
                calendarZoom, contactCustomFields, contactsQuickEdit, preferences, trash);
    }

    public CrmDataSnapshot detached() {
        return detachedCopyOf(contacts, tasksByDate, notes, noteFolders, selectedDate, calendarViewMode,
                calendarZoom, contactCustomFields, contactsQuickEdit).withExtras(preferences, trash.detached(contactCustomFields));
    }

    public CrmDataSnapshot(List<Contact> contacts, Map<LocalDate, List<Task>> tasksByDate,
                           List<Note> notes, List<NoteFolder> noteFolders, LocalDate selectedDate,
                           String calendarViewMode, double calendarZoom, List<String> contactCustomFields) {
        this(contacts, tasksByDate, notes, noteFolders, selectedDate, calendarViewMode, calendarZoom,
                contactCustomFields, false);
    }

    public CrmDataSnapshot(List<Contact> contacts, Map<LocalDate, List<Task>> tasksByDate,
                           List<Note> notes, List<NoteFolder> noteFolders, LocalDate selectedDate,
                           String calendarViewMode, double calendarZoom) {
        this(contacts, tasksByDate, notes, noteFolders, selectedDate, calendarViewMode, calendarZoom, List.of());
    }

    public CrmDataSnapshot(List<Contact> contacts, Map<LocalDate, List<Task>> tasksByDate,
                           LocalDate selectedDate, String calendarViewMode, double calendarZoom) {
        this(contacts, tasksByDate, List.of(), List.of(), selectedDate, calendarViewMode, calendarZoom);
    }

    public CrmDataSnapshot(List<Contact> contacts, Map<LocalDate, List<Task>> tasksByDate,
                           List<Note> notes, LocalDate selectedDate,
                           String calendarViewMode, double calendarZoom) {
        this(contacts, tasksByDate, notes, List.of(), selectedDate, calendarViewMode, calendarZoom);
    }

    /**
     * Creates a detached state copy on the JavaFX thread before it is handed to I/O.
     * The copied contacts and tasks do not share mutable state with the UI models.
     */
    public static CrmDataSnapshot detachedCopyOf(List<Contact> contacts,
                                                   Map<LocalDate, List<Task>> tasksByDate,
                                                   List<Note> notes,
                                                   List<NoteFolder> noteFolders,
                                                   LocalDate selectedDate,
                                                   String calendarViewMode,
                                                   double calendarZoom) {
        return detachedCopyOf(contacts, tasksByDate, notes, noteFolders,
                selectedDate, calendarViewMode, calendarZoom, List.of(), false);
    }

    public static CrmDataSnapshot detachedCopyOf(List<Contact> contacts,
                                                   Map<LocalDate, List<Task>> tasksByDate,
                                                   List<Note> notes,
                                                   List<NoteFolder> noteFolders,
                                                   LocalDate selectedDate,
                                                   String calendarViewMode,
                                                   double calendarZoom,
                                                   List<String> contactCustomFields,
                                                   boolean contactsQuickEdit) {
        List<Contact> copiedContacts = contacts.stream()
                .map(contact -> {
                    Contact copy = new Contact(contact.getId(), contact.nameProperty().get(),
                            contact.companyProperty().get(), contact.titleProperty().get(),
                            contact.emailProperty().get(), contact.phoneProperty().get(),
                            contact.lastInteractionProperty().get(), contact.tagsProperty().get(),
                            contact.descriptionProperty().get());
                    for (String field : contactCustomFields) copy.setCustomField(field, contact.customFieldValue(field));
                    copy.setInteractions(contact.getInteractions());
                    return copy;
                })
                .toList();

        Map<LocalDate, List<Task>> copiedTasks = new LinkedHashMap<>();
        tasksByDate.forEach((date, tasks) -> copiedTasks.put(date, tasks.stream()
                .map(Task::copy)
                .toList()));

        List<Note> copiedNotes = notes.stream()
                .map(note -> {
                    Note copy = new Note(note.getId(), note.getTitle(), note.getContent(),
                        note.getFormat(), note.getLinkedTaskIds(), note.getFontFamily(),
                        note.getFontSize(), note.getFontWeight(), note.isItalic(),
                        note.getPreviewFontFamily(), note.getPreviewFontSize(), note.getPreviewTextColor(),
                        note.getFolderId());
                    copy.setContactId(note.getContactId());
                    return copy;
                })
                .toList();

        List<NoteFolder> copiedFolders = noteFolders.stream()
                .map(folder -> new NoteFolder(folder.getId(), folder.getName(), folder.getParentFolderId()))
                .toList();

        return new CrmDataSnapshot(List.copyOf(copiedContacts), Map.copyOf(copiedTasks), List.copyOf(copiedNotes),
                List.copyOf(copiedFolders), selectedDate, calendarViewMode, calendarZoom,
                List.copyOf(contactCustomFields), contactsQuickEdit);
    }

    public static CrmDataSnapshot detachedCopyOf(List<Contact> contacts,
                                                  Map<LocalDate, List<Task>> tasksByDate,
                                                  List<Note> notes,
                                                  LocalDate selectedDate,
                                                  String calendarViewMode,
                                                  double calendarZoom) {
        return detachedCopyOf(contacts, tasksByDate, notes, List.of(),
                selectedDate, calendarViewMode, calendarZoom);
    }

    public static CrmDataSnapshot detachedCopyOf(List<Contact> contacts,
                                                  Map<LocalDate, List<Task>> tasksByDate,
                                                  LocalDate selectedDate,
                                                  String calendarViewMode,
                                                  double calendarZoom) {
        return detachedCopyOf(contacts, tasksByDate, List.of(), List.of(), selectedDate, calendarViewMode, calendarZoom);
    }
}
