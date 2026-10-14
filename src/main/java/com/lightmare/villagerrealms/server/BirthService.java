package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.ai.RecordVillageView;
import com.lightmare.villagerrealms.core.birth.AgeUp;
import com.lightmare.villagerrealms.core.birth.Births;
import com.lightmare.villagerrealms.core.birth.Conception;
import com.lightmare.villagerrealms.core.birth.Delivery;
import com.lightmare.villagerrealms.core.birth.Partners;
import com.lightmare.villagerrealms.core.birth.Readiness;
import com.lightmare.villagerrealms.core.birth.ScheduleKinds;
import com.lightmare.villagerrealms.core.persist.store.NPCRegistry;
import com.lightmare.villagerrealms.core.persist.store.ScheduleStore;
import com.lightmare.villagerrealms.core.record.Gender;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.ScheduledEvent;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Phase 3 step 11: births. Server-side driver for the pure rules in
 * {@code core/birth}:
 *
 *   - {@link #tryConceive}: the single conception path, used by the Tier 0
 *     {@code conceive} substep and by the Tier 2 sweep. Reserves a bed,
 *     schedules BIRTH, stamps both parents.
 *   - The schedule pump: every {@link #PUMP_INTERVAL_TICKS}, due BIRTH and
 *     AGE_UP events fire. A birth with no reclaimable bed is dropped (the
 *     reservation is what normally guarantees one). An age-up for a child
 *     that died is dropped.
 *   - The abstract sweep: villages with no ACTIVE NPC (so IAUS isn't
 *     running conceive) roll {@link Births#ABSTRACT_CONCEIVE_CHANCE} per
 *     ready adult per 100-tick cycle. Villages are staggered by id hash
 *     across the cycle so the work is smeared over ticks, like the
 *     abstract scheduler.
 *
 * Single-threaded; server tick.
 */
public final class BirthService {

    private static final Logger LOG = LoggerFactory.getLogger(BirthService.class);

    public static final int PUMP_INTERVAL_TICKS = 100;
    public static final int SWEEP_CYCLE_TICKS = 100;

    private static final Random RNG = new Random();
    private static long lastPumpTick = Long.MIN_VALUE;

    private BirthService() {}

    public static void reset() {
        lastPumpTick = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();

        if (now - lastPumpTick >= PUMP_INTERVAL_TICKS) {
            lastPumpTick = now;
            pump(svc, server, now);
        }
        abstractSweep(svc, now);
    }

    // -------- conception --------------------------------------------------

    /**
     * Try to make {@code mother} a parent now. Returns the partner's uuid on
     * success, null if not ready, no partner, or no free bed.
     */
    public static UUID tryConceive(PersistenceService svc, NPCRecord mother, long now) {
        String villageId = mother.location().homeVillageId();
        if (villageId == null || villageId.isEmpty()) return null;
        VillageRecord village = svc.villages().get(villageId).orElse(null);
        if (village == null) return null;
        if (!Readiness.isReady(mother, new RecordVillageView(village), now)) return null;

        NPCRecord father = Partners.choose(mother, svc.registry().byVillage(villageId), now);
        if (father == null) return null;

        Conception.Plan plan = Conception.plan(mother, father, village, now, UUID.randomUUID());
        if (plan == null) return null;

        svc.villages().put(plan.village());
        svc.schedule().put(plan.birth());
        svc.registry().put(plan.mother());
        svc.registry().put(plan.father());
        LOG.info("[vr] {} and {} of {} are expecting (child {} due at {})",
                mother.identity().name(), father.identity().name(), villageId,
                plan.childUuid(), plan.birth().dueTick());
        return father.identity().uuid();
    }

    // -------- schedule pump ----------------------------------------------

    private static void pump(PersistenceService svc, MinecraftServer server, long now) {
        ScheduleStore schedule = svc.schedule();
        for (ScheduledEvent ev : schedule.due(now)) {
            schedule.remove(ev.id());
            try {
                switch (ev.kind()) {
                    case ScheduleKinds.BIRTH -> deliver(svc, server, ev, now);
                    case ScheduleKinds.AGE_UP -> ageUp(svc, ev, now);
                    default -> LOG.warn("[vr] dropping scheduled event of unknown kind {}", ev.kind());
                }
            } catch (RuntimeException ex) {
                LOG.error("[vr] scheduled event {} ({}) failed: {}", ev.id(), ev.kind(), ex.toString(), ex);
            }
        }
    }

    private static void deliver(PersistenceService svc, MinecraftServer server, ScheduledEvent ev, long now) {
        NPCRegistry registry = svc.registry();
        VillageRecord village = ev.villageId() == null ? null : svc.villages().get(ev.villageId()).orElse(null);
        if (village == null) {
            LOG.warn("[vr] birth {} dropped: village {} is gone", ev.subject(), ev.villageId());
            return;
        }
        NPCRecord mother = registry.get(ev.uuidDatum(ScheduleKinds.DATA_MOTHER)).orElse(null);
        NPCRecord father = registry.get(ev.uuidDatum(ScheduleKinds.DATA_FATHER)).orElse(null);
        Tier tier = mother != null ? mother.location().tier()
                : father != null ? father.location().tier()
                : Tier.DORMANT;
        Gender gender = Gender.byOrdinal(RNG.nextInt(Gender.values().length));

        Delivery.Result r = Delivery.deliver(ev.subject(), village, mother, father, now, gender, tier);
        if (r == null) {
            LOG.warn("[vr] birth {} in {} dropped: no bed could be claimed", ev.subject(), ev.villageId());
            svc.villages().put(village.releaseReservation(ev.subject()));
            return;
        }

        svc.villages().put(r.village());
        registry.put(r.child());
        if (r.mother() != null) registry.put(r.mother());
        if (r.father() != null) registry.put(r.father());
        svc.schedule().put(r.ageUp());
        LOG.info("[vr] {} born in {} to {} and {}", r.child().identity().name(), ev.villageId(),
                mother == null ? "<dead>" : mother.identity().name(),
                father == null ? "<dead>" : father.identity().name());

        ServerLevel level = levelFor(server, r.village().dimension());
        NPCRecord child = r.child();
        if (level != null && level.hasChunk(child.location().chunkX(), child.location().chunkZ())) {
            Reconciler.spawnFromRecord(level, child);
        }
    }

    private static void ageUp(PersistenceService svc, ScheduledEvent ev, long now) {
        NPCRecord child = ev.subject() == null ? null : svc.registry().get(ev.subject()).orElse(null);
        if (child == null) return; // died in childhood; nothing to grow
        VillageRecord village = ev.villageId() == null ? null : svc.villages().get(ev.villageId()).orElse(null);
        NPCRecord grown = AgeUp.apply(child, village, now);
        svc.registry().put(grown);
        LOG.info("[vr] {} of {} grew up as {}", grown.identity().name(), ev.villageId(), grown.role().roleId());
    }

    // -------- Tier 2 sweep ------------------------------------------------

    private static void abstractSweep(PersistenceService svc, long now) {
        int slot = (int) Math.floorMod(now, (long) SWEEP_CYCLE_TICKS);
        for (VillageRecord v : new ArrayList<>(svc.villages().all())) {
            if (Math.floorMod(v.villageId().hashCode(), SWEEP_CYCLE_TICKS) != slot) continue;
            if (v.freeBedCount() == 0) continue;

            Collection<NPCRecord> villagers = svc.registry().byVillage(v.villageId());
            boolean anyActive = false, anyWarm = false;
            for (NPCRecord n : villagers) {
                Tier t = n.location().tier();
                if (t == Tier.ACTIVE) anyActive = true;
                if (t != Tier.COLD) anyWarm = true;
            }
            if (anyActive || !anyWarm) continue; // IAUS handles ACTIVE; COLD is frozen

            List<NPCRecord> candidates = new ArrayList<>(villagers);
            for (NPCRecord n : candidates) {
                if (RNG.nextFloat() >= Births.ABSTRACT_CONCEIVE_CHANCE) continue;
                NPCRecord fresh = svc.registry().get(n.identity().uuid()).orElse(null);
                if (fresh == null) continue;
                if (tryConceive(svc, fresh, now) != null) break; // one conception per village per cycle
            }
        }
    }

    private static ServerLevel levelFor(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) return level;
        }
        return null;
    }
}
