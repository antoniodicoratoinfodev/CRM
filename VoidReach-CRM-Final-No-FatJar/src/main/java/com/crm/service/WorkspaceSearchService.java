package com.crm.service;

import com.crm.model.Contact;
import com.crm.model.Note;
import com.crm.model.Task;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** An in-memory search index rebuilt from the active workspace when search opens. */
public final class WorkspaceSearchService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public enum Kind { CONTACT, TASK, NOTE }

    public record Result(Kind kind, String id, String title, String detail, LocalDate date) { }

    private record Document(Result result, String title, String text) { }
    private final List<Document> documents = new ArrayList<>();

    public WorkspaceSearchService(List<Contact> contacts, List<String> customFields,
                                  Map<LocalDate, List<Task>> tasks, List<Note> notes) {
        for (Contact contact : contacts) {
            String title = display(contact.nameProperty().get(), "Unnamed contact");
            String detail = join(contact.companyProperty().get(), contact.emailProperty().get());
            StringBuilder text = new StringBuilder(join(title, detail, contact.titleProperty().get(),
                    contact.phoneProperty().get(), contact.tagsProperty().get(),
                    contact.lastInteractionProperty().get(), contact.descriptionProperty().get()));
            customFields.forEach(field -> text.append(' ').append(contact.customFieldValue(field)));
            contact.getInteractions().forEach(item -> text.append(' ').append(item.summary()));
            add(new Result(Kind.CONTACT, contact.getId(), title, detail, null), text.toString());
        }
        tasks.forEach((date, entries) -> entries.forEach(task -> {
            String title = display(task.getTitle(), "Untitled task");
            LocalDate occurrence = task.getFrequency() == Task.Frequency.NONE ? date :
                    CalendarOccurrenceService.next(date, task, java.time.LocalDateTime.now()).map(CalendarOccurrenceService.Occurrence::date).orElse(date);
            String detail = (!task.isScheduled() ? task.getDueDate() == null ? "No deadline" : "Due " + task.getDueDate().format(DATE) :
                    occurrence.format(DATE) + " · " + (task.isAllDay() ? "All day" : String.format(Locale.ROOT, "%02d:%02d", task.getStartMin() / 60, task.getStartMin() % 60)))
                    + (task.getFrequency() == Task.Frequency.NONE ? "" : " · Repeats " + task.getFrequency().name().toLowerCase(Locale.ROOT))
                    + (task.isCompleted() ? " · Completed" : "");
            add(new Result(Kind.TASK, task.getId(), title, detail, occurrence),
                    join(title, detail, date.toString(), task.getDescription()));
        }));
        for (Note note : notes) {
            String title = display(note.getTitle(), "Untitled note");
            String content = note.getContent();
            String excerpt = content.substring(0, Math.min(content.length(), 160))
                    .replaceAll("\\s+", " ").trim();
            add(new Result(Kind.NOTE, note.getId(), title, display(excerpt, note.getFormat().name()), null),
                    join(title, content));
        }
    }

    private void add(Result result, String text) {
        documents.add(new Document(result, normalize(result.title()), normalize(text)));
    }

    /** Every word must match; title matches precede matches found only in the content. */
    public List<Result> search(String query, int limit) {
        String normalized = normalize(query).trim();
        if (normalized.isEmpty() || limit <= 0) return List.of();
        List<String> words = List.of(normalized.split("\\s+"));
        return documents.stream()
                .filter(document -> words.stream().allMatch(document.text()::contains))
                .sorted(Comparator.<Document>comparingInt(document -> rank(document, normalized, words))
                        .thenComparing(Document::title)
                        .thenComparing(document -> document.result().kind())
                        .thenComparing(document -> document.result().id()))
                .limit(limit)
                .map(Document::result)
                .toList();
    }

    private static int rank(Document document, String query, List<String> words) {
        if (document.title().equals(query)) return 0;
        if (document.title().startsWith(query)) return 1;
        if (words.stream().allMatch(document.title()::contains)) return 2;
        return 3;
    }

    public static boolean matches(String text, String query) {
        String normalized = normalize(text);
        return List.of(normalize(query).trim().split("\\s+")).stream().allMatch(normalized::contains);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static String join(String... values) {
        return String.join(" · ", java.util.Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank()).toList());
    }

    private static String display(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
