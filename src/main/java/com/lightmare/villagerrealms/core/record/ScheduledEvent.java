package com.lightmare.villagerrealms.core.record;

import java.util.Map;
import java.util.UUID;

/**
 * One entry in the persisted simulation schedule (CLAUDE.md
 * "SimulationScheduler — persisted task queue: pending births, scheduled
 * events"). Fires once when gameTime reaches {@code dueTick}.
 *
 * {@code kind} selects the handler; {@code subject} is the uuid the event
 * is about (an unborn child, a growing child, …); {@code data} carries
 * whatever else the handler needs as plain strings so the schedule format
 * never has to change when a new kind is added.
 */
public record ScheduledEvent(
        UUID id,
        long dueTick,
        String kind,
        String villageId,
        UUID subject,
        Map<String, String> data
) {
    public ScheduledEvent {
        if (id == null) throw new IllegalArgumentException("id required");
        if (kind == null || kind.isEmpty()) throw new IllegalArgumentException("kind required");
        if (data == null) data = Map.of();
        data = Map.copyOf(data);
    }

    public String datum(String key) {
        return data.get(key);
    }

    public UUID uuidDatum(String key) {
        String s = data.get(key);
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
