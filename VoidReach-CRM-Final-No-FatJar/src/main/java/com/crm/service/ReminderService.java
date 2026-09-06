package com.crm.service;

import com.crm.model.Task;
import java.time.*;
import java.util.*;

/** Local reminders, checked only while the desktop process is alive. Snoozes are persisted on the task. */
public final class ReminderService {
    private ReminderService() { }
    public static List<CalendarOccurrenceService.Occurrence> due(Map<LocalDate, List<Task>> tasks, LocalDateTime now) {
        int leadDays = tasks.values().stream().flatMap(List::stream).mapToInt(Task::getReminderMinutes).max().orElse(0) / 1440 + 1;
        return CalendarOccurrenceService.between(tasks, now.toLocalDate().minusDays(1), now.toLocalDate().plusDays(leadDays)).stream()
                .filter(entry -> !entry.task().isCompleted() && entry.task().getReminderMinutes() >= 0)
                .filter(entry -> !entry.task().isReminderAcknowledged(entry.date()))
                .filter(entry -> {
                    LocalDateTime snooze = entry.task().getSnoozedUntil();
                    boolean snoozed = snooze != null && entry.date().toString().equals(entry.task().getSnoozedOccurrence());
                    LocalDateTime trigger = entry.start().minusMinutes(entry.task().getReminderMinutes());
                    return !trigger.isAfter(now) && (entry.end().isAfter(now) || snoozed)
                            && (snoozed ? !snooze.isAfter(now) : !trigger.isBefore(now.minusDays(1)));
                }).toList();
    }
}
