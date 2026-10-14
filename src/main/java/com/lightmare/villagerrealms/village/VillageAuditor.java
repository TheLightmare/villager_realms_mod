package com.lightmare.villagerrealms.village;

import com.lightmare.villagerrealms.core.persist.store.FactionStore;
import com.lightmare.villagerrealms.core.persist.store.NPCRegistry;
import com.lightmare.villagerrealms.core.persist.store.VillageStore;
import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.DeathRecord;
import com.lightmare.villagerrealms.core.record.EconomicState;
import com.lightmare.villagerrealms.core.record.Faction;
import com.lightmare.villagerrealms.core.record.Gender;
import com.lightmare.villagerrealms.core.record.Identity;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Relationships;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.Traits;
import com.lightmare.villagerrealms.core.record.VillageBed;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.core.record.VillageWorkstation;
import com.lightmare.villagerrealms.core.record.Vitals;
import com.lightmare.villagerrealms.core.role.Role;
import com.lightmare.villagerrealms.core.role.RoleAssignment;
import com.lightmare.villagerrealms.core.role.RoleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scans a village structure's bounding box, builds a {@link VillageRecord}, and
 * emits one {@link NPCRecord} per bed.
 *
 * Idempotent: NPC UUIDs are derived from (villageId, bed pos) so re-running the
 * audit on the same village produces the same NPC identities.
 *
 * Beds and workstations are sourced from vanilla's {@link PoiManager} — the
 * same index villagers use for their schedules. This is O(POIs in bbox), not
 * O(blocks in bbox), so an audit costs ~1 ms instead of multiple seconds.
 */
public final class VillageAuditor {

    private static final Logger LOG = LoggerFactory.getLogger(VillageAuditor.class);

    private VillageAuditor() {}

    /** A pending village placeholder — bounds known, not yet scanned. */
    public static VillageRecord pending(String villageId, String dimension, BoundingBox bounds) {
        return new VillageRecord(
                VillageRecord.CURRENT_VERSION,
                villageId,
                dimension,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                false,
                java.util.List.of(),
                java.util.List.of(),
                VillageMarket.EMPTY,
                null,
                java.util.List.of());
    }

    /** Faction id stamped on a village's NPCs. v1: one faction per village. */
    public static String factionIdFor(String villageId) {
        return "faction:" + villageId;
    }

    public static VillageRecord audit(
            ServerLevel level,
            String villageId,
            BoundingBox bounds,
            VillageStore villages,
            NPCRegistry npcs,
            FactionStore factions) {

        long t0 = System.nanoTime();
        PoiManager poi = level.getPoiManager();
        VillageRecord prior = villages.get(villageId).orElse(null);

        List<BlockPos> beds = new ArrayList<>();
        List<WorkstationHit> workstations = new ArrayList<>();

        int fromCx = bounds.minX() >> 4;
        int toCx   = bounds.maxX() >> 4;
        int fromCz = bounds.minZ() >> 4;
        int toCz   = bounds.maxZ() >> 4;

        for (int cx = fromCx; cx <= toCx; cx++) {
            for (int cz = fromCz; cz <= toCz; cz++) {
                ChunkPos cpos = new ChunkPos(cx, cz);

                poi.getInChunk(h -> h.is(PoiTypes.HOME), cpos, PoiManager.Occupancy.ANY)
                        .forEach(rec -> {
                            BlockPos p = rec.getPos();
                            if (bounds.isInside(p)) beds.add(p.immutable());
                        });

                poi.getInChunk(h -> RoleMapping.roleForPoi(h) != null, cpos, PoiManager.Occupancy.ANY)
                        .forEach(rec -> {
                            BlockPos p = rec.getPos();
                            if (!bounds.isInside(p)) return;
                            String role = RoleMapping.roleForPoi(rec.getPoiType());
                            if (role != null) workstations.add(new WorkstationHit(p.immutable(), role));
                        });
            }
        }

        // Stable iteration order so audits are repeatable.
        workstations.sort(Comparator
                .comparingInt((WorkstationHit w) -> w.pos.getY())
                .thenComparingInt(w -> w.pos.getX())
                .thenComparingInt(w -> w.pos.getZ()));
        beds.sort(Comparator
                .comparingInt((BlockPos b) -> b.getY())
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));

        Set<BlockPos> taken = new HashSet<>();
        List<VillageBed> bedRecords = new ArrayList<>(beds.size());
        List<BedAssignment> assignments = new ArrayList<>(beds.size());

        for (WorkstationHit ws : workstations) {
            BlockPos best = null;
            double bestSq = Double.MAX_VALUE;
            for (BlockPos bed : beds) {
                if (taken.contains(bed)) continue;
                double d = bed.distSqr(ws.pos);
                if (d < bestSq) { bestSq = d; best = bed; }
            }
            if (best != null) {
                taken.add(best);
                assignments.add(new BedAssignment(best, ws.role, ws.pos));
            }
        }
        for (BlockPos bed : beds) {
            if (!taken.contains(bed)) {
                assignments.add(new BedAssignment(bed, RoleRegistry.LABORER, null));
            }
        }
        assignments = finalizeRoles(assignments, beds, bounds);

        String dim = level.dimension().location().toString();
        String factionId = factionIdFor(villageId);
        ensureFaction(factions, factionId, villageId);

