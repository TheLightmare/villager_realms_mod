package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.record.VillageMarket;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Narrow handle the AI scheduler uses to read and update village markets,
 * decoupled from the production VillageStore. Production wires this to a
 * VillageStore-backed adapter; tests use an in-memory map.
 */
public interface Markets {

    Optional<VillageMarket> get(String villageId);

    /**
     * Read-modify-write a market atomically (single-threaded server, so
     * "atomic" just means "no two callers see a stale snapshot mid-update").
     * Returns the new market, or null if the village was unknown.
     */
    VillageMarket update(String villageId, UnaryOperator<VillageMarket> fn);
}
