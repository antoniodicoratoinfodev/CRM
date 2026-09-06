package com.crm.service;

import com.crm.model.Task;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskScheduleServiceTest {
    private final LocalDate today = LocalDate.of(2026, 9, 5);
    private final LocalDateTime now = today.atTime(10, 0);

    @Test void nextActivitySkipsCompletedWorkAndFindsTomorrow() {
        Task completed = task(11 * 60, 30);
        completed.setCompleted(true);
        Task tomorrow = task(8 * 60, 60);
        var tasks = Map.of(today, List.of(task(9 * 60, 30), completed),
                today.plusDays(1), List.of(tomorrow));

        var next = TaskScheduleService.nextTask(tasks, now).orElseThrow();
        assertEquals(today.plusDays(1), next.date());
        assertSame(tomorrow, next.task());
    }

    @Test void activityInProgressComesBeforeLaterAppointments() {
        Task ongoing = task(9 * 60 + 30, 60);
        var tasks = Map.of(today.plusDays(1), List.of(task(6 * 60, 30)),
                today, List.of(task(13 * 60, 30), ongoing));
        assertSame(ongoing, TaskScheduleService.nextTask(tasks, now).orElseThrow().task());
    }

    @Test void overdueBoundaryIsTheEndTimeAndCompletedWorkNeverCounts() {
        Task endsNow = task(9 * 60, 60);
        assertFalse(TaskScheduleService.isOverdue(today, endsNow, now.minusSeconds(1)));
        assertTrue(TaskScheduleService.isOverdue(today, endsNow, now));
        endsNow.setCompleted(true);
        assertFalse(TaskScheduleService.isOverdue(today.minusDays(1), endsNow, now));
        assertEquals(1, TaskScheduleService.overdueCount(Map.of(today,
                List.of(endsNow, task(8 * 60, 30), task(11 * 60, 30))), now));
    }

    @Test void endOfDayTaskExpiresAtTheFollowingMidnight() {
        Task late = task(23 * 60, 60);
        assertFalse(TaskScheduleService.isOverdue(today, late, today.atTime(23, 59, 59)));
        assertTrue(TaskScheduleService.isOverdue(today, late, today.plusDays(1).atStartOfDay()));
    }

    @Test void emptyOrFullyCompletedSchedulesHaveNoNextActivity() {
        assertTrue(TaskScheduleService.nextTask(Map.of(), now).isEmpty());
        Task completed = task(14 * 60, 60);
        completed.setCompleted(true);
        assertTrue(TaskScheduleService.nextTask(Map.of(today, List.of(completed)), now).isEmpty());
    }

    private Task task(int start, int duration) { return new Task("Review", "", start, duration, "Blue"); }
}
