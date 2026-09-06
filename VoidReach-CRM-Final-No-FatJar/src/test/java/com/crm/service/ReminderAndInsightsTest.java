package com.crm.service;

import com.crm.model.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReminderAndInsightsTest {
    private final LocalDate day = LocalDate.of(2026, 9, 6);
    @Test void overlappingRecurringRemindersRememberBothDismissals() {
        Task event = Task.scheduled("overlap", "Launch", "", 600, 2 * 1440, "Blue", false);
        event.setFrequency(Task.Frequency.DAILY); event.setReminderMinutes(0);
        var tasks = Map.of(day, List.of(event));
        assertEquals(2, ReminderService.due(tasks, day.plusDays(1).atTime(10, 0)).size());
        event.acknowledgeReminder(day.plusDays(1)); event.acknowledgeReminder(day);
        assertTrue(ReminderService.due(tasks, day.plusDays(1).atTime(10, 1)).isEmpty());
    }
    @Test void remindersRespectLeadTimeSnoozeAcrossMidnightAcknowledgmentAndCompletion() {
        Task event = Task.scheduled("reminder", "Late call", "", 23 * 60 + 50, 5, "Blue", false); event.setReminderMinutes(10);
        var tasks = Map.of(day, List.of(event));
        assertTrue(ReminderService.due(tasks, day.atTime(23, 39)).isEmpty());
        assertEquals(1, ReminderService.due(tasks, day.atTime(23, 40)).size());
        event.snooze(day, day.plusDays(1).atTime(0, 5));
        assertTrue(ReminderService.due(tasks, day.plusDays(1).atTime(0, 4)).isEmpty());
        assertEquals(1, ReminderService.due(tasks, day.plusDays(1).atTime(0, 5)).size());
        event.acknowledgeReminder(day); assertTrue(ReminderService.due(tasks, day.plusDays(1).atTime(0, 6)).isEmpty());
    }
    @Test void recurringAcknowledgmentDoesNotSuppressTheNextInstance() {
        Task event = new Task("Daily", "", 600, 60, "Blue"); event.setFrequency(Task.Frequency.DAILY); event.setReminderMinutes(0); event.acknowledgeReminder(day);
        var tasks = Map.of(day, List.of(event));
        assertEquals(1, ReminderService.due(tasks, day.plusDays(1).atTime(10, 0)).size());
        event.setCompleted(true); assertTrue(ReminderService.due(tasks, day.plusDays(1).atTime(10, 0)).isEmpty());
    }
    @Test void workloadClipsOvernightSpansAndExcludesAllDayAndCompletedWork() {
        Task overnight = Task.scheduled("overnight", "Travel", "", 1380, 180, "Blue", false);
        Task allDay = Task.scheduled("all", "Launch", "", 0, 1440, "Blue", false); allDay.setAllDay(true);
        Task done = new Task("Done", "", 600, 60, "Blue"); done.setCompleted(true);
        var load = WorkspaceInsightsService.workload(Map.of(day, List.of(overnight, allDay, done)), day, new CalendarPreferences(DayOfWeek.MONDAY, 540, 630, 15));
        assertEquals(60, load.get(0).minutes()); assertFalse(load.get(0).overloaded());
        assertEquals(120, load.get(1).minutes()); assertTrue(load.get(1).overloaded());
    }
    @Test void reconnectionUsesDatedHistoryAndTreatsLegacyTextAsUnknown() {
        Contact old = new Contact("Old", "", "", "", "", day.minusDays(40).toString(), "", "");
        Contact recent = new Contact("Recent", "", "", "", "", "Yesterday", "", ""); recent.addInteraction(new ContactInteraction(day.minusDays(1), "Call", "Agreed next step"));
        assertEquals(List.of(old), WorkspaceInsightsService.reconnect(List.of(recent, old), day));
    }
}
