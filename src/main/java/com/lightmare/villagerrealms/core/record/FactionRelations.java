package com.lightmare.villagerrealms.core.record;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Directed sparse faction-faction opinion matrix. {@code by[viewer][target]}
 * is the viewer faction's opinion of the target faction. Asymmetric on
 * purpose — A may distrust B more than B distrusts A.
 *
 * Only non-zero entries are stored; absent pairs read as 0.
 */
public record FactionRelations(Map<String, Map<String, Integer>> by) {

    public static final FactionRelations EMPTY = new FactionRelations(Map.of());

    public FactionRelations {
        if (by == null) throw new IllegalArgumentException("by required");
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>(by.size());
        for (var e : by.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            copy.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        by = Collections.unmodifiableMap(copy);
    }

    public int opinion(String viewerFaction, String targetFaction) {
        if (viewerFaction == null || targetFaction == null) return 0;
        Map<String, Integer> row = by.get(viewerFaction);
        if (row == null) return 0;
        Integer v = row.get(targetFaction);
        return v == null ? 0 : v;
    }

    public FactionRelations withOpinion(String viewerFaction, String targetFaction, int value) {
        if (viewerFaction == null || targetFaction == null) {
            throw new IllegalArgumentException("faction id required");
        }
        Map<String, Map<String, Integer>> next = new LinkedHashMap<>(by.size() + 1);
        for (var e : by.entrySet()) next.put(e.getKey(), new LinkedHashMap<>(e.getValue()));

        Map<String, Integer> row = next.computeIfAbsent(viewerFaction, k -> new LinkedHashMap<>());
        if (value == 0) row.remove(targetFaction);
        else row.put(targetFaction, value);
        if (row.isEmpty()) next.remove(viewerFaction);
        return new FactionRelations(next);
    }
}
