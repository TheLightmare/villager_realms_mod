package com.lightmare.villagerrealms.core.record;

import java.util.ArrayList;
import java.util.List;

public record MemoryLog(int capacity, List<MemoryEvent> events) {
    public static final int DEFAULT_CAPACITY = 32;

    public MemoryLog {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (events == null) throw new IllegalArgumentException("events required");
        if (events.size() > capacity) {
            events = events.subList(events.size() - capacity, events.size());
        }
        events = List.copyOf(events);
    }

    public static MemoryLog empty() {
        return new MemoryLog(DEFAULT_CAPACITY, List.of());
    }

    public MemoryLog append(MemoryEvent ev) {
        var next = new ArrayList<MemoryEvent>(Math.min(events.size() + 1, capacity));
        int start = events.size() + 1 > capacity ? 1 : 0;
        for (int i = start; i < events.size(); i++) next.add(events.get(i));
        next.add(ev);
        return new MemoryLog(capacity, next);
    }
}
