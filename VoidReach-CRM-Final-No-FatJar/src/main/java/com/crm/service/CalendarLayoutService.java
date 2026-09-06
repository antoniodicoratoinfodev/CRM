package com.crm.service;

import java.util.*;

/** Interval partitioning: touching endpoints do not conflict, transitively overlapping groups share columns. */
public final class CalendarLayoutService {
    private CalendarLayoutService() { }
    public record Interval(String key, int start, int end) {
        public Interval { if (start < 0 || end <= start || end > 1440) throw new IllegalArgumentException("Invalid interval"); }
    }
    public record Placement(String key, int column, int columns) { }
    public static Map<String, Placement> arrange(List<Interval> source) {
        List<Interval> intervals = source.stream().sorted(Comparator.comparingInt(Interval::start)
                .thenComparing(Comparator.comparingInt(Interval::end).reversed()).thenComparing(Interval::key)).toList();
        Map<String, Placement> result = new LinkedHashMap<>();
        List<Interval> group = new ArrayList<>();
        int groupEnd = -1;
        for (Interval interval : intervals) {
            if (!group.isEmpty() && interval.start() >= groupEnd) { arrangeGroup(group, result); group.clear(); }
            group.add(interval); groupEnd = Math.max(group.isEmpty() ? -1 : groupEnd, interval.end());
        }
        arrangeGroup(group, result);
        return Map.copyOf(result);
    }
    private static void arrangeGroup(List<Interval> group, Map<String, Placement> result) {
        record Busy(int end, int column) { }
        PriorityQueue<Busy> occupied = new PriorityQueue<>(Comparator.comparingInt(Busy::end));
        PriorityQueue<Integer> available = new PriorityQueue<>();
        Map<String, Integer> assigned = new LinkedHashMap<>(); int columnCount = 0;
        for (Interval item : group) {
            while (!occupied.isEmpty() && occupied.peek().end() <= item.start()) available.add(occupied.remove().column());
            int column = available.isEmpty() ? columnCount++ : available.remove();
            occupied.add(new Busy(item.end(), column));
            assigned.put(item.key(), column);
        }
        int columns = columnCount;
        assigned.forEach((key, column) -> result.put(key, new Placement(key, column, columns)));
    }
}
