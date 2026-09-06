package com.crm.service;

import com.crm.model.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CalendarUpgradeTest {
    @Test void denseOverlapAllocationIsBoundedAndDeterministic() {
        var entries = java.util.stream.IntStream.range(0, 20000).mapToObj(i -> new CalendarLayoutService.Interval("event-" + i, 600, 660)).toList();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertEquals(20000, CalendarLayoutService.arrange(entries).get("event-0").columns()));
    }
    @Test void ancientMonthlySeriesDoesNotWalkEveryPreviousMonth() {
        Task monthly = task("ancient", 600, 60); monthly.setFrequency(Task.Frequency.MONTHLY); monthly.setRepeatCount(100000);
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertTrue(CalendarOccurrenceService.between(
                Map.of(LocalDate.of(-10000000, 1, 31), List.of(monthly)), date, date.plusYears(1)).isEmpty()));
    }
    @Test void duplicateAndDetachedEventsDoNotReuseAnImportedUidOrReminderState() {
        Task original = task("original", 600, 60); original.applyMetadata(Map.of("icsUid", "foreign-calendar-id")); original.acknowledgeReminder(date);
        Task copied = original.copyWithId("duplicate"); assertFalse(copied.metadata().containsKey("icsUid")); assertEquals("", copied.getReminderAcknowledged());
        assertEquals("foreign-calendar-id", original.copy().metadata().get("icsUid"));
    }
    @Test void unsupportedExceptionSeriesIsNotImportedAsAnIncorrectMaster() {
        var result = ICalendarService.read("BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:series\nDTSTART:20260906T090000\nRRULE:FREQ=DAILY\nEND:VEVENT\n"
                + "BEGIN:VEVENT\nUID:series\nRECURRENCE-ID:20260907T090000\nDTSTART:20260907T110000\nEND:VEVENT\nEND:VCALENDAR", ZoneId.of("Europe/Rome"));
        assertEquals(0, result.count()); assertEquals(2, result.warnings().size());
    }
    @Test void monthlyByDayIsNeverSimplifiedToAMonthDay() {
        var result = ICalendarService.read("BEGIN:VCALENDAR\nBEGIN:VEVENT\nDTSTART:20260906T090000\nRRULE:FREQ=MONTHLY;BYDAY=SU\nEND:VEVENT\nEND:VCALENDAR", ZoneId.systemDefault());
        assertEquals(0, result.count()); assertEquals(1, result.warnings().size());
    }
    private final LocalDate date = LocalDate.of(2026, 9, 6);
    private Task task(String id, int start, int length) { return Task.scheduled(id, "Review", "Context", start, length, "Blue", false); }
    @Test void overlappingEventsGetSeparateColumnsButTouchingEventsDoNot() {
        var layout = CalendarLayoutService.arrange(List.of(new CalendarLayoutService.Interval("a", 600, 660),
                new CalendarLayoutService.Interval("b", 600, 660), new CalendarLayoutService.Interval("c", 660, 700)));
        assertEquals(2, layout.get("a").columns()); assertNotEquals(layout.get("a").column(), layout.get("b").column());
        assertEquals(1, layout.get("c").columns());
    }
    @Test void transitiveConflictsAndInputOrderHaveStableColumns() {
        var entries = List.of(new CalendarLayoutService.Interval("a", 540, 720), new CalendarLayoutService.Interval("b", 600, 660),
                new CalendarLayoutService.Interval("c", 630, 750), new CalendarLayoutService.Interval("d", 750, 800));
        var layout = CalendarLayoutService.arrange(entries);
        assertEquals(3, layout.get("c").columns()); assertEquals(1, layout.get("d").columns());
        List<CalendarLayoutService.Interval> shuffled = new ArrayList<>(entries); Collections.reverse(shuffled);
        assertEquals(layout, CalendarLayoutService.arrange(shuffled));
    }
    @Test void weeklyRecurrenceRespectsIntervalEndAndExceptions() {
        Task task = task("weekly", 600, 60); task.setFrequency(Task.Frequency.WEEKLY); task.setRepeatInterval(2); task.setRepeatUntil(date.plusWeeks(6));
        task.exclude(date.plusWeeks(2));
        var events = CalendarOccurrenceService.between(Map.of(date, List.of(task)), date, date.plusWeeks(8));
        assertEquals(List.of(date, date.plusWeeks(4), date.plusWeeks(6)), events.stream().map(CalendarOccurrenceService.Occurrence::date).toList());
    }
    @Test void monthlyDatesSkipInvalidMonthsAndCountRealInstances() {
        Task task = task("monthly", 600, 60); task.setFrequency(Task.Frequency.MONTHLY); task.setRepeatCount(3);
        LocalDate anchor = LocalDate.of(2026, 1, 31);
        assertEquals(List.of(anchor, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31)),
                CalendarOccurrenceService.between(Map.of(anchor, List.of(task)), anchor, anchor.plusYears(1)).stream().map(CalendarOccurrenceService.Occurrence::date).toList());
        assertEquals(2, CalendarOccurrenceService.countBefore(task, anchor, LocalDate.of(2026, 5, 31)));
    }
    @Test void overnightSegmentsRespectExclusiveMidnightAndCopiesKeepMetadata() {
        Task task = task("overnight", 23 * 60, 120); task.setContactId("person"); task.setPriority(Task.Priority.URGENT); task.setReminderMinutes(15);
        var event = CalendarOccurrenceService.between(Map.of(date, List.of(task)), date.plusDays(1), date.plusDays(1)).getFirst();
        assertEquals(0, event.startMinuteOn(date.plusDays(1))); assertEquals(60, event.endMinuteOn(date.plusDays(1)));
        assertEquals(task.metadata(), task.copy().metadata());
        Task endsAtMidnight = task("midnight", 1380, 60);
        assertTrue(CalendarOccurrenceService.between(Map.of(date, List.of(endsAtMidnight)), date.plusDays(1), date.plusDays(1)).isEmpty());
    }
    @Test void unscheduledTasksHaveOptionalDayBasedDeadlines() {
        Task task = task("todo", 0, 60); task.setScheduled(false);
        assertFalse(TaskScheduleService.isOverdue(date, task, date.plusDays(5).atStartOfDay()));
        assertTrue(CalendarOccurrenceService.between(Map.of(date, List.of(task)), date, date.plusDays(5)).isEmpty());
        task.setDueDate(date);
        assertFalse(TaskScheduleService.isOverdue(date, task, date.atTime(23, 59)));
        assertTrue(TaskScheduleService.isOverdue(date, task, date.plusDays(1).atStartOfDay()));
    }
    @Test void recurrenceWindowDoesNotEnumerateDecadesBeforeTheViewport() {
        Task task = task("old", 600, 60); task.setFrequency(Task.Frequency.DAILY);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> assertEquals(7,
                CalendarOccurrenceService.between(Map.of(LocalDate.of(1900, 1, 1), List.of(task)), date, date.plusDays(6)).size()));
    }
    @Test void icsRoundTripPreservesLocalSchedulesRecurrenceUnicodeAndStableIds() {
        Task task = Task.scheduled("stable-id", "Riunione, città; 日本語 ".repeat(8), "First\nSecond\\line", 600, 1500, "Blue", false);
        task.setFrequency(Task.Frequency.WEEKLY); task.setRepeatCount(4); task.exclude(date.plusWeeks(1)); task.setReminderMinutes(15);
        String text = ICalendarService.write(Map.of(date, List.of(task)), ZoneId.of("Europe/Rome"));
        for (String line : text.split("\r\n")) assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
        var imported = ICalendarService.read(text, ZoneId.of("Europe/Rome"));
        assertTrue(imported.warnings().isEmpty(), imported.warnings().toString()); Task copy = imported.tasks().get(date).getFirst();
        assertEquals(task.getId(), copy.getId()); assertEquals(task.getTitle(), copy.getTitle()); assertEquals(task.getDescription(), copy.getDescription());
        assertEquals(1500, copy.getDuration()); assertEquals(4, copy.getRepeatCount()); assertEquals(task.getExcludedDates(), copy.getExcludedDates()); assertEquals(15, copy.getReminderMinutes());
    }
    @Test void icsAllDayEndIsExclusiveAndUnsupportedRulesAreReported() {
        Task task = task("all-day", 0, 2880); task.setAllDay(true);
        var copy = ICalendarService.read(ICalendarService.write(Map.of(date, List.of(task)), ZoneId.systemDefault()), ZoneId.systemDefault()).tasks().get(date).getFirst();
        assertTrue(copy.isAllDay()); assertEquals(2880, copy.getDuration());
        var unsupported = ICalendarService.read("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20260906T090000\r\nRRULE:FREQ=MONTHLY;BYSETPOS=2\r\nEND:VEVENT\r\nEND:VCALENDAR", ZoneId.systemDefault());
        assertEquals(0, unsupported.count()); assertEquals(1, unsupported.warnings().size());
    }
}
