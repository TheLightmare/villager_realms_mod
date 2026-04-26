package com.lightmare.villagerrealms.core.record;

import java.util.Map;
import java.util.UUID;

/**
 * An NPC's personal opinion overrides. Both maps are sparse: absent keys mean
 * "no override, fall back to the faction-faction baseline".
 *
 * <ul>
 *   <li>{@link #factionOpinions} — viewer's deviation from the faction-faction
 *       opinion of {@code factionId}. Looked up first when comparing factions.</li>
 *   <li>{@link #opinions} — sparse NPC-by-UUID overrides for specific
 *       individuals (you killed their brother, they owe you a debt). Looked
 *       up before any faction-level reasoning.</li>
 * </ul>
 *
 * Per the design doc, dense per-NPC opinion matrices are forbidden (O(n²)).
 * Only store an entry here when something has actually happened.
 */
public record Relationships(
        Map<String, Integer> factionOpinions,
        Map<UUID, Integer> opinions
) {

    public static final Relationships EMPTY = new Relationships(Map.of(), Map.of());

    public Relationships {
        if (factionOpinions == null) throw new IllegalArgumentException("factionOpinions required");
        if (opinions == null) throw new IllegalArgumentException("opinions required");
        factionOpinions = Map.copyOf(factionOpinions);
        opinions = Map.copyOf(opinions);
    }

    public Relationships withOpinion(UUID target, int value) {
        var next = new java.util.LinkedHashMap<>(opinions);
        if (value == 0) next.remove(target);
        else next.put(target, value);
        return new Relationships(factionOpinions, next);
    }

    public Relationships withFactionOpinion(String faction, int value) {
        var next = new java.util.LinkedHashMap<>(factionOpinions);
        if (value == 0) next.remove(faction);
        else next.put(faction, value);
        return new Relationships(next, opinions);
    }
}
