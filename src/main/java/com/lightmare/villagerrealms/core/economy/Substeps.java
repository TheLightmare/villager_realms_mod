package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.ai.sched.Markets;
import com.lightmare.villagerrealms.core.record.EconomicState;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Provenance;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.Vitals;
import com.lightmare.villagerrealms.core.role.Role;
import com.lightmare.villagerrealms.core.role.RoleItemSpec;
import com.lightmare.villagerrealms.core.role.RoleRegistry;

/**
 * Pure-function substep effects. These mutate records (returning new ones)
 * and side-effect village markets through the supplied {@link Markets}
 * handle. They do NOT decide *when* to fire — that is the active step
 * runtime's job at Tier 0/1 (entity-bound) and the abstract simulator's
 * job at Tier 2.
 *
 * Each method returns the new record (or the same record reference if
 * nothing changed) so callers can short-circuit a registry write.
 */
public final class Substeps {

    public static final float MAX_HUNGER = 20f;
    public static final float MAX_ENERGY = 1f;

    /**
     * Per-server-tick rate at which an idle NPC's hunger drains. With
     * MAX_HUNGER = 20, a fully-fed NPC starves to zero in 24000 ticks
     * (one Minecraft day) if never fed. Active runtime and abstract tick
     * both drain at this rate so transitions stay coherent.
     */
    public static final float HUNGER_DRAIN_PER_TICK = 1f / 1200f;

    /**
     * Per-tick energy decay. Energy is on a 0..1 scale; 1.0 → 0.0 over
     * exactly one Minecraft day. Workers will drift toward sleep over a
     * full day of activity if never rested.
     */
    public static final float ENERGY_DRAIN_PER_TICK = 1f / 24000f;

    /**
     * Per-tick energy restoration while sleeping in a bed. 0.0 → 1.0 in
     * 400 ticks (~20 seconds at 20 TPS). Faster than drain so a single
     * sleep cycle clearly resets the NPC.
     */
    public static final float ENERGY_RESTORE_PER_TICK = 1f / 400f;

    private Substeps() {}

    /**
     * Drain hunger and energy proportional to {@code elapsedTicks}. Returns
     * the same record if neither value changed (e.g. both already at zero).
     * Pure: no markets, no I/O, single record in / single record out.
     */
    public static NPCRecord drain(NPCRecord npc, long elapsedTicks) {
        if (elapsedTicks <= 0L) return npc;
        Vitals v = npc.vitals();
        float newHunger = Math.max(0f, v.hunger() - HUNGER_DRAIN_PER_TICK * elapsedTicks);
        float newEnergy = Math.max(0f, v.energy() - ENERGY_DRAIN_PER_TICK * elapsedTicks);
        if (newHunger == v.hunger() && newEnergy == v.energy()) return npc;
        Vitals nv = new Vitals(v.health(), newHunger, newEnergy, v.mood());
        return rebuild(npc, npc.inventory(), npc.economy(), nv);
    }

    /**
     * Restore energy proportional to {@code elapsedTicks}. Caps at
     * MAX_ENERGY. Used by the active sleep-at-bed handler and the abstract
     * off-work-time sleep cycle.
     */
    public static NPCRecord sleep(NPCRecord npc, long elapsedTicks) {
        if (elapsedTicks <= 0L) return npc;
        Vitals v = npc.vitals();
        if (v.energy() >= MAX_ENERGY) return npc;
        float restored = Math.min(MAX_ENERGY, v.energy() + ENERGY_RESTORE_PER_TICK * elapsedTicks);
        if (restored == v.energy()) return npc;
        Vitals nv = new Vitals(v.health(), v.hunger(), restored, v.mood());
        return rebuild(npc, npc.inventory(), npc.economy(), nv);
    }

    /** Eat one unit of {@code preferredFoodId} (or any food on hand). */
    public static NPCRecord consume(NPCRecord npc, String preferredFoodId, long tick) {
        NPCInventory inv = npc.inventory();
        String foodId = preferredFoodId;
        if (foodId == null || foodId.isEmpty() || InventoryOps.countOf(inv, foodId) <= 0) {
            foodId = InventoryOps.firstMatching(inv, Foods::isFood);
        }
        if (foodId == null) return npc;

        NPCInventory next = InventoryOps.remove(inv, foodId, 1);
        float restore = Foods.restoreFor(foodId);
        Vitals v = npc.vitals();
        float newHunger = Math.min(MAX_HUNGER, v.hunger() + restore);
        Vitals nv = new Vitals(v.health(), newHunger, v.energy(), v.mood());
        return rebuild(npc, next, npc.economy(), nv);
    }

