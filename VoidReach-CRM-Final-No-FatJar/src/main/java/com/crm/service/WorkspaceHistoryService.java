package com.crm.service;

import com.crm.model.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Bounded session history; deleted records also live in the persisted workspace trash. */
public final class WorkspaceHistoryService {
    private static final int LIMIT = 25;
    private final Deque<CrmDataSnapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private CrmDataSnapshot current;
    public void reset(CrmDataSnapshot value) { undo.clear(); redo.clear(); current = value.detached(); }
    public CrmDataSnapshot record(CrmDataSnapshot value) {
        if (current == null) { reset(value); return value; }
        if (content(current).equals(content(value))) { current = value; return value; }
        CrmDataSnapshot next = value.withExtras(value.preferences(), retainDeleted(current, value));
        undo.push(current); if (undo.size() > LIMIT) undo.removeLast(); redo.clear(); current = next;
        return next;
    }
    public Optional<CrmDataSnapshot> undo() {
        if (undo.isEmpty()) return Optional.empty(); redo.push(current); current = undo.pop(); return Optional.of(current.detached());
    }
    public Optional<CrmDataSnapshot> redo() {
        if (redo.isEmpty()) return Optional.empty(); undo.push(current); current = redo.pop(); return Optional.of(current.detached());
    }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    private static CrmTrash retainDeleted(CrmDataSnapshot before, CrmDataSnapshot after) {
        List<Contact> contacts = new ArrayList<>(after.trash().contacts());
        retain(before.contacts(), after.contacts(), contacts, Contact::getId);
        List<Note> notes = new ArrayList<>(after.trash().notes()); retain(before.notes(), after.notes(), notes, Note::getId);
        Map<LocalDate, List<Task>> tasks = new LinkedHashMap<>(); after.trash().tasks().forEach((date, items) -> tasks.put(date, new ArrayList<>(items)));
        Set<String> active = new HashSet<>(), archived = new HashSet<>();
        after.tasksByDate().values().forEach(items -> items.forEach(task -> active.add(task.getId())));
        tasks.values().forEach(items -> items.forEach(task -> archived.add(task.getId())));
        before.tasksByDate().forEach((date, items) -> items.forEach(task -> {
            if (!active.contains(task.getId()) && archived.add(task.getId())) tasks.computeIfAbsent(date, ignored -> new ArrayList<>()).add(archiveTask(task, before.notes()));
        }));
        return new CrmTrash(List.copyOf(contacts), Map.copyOf(tasks), List.copyOf(notes));
    }
    public static Task archiveTask(Task task, List<Note> notes) {
        Task archived = task.copy();
        String links = String.join(",", notes.stream().filter(note -> note.getLinkedTaskIds().contains(task.getId()))
                .map(note -> Base64.getUrlEncoder().withoutPadding().encodeToString(note.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8))).toList());
        archived.applyMetadata(Map.of("trash.noteIds", links)); return archived;
    }
    public static void restoreTaskLinks(Task task, List<Note> notes) {
        Set<String> ids = new HashSet<>();
        for (String encoded : task.metadata().getOrDefault("trash.noteIds", "").split(",")) {
            try { if (!encoded.isBlank()) ids.add(new String(Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8)); }
            catch (IllegalArgumentException ignored) { /* A damaged optional link does not block record restoration. */ }
        }
        notes.stream().filter(note -> ids.contains(note.getId())).forEach(note -> note.linkTask(task.getId()));
    }
    private static <T> void retain(List<T> before, List<T> after, List<T> trash, Function<T, String> id) {
        Set<String> present = new HashSet<>(); after.forEach(item -> present.add(id.apply(item))); trash.forEach(item -> present.add(id.apply(item)));
        before.forEach(item -> { if (present.add(id.apply(item))) trash.add(item); });
    }
    /** Value views deliberately exclude navigation/zoom, so browsing does not consume history. */
    private static Object content(CrmDataSnapshot data) {
        List<Object> contacts = new ArrayList<>(), notes = new ArrayList<>(); Map<LocalDate, List<Object>> tasks = new HashMap<>();
        data.contacts().forEach(c -> contacts.add(Arrays.asList(c.getId(), c.nameProperty().get(), c.companyProperty().get(), c.titleProperty().get(),
                c.emailProperty().get(), c.phoneProperty().get(), c.tagsProperty().get(), c.lastInteractionProperty().get(), c.descriptionProperty().get(),
                c.getInteractions(), data.contactCustomFields().stream().map(c::customFieldValue).toList())));
        data.notes().forEach(n -> notes.add(Arrays.asList(n.getId(), n.getTitle(), n.getContent(), n.getFolderId(), n.getFormat(), n.getContactId(), n.getLinkedTaskIds(),
                n.getFontFamily(), n.getFontSize(), n.getFontWeight(), n.isItalic(), n.getPreviewFontFamily(), n.getPreviewFontSize(), n.getPreviewTextColor())));
        data.tasksByDate().forEach((date, items) -> tasks.put(date, items.stream().map(t -> (Object)Arrays.asList(t.getId(), t.getTitle(), t.getDescription(),
                t.getStartMin(), t.getDuration(), t.getColor(), t.isCompleted(), t.metadata())).toList()));
        return Arrays.asList(contacts, tasks, notes, data.noteFolders().stream().map(f -> List.of(f.getId(), f.getName(), f.getParentFolderId())).toList(), data.contactCustomFields());
    }
}
