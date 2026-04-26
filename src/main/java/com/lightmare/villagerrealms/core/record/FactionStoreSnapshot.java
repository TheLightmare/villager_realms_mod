package com.lightmare.villagerrealms.core.record;

import java.util.List;

/**
 * On-disk shape for the FactionStore: the full set of {@link Faction}s plus
 * the directed faction-faction opinion matrix. Held as a single blob; the
 * data is small enough that sharding adds no value.
 */
public record FactionStoreSnapshot(List<Faction> factions, FactionRelations relations) {
    public FactionStoreSnapshot {
        if (factions == null) throw new IllegalArgumentException("factions required");
        if (relations == null) relations = FactionRelations.EMPTY;
        factions = List.copyOf(factions);
    }

    public static FactionStoreSnapshot empty() {
        return new FactionStoreSnapshot(List.of(), FactionRelations.EMPTY);
    }
}