        for (BedAssignment a : assignments) {
            UUID uuid = bedUuid(villageId, a.bed);
            if (prior != null && prior.isDead(uuid)) {
                // The NPC born to this bed has died; the bed stays free.
                bedRecords.add(new VillageBed(a.bed.getX(), a.bed.getY(), a.bed.getZ(), a.role, null));
                continue;
            }
            bedRecords.add(new VillageBed(
                    a.bed.getX(), a.bed.getY(), a.bed.getZ(), a.role, uuid));

            if (npcs.get(uuid).isPresent()) continue; // idempotent re-audit

            String workplaceRef = a.workstation == null
                    ? null
                    : a.workstation.getX() + "," + a.workstation.getY() + "," + a.workstation.getZ();
            String name = roleName(a.role) + "-" + uuid.toString().substring(0, 6);

            NPCRecord npc = new NPCRecord(
                    NPCRecord.CURRENT_VERSION,
                    new Identity(uuid, name, 30, Gender.OTHER, Traits.NEUTRAL),
                    new Location(villageId,
                            a.bed.getX() + 0.5, a.bed.getY(), a.bed.getZ() + 0.5,
                            dim, Tier.ACTIVE),
                    Vitals.FRESH,
                    NPCInventory.EMPTY,
                    EconomicState.ZERO,
                    new RoleState(a.role, workplaceRef, 0L),
                    factionId,
                    Relationships.EMPTY,
                    MemoryLog.empty(),
                    ActionState.IDLE);
            npcs.put(npc);
        }

        List<VillageWorkstation> wsRecords = new ArrayList<>(workstations.size());
        for (WorkstationHit w : workstations) {
            wsRecords.add(new VillageWorkstation(
                    w.pos.getX(), w.pos.getY(), w.pos.getZ(), w.role));
        }

        VillageMarket market = prior != null ? prior.market() : VillageMarket.EMPTY;
        List<DeathRecord> deaths = prior != null ? prior.deaths() : List.of();
        VillageRecord village = new VillageRecord(
                VillageRecord.CURRENT_VERSION,
                villageId,
                dim,
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ(),
                true,
                bedRecords,
                wsRecords,
                market,
                factionId,
                deaths);
        villages.put(village);

        LOG.info("[vr] Audited village {}: {} beds ({} assigned), {} workstations, took {} ms",
                villageId, bedRecords.size(),
                workstations.size() - leftover(workstations.size(), bedRecords.size()),
                workstations.size(),
                (System.nanoTime() - t0) / 1_000_000);
        return village;
    }

    private static int leftover(int wsCount, int bedCount) {
        return Math.max(0, wsCount - bedCount);
    }

    /**
     * Second pass over the POI-driven assignment:
     *   1. Derive bakers from leftover beds ({@link RoleAssignment}) — vanilla
     *      has no baker workstation, and wheat needs a consumer.
     *   2. Resolve each NPC's workplace. Roles with a workstation kind keep
     *      the POI they were matched to; everything else (bakers, laborers,
     *      the non-food professions that forage) works at the village centre
     *      at ground level, so the active runtime can actually arrive there.
     */
    private static List<BedAssignment> finalizeRoles(
            List<BedAssignment> assignments, List<BlockPos> beds, BoundingBox bounds) {
        List<String> roleIds = new ArrayList<>(assignments.size());
        for (BedAssignment a : assignments) roleIds.add(a.role);
        List<String> derived = RoleAssignment.deriveBakers(roleIds);

        int[] ys = new int[beds.size()];
        for (int i = 0; i < ys.length; i++) ys[i] = beds.get(i).getY();
        java.util.Arrays.sort(ys);
        int groundY = ys.length == 0 ? bounds.minY() : ys[ys.length / 2];
        BlockPos centre = new BlockPos(
                (bounds.minX() + bounds.maxX()) / 2, groundY, (bounds.minZ() + bounds.maxZ()) / 2);

        List<BedAssignment> out = new ArrayList<>(assignments.size());
        for (int i = 0; i < assignments.size(); i++) {
            BedAssignment a = assignments.get(i);
            String role = derived.get(i);
            Role def = RoleRegistry.getOrLaborer(role);
            boolean usesStation = !def.workstationKind().isEmpty() && a.workstation != null;
            BlockPos workplace = usesStation ? a.workstation : (def.worker() ? centre : null);
            out.add(new BedAssignment(a.bed, role, workplace));
        }
        return out;
    }

    /**
     * Idempotent: if the faction already exists, expand its claim set to
     * include this village. Otherwise create it.
     */
    private static void ensureFaction(FactionStore factions, String factionId, String villageId) {
        Faction prior = factions.get(factionId).orElse(null);
        if (prior == null) {
            factions.put(new Faction(
                    Faction.CURRENT_VERSION,
                    factionId,
                    villageId,
                    null,
                    java.util.Set.of(villageId)));
            return;
        }
        if (prior.claimedVillageIds().contains(villageId)) return;
        var nextClaims = new java.util.HashSet<>(prior.claimedVillageIds());
        nextClaims.add(villageId);
        factions.put(prior.withClaimedVillages(nextClaims));
    }

    public static String villageIdFor(String dimension, BoundingBox bounds) {
        return "village_" + dimension.replace(':', '_')
                + "_" + bounds.minX() + "_" + bounds.minY() + "_" + bounds.minZ();
    }

    private static UUID bedUuid(String villageId, BlockPos bed) {
        String key = "vr-bed:" + villageId + ":" + bed.getX() + ":" + bed.getY() + ":" + bed.getZ();
        return UUID.nameUUIDFromBytes(key.getBytes());
    }

    private static String roleName(String role) {
        if (role == null || role.isEmpty()) return "NPC";
        return Character.toUpperCase(role.charAt(0)) + role.substring(1);
    }

    private record WorkstationHit(BlockPos pos, String role) {}
    private record BedAssignment(BlockPos bed, String role, BlockPos workstation) {}
}