    /**
     * One unit of work: pull {@code role.consumes()} from the village market
     * (no-op if any input is missing), deposit {@code role.produces()} into
     * the market, and credit the worker the produced unit's market price.
     */
    public static NPCRecord work(NPCRecord npc, Markets markets, long tick) {
        Role role = RoleRegistry.getOrLaborer(npc.role().roleId());
        if (!role.worker()) return npc;

        String villageId = npc.location().homeVillageId();
        if (villageId == null || villageId.isEmpty()) return npc;
        if (markets.get(villageId).isEmpty()) return npc;

        for (RoleItemSpec need : role.consumes()) {
            VillageMarket cur = markets.get(villageId).orElse(null);
            if (cur == null || cur.stockOf(need.itemId()) < need.count()) {
                return npc;
            }
        }
        for (RoleItemSpec need : role.consumes()) {
            markets.update(villageId, m -> MarketOps.withdraw(m, need.itemId(), need.count()));
        }

        long earned = 0L;
        for (RoleItemSpec out : role.produces()) {
            markets.update(villageId, m -> MarketOps.addStock(m, out.itemId(), out.count()));
            VillageMarket post = markets.get(villageId).orElse(null);
            long unitPrice = post == null
                    ? MarketPricing.basePrice(out.itemId())
                    : MarketPricing.currentPrice(post, out.itemId());
            earned += unitPrice * out.count();
        }
        if (earned <= 0L) return npc;

        final long pay = earned;
        markets.update(villageId, m -> MarketOps.adjustGold(m, -pay));
        EconomicState econ = npc.economy();
        EconomicState ne = new EconomicState(econ.gold() + pay, econ.debts(), econ.ownedProperty());
        return rebuild(npc, npc.inventory(), ne, npc.vitals());
    }

    /** Buy one unit of {@code itemId} from the village market. */
    public static NPCRecord buy(NPCRecord npc, String itemId, Markets markets, long tick) {
        if (itemId == null || itemId.isEmpty()) return npc;
        String villageId = npc.location().homeVillageId();
        if (villageId == null || villageId.isEmpty()) return npc;
        VillageMarket m = markets.get(villageId).orElse(null);
        if (m == null || m.stockOf(itemId) <= 0) return npc;

        long price = MarketPricing.currentPrice(m, itemId);
        if (npc.economy().gold() < price) return npc;

        markets.update(villageId, mm -> {
            mm = MarketOps.withdraw(mm, itemId, 1);
            mm = MarketOps.bumpDemand(mm, itemId, 1);
            mm = MarketOps.adjustGold(mm, price);
            return mm;
        });

        EconomicState econ = npc.economy();
        EconomicState ne = new EconomicState(
                econ.gold() - price, econ.debts(), econ.ownedProperty());
        NPCInventory ni = InventoryOps.add(npc.inventory(), itemId, 1, Provenance.BOUGHT, tick);
        return rebuild(npc, ni, ne, npc.vitals());
    }

    /** Take one unit out of the market without paying. */
    public static NPCRecord withdraw(NPCRecord npc, String itemId, Markets markets, long tick) {
        if (itemId == null || itemId.isEmpty()) return npc;
        String villageId = npc.location().homeVillageId();
        if (villageId == null || villageId.isEmpty()) return npc;
        VillageMarket m = markets.get(villageId).orElse(null);
        if (m == null || m.stockOf(itemId) <= 0) return npc;

        markets.update(villageId, mm -> MarketOps.withdraw(mm, itemId, 1));
        NPCInventory next = InventoryOps.add(npc.inventory(), itemId, 1, Provenance.GIFTED, tick);
        return rebuild(npc, next, npc.economy(), npc.vitals());
    }

    private static NPCRecord rebuild(NPCRecord r, NPCInventory inv, EconomicState e, Vitals v) {
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(),
                v, inv, e, r.role(),
                r.factionId(), r.relationships(), r.memory(), r.action());
    }
}
