package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.ai.sched.Markets;
import com.lightmare.villagerrealms.core.record.VillageMarket;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** In-memory {@link Markets} implementation for unit tests. */
public final class MapMarkets implements Markets {

    private final Map<String, VillageMarket> byId = new HashMap<>();

    public void put(String villageId, VillageMarket market) {
        byId.put(villageId, market);
    }

    @Override
    public Optional<VillageMarket> get(String villageId) {
        return Optional.ofNullable(byId.get(villageId));
    }

    @Override
    public VillageMarket update(String villageId, UnaryOperator<VillageMarket> fn) {
        VillageMarket cur = byId.get(villageId);
        if (cur == null) return null;
        VillageMarket next = fn.apply(cur);
        if (next != null && next != cur) {
            byId.put(villageId, next);
            return next;
        }
        return cur;
    }
}
