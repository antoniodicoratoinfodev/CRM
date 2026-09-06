package com.crm.model;

import java.time.DayOfWeek;
import java.util.Map;

public record CalendarPreferences(DayOfWeek firstDay, int workStart, int workEnd, int snapMinutes) {
    public static final int GRID_MINUTES = 15;
    public enum Snap {
        GRID(0, "Grid (15 min)"), ONE(1, "1 min"), FIVE(5, "5 min"), TEN(10, "10 min"), FIFTEEN(15, "15 min"), THIRTY(30, "30 min");
        private final int minutes;
        private final String label;
        Snap(int minutes, String label) { this.minutes = minutes; this.label = label; }
        public int minutes() { return minutes; }
        @Override public String toString() { return label; }
        public static Snap fromMinutes(int minutes) {
            return java.util.Arrays.stream(values()).filter(value -> value.minutes == minutes).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Choose Grid, 1, 5, 10, 15 or 30 minutes."));
        }
    }
    public static final CalendarPreferences DEFAULT = new CalendarPreferences(DayOfWeek.MONDAY, 8 * 60, 18 * 60, 15);
    public CalendarPreferences {
        if (firstDay == null || workStart < 0 || workEnd > 1440 || workEnd <= workStart)
            throw new IllegalArgumentException("Choose valid working hours.");
        Snap.fromMinutes(snapMinutes);
    }
    public Snap snapMode() { return Snap.fromMinutes(snapMinutes); }
    public int snapStepMinutes() { return snapMinutes == 0 ? GRID_MINUTES : snapMinutes; }
    public CalendarPreferences withSnap(Snap snap) { return new CalendarPreferences(firstDay, workStart, workEnd, snap.minutes()); }
    public int snapStart(double minute, boolean exact) {
        int step = exact ? 1 : snapStepMinutes();
        return Math.max(0, Math.min(1440 - step, (int)Math.round(minute / step) * step));
    }
    public int snapDuration(int start, int duration, double deltaMinutes, boolean exact) {
        int step = exact ? 1 : snapStepMinutes();
        int end = (int)Math.round((start + duration + deltaMinutes) / step) * step;
        return Math.max(1, Math.min(366 * 1440, end - start));
    }
    public static CalendarPreferences from(Map<String, String> values) {
        try { return new CalendarPreferences(DayOfWeek.valueOf(values.getOrDefault("calendar.firstDay", "MONDAY")),
                Integer.parseInt(values.getOrDefault("calendar.workStart", "480")),
                Integer.parseInt(values.getOrDefault("calendar.workEnd", "1080")),
                Integer.parseInt(values.getOrDefault("calendar.snap", "15"))); }
        catch (RuntimeException invalid) { return DEFAULT; }
    }
    public Map<String, String> values() { return Map.of("calendar.firstDay", firstDay.name(), "calendar.workStart", "" + workStart,
            "calendar.workEnd", "" + workEnd, "calendar.snap", "" + snapMinutes); }
}
