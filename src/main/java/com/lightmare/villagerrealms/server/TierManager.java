package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.ai.sched.TierPolicy;
import com.lightmare.villagerrealms.core.persist.store.NPCRegistry;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 2 step 10: dynamic tiering.
 *
 * Periodically sweeps every known village and assigns one {@link Tier} to the
 * whole village from its chunk-load state and player proximity, then pins all
 * the village's NPCs to that tier. Per CLAUDE.md tiering is village-scoped,
 * not per-NPC-position, so groups stay coherent — the entire village promotes
 * and demotes together.
 *
 * The tier field is the interlock that makes the rest of the substrate run:
 *   - ACTIVE / NEARBY records are picked up by the {@code AIScheduler} (and,
 *     when an entity exists, driven by {@link ActiveStepRuntime}).
 *   - DORMANT records are picked up by the {@code AbstractScheduler}.
 *   - COLD records are simulated by neither; they're frozen until a chunk
 *     load wakes them via the catch-up path in {@link Reconciler}.
 *
 * Before this class existed, every NPC sat permanently at ACTIVE, so the
 * abstract simulator (which only runs DORMANT NPCs) never fired in practice.
 *
 * Catch-up on promotion (Tier 2 -> Tier 0) is NOT done here: it happens at the
 * entity-projection site in {@link Reconciler}, which is driven by chunk load.
 * This class only owns the tier field.
 *
 * Single-threaded; runs on the server tick.
 */
public final class TierManager {

    private static final Logger LOG = LoggerFactory.getLogger(TierManager.class);

    /** How often the sweep runs. Tiering doesn't need per-tick precision. */
    public static final int SWEEP_INTERVAL_TICKS = 40;

    /** A loaded village with a player within this block radius is ACTIVE. */
    public static final double ACTIVE_RADIUS = 96.0;

    /** Continuous dormancy after which a village is demoted DORMANT -> COLD (1h). */
    public static final long COLD_AFTER_TICKS = 72_000L;

    /** villageId -> gameTime at which it last became unloaded. Cleared when loaded. */
    private static final Map<String, Long> dormantSince = new HashMap<>();

    /** Tier histogram from the most recent sweep, indexed by {@link Tier#ordinal()}. */
    private static final int[] lastTierCounts = new int[Tier.values().length];

    private static int counter;

    private TierManager() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        counter++;
        if (counter % SWEEP_INTERVAL_TICKS != 0) return;

        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;

        sweep(event.getServer(), svc);
    }

    /** Wipe transient state — call on shutdown so a fresh world doesn't inherit it. */
    public static void reset() {
        dormantSince.clear();
        counter = 0;
        for (int i = 0; i < lastTierCounts.length; i++) lastTierCounts[i] = 0;
    }

    /** Tier histogram from the last sweep (copy), for observability. */
    public static int[] tierCounts() {
        return lastTierCounts.clone();
    }

    private static void sweep(MinecraftServer server, PersistenceService svc) {
        Map<String, ServerLevel> levels = levelsByDimension(server);
        long now = server.overworld().getGameTime();
        NPCRegistry registry = svc.registry();

        for (int i = 0; i < lastTierCounts.length; i++) lastTierCounts[i] = 0;

        for (VillageRecord v : svc.villages().all()) {
            ServerLevel level = levels.get(v.dimension());
            boolean loaded = level != null && centerLoaded(level, v);

            double nearestSq;
            long dormantTicks;
            if (loaded) {
                dormantSince.remove(v.villageId());
                nearestSq = nearestPlayerDistSq(level, v);
                dormantTicks = 0L;
            } else {
                long since = dormantSince.computeIfAbsent(v.villageId(), k -> now);
                dormantTicks = now - since;
                nearestSq = -1.0;
            }

            Tier target = TierPolicy.resolve(
                    loaded, nearestSq, ACTIVE_RADIUS, dormantTicks, COLD_AFTER_TICKS);

            applyTier(registry, v.villageId(), target);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("[vr] tier sweep @ {}: active={} nearby={} dormant={} cold={}",
                    now,
                    lastTierCounts[Tier.ACTIVE.ordinal()],
                    lastTierCounts[Tier.NEARBY.ordinal()],
                    lastTierCounts[Tier.DORMANT.ordinal()],
                    lastTierCounts[Tier.COLD.ordinal()]);
        }
    }

    /**
     * Pin every NPC of {@code villageId} to {@code target}, writing back only
     * those whose tier actually changed. {@code byVillage} returns a fresh
     * list, so put() during iteration is safe.
     */
    private static void applyTier(NPCRegistry registry, String villageId, Tier target) {
        for (NPCRecord rec : registry.byVillage(villageId)) {
            Location loc = rec.location();
            lastTierCounts[target.ordinal()]++;
            if (loc.tier() == target) continue;
            registry.put(rec.withLocation(loc.withTier(target)));
        }
    }

    private static boolean centerLoaded(ServerLevel level, VillageRecord v) {
        int cx = ((v.minX() + v.maxX()) / 2) >> 4;
        int cz = ((v.minZ() + v.maxZ()) / 2) >> 4;
        return level.hasChunk(cx, cz);
    }

    private static double nearestPlayerDistSq(ServerLevel level, VillageRecord v) {
        double cx = (v.minX() + v.maxX()) / 2.0 + 0.5;
        double cy = (v.minY() + v.maxY()) / 2.0;
        double cz = (v.minZ() + v.maxZ()) / 2.0 + 0.5;
        double best = -1.0;
        for (ServerPlayer p : level.players()) {
            double dx = p.getX() - cx;
            double dy = p.getY() - cy;
            double dz = p.getZ() - cz;
            double d = dx * dx + dy * dy + dz * dz;
            if (best < 0.0 || d < best) best = d;
        }
        return best;
    }

    private static Map<String, ServerLevel> levelsByDimension(MinecraftServer server) {
        Map<String, ServerLevel> out = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            out.put(level.dimension().location().toString(), level);
        }
        return out;
    }
}
