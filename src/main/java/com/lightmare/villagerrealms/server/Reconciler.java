package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.persist.store.NPCRegistry;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.entity.NPCEntity;
import com.lightmare.villagerrealms.entity.NPCEntityType;
import com.lightmare.villagerrealms.entity.Projector;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.village.VillageAuditor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles entity-world state with the record registry.
 *
 *   Entity exists, no record  -> orphan, despawn (don't synthesize a placeholder).
 *   Record claims this chunk, no entity -> spawn from record (self-healing).
 *   Both exist -> project record onto entity (record is source of truth).
 *
 * Also: cancels vanilla Villager joins (overrides vanilla village population)
 * and triggers village audits when a #minecraft:village structure's bbox is
 * fully chunk-loaded.
 */
public final class Reconciler {

    private static final Logger LOG = LoggerFactory.getLogger(Reconciler.class);

    /** villageIds whose audit task is already in the server tick queue. */
    private static final Set<String> auditScheduled = new HashSet<>();

    private Reconciler() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity e = event.getEntity();

        if (e.getType() == EntityType.VILLAGER) {
            event.setCanceled(true);
            return;
        }

        if (!(e instanceof NPCEntity npc)) return;
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;

        NPCRecord rec = svc.registry().get(npc.getUUID()).orElse(null);
        if (rec == null) {
            LOG.warn("[vr] Despawning orphan NPCEntity {} — no matching record", npc.getUUID());
            event.setCanceled(true);
            return;
        }
        rec = applyCatchUp(svc, rec, event.getLevel().getGameTime());
        Projector.project(rec, npc);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof NPCEntity npc)) return;
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;

        NPCRecord prior = svc.registry().get(npc.getUUID()).orElse(null);
        if (prior == null) return;
        NPCRecord updated = Projector.extract(prior, npc);
        if (!updated.equals(prior)) {
            svc.registry().put(updated);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;

        ChunkAccess chunk = event.getChunk();
        ChunkPos pos = chunk.getPos();

        auditNewVillages(level, chunk, svc);
        spawnMissingForChunk(level, pos, svc);
    }

    private static void auditNewVillages(ServerLevel level, ChunkAccess chunk, PersistenceService svc) {
        // Phase 1: register pending entries for any village starts in THIS chunk.
        Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (!starts.isEmpty()) {
            Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            String dim = level.dimension().location().toString();

            for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                Structure structure = entry.getKey();
                if (!structures.wrapAsHolder(structure).is(StructureTags.VILLAGE)) continue;

                BoundingBox bounds = entry.getValue().getBoundingBox();
                String villageId = VillageAuditor.villageIdFor(dim, bounds);
                if (svc.villages().get(villageId).isPresent()) continue;

                LOG.info("[vr] new pending village {} bbox=({},{},{})-({},{},{}) span=({}x{}x{})",
                        villageId,
                        bounds.minX(), bounds.minY(), bounds.minZ(),
                        bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                        bounds.maxX() - bounds.minX() + 1,
                        bounds.maxY() - bounds.minY() + 1,
                        bounds.maxZ() - bounds.minZ() + 1);
                svc.villages().put(VillageAuditor.pending(villageId, dim, bounds));
            }
        }

        // Phase 2: schedule audits for villages whose bbox chunks are now all loaded.
        // Beds and workstations come from PoiManager (sourced from those chunks), so
        // we still gate on full coverage to avoid missing beds. Each village's audit
        // is scheduled at most once via the auditScheduled guard.
        String thisDim = level.dimension().location().toString();
        for (VillageRecord pending : new ArrayList<>(svc.villages().all())) {
            if (pending.audited()) continue;
            if (!pending.dimension().equals(thisDim)) continue;
            if (auditScheduled.contains(pending.villageId())) continue;
            if (!allChunksLoaded(level, pending)) continue;

            auditScheduled.add(pending.villageId());
            final VillageRecord toAudit = pending;
            // Defer off the chunk-load tick. The audit itself is cheap with POI,
            // but doing entity work mid-ChunkEvent.Load is fragile (see spawnMissingForChunk).
            level.getServer().execute(() -> runAudit(level, svc, toAudit.villageId()));
        }
    }

    private static void runAudit(ServerLevel level, PersistenceService svc, String villageId) {
        try {
            VillageRecord latest = svc.villages().get(villageId).orElse(null);
            if (latest == null || latest.audited()) return;
            if (!allChunksLoaded(level, latest)) {
                // Chunks unloaded between scheduling and now — let a future
                // chunk-load re-trigger the audit.
                return;
            }

            BoundingBox bounds = new BoundingBox(
                    latest.minX(), latest.minY(), latest.minZ(),
                    latest.maxX(), latest.maxY(), latest.maxZ());
            VillageAuditor.audit(level, latest.villageId(), bounds,
                    svc.villages(), svc.registry(), svc.factions());

            int fromCx = latest.minX() >> 4;
            int toCx   = latest.maxX() >> 4;
            int fromCz = latest.minZ() >> 4;
            int toCz   = latest.maxZ() >> 4;
            for (int cx = fromCx; cx <= toCx; cx++) {
                for (int cz = fromCz; cz <= toCz; cz++) {
                    spawnMissingForChunk(level, new ChunkPos(cx, cz), svc);
                }
            }
        } finally {
            auditScheduled.remove(villageId);
        }
    }

    private static boolean allChunksLoaded(ServerLevel level, VillageRecord v) {
        int fromCx = v.minX() >> 4;
        int toCx   = v.maxX() >> 4;
        int fromCz = v.minZ() >> 4;
        int toCz   = v.maxZ() >> 4;
        for (int cx = fromCx; cx <= toCx; cx++) {
            for (int cz = fromCz; cz <= toCz; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    private static void spawnMissingForChunk(ServerLevel level, ChunkPos pos, PersistenceService svc) {
        String dim = level.dimension().location().toString();
        NPCRegistry reg = svc.registry();
        var records = reg.byChunk(pos.x, pos.z);
        if (records.isEmpty()) return;
        for (NPCRecord rec : records) {
            if (!rec.location().dimension().equals(dim)) continue;
            if (level.getEntity(rec.identity().uuid()) != null) continue;
            // Defer to next tick: addFreshEntity during ChunkEvent.Load adds the entity
            // to the level's UUID map but the chunk's entity section is mid-transition,
            // so client tracking doesn't pick it up. Running on a clean tick fixes this.
            final NPCRecord recRef = rec;
            level.getServer().execute(() -> {
                if (level.getEntity(recRef.identity().uuid()) != null) return;
                spawnFromRecord(level, recRef);
            });
        }
    }

    /**
     * Spawn an entity for an existing record. Caller must have already put the record
     * into the registry — otherwise the EntityJoinLevelEvent handler will cancel it
     * as an orphan.
     */
    public static NPCEntity spawnFromRecord(ServerLevel level, NPCRecord rec) {
        NPCEntity npc = NPCEntityType.NPC.get().create(level);
        if (npc == null) {
            LOG.error("[vr] EntityType.create returned null for NPC {}", rec.identity().uuid());
            return null;
        }
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc != null) {
            rec = applyCatchUp(svc, rec, level.getGameTime());
        }
        Projector.project(rec, npc);
        if (!level.addFreshEntity(npc)) return null;
        return npc;
    }

    /**
     * Run any overdue Tier 2 abstract cycles for {@code rec} and commit the
     * result. Returns the post-catch-up record (or the input if no catch-up
     * applied). Called at every projection site so a freshly-loaded entity
     * is never built from a stale dormant snapshot.
     *
     * Today tier assignment is essentially static so this is mostly a no-op
     * — Phase 2 step 10 (dynamic tiering) makes Tier 2 → Tier 0 transitions
     * a real event, and this hook is what keeps that transition coherent.
     */
    private static NPCRecord applyCatchUp(PersistenceService svc, NPCRecord rec, long tick) {
        AIService ai = AIService.getOrNull();
        if (ai == null) return rec;
        NPCRecord updated = ai.catchUp(rec, tick);
        if (updated != null && updated != rec) {
            svc.registry().put(updated);
            return updated;
        }
        return rec;
    }
}
