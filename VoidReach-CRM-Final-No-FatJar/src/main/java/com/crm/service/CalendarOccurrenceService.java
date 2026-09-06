package com.crm.service;

import com.crm.model.Task;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Expands only the requested date window; recurring masters are never multiplied on disk. */
public final class CalendarOccurrenceService {
    private CalendarOccurrenceService() { }
    public record Occurrence(LocalDate sourceDate, LocalDate date, Task task) {
        public LocalDateTime start() { return date.atStartOfDay().plusMinutes(task.getStartMin()); }
        public LocalDateTime end() { return start().plusMinutes(task.getDuration()); }
        public String key() { return task.getId() + "@" + date; }
        public int startMinuteOn(LocalDate day) { return day.equals(date) ? task.getStartMin() : 0; }
        public int endMinuteOn(LocalDate day) {
            return (int)Math.min(1440, ChronoUnit.MINUTES.between(day.atStartOfDay(), end()));
        }
    }

    public static List<Occurrence> between(Map<LocalDate, List<Task>> masters, LocalDate from, LocalDate through) {
        if (through.isBefore(from) || ChronoUnit.DAYS.between(from, through) > 3660)
            throw new IllegalArgumentException("Calendar window must be between one day and ten years.");
        List<Occurrence> result = new ArrayList<>();
        masters.forEach((anchor, tasks) -> tasks.forEach(task -> {
            if (!task.isScheduled()) return;
            LocalDate earliest = from.minusDays((task.getStartMin() + task.getDuration() - 1L) / 1440);
            if (task.getFrequency() == Task.Frequency.NONE) {
                if (!anchor.isBefore(earliest) && !anchor.isAfter(through)) result.add(new Occurrence(anchor, anchor, task));
                return;
            }
            Set<LocalDate> exclusions = task.getExcludedDates();
            long index = firstCandidate(task, anchor, earliest);
            long emitted = index;
            // Bounded by the displayed window, independently of how old the series is.
            for (int attempt = 0; attempt < 40000; attempt++, index++) {
                LocalDate date = dateAt(task, anchor, index);
                if (date == null) continue; // e.g. the 31st does not occur in every month
                if (date.isAfter(through) || task.getRepeatUntil() != null && date.isAfter(task.getRepeatUntil())) break;
                emitted = ordinal(task, anchor, index);
                if (task.getRepeatCount() > 0 && emitted >= task.getRepeatCount()) break;
                if (!date.isBefore(earliest) && !exclusions.contains(date)) result.add(new Occurrence(anchor, date, task));
            }
        }));
        result.sort(Comparator.comparing(Occurrence::start).thenComparing(o -> o.task().getId()));
        return List.copyOf(result);
    }

    private static long firstCandidate(Task task, LocalDate anchor, LocalDate from) {
        long distance = switch (task.getFrequency()) {
            case DAILY -> ChronoUnit.DAYS.between(anchor, from);
            case WEEKLY -> ChronoUnit.WEEKS.between(anchor, from);
            case MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(from));
            default -> 0;
        };
        return Math.max(0, distance / task.getRepeatInterval() - 1);
    }
    public static LocalDate dateAt(Task task, LocalDate anchor, long index) {
        long step = Math.multiplyExact(index, task.getRepeatInterval());
        return switch (task.getFrequency()) {
            case DAILY -> anchor.plusDays(step);
            case WEEKLY -> anchor.plusWeeks(step);
            case MONTHLY -> {
                YearMonth month = YearMonth.from(anchor).plusMonths(step);
                yield anchor.getDayOfMonth() <= month.lengthOfMonth() ? month.atDay(anchor.getDayOfMonth()) : null;
            }
            default -> index == 0 ? anchor : null;
        };
    }
    public static int countBefore(Task task, LocalDate anchor, LocalDate before) {
        if (task.getFrequency() == Task.Frequency.NONE) return anchor.isBefore(before) ? 1 : 0;
        long index = firstCandidate(task, anchor, before);
        while (true) {
            LocalDate date = dateAt(task, anchor, index);
            if (date != null && !date.isBefore(before)) return Math.toIntExact(ordinal(task, anchor, index));
            index++;
        }
    }
    public static Optional<Occurrence> next(LocalDate anchor, Task task, LocalDateTime now) {
        if (!task.isScheduled() || task.isCompleted()) return Optional.empty();
        if (task.getFrequency() == Task.Frequency.NONE) {
            Occurrence occurrence = new Occurrence(anchor, anchor, task);
            return occurrence.end().isAfter(now) ? Optional.of(occurrence) : Optional.empty();
        }
        long index = firstCandidate(task, anchor, now.toLocalDate().minusDays((task.getStartMin() + task.getDuration() - 1L) / 1440));
        Set<LocalDate> exclusions = task.getExcludedDates();
        for (int attempts = 0; attempts < 40000; attempts++, index++) {
            LocalDate date = dateAt(task, anchor, index); if (date == null) continue;
            if (task.getRepeatUntil() != null && date.isAfter(task.getRepeatUntil()) || task.getRepeatCount() > 0 && ordinal(task, anchor, index) >= task.getRepeatCount()) break;
            Occurrence occurrence = new Occurrence(anchor, date, task);
            if (occurrence.end().isAfter(now) && !exclusions.contains(date)) return Optional.of(occurrence);
        }
        return Optional.empty();
    }
    private static long ordinal(Task task, LocalDate anchor, long index) {
        if (task.getFrequency() != Task.Frequency.MONTHLY || anchor.getDayOfMonth() <= 28)
            return index;
        // The Gregorian calendar repeats after 4,800 months: never walk centuries of instances.
        int a = 4800, b = task.getRepeatInterval();
        while (b != 0) { int remainder = a % b; a = b; b = remainder; }
        int cycle = 4800 / a;
        long cycles = index / cycle, remainder = index % cycle, full = 0, partial = 0;
        for (int i = 0; i < (cycles == 0 ? remainder : cycle); i++) {
            if (dateAt(task, anchor, i) != null) { full++; if (i < remainder) partial++; }
        }
        return cycles == 0 ? full : cycles * full + partial;
    }
}
