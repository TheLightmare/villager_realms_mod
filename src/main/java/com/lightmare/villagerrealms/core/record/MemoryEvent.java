package com.lightmare.villagerrealms.core.record;

public record MemoryEvent(
        long tick,
        String kind,
        String subjectRef,
        String detail
) {
    public MemoryEvent {
        if (kind == null) throw new IllegalArgumentException("kind required");
    }
}
