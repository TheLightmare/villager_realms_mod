package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.ai.Action;
import com.lightmare.villagerrealms.core.ai.ActionStep;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.Evaluators;
import com.lightmare.villagerrealms.core.ai.VillageView;
import com.lightmare.villagerrealms.core.economy.Substeps;
import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.VillageBed;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.entity.NPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tier 0/1 execution layer. For every loaded NPCEntity each tick:
 *
 *   1. Resolve the action + current substep from the record.
 *   2. {@code pathfind <target>}: drive the entity's navigation toward the
 *      target. When within {@link #ARRIVE_RADIUS_SQ}, advance the substep
 *      so the next visit runs the productive step.
 *   3. {@code consume / work / buy / withdraw}: only fire the effect when
 *      the entity is physically at the right place AND the throttle has
 *      elapsed. Effects come from {@link Substeps} (pure functions).
 *   4. {@code sleep / idle}: stand still.
 *
 * The scheduler-side StepExecutor never mutates economy/inventory/vitals
 * any more — those are entity-driven and live here. NPCs in unloaded
 * chunks have no entity, so this runtime simply skips them; their
 * eventual abstract simulation lives elsewhere (Tier 2).
 */
public final class ActiveStepRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveStepRuntime.class);

    /** How often the runtime drives each entity. */
    public static final int DRIVE_INTERVAL_TICKS = 10;
    /** How often {@code work} fires while the worker is at the workstation. */
    public static final int WORK_CADENCE_TICKS = 100;
    /** Same throttle for consume/buy/withdraw to avoid burning everything per tick. */
    public static final int ACTION_CADENCE_TICKS = 20;

    private static final double MOVE_SPEED = 0.6;
    private static final double ARRIVE_RADIUS_SQ = 2.5 * 2.5;

    /** Last tick each NPC fired a productive substep effect, keyed by UUID. */
    private static final Map<UUID, Long> lastFiredAt = new HashMap<>();
    /** Tracks the action key (id+startedAt) so cadence resets on action change. */
    private static final Map<UUID, String> currentActionKey = new HashMap<>();

    private static int counter;

    private ActiveStepRuntime() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        counter++;
        if (counter % DRIVE_INTERVAL_TICKS != 0) return;

        PersistenceService svc = PersistenceService.getOrNull();
        AIService ai = AIService.getOrNull();
        if (svc == null || ai == null) return;

        long tick = event.getServer().getTickCount();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof NPCEntity npc)) continue;
                drive(level, npc, svc, ai, tick);
            }
        }
    }

    /** Wipe transient state — call from tier transitions and shutdown. */
    public static void reset() {
        lastFiredAt.clear();
        currentActionKey.clear();
    }

    private static void drive(ServerLevel level, NPCEntity entity,
                              PersistenceService svc, AIService ai, long tick) {
        UUID id = entity.getUUID();
        NPCRecord rec = svc.registry().get(id).orElse(null);
        if (rec == null) return;

        // Drain hunger and energy before any substep effect runs. Drive
        // fires every DRIVE_INTERVAL_TICKS, so 10-tick worth of decay per
        // call. Same per-tick rate as AbstractTick → no jolt across tiers.
        NPCRecord drained = Substeps.drain(rec, DRIVE_INTERVAL_TICKS);
        if (drained != rec) {
            svc.registry().put(drained);
            rec = drained;
        }

        ActionState act = rec.action();
        if (act == null) return;

        // Reset cadence when the underlying action restarts (different id, or same
        // id but a fresh ActionState — startedAtTick changed).
        String key = act.actionId() + "@" + act.startedAtTick();
        String prior = currentActionKey.get(id);
        if (!key.equals(prior)) {
            currentActionKey.put(id, key);
            lastFiredAt.remove(id);
        }

        Action chosen = findAction(act.actionId());
        if (chosen == null) return;

        VillageView view = ai.viewFor(rec);
        EvalContext ctx = new EvalContext(rec, tick, level.getDayTime(), view);
        List<ActionStep> steps = chosen.plan(ctx);
        if (steps.isEmpty()) {
            entity.getNavigation().stop();
            return;
        }
        int idx = Math.min(act.subStep(), steps.size() - 1);
        ActionStep step = steps.get(idx);

        // Vanilla wake-up: if we entered the SLEEPING pose on a previous tick
        // but the current substep is no longer "sleep" (action switched, or
        // we're back to pathfind on a fresh sleep cycle), drop the pose
        // before routing — otherwise startSleeping's setPos clamp keeps us
        // glued to the bed during pathfind.
        if (entity.isSleeping() && !"sleep".equals(step.kind())) {
            entity.stopSleeping();
        }

        switch (step.kind()) {
            case "pathfind": handlePathfind(level, entity, rec, act, step, svc, idx, steps.size()); return;
            case "consume":  handleConsume(entity, rec, step, svc, ai, tick); return;
            case "work":     handleWork(entity, rec, svc, ai, tick); return;
            case "buy":      handleBuy(entity, rec, step, svc, ai, tick); return;
            case "withdraw": handleWithdraw(entity, rec, step, svc, ai, tick); return;
            case "sleep":    handleSleep(level, entity, rec, svc, tick); return;
            default:
                // idle / unknown: stand still.
                if (!entity.getNavigation().isDone()) entity.getNavigation().stop();
        }
    }

    // -------- pathfind ---------------------------------------------------

    private static void handlePathfind(ServerLevel level, NPCEntity entity,
                                       NPCRecord rec, ActionState act, ActionStep step,
                                       PersistenceService svc, int idx, int stepCount) {
        BlockPos target = resolveTarget(level, rec, step.target(), svc);
        if (target == null) {
            // No resolvable target — skip the pathfind and let the next step run.
            advanceSubstep(rec, act, idx + 1, svc);
            entity.getNavigation().stop();
            return;
        }
        double distSq = entity.distanceToSqr(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (distSq < ARRIVE_RADIUS_SQ) {
            entity.getNavigation().stop();
            if (idx < stepCount - 1) advanceSubstep(rec, act, idx + 1, svc);
            return;
        }
        if (entity.getNavigation().isInProgress()) return;
        boolean ok = entity.getNavigation().moveTo(
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5, MOVE_SPEED);
        if (!ok && LOG.isDebugEnabled()) {
            LOG.debug("[vr] moveTo({}) refused for {}", target, entity.getUUID());
        }
    }

    // -------- effect substeps -------------------------------------------

    private static void handleConsume(NPCEntity entity, NPCRecord rec, ActionStep step,
                                      PersistenceService svc, AIService ai, long tick) {
        if (!entity.getNavigation().isDone()) entity.getNavigation().stop();
        if (!cadenceElapsed(entity.getUUID(), tick, ACTION_CADENCE_TICKS)) return;
        NPCRecord next = Substeps.consume(rec, step.target(), tick);
        if (next != rec) {
            svc.registry().put(next);
            lastFiredAt.put(entity.getUUID(), tick);
        }
    }

    private static void handleWork(NPCEntity entity, NPCRecord rec,
                                   PersistenceService svc, AIService ai, long tick) {
        // Worker must be at their workstation. The workplaceRef is the
        // single source of truth for that location.
        BlockPos ws = parseCoords(rec.role().workplaceRef());
        if (ws == null) {
            if (!entity.getNavigation().isDone()) entity.getNavigation().stop();
            return;
        }
        double distSq = entity.distanceToSqr(ws.getX() + 0.5, ws.getY(), ws.getZ() + 0.5);
        if (distSq >= ARRIVE_RADIUS_SQ) {
            // Off-station: not working. Re-issue movement so they actually walk back.
            if (entity.getNavigation().isDone()) {
                entity.getNavigation().moveTo(ws.getX() + 0.5, ws.getY(), ws.getZ() + 0.5, MOVE_SPEED);
            }
            return;
        }
        if (!entity.getNavigation().isDone()) entity.getNavigation().stop();
        if (!cadenceElapsed(entity.getUUID(), tick, WORK_CADENCE_TICKS)) return;
        NPCRecord next = Substeps.work(rec, ai.markets(), tick);
        if (next != rec) {
            svc.registry().put(next);
        }
        lastFiredAt.put(entity.getUUID(), tick);
    }

    private static void handleBuy(NPCEntity entity, NPCRecord rec, ActionStep step,
                                  PersistenceService svc, AIService ai, long tick) {
        if (!atVillageCenter(entity, rec, svc)) return;
        if (!entity.getNavigation().isDone()) entity.getNavigation().stop();
        if (!cadenceElapsed(entity.getUUID(), tick, ACTION_CADENCE_TICKS)) return;
        NPCRecord next = Substeps.buy(rec, step.target(), ai.markets(), tick);
        if (next != rec) {
            svc.registry().put(next);
            lastFiredAt.put(entity.getUUID(), tick);
        }
    }

    private static void handleWithdraw(NPCEntity entity, NPCRecord rec, ActionStep step,
                                       PersistenceService svc, AIService ai, long tick) {
        if (!atVillageCenter(entity, rec, svc)) return;
        if (!entity.getNavigation().isDone()) entity.getNavigation().stop();
        if (!cadenceElapsed(entity.getUUID(), tick, ACTION_CADENCE_TICKS)) return;
        NPCRecord next = Substeps.withdraw(rec, step.target(), ai.markets(), tick);
        if (next != rec) {
            svc.registry().put(next);
            lastFiredAt.put(entity.getUUID(), tick);
        }
    }

    private static void handleSleep(ServerLevel level, NPCEntity entity, NPCRecord rec,
                                    PersistenceService svc, long tick) {
        // The bed assignment is the source of truth — if there's no assigned
        // bed (e.g. an audit hasn't run yet) we can't sleep visually.
        BlockPos bed = bedFor(villageOf(rec, svc), rec.identity().uuid());
        if (bed == null) {
            if (entity.isSleeping()) entity.stopSleeping();
            if (!entity.getNavigation().isDone()) entity.getNavigation().stop();
            return;
        }
        // Resolve to the head half so vanilla flips OCCUPIED on the right
        // block (and the entity lies in the part with the pillow). If the
        // bed has been broken since the audit, fall back to no-sleep.
        BlockPos head = resolveBedHead(level, bed);
        if (head == null) {
            if (entity.isSleeping()) entity.stopSleeping();
            return;
        }
        double distSq = entity.distanceToSqr(head.getX() + 0.5, head.getY(), head.getZ() + 0.5);
        if (distSq >= ARRIVE_RADIUS_SQ) {
            // Off-bed: drop the pose and walk home.
            if (entity.isSleeping()) entity.stopSleeping();
            if (entity.getNavigation().isDone()) {
                entity.getNavigation().moveTo(head.getX() + 0.5, head.getY(), head.getZ() + 0.5, MOVE_SPEED);
            }
            return;
        }
        if (!entity.getNavigation().isDone()) entity.getNavigation().stop();

        // At the bed: enter the vanilla SLEEPING pose. startSleeping sets
        // the bed's OCCUPIED state, snaps the entity to bed-center, zeros
        // motion, and broadcasts the pose to clients.
        if (!entity.isSleeping()) {
            entity.startSleeping(head);
        }

        if (!cadenceElapsed(entity.getUUID(), tick, ACTION_CADENCE_TICKS)) return;
        NPCRecord next = Substeps.sleep(rec, ACTION_CADENCE_TICKS);
        if (next != rec) {
            svc.registry().put(next);
        }
        lastFiredAt.put(entity.getUUID(), tick);
    }

    /**
     * Returns the head half of the bed at {@code pos}, or null if the block
     * is no longer a bed. Beds register a HOME POI on either half; vanilla
     * positions sleepers at the head, so we normalize before calling
     * {@code startSleeping}.
     */
    private static BlockPos resolveBedHead(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) return null;
        if (state.getValue(BedBlock.PART) == BedPart.HEAD) return pos;
        BlockPos other = pos.relative(state.getValue(BedBlock.FACING));
        BlockState otherState = level.getBlockState(other);
        if (!(otherState.getBlock() instanceof BedBlock)) return null;
        return other;
    }

    // -------- helpers ---------------------------------------------------

    private static boolean atVillageCenter(NPCEntity entity, NPCRecord rec, PersistenceService svc) {
        VillageRecord village = villageOf(rec, svc);
        if (village == null) return false;
        BlockPos c = centerOf(village);
        if (c == null) return false;
        return entity.distanceToSqr(c.getX() + 0.5, c.getY(), c.getZ() + 0.5) < ARRIVE_RADIUS_SQ;
    }

    private static boolean cadenceElapsed(UUID id, long tick, int cadence) {
        Long last = lastFiredAt.get(id);
        return last == null || tick - last >= cadence;
    }

    private static void advanceSubstep(NPCRecord rec, ActionState act, int newSub, PersistenceService svc) {
        ActionState next = new ActionState(
                act.actionId(), newSub, act.startedAtTick(), act.checkpointToken());
        NPCRecord updated = new NPCRecord(
                rec.dataVersion(), rec.identity(), rec.location(),
                rec.vitals(), rec.inventory(), rec.economy(), rec.role(),
                rec.factionId(), rec.relationships(), rec.memory(), next);
        svc.registry().put(updated);
    }

    private static Action findAction(String id) {
        for (Action a : Evaluators.defaultActions()) {
            if (a.id().equals(id)) return a;
        }
        return null;
    }

    private static BlockPos resolveTarget(ServerLevel level, NPCRecord rec,
                                          String spec, PersistenceService svc) {
        if (spec == null || spec.isEmpty()) return null;
        BlockPos coord = parseCoords(spec);
        if (coord != null) return coord;
        VillageRecord village = villageOf(rec, svc);
        switch (spec) {
            case "bed":    return bedFor(village, rec.identity().uuid());
            case "market": return centerOf(village);
            default:       return null;
        }
    }

    private static BlockPos parseCoords(String spec) {
        if (spec == null) return null;
        int c1 = spec.indexOf(',');
        if (c1 < 0) return null;
        int c2 = spec.indexOf(',', c1 + 1);
        if (c2 < 0) return null;
        try {
            int x = Integer.parseInt(spec.substring(0, c1).trim());
            int y = Integer.parseInt(spec.substring(c1 + 1, c2).trim());
            int z = Integer.parseInt(spec.substring(c2 + 1).trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static VillageRecord villageOf(NPCRecord rec, PersistenceService svc) {
        String vid = rec.location().homeVillageId();
        if (vid == null || vid.isEmpty()) return null;
        return svc.villages().get(vid).orElse(null);
    }

    private static BlockPos bedFor(VillageRecord village, UUID npcId) {
        if (village == null) return null;
        for (VillageBed b : village.beds()) {
            if (npcId.equals(b.occupant())) return new BlockPos(b.x(), b.y(), b.z());
        }
        return null;
    }

    private static BlockPos centerOf(VillageRecord village) {
        if (village == null) return null;
        int cx = (village.minX() + village.maxX()) / 2;
        int cy = (village.minY() + village.maxY()) / 2;
        int cz = (village.minZ() + village.maxZ()) / 2;
        return new BlockPos(cx, cy, cz);
    }
}
