package com.crm.service;

import com.crm.model.Task;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** RFC 5545 interchange. Unsupported rules are reported, never silently simplified. */
public final class ICalendarService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private ICalendarService() { }
    public record Imported(Map<LocalDate, List<Task>> tasks, List<String> warnings) {
        public int count() { return tasks.values().stream().mapToInt(List::size).sum(); }
    }
    private record Property(String name, String parameters, String value) { }

    public static Imported read(String text, ZoneId zone) {
        if (text.length() > 20_000_000) throw new IllegalArgumentException("Calendar files must be under 20 MB.");
        String unfolded = text.replace("\r\n", "\n").replaceAll("\n[ \t]", "");
        if (!unfolded.contains("BEGIN:VCALENDAR")) throw new IllegalArgumentException("This is not an iCalendar file.");
        Map<LocalDate, List<Task>> result = new LinkedHashMap<>(); List<String> warnings = new ArrayList<>();
        List<Property> event = null; List<List<Property>> events = new ArrayList<>(); boolean alarm = false;
        for (String line : unfolded.split("\n")) {
            if (line.equalsIgnoreCase("BEGIN:VEVENT")) { event = new ArrayList<>(); continue; }
            if (line.equalsIgnoreCase("END:VEVENT") && event != null) {
                events.add(event);
                event = null; continue;
            }
            if (event == null) continue;
            if (line.equalsIgnoreCase("BEGIN:VALARM")) { alarm = true; continue; }
            if (line.equalsIgnoreCase("END:VALARM")) { alarm = false; continue; }
            int colon = line.indexOf(':'); if (colon < 0) continue;
            String head = line.substring(0, colon); int semi = head.indexOf(';');
            if (alarm && !head.toUpperCase(Locale.ROOT).startsWith("TRIGGER")) continue;
            event.add(new Property((semi < 0 ? head : head.substring(0, semi)).toUpperCase(Locale.ROOT),
                    semi < 0 ? "" : head.substring(semi + 1), line.substring(colon + 1)));
        }
        if (event != null) throw new IllegalArgumentException("The calendar contains an unfinished event.");
        Set<String> exceptions = new HashSet<>();
        events.stream().filter(fields -> property(fields, "RECURRENCE-ID") != null)
                .forEach(fields -> exceptions.add(value(fields, "UID", "")));
        for (List<Property> fields : events) {
            try {
                if (exceptions.contains(value(fields, "UID", ""))) throw new IllegalArgumentException("Series with RECURRENCE-ID overrides must be converted before import");
                var parsed = readEvent(fields, zone);
                result.computeIfAbsent(parsed.getKey(), ignored -> new ArrayList<>()).add(parsed.getValue());
            } catch (RuntimeException unsupported) { warnings.add(value(fields, "SUMMARY", "Untitled event") + ": " + unsupported.getMessage()); }
        }
        return new Imported(Map.copyOf(result), List.copyOf(warnings));
    }
    private static Map.Entry<LocalDate, Task> readEvent(List<Property> fields, ZoneId zone) {
        for (String single : List.of("DTSTART", "DTEND", "DURATION", "RRULE", "UID", "TRIGGER"))
            if (fields.stream().filter(field -> field.name().equals(single)).count() > 1) throw new IllegalArgumentException("Multiple " + single + " properties are not supported");
        if ("CANCELLED".equalsIgnoreCase(value(fields, "STATUS", ""))) throw new IllegalArgumentException("Cancelled event was not imported");
        Property startProperty = property(fields, "DTSTART");
        if (startProperty == null) throw new IllegalArgumentException("Missing start date");
        if (property(fields, "RDATE") != null || property(fields, "RECURRENCE-ID") != null || property(fields, "EXRULE") != null)
            throw new IllegalArgumentException("RDATE/RECURRENCE-ID exceptions require conversion before import");
        boolean allDay = startProperty.value().matches("\\d{8}");
        LocalDateTime start = parseDateTime(startProperty, zone);
        Property endProperty = property(fields, "DTEND");
        LocalDateTime end = endProperty == null ? start.plusMinutes(allDay ? 1440 : 60) : parseDateTime(endProperty, zone);
        Property duration = property(fields, "DURATION");
        if (endProperty != null && duration != null) throw new IllegalArgumentException("Use DTEND or DURATION, not both");
        if (allDay && endProperty != null && !endProperty.value().matches("\\d{8}")) throw new IllegalArgumentException("All-day end must be a date");
        if (endProperty == null && duration != null) end = start.plus(java.time.Duration.parse(duration.value()));
        long minutes = ChronoUnit.MINUTES.between(start, end);
        if (minutes < 1 || minutes > 366 * 1440 || start.getSecond() != 0 || end.getSecond() != 0)
            throw new IllegalArgumentException("Unsupported duration or sub-minute precision");
        String uid = value(fields, "UID", UUID.randomUUID().toString());
        String id = uid.matches("[A-Za-z0-9-]+@voidreach") ? uid.substring(0, uid.length() - 10) : "ics-" + UUID.nameUUIDFromBytes(uid.getBytes(StandardCharsets.UTF_8));
        Task task = Task.scheduled(id,
                unescape(value(fields, "SUMMARY", "Untitled event")),
                unescape(value(fields, "DESCRIPTION", "")) + (property(fields, "LOCATION") == null ? "" : "\nLocation: " + unescape(value(fields, "LOCATION", ""))),
                start.getHour() * 60 + start.getMinute(), (int)minutes, "Blue", false);
        task.setAllDay(allDay);
        task.applyMetadata(Map.of("icsUid", uid));
        String rule = value(fields, "RRULE", "");
        if (!rule.isBlank()) {
            if (startProperty.value().endsWith("Z") && !zone.getRules().isFixedOffset()
                    || startProperty.parameters().contains("TZID=") && !parameter(startProperty, "TZID", zone.getId()).equals(zone.getId()))
                throw new IllegalArgumentException("A recurring series in another time zone cannot be converted without changing future times");
            Set<String> parts = new HashSet<>();
            for (String part : rule.split(";")) {
                String[] pair = part.split("=", 2); if (pair.length != 2) throw new IllegalArgumentException("Invalid recurrence rule");
                if (!parts.add(pair[0].toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate recurrence part");
                switch (pair[0].toUpperCase(Locale.ROOT)) {
                    case "FREQ" -> task.setFrequency(Task.Frequency.valueOf(pair[1]));
                    case "INTERVAL" -> task.setRepeatInterval(Integer.parseInt(pair[1]));
                    case "COUNT" -> task.setRepeatCount(Integer.parseInt(pair[1]));
                    case "UNTIL" -> {
                        LocalDateTime until = pair[1].length() == 8 ? LocalDate.parse(pair[1], DATE).atTime(23, 59, 59) : parseDateTime(new Property("UNTIL", "", pair[1]), zone);
                        task.setRepeatUntil(until.toLocalTime().isBefore(start.toLocalTime()) ? until.toLocalDate().minusDays(1) : until.toLocalDate());
                    }
                    case "WKST" -> { /* A single weekday anchored weekly series has the same instances. */ }
                    case "BYDAY" -> {
                        String weekday = start.getDayOfWeek().name().substring(0, 2);
                        if (!pair[1].equals(weekday)) throw new IllegalArgumentException("Multiple/ordinal weekdays are not supported yet");
                    }
                    default -> throw new IllegalArgumentException("Unsupported recurrence part: " + pair[0]);
                }
            }
            if (task.getFrequency() == Task.Frequency.NONE) throw new IllegalArgumentException("Missing recurrence frequency");
            if (parts.contains("BYDAY") && task.getFrequency() != Task.Frequency.WEEKLY) throw new IllegalArgumentException("BYDAY is supported only for single-weekday weekly series");
            if (task.getRepeatCount() > 0 && task.getRepeatUntil() != null) throw new IllegalArgumentException("Use COUNT or UNTIL, not both");
        }
        for (Property exclusion : fields) if (exclusion.name().equals("EXDATE")) for (String date : exclusion.value().split(","))
            task.exclude(parseDateTime(new Property("EXDATE", exclusion.parameters(), date), zone).toLocalDate());
        Property trigger = property(fields, "TRIGGER");
        if (trigger != null && (trigger.parameters().contains("RELATED=END") || trigger.parameters().contains("VALUE=DATE-TIME")))
            throw new IllegalArgumentException("Only relative reminders before the event start are supported");
        if (trigger != null && !trigger.parameters().contains("VALUE=DATE-TIME")) {
            long before = -java.time.Duration.parse(trigger.value()).toMinutes();
            if (before >= 0 && before <= 525600) task.setReminderMinutes((int)before);
            else throw new IllegalArgumentException("Reminder is outside the supported range");
        }
        return Map.entry(start.toLocalDate(), task);
    }
    public static String write(Map<LocalDate, List<Task>> tasks, ZoneId zone) {
        List<String> lines = new ArrayList<>(List.of("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//VoidReach//Calendar 2//EN", "CALSCALE:GREGORIAN"));
        tasks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> entry.getValue().stream().filter(Task::isScheduled).forEach(task -> {
            LocalDateTime start = entry.getKey().atStartOfDay().plusMinutes(task.getStartMin()), end = start.plusMinutes(task.getDuration());
            lines.add("BEGIN:VEVENT"); lines.add("UID:" + task.metadata().getOrDefault("icsUid", task.getId() + "@voidreach").replaceAll("[\\r\\n]", ""));
            lines.add("DTSTAMP:" + TIME.format(LocalDateTime.now(ZoneOffset.UTC)) + "Z");
            // Floating local times are deliberate: the desktop calendar is a local-time workspace.
            lines.add(task.isAllDay() ? "DTSTART;VALUE=DATE:" + DATE.format(start.toLocalDate()) : "DTSTART:" + TIME.format(start));
            lines.add(task.isAllDay() ? "DTEND;VALUE=DATE:" + DATE.format(end.toLocalDate()) : "DTEND:" + TIME.format(end));
            lines.add("SUMMARY:" + escape(task.getTitle())); lines.add("DESCRIPTION:" + escape(task.getDescription()));
            if (task.getFrequency() != Task.Frequency.NONE) {
                String rule = "RRULE:FREQ=" + task.getFrequency() + ";INTERVAL=" + task.getRepeatInterval();
                if (task.getRepeatCount() > 0) rule += ";COUNT=" + task.getRepeatCount();
                else if (task.getRepeatUntil() != null) rule += ";UNTIL=" + (task.isAllDay() ? DATE.format(task.getRepeatUntil()) : TIME.format(task.getRepeatUntil().atTime(23, 59, 59)));
                lines.add(rule);
                if (!task.getExcludedDates().isEmpty()) lines.add((task.isAllDay() ? "EXDATE;VALUE=DATE:" : "EXDATE:") + String.join(",", task.getExcludedDates().stream()
                        .map(date -> task.isAllDay() ? DATE.format(date) : TIME.format(date.atStartOfDay().plusMinutes(task.getStartMin()))).toList()));
            }
            if (task.getReminderMinutes() >= 0) lines.addAll(List.of("BEGIN:VALARM", "ACTION:DISPLAY", "DESCRIPTION:" + escape(task.getTitle()),
                    "TRIGGER:-PT" + task.getReminderMinutes() + "M", "END:VALARM"));
            lines.add("END:VEVENT");
        }));
        lines.add("END:VCALENDAR");
        return String.join("\r\n", lines.stream().map(ICalendarService::fold).toList()) + "\r\n";
    }
    /** Complete temporary file followed by replacement: an interrupted export keeps the old file. */
    public static void writeFile(java.nio.file.Path target, Map<LocalDate, List<Task>> tasks, ZoneId zone) throws java.io.IOException {
        java.nio.file.Path resolved = target.toAbsolutePath(), temporary = java.nio.file.Files.createTempFile(resolved.getParent(), ".voidreach-calendar-", ".tmp");
        try {
            java.nio.file.Files.writeString(temporary, write(tasks, zone), StandardCharsets.UTF_8);
            try { java.nio.file.Files.move(temporary, resolved, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { java.nio.file.Files.move(temporary, resolved, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } finally { java.nio.file.Files.deleteIfExists(temporary); }
    }
    private static Property property(List<Property> list, String name) { return list.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null); }
    private static String value(List<Property> list, String name, String fallback) { Property p = property(list, name); return p == null ? fallback : p.value(); }
    private static String parameter(Property property, String key, String fallback) {
        for (String item : property.parameters().split(";")) if (item.startsWith(key + "=")) return item.substring(key.length() + 1).replace("\"", "");
        return fallback;
    }
    private static LocalDateTime parseDateTime(Property property, ZoneId zone) {
        String value = property.value();
        if (value.matches("\\d{8}")) return LocalDate.parse(value, DATE).atStartOfDay();
        if (value.endsWith("Z")) return LocalDateTime.parse(value.substring(0, value.length() - 1), TIME).atZone(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDateTime();
        LocalDateTime local = LocalDateTime.parse(value, TIME);
        return local.atZone(ZoneId.of(parameter(property, "TZID", zone.getId()))).withZoneSameInstant(zone).toLocalDateTime();
    }
    private static String escape(String text) { return Objects.requireNonNullElse(text, "").replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,"); }
    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(); boolean escape = false;
        for (char c : text.toCharArray()) { if (escape) { out.append(c == 'n' || c == 'N' ? '\n' : c); escape = false; } else if (c == '\\') escape = true; else out.append(c); }
        if (escape) out.append('\\'); return out.toString();
    }
    static String fold(String line) {
        StringBuilder out = new StringBuilder(); int bytes = 0;
        for (int codepoint : line.codePoints().toArray()) {
            String character = new String(Character.toChars(codepoint)); int length = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + length > 75) { out.append("\r\n "); bytes = 1; }
            out.append(character); bytes += length;
        }
        return out.toString();
    }
}
