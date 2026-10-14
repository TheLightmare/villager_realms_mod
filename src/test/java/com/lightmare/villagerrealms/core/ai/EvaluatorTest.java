package com.lightmare.villagerrealms.core.ai;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.ai.actions.ConceiveAction;
import com.lightmare.villagerrealms.core.ai.actions.EatAction;
import com.lightmare.villagerrealms.core.ai.actions.SleepAction;
import com.lightmare.villagerrealms.core.ai.actions.WorkAction;
import com.lightmare.villagerrealms.core.ai.cache.ConsiderationCache;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Vitals;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluatorTest {

    private static NPCRecord vitals(Vitals v) {
        NPCRecord base = Fixtures.npc(UUID.randomUUID(), "village", 0, 0);
        return new NPCRecord(
                base.dataVersion(), base.identity(), base.location(),
                v, base.inventory(), base.economy(), base.role(),
                base.factionId(), base.relationships(), base.memory(), base.action());
    }

    @Test
    void hungryNpcWithFoodEats() {
        var npc = vitals(new Vitals(20f, 1f, 1f, 0.5f));
        Decision d = Evaluators.full().evaluate(EvalContext.of(npc, 6000L), new ConsiderationCache());
        assertEquals(EatAction.ID, d.action().id());
    }

    @Test
    void exhaustedNpcSleepsAtNight() {
        // Sleep is now gated on vanilla bed timing — even an exhausted NPC
        // won't seek a bed during the day. 18000 = night.
        var npc = vitals(new Vitals(20f, 20f, 0.05f, 0.5f));
        Decision d = Evaluators.full().evaluate(EvalContext.of(npc, 18000L), new ConsiderationCache());
        assertEquals(SleepAction.ID, d.action().id());
    }

    @Test
    void exhaustedNpcDoesNotSleepAtMidday() {
        var npc = vitals(new Vitals(20f, 20f, 0.05f, 0.5f));
        Decision d = Evaluators.full().evaluate(EvalContext.of(npc, 6000L), new ConsiderationCache());
        // At midday the NightTimeConsideration zeros SleepAction utility,
        // so a tired-but-not-hungry NPC should NOT pick sleep.
        assertEquals(WorkAction.ID, d.action().id(),
                "tired NPC at midday with workplace should still work, not sleep");
    }

    @Test
    void wellFedRestedNpcWorksAtMidday() {
        var npc = vitals(new Vitals(20f, 20f, 1f, 0.5f));
        Decision d = Evaluators.full().evaluate(EvalContext.of(npc, 6000L), new ConsiderationCache());
        assertEquals(WorkAction.ID, d.action().id());
    }

    @Test
    void strippedAndFullPickSameOnEssentialPath() {
        var npc = vitals(new Vitals(20f, 1f, 1f, 0.5f));
        var ctx = EvalContext.of(npc, 6000L);
        var fullPick = Evaluators.full().evaluate(ctx, new ConsiderationCache());
        var stripPick = Evaluators.stripped().evaluate(ctx, new ConsiderationCache());
        assertEquals(fullPick.action().id(), stripPick.action().id());
    }

    private static VillageView readyVillage() {
        return new VillageView() {
            @Override public String villageId() { return "village"; }
            @Override public int communalFoodUnits() { return 5; }
            @Override public int freeBeds() { return 1; }
            @Override public int marketFoodUnits() { return 5; }
            @Override public String cheapestFoodInMarket() { return "minecraft:bread"; }
            @Override public long marketPriceOf(String itemId) { return 3L; }
            @Override public long villageGold() { return 0L; }
        };
    }

    @Test
    void fedRestedNpcWithAFreeBedConceivesAtNight() {
        var npc = vitals(new Vitals(20f, 20f, 1f, 0.5f)); // Fixtures: age 30, no memory cooldown
        Decision d = Evaluators.full().evaluate(new EvalContext(npc, 18000L, 18000L, readyVillage()), new ConsiderationCache());
        assertEquals(ConceiveAction.ID, d.action().id(), "nothing pressing at night → start a family");
    }

    @Test
    void conceiveNeverOutranksWorkAtMidday() {
        var npc = vitals(new Vitals(20f, 20f, 1f, 0.5f));
        Decision d = Evaluators.full().evaluate(new EvalContext(npc, 6000L, 6000L, readyVillage()), new ConsiderationCache());
        assertEquals(WorkAction.ID, d.action().id());
    }

    @Test
    void conceiveIsNotInTheStrippedSet() {
        boolean has = Evaluators.stripped().candidates().stream().anyMatch(a -> a.id().equals(ConceiveAction.ID));
        assertTrue(!has, "Tier-1 never conceives via IAUS; the abstract sweep covers it");
    }

    @Test
    void strippedDropsNonEssentialActions() {
        boolean hasWork = Evaluators.stripped().candidates().stream()
                .anyMatch(a -> a.id().equals(WorkAction.ID));
        assertTrue(!hasWork, "Tier-1 stripped evaluator must not include WorkAction");
    }
}
