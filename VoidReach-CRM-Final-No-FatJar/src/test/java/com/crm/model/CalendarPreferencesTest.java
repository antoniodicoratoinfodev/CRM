package com.crm.model;

import java.time.DayOfWeek;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalendarPreferencesTest {
    @Test void allSnapModesPersistWithoutLosingOtherPreferences() {
        for (CalendarPreferences.Snap snap : CalendarPreferences.Snap.values()) {
            var value = new CalendarPreferences(DayOfWeek.SUNDAY, 420, 1200, snap.minutes());
            assertEquals(value, CalendarPreferences.from(value.values()));
            assertEquals(snap, value.snapMode());
            assertTrue(value.snapStepMinutes() > 0);
        }
        assertEquals(CalendarPreferences.DEFAULT, CalendarPreferences.from(Map.of()));
        assertEquals(CalendarPreferences.DEFAULT, CalendarPreferences.from(Map.of("calendar.snap", "7")));
    }

    @Test void draggingAlignsAbsoluteTimeEvenIfOriginalStartWasOffGrid() {
        assertEquals(610, CalendarPreferences.DEFAULT.withSnap(CalendarPreferences.Snap.TEN).snapStart(607 + 4, false));
        assertEquals(615, CalendarPreferences.DEFAULT.withSnap(CalendarPreferences.Snap.GRID).snapStart(607 + 4, false));
        assertEquals(610, CalendarPreferences.DEFAULT.withSnap(CalendarPreferences.Snap.FIVE).snapStart(607 + 4, false));
        assertEquals(611, CalendarPreferences.DEFAULT.withSnap(CalendarPreferences.Snap.ONE).snapStart(607 + 4, false));
        assertEquals(611, CalendarPreferences.DEFAULT.snapStart(607 + 4, true));
    }

    @Test void snapStaysOnGridAtBothDayEdges() {
        for (CalendarPreferences.Snap snap : CalendarPreferences.Snap.values()) {
            var value = CalendarPreferences.DEFAULT.withSnap(snap);
            assertEquals(0, value.snapStart(-100, false));
            int last = value.snapStart(1600, false);
            assertTrue(last < 1440); assertEquals(0, last % value.snapStepMinutes());
            assertEquals(1439, value.snapStart(1600, true));
        }
    }

    @Test void resizingSnapsTheEndAndKeepsValidDurations() {
        var preferences = CalendarPreferences.DEFAULT.withSnap(CalendarPreferences.Snap.TEN);
        assertEquals(63, preferences.snapDuration(607, 60, 4, false)); // 11:10 end, not 11:17.
        assertEquals(64, preferences.snapDuration(607, 60, 4, true));
        assertEquals(1, preferences.snapDuration(607, 60, -500, false));
        assertEquals(366 * 1440, preferences.snapDuration(0, 366 * 1440, 100, false));
    }
}
