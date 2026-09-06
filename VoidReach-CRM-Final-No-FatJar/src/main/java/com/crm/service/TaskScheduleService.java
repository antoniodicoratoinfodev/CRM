package com.crm.service;

import com.crm.model.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shared time boundaries for the Home summary, reminders, and task filters. */
public final class TaskScheduleService {
    private TaskScheduleService() { }

    public record DatedTask(LocalDate date, Task task) { }

    public static boolean isOverdue(LocalDate date, Task task, LocalDateTime now) {
        if (!task.isScheduled()) return !task.isCompleted() && task.getDueDate() != null && task.getDueDate().isBefore(now.toLocalDate());
        return !task.isCompleted()
                && !date.atStartOfDay().plusMinutes(task.getStartMin() + task.getDuration()).isAfter(now);
    }

    public static List<DatedTask> openTasks(Map<LocalDate, List<Task>> tasks) {
        return tasks.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().filter(task -> !task.isCompleted())
                        .map(task -> new DatedTask(entry.getKey(), task)))
                .sorted(Comparator.comparing(DatedTask::date)
                        .thenComparingInt(entry -> entry.task().getStartMin())
                        .thenComparing(entry -> entry.task().getId()))
                .toList();
    }

    /** Includes an activity already in progress, and looks beyond the current day. */
    public static Optional<DatedTask> nextTask(Map<LocalDate, List<Task>> tasks, LocalDateTime now) {
        return tasks.entrySet().stream().flatMap(entry -> entry.getValue().stream()
                        .flatMap(task -> CalendarOccurrenceService.next(entry.getKey(), task, now).stream()))
                .sorted(Comparator.comparing(CalendarOccurrenceService.Occurrence::start))
                .map(entry -> new DatedTask(entry.date(), entry.task()))
                .filter(entry -> !entry.task().isCompleted())
                .filter(entry -> !isOverdue(entry.date(), entry.task(), now))
                .findFirst();
    }

    public static long overdueCount(Map<LocalDate, List<Task>> tasks, LocalDateTime now) {
        return listedTasks(tasks, now.toLocalDate()).stream().filter(entry -> isOverdue(entry.date(), entry.task(), now)).count();
    }
    /** All non-repeating records; recurring occurrences use the explicit task-list horizon. */
    public static List<DatedTask> listedTasks(Map<LocalDate, List<Task>> tasks, LocalDate today) {
        java.util.ArrayList<DatedTask> result = new java.util.ArrayList<>();
        java.util.Map<LocalDate, List<Task>> repeating = new java.util.HashMap<>();
        tasks.forEach((date, entries) -> entries.forEach(task -> {
            if (task.getFrequency() == Task.Frequency.NONE) result.add(new DatedTask(date, task));
            else repeating.computeIfAbsent(date, ignored -> new java.util.ArrayList<>()).add(task);
        }));
        CalendarOccurrenceService.between(repeating, today.minusDays(30), today.plusDays(366))
                .forEach(entry -> result.add(new DatedTask(entry.date(), entry.task())));
        return List.copyOf(result);
    }
}
