package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.ai.sched.Markets;
import com.lightmare.villagerrealms.core.persist.store.VillageStore;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * {@link Markets} adapter over a {@link VillageStore}. Reads come straight
 * off the in-memory map; writes rebuild the {@link VillageRecord} via
 * {@link VillageRecord#withMarket} and put it back, so the village's
 * dirty bit is set and the next autosave persists the change.
 */
public final class VillageStoreMarkets implements Markets {

    private final VillageStore villages;

    public VillageStoreMarkets(VillageStore villages) {
        if (villages == null) throw new IllegalArgumentException("villages required");
        this.villages = villages;
    }

    @Override
    public Optional<VillageMarket> get(String villageId) {
        return villages.get(villageId).map(VillageRecord::market);
    }

    @Override
    public VillageMarket update(String villageId, UnaryOperator<VillageMarket> fn) {
        VillageRecord rec = villages.get(villageId).orElse(null);
        if (rec == null) return null;
        VillageMarket next = fn.apply(rec.market());
        if (next == null || next == rec.market()) return rec.market();
        villages.put(rec.withMarket(next));
        return next;
    }
}
