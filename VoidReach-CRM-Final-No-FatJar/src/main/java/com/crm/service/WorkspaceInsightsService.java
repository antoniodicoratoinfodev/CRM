package com.crm.service;
import com.crm.model.*;
import java.time.*;
import java.util.*;

public final class WorkspaceInsightsService {
    private WorkspaceInsightsService() { }
    public record DayLoad(LocalDate date, long minutes, boolean overloaded) { }
    public static List<DayLoad> workload(Map<LocalDate, List<Task>> tasks, LocalDate first, CalendarPreferences preferences) {
        var events = CalendarOccurrenceService.between(tasks, first, first.plusDays(6));
        List<DayLoad> result = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            LocalDate date = first.plusDays(day);
            long minutes = events.stream().filter(entry -> !entry.task().isAllDay() && !entry.task().isCompleted()
                            && entry.start().isBefore(date.plusDays(1).atStartOfDay()) && entry.end().isAfter(date.atStartOfDay()))
                    .mapToLong(entry -> entry.endMinuteOn(date) - entry.startMinuteOn(date)).sum();
            result.add(new DayLoad(date, minutes, minutes > preferences.workEnd() - preferences.workStart()));
        }
        return List.copyOf(result);
    }
    public static Optional<LocalDate> lastContact(Contact contact) {
        Optional<LocalDate> date = contact.getInteractions().stream().map(ContactInteraction::date).max(Comparator.naturalOrder());
        if (date.isPresent()) return date;
        try { return Optional.of(LocalDate.parse(contact.lastInteractionProperty().get())); }
        catch (RuntimeException unknown) { return Optional.empty(); }
    }
    public static List<Contact> reconnect(List<Contact> contacts, LocalDate today) {
        return contacts.stream().filter(contact -> lastContact(contact).map(date -> !date.isAfter(today.minusDays(30))).orElse(true))
                .sorted(Comparator.comparing(contact -> lastContact(contact).orElse(LocalDate.MIN))).toList();
    }
}
