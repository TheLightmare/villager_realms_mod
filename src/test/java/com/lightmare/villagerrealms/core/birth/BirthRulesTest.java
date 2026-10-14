package com.lightmare.villagerrealms.core.birth;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.ai.VillageView;
import com.lightmare.villagerrealms.core.consequence.Consequences;
import com.lightmare.villagerrealms.core.consequence.MemoryKinds;
import com.lightmare.villagerrealms.core.consequence.Refs;
import com.lightmare.villagerrealms.core.record.EconomicState;
import com.lightmare.villagerrealms.core.record.Gender;
import com.lightmare.villagerrealms.core.record.Identity;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Provenance;
import com.lightmare.villagerrealms.core.record.Relationships;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.VillageBed;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.core.record.VillageWorkstation;
import com.lightmare.villagerrealms.core.record.Vitals;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BirthRulesTest {

    // -------- fixtures ---------------------------------------------------

    private static NPCRecord adult(UUID id, int age) {
        NPCRecord r = Fixtures.npc(id, "v", 0, 0);
        return new NPCRecord(r.dataVersion(),
                new Identity(id, "Adult-" + id.toString().substring(0, 4), age, Gender.OTHER, r.identity().traits()),
                r.location(), new Vitals(20f, 20f, 1f, 0.5f), r.inventory(),
                new EconomicState(50L, List.of(), List.of()), r.role(), r.factionId(),
                Relationships.EMPTY, MemoryLog.empty(), r.action());
    }

    private static NPCRecord adult() { return adult(UUID.randomUUID(), 30); }

    private static NPCRecord withVitals(NPCRecord r, Vitals v) {
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(), v, r.inventory(),
                r.economy(), r.role(), r.factionId(), r.relationships(), r.memory(), r.action());
    }

    private static NPCRecord withRole(NPCRecord r, String role) {
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(), r.vitals(), r.inventory(),
                r.economy(), new RoleState(role, null, 0L), r.factionId(), r.relationships(), r.memory(), r.action());
    }

    private static VillageRecord village(List<VillageBed> beds, List<VillageWorkstation> ws) {
        return new VillageRecord(VillageRecord.CURRENT_VERSION, "v", "minecraft:overworld",
                0, 60, 0, 40, 90, 20, true, beds, ws, VillageMarket.EMPTY, "faction:v", List.of());
    }

    private static VillageView view(int freeBeds, int food) {
        return new VillageView() {
            @Override public String villageId() { return "v"; }
            @Override public int communalFoodUnits() { return food; }
            @Override public int freeBeds() { return freeBeds; }
            @Override public int marketFoodUnits() { return food; }
            @Override public String cheapestFoodInMarket() { return food > 0 ? "minecraft:bread" : null; }
            @Override public long marketPriceOf(String itemId) { return 3L; }
            @Override public long villageGold() { return 0L; }
        };
    }

    // -------- readiness ---------------------------------------------------

    @Test
    void readyWhenAdultFedRestedWithBedAndFood() {
        assertTrue(Readiness.isReady(adult(), view(1, 5), 1000L));
        assertEquals(1f, Readiness.score(adult(), view(1, 5), 1000L));
    }

    @Test
    void notReadyWithoutBedOrFoodOrView() {
        assertFalse(Readiness.isReady(adult(), view(0, 5), 1000L));
        assertFalse(Readiness.isReady(adult(), view(1, 0), 1000L));
        assertFalse(Readiness.isReady(adult(), null, 1000L));
    }

    @Test
    void notReadyWhenHungryTiredChildOrOutOfAgeRange() {
        assertFalse(Readiness.isReady(withVitals(adult(), new Vitals(20f, 10f, 1f, 0.5f)), view(1, 5), 1000L));
        assertFalse(Readiness.isReady(withVitals(adult(), new Vitals(20f, 20f, 0.2f, 0.5f)), view(1, 5), 1000L));
        assertFalse(Readiness.isReady(withRole(adult(), Births.CHILD_ROLE), view(1, 5), 1000L));
        assertFalse(Readiness.isReady(adult(UUID.randomUUID(), 12), view(1, 5), 1000L));
        assertFalse(Readiness.isReady(adult(UUID.randomUUID(), 70), view(1, 5), 1000L));
    }

    @Test
    void conceptionMemoryStartsACooldownThatExpires() {
        NPCRecord a = Consequences.remember(adult(), new MemoryEvent(
                1000L, MemoryKinds.CHILD_CONCEIVED, Refs.npc(UUID.randomUUID()), "x"));
        assertTrue(Readiness.inCooldown(a, 1000L + Births.CONCEIVE_COOLDOWN_TICKS - 1));
        assertFalse(Readiness.inCooldown(a, 1000L + Births.CONCEIVE_COOLDOWN_TICKS));
        assertFalse(Readiness.isReady(a, view(1, 5), 2000L));
        assertTrue(Readiness.isReady(a, view(1, 5), 1000L + Births.CONCEIVE_COOLDOWN_TICKS));
    }

    // -------- partners ----------------------------------------------------

    @Test
    void partnerIsAnotherEligibleVillagerPreferringMutualWarmth() {
        NPCRecord me = adult();
        NPCRecord cold = adult();
        NPCRecord warm = adult();
        NPCRecord child = withRole(adult(), Births.CHILD_ROLE);
        NPCRecord elsewhere = adult();
        elsewhere = new NPCRecord(elsewhere.dataVersion(), elsewhere.identity(),
                new com.lightmare.villagerrealms.core.record.Location("w", 0, 64, 0, "minecraft:overworld", Tier.ACTIVE),
                elsewhere.vitals(), elsewhere.inventory(), elsewhere.economy(), elsewhere.role(), elsewhere.factionId(),
                elsewhere.relationships(), elsewhere.memory(), elsewhere.action());
        NPCRecord meLikesWarm = Consequences.adjustPersonal(me, warm.identity().uuid(), 15);

        NPCRecord pick = Partners.choose(meLikesWarm, List.of(me, cold, warm, child, elsewhere), 1000L);
        assertNotNull(pick);
        assertEquals(warm.identity().uuid(), pick.identity().uuid());
    }

    @Test
    void partnerExcludesAnyoneWhoDislikesOrIsDislikedAndIsDeterministic() {
        NPCRecord me = adult();
        NPCRecord hater = Consequences.adjustPersonal(adult(), me.identity().uuid(), -5);
        NPCRecord a = adult(UUID.fromString("00000000-0000-0000-0000-00000000000a"), 30);
        NPCRecord b = adult(UUID.fromString("00000000-0000-0000-0000-00000000000b"), 30);

        NPCRecord pick1 = Partners.choose(me, List.of(hater, b, a), 1000L);
        NPCRecord pick2 = Partners.choose(me, List.of(a, hater, b), 1000L);
        assertEquals(a.identity().uuid(), pick1.identity().uuid(), "lowest uuid wins ties");
        assertEquals(pick1.identity().uuid(), pick2.identity().uuid(), "order-independent");

        NPCRecord meHates = Consequences.adjustPersonal(me, a.identity().uuid(), -1);
        assertEquals(b.identity().uuid(), Partners.choose(meHates, List.of(a, b), 1000L).identity().uuid());
        assertNull(Partners.choose(me, List.of(me, hater), 1000L));
    }

    // -------- conception --------------------------------------------------

    @Test
    void conceptionReservesABedSchedulesBirthAndStampsBothParents() {
        NPCRecord m = adult(), f = adult();
        VillageRecord v = village(List.of(
                new VillageBed(1, 64, 1, "farmer", m.identity().uuid()),
                new VillageBed(2, 64, 2, "baker", null)), List.of());
        UUID child = UUID.randomUUID();

        Conception.Plan plan = Conception.plan(m, f, v, 5000L, child);
        assertNotNull(plan);
        assertEquals(1, plan.bedIndex());
        assertEquals(child, plan.village().beds().get(1).reservedFor());
        assertEquals(0, plan.village().freeBedCount());

        assertEquals(ScheduleKinds.BIRTH, plan.birth().kind());
        assertEquals(5000L + Births.PREGNANCY_TICKS, plan.birth().dueTick());
        assertEquals(child, plan.birth().subject());
        assertEquals(m.identity().uuid(), plan.birth().uuidDatum(ScheduleKinds.DATA_MOTHER));
        assertEquals(f.identity().uuid(), plan.birth().uuidDatum(ScheduleKinds.DATA_FATHER));

        assertTrue(Readiness.inCooldown(plan.mother(), 5001L));
        assertTrue(Readiness.inCooldown(plan.father(), 5001L));
        assertEquals(Births.FAMILY_BOND, plan.mother().relationships().opinions().get(f.identity().uuid()));
        assertEquals(Births.FAMILY_BOND, plan.father().relationships().opinions().get(m.identity().uuid()));

        assertNull(Conception.plan(m, f, plan.village(), 5000L, UUID.randomUUID()), "no second free bed");
    }

    // -------- delivery ----------------------------------------------------

    @Test
    void deliveryClaimsTheBedBuildsTheChildTransfersGoldAndSchedulesAgeUp() {
        NPCRecord m = adult(), f = adult();
        UUID child = UUID.randomUUID();
        VillageRecord v = village(List.of(new VillageBed(7, 64, 9, "farmer", null)), List.of()).reserveBed(0, child);

        Delivery.Result r = Delivery.deliver(child, v, m, f, 9000L, Gender.FEMALE, Tier.NEARBY);
        assertNotNull(r);

        NPCRecord c = r.child();
        assertEquals(child, c.identity().uuid());
        assertEquals(0, c.identity().age());
        assertEquals(Gender.FEMALE, c.identity().gender());
        assertEquals(Births.CHILD_ROLE, c.role().roleId());
        assertNull(c.role().workplaceRef());
        assertEquals("v", c.location().homeVillageId());
        assertEquals(7.5, c.location().x(), 0.001);
        assertEquals(Tier.NEARBY, c.location().tier());
        assertEquals("faction:v", c.factionId());
        assertEquals(2 * Births.CHILD_GOLD_FROM_EACH_PARENT, c.economy().gold());
        assertEquals(50L - Births.CHILD_GOLD_FROM_EACH_PARENT, r.mother().economy().gold());
        assertEquals(50L - Births.CHILD_GOLD_FROM_EACH_PARENT, r.father().economy().gold());
        assertEquals(Provenance.GIFTED, c.inventory().items().get(0).provenance());
        assertEquals(Births.CHILD_STARTING_FOOD_COUNT, c.inventory().items().get(0).count());

        assertEquals(Births.FAMILY_BOND, c.relationships().opinions().get(m.identity().uuid()));
        assertEquals(Births.FAMILY_BOND, r.mother().relationships().opinions().get(child));
        assertEquals(2, c.memory().events().size());
        assertEquals(MemoryKinds.CHILD_BORN, r.father().memory().events().get(0).kind());

        assertEquals(child, r.village().beds().get(0).occupant());
        assertNull(r.village().beds().get(0).reservedFor());

        assertEquals(ScheduleKinds.AGE_UP, r.ageUp().kind());
        assertEquals(9000L + Births.CHILDHOOD_TICKS, r.ageUp().dueTick());
        assertEquals(child, r.ageUp().subject());
    }

    @Test
    void deliveryWorksWithDeadParentsAndFailsWithoutABed() {
        UUID child = UUID.randomUUID();
        VillageRecord v = village(List.of(new VillageBed(1, 64, 1, "farmer", null)), List.of()).reserveBed(0, child);
        Delivery.Result r = Delivery.deliver(child, v, null, null, 1L, null, null);
        assertNotNull(r);
        assertEquals(0L, r.child().economy().gold());
        assertEquals(Gender.OTHER, r.child().identity().gender());
        assertEquals(Tier.DORMANT, r.child().location().tier());
        assertTrue(r.child().relationships().opinions().isEmpty());
        assertNull(r.mother());

        VillageRecord full = village(List.of(new VillageBed(1, 64, 1, "farmer", UUID.randomUUID())), List.of());
        assertNull(Delivery.deliver(child, full, null, null, 1L, null, null));
    }

    // -------- age-up ------------------------------------------------------

    @Test
    void ageUpInheritsTheBedRoleAndNearestWorkstation() {
        UUID child = UUID.randomUUID();
        VillageRecord v = village(
                List.of(new VillageBed(1, 64, 1, "farmer", child)),
                List.of(new VillageWorkstation(30, 64, 30, "farmer"), new VillageWorkstation(3, 64, 3, "farmer"),
                        new VillageWorkstation(2, 64, 2, "butcher")));
        NPCRecord kid = Delivery.deliver(child, v.withBeds(List.of(new VillageBed(1, 64, 1, "farmer", null))).reserveBed(0, child),
                null, null, 1L, null, null).child();

        NPCRecord grown = AgeUp.apply(kid, v, 500L);
        assertEquals("farmer", grown.role().roleId());
        assertEquals("3,64,3", grown.role().workplaceRef(), "nearest farmer station");
        assertEquals(Births.ADULT_AGE, grown.identity().age());
        assertTrue(grown.identity().name().startsWith("Farmer-"));
        var last = grown.memory().events().get(grown.memory().events().size() - 1);
        assertEquals(MemoryKinds.ROLE_CHANGED, last.kind());
        assertEquals("child -> farmer", last.detail());
    }

    @Test
    void ageUpFallsBackToLaborerAtTheCentreWithoutABedRole() {
        UUID child = UUID.randomUUID();
        VillageRecord v = village(List.of(new VillageBed(1, 64, 1, "laborer", child)), List.of());
        NPCRecord kid = withRole(adult(child, 0), Births.CHILD_ROLE);
        NPCRecord grown = AgeUp.apply(kid, v, 500L);
        assertEquals("laborer", grown.role().roleId());
        assertEquals(v.centerX() + "," + v.groundY() + "," + v.centerZ(), grown.role().workplaceRef());

        NPCRecord noBed = AgeUp.apply(kid, village(List.of(), List.of()), 500L);
        assertEquals("laborer", noBed.role().roleId());
    }
}
