package com.crm.model;

import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

public class Task {
    public static final int MINUTES_PER_DAY = 24 * 60;
    public static final int MIN_DURATION_MINUTES = 1;

    private String title;
    private String description;
    private int startMin;
    private int duration;
    private String color;
    private boolean completed;
    private final String id;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    public enum Priority { LOW, NORMAL, HIGH, URGENT }
    public enum Status { TODO, IN_PROGRESS, COMPLETED }
    public enum Frequency { NONE, DAILY, WEEKLY, MONTHLY }

    public Task(String title, String description, int startMin, int duration, String color) {
        this(UUID.randomUUID().toString(), title, description, startMin, duration, color);
    }

    public Task(String id, String title, String description, int startMin, int duration, String color) {
        this(id, title, description, startMin, duration, color, false);
    }

    public Task(String id, String title, String description, int startMin, int duration, String color,
                boolean completed) {
        validateSchedule(startMin, duration);
        this.id = id;
        this.title = title;
        this.description = description;
        this.startMin = startMin;
        this.duration = duration;
        this.color = color;
        this.completed = completed;
    }

    public String getTitle() { return title; }

    public String getDescription() { return description; }

    public int getStartMin() { return startMin; }
    public void setStartMin(int startMin) {
        validateCurrentSchedule(startMin, duration);
        this.startMin = startMin;
    }

    public int getDuration() { return duration; }
    public void setDuration(int duration) {
        validateCurrentSchedule(startMin, duration);
        this.duration = duration;
    }

    public String getColor() { return color; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) {
        this.completed = completed;
        metadata.put("status", completed ? "COMPLETED" : "TODO");
    }
    public String getId() { return id; }

    /** Legacy constructors retain their one-day validation. Extended schedules are explicit. */
    public static Task scheduled(String id, String title, String description, int start, int duration,
                                 String color, boolean completed) {
        Task task = new Task(id, title, description, start, 1, color, completed);
        task.metadata.put("extendedSchedule", "true");
        task.setDuration(duration);
        return task;
    }

    private void validateCurrentSchedule(int start, int length) {
        if (!Boolean.parseBoolean(metadata.get("extendedSchedule"))) { validateSchedule(start, length); return; }
        if (start < 0 || start >= MINUTES_PER_DAY || length < 1 || length > 366 * MINUTES_PER_DAY)
            throw new IllegalArgumentException("Use a valid start time and a duration of at most 366 days.");
    }

    public Task copy() { return copyWithId(id); }
    public Task copyWithId(String newId) {
        Task copy = scheduled(newId, title, description, startMin, duration, color, completed);
        copy.metadata.clear(); copy.metadata.putAll(metadata);
        if (!id.equals(newId)) {
            copy.metadata.remove("icsUid"); copy.metadata.remove("reminderAcknowledged");
            copy.metadata.remove("snoozedUntil"); copy.metadata.remove("snoozedOccurrence");
        }
        return copy;
    }
    public Map<String, String> metadata() { return Map.copyOf(metadata); }
    public void applyMetadata(Map<String, String> values) {
        metadata.putAll(values);
        getPriority(); getStatus(); getFrequency(); getDueDate(); getRepeatUntil(); getExcludedDates();
        getRepeatInterval(); getRepeatCount(); getReminderMinutes(); getSnoozedUntil();
    }
    public boolean isScheduled() { return !"false".equals(metadata.get("scheduled")); }
    public void setScheduled(boolean value) { metadata.put("scheduled", String.valueOf(value)); }
    public boolean isAllDay() { return Boolean.parseBoolean(metadata.get("allDay")); }
    public void setAllDay(boolean value) { metadata.put("allDay", String.valueOf(value)); }
    public LocalDate getDueDate() { return date("dueDate"); }
    public void setDueDate(LocalDate value) { putDate("dueDate", value); }
    public Priority getPriority() { return Priority.valueOf(metadata.getOrDefault("priority", "NORMAL")); }
    public void setPriority(Priority value) { metadata.put("priority", value.name()); }
    public Status getStatus() { return completed ? Status.COMPLETED : Status.valueOf(metadata.getOrDefault("status", "TODO")); }
    public void setStatus(Status value) { completed = value == Status.COMPLETED; metadata.put("status", value.name()); }
    public String getContactId() { return metadata.getOrDefault("contactId", ""); }
    public void setContactId(String value) { metadata.put("contactId", value == null ? "" : value); }
    public Frequency getFrequency() { return Frequency.valueOf(metadata.getOrDefault("frequency", "NONE")); }
    public void setFrequency(Frequency value) { metadata.put("frequency", value.name()); }
    public int getRepeatInterval() { return integer("repeatInterval", 1, 1, 999); }
    public void setRepeatInterval(int value) {
        if (value < 1 || value > 999) throw new IllegalArgumentException("Repeat interval must be between 1 and 999.");
        metadata.put("repeatInterval", String.valueOf(value));
    }
    public int getRepeatCount() { return integer("repeatCount", 0, 0, 100000); }
    public void setRepeatCount(int value) {
        if (value < 0 || value > 100000) throw new IllegalArgumentException("Repeat count is out of range.");
        metadata.put("repeatCount", String.valueOf(value));
    }
    public LocalDate getRepeatUntil() { return date("repeatUntil"); }
    public void setRepeatUntil(LocalDate value) { putDate("repeatUntil", value); }
    public Set<LocalDate> getExcludedDates() {
        Set<LocalDate> result = new TreeSet<>();
        String text = metadata.getOrDefault("exclusions", "");
        if (!text.isBlank()) for (String date : text.split(",")) result.add(LocalDate.parse(date));
        return result;
    }
    public void exclude(LocalDate date) {
        Set<LocalDate> dates = getExcludedDates(); dates.add(date);
        metadata.put("exclusions", String.join(",", dates.stream().map(LocalDate::toString).toList()));
    }
    public void clearExclusions() { metadata.remove("exclusions"); }
    public int getReminderMinutes() { return integer("reminder", -1, -1, 525600); }
    public void setReminderMinutes(int value) {
        if (value < -1 || value > 525600) throw new IllegalArgumentException("Reminder is out of range.");
        metadata.put("reminder", String.valueOf(value));
    }
    public LocalDateTime getSnoozedUntil() {
        String value = metadata.get("snoozedUntil");
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }
    public void setSnoozedUntil(LocalDateTime value) { metadata.put("snoozedUntil", value == null ? "" : value.toString()); }
    public String getSnoozedOccurrence() { return metadata.getOrDefault("snoozedOccurrence", ""); }
    public void snooze(LocalDate occurrence, LocalDateTime until) { metadata.put("snoozedOccurrence", occurrence.toString()); setSnoozedUntil(until); }
    public String getReminderAcknowledged() { return metadata.getOrDefault("reminderAcknowledged", ""); }
    public boolean isReminderAcknowledged(LocalDate occurrence) {
        return java.util.Arrays.asList(getReminderAcknowledged().split(",")).contains(occurrence.toString());
    }
    public void acknowledgeReminder(LocalDate occurrence) {
        TreeSet<String> dates = new TreeSet<>(java.util.Arrays.asList(getReminderAcknowledged().split(",")));
        dates.remove(""); dates.add(occurrence.toString());
        while (dates.size() > 512) dates.pollFirst();
        metadata.put("reminderAcknowledged", String.join(",", dates));
    }
    private LocalDate date(String key) {
        String value = metadata.get(key); return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
    private void putDate(String key, LocalDate value) { metadata.put(key, value == null ? "" : value.toString()); }
    private int integer(String key, int fallback, int min, int max) {
        int value = Integer.parseInt(metadata.getOrDefault(key, String.valueOf(fallback)));
        if (value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }

    public static void validateSchedule(int startMin, int duration) {
        if (startMin < 0 || startMin >= MINUTES_PER_DAY) {
            throw new IllegalArgumentException("The start time must be between 00:00 and 23:59.");
        }
        if (duration < MIN_DURATION_MINUTES) {
            throw new IllegalArgumentException("A task must have a positive duration.");
        }
        if (duration > MINUTES_PER_DAY - startMin) {
            throw new IllegalArgumentException("A task cannot end after 24:00.");
        }
    }
}
