package com.lightmare.villagerrealms.core.record;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record VillageRecord(
        int dataVersion,
        String villageId,
        String dimension,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        boolean audited,
        List<VillageBed> beds,
        List<VillageWorkstation> workstations,
        VillageMarket market,
        String factionOwnerId,
        List<DeathRecord> deaths
) implements Versioned {

    public static final int CURRENT_VERSION = 5;

    /**
     * Ring cap on the death ledger. Old deaths roll off; the ledger exists
     * for memory/observability and to stop a re-audit repopulating a dead
     * NPC's bed, and both only need recent history.
     */
    public static final int MAX_DEATHS = 256;

    public VillageRecord {
        if (villageId == null) throw new IllegalArgumentException("villageId required");
        if (dimension == null) throw new IllegalArgumentException("dimension required");
        if (beds == null) throw new IllegalArgumentException("beds required");
        if (workstations == null) throw new IllegalArgumentException("workstations required");
        if (market == null) market = VillageMarket.EMPTY;
        if (deaths == null) deaths = List.of();
        if (deaths.size() > MAX_DEATHS) deaths = deaths.subList(deaths.size() - MAX_DEATHS, deaths.size());
        beds = List.copyOf(beds);
        workstations = List.copyOf(workstations);
        deaths = List.copyOf(deaths);
    }

    /** Block X of the bounding-box centre. */
    public int centerX() { return (minX + maxX) / 2; }

    /** Block Z of the bounding-box centre. */
    public int centerZ() { return (minZ + maxZ) / 2; }

    /**
     * Ground level for "go to the village centre" targets: the median bed Y.
     * Beds sit on floors at walking height, whereas the bbox Y-midpoint is
     * usually mid-air (structures span foundations to roofs), which an NPC
     * can never get within arrival radius of. Falls back to {@code minY}
     * when there are no beds.
     */
    public int groundY() {
        return groundYOf(beds, minY);
    }

    public static int groundYOf(List<VillageBed> beds, int fallback) {
        if (beds.isEmpty()) return fallback;
        int[] ys = new int[beds.size()];
        for (int i = 0; i < ys.length; i++) ys[i] = beds.get(i).y();
        java.util.Arrays.sort(ys);
        return ys[ys.length / 2];
    }

    /** True if this village has buried the NPC with that uuid. */
    public boolean isDead(UUID uuid) {
        if (uuid == null) return false;
        for (DeathRecord d : deaths) if (uuid.equals(d.uuid())) return true;
        return false;
    }

    /**
     * Bury an NPC: free the bed they occupied and append to the ledger. The
     * registry removal is the caller's job (different store).
     */
    public VillageRecord recordDeath(DeathRecord death) {
        List<VillageBed> nextBeds = new ArrayList<>(beds.size());
        for (VillageBed b : beds) {
            nextBeds.add(death.uuid().equals(b.occupant()) ? b.withOccupant(null) : b);
        }
        List<DeathRecord> nextDeaths = new ArrayList<>(deaths.size() + 1);
        nextDeaths.addAll(deaths);
        nextDeaths.add(death);
        return new VillageRecord(
                dataVersion, villageId, dimension,
                minX, minY, minZ, maxX, maxY, maxZ,
                audited, nextBeds, workstations, market, factionOwnerId, nextDeaths);
    }

    // -------- beds: occupancy and reservation ---------------------------

    /** Index of the first bed with neither occupant nor reservation, or -1. */
    public int firstFreeBedIndex() {
        for (int i = 0; i < beds.size(); i++) if (beds.get(i).isFree()) return i;
        return -1;
    }

    public int freeBedCount() {
        int n = 0;
        for (VillageBed b : beds) if (b.isFree()) n++;
        return n;
    }

    /** Index of the bed {@code uuid} occupies, or -1. */
    public int bedIndexOf(UUID uuid) {
        if (uuid == null) return -1;
        for (int i = 0; i < beds.size(); i++) if (uuid.equals(beds.get(i).occupant())) return i;
        return -1;
    }

    /** Index of the bed reserved for unborn {@code child}, or -1. */
    public int reservedBedIndexOf(UUID child) {
        if (child == null) return -1;
        for (int i = 0; i < beds.size(); i++) if (child.equals(beds.get(i).reservedFor())) return i;
        return -1;
    }

    public VillageRecord withBeds(List<VillageBed> newBeds) {
        return new VillageRecord(
                dataVersion, villageId, dimension,
                minX, minY, minZ, maxX, maxY, maxZ,
                audited, newBeds, workstations, market, factionOwnerId, deaths);
    }

    /** Reserve the free bed at {@code index} for unborn {@code child}. The bed must be free. */
    public VillageRecord reserveBed(int index, UUID child) {
        if (child == null) throw new IllegalArgumentException("child required");
        VillageBed bed = beds.get(index);
        if (!bed.isFree()) throw new IllegalStateException("bed " + index + " is not free");
        List<VillageBed> next = new ArrayList<>(beds);
        next.set(index, bed.withReservation(child));
        return withBeds(next);
    }

    /** Drop the reservation held for {@code child}, if any. */
    public VillageRecord releaseReservation(UUID child) {
        int idx = reservedBedIndexOf(child);
        if (idx < 0) return this;
        List<VillageBed> next = new ArrayList<>(beds);
        next.set(idx, beds.get(idx).withReservation(null));
        return withBeds(next);
    }

    /**
     * Turn {@code child}'s reservation into occupancy. If the reservation
     * was lost, falls back to any free bed. Returns null if no bed can be
     * claimed — the caller decides what a birth without a bed means.
     */
    public VillageRecord occupyReserved(UUID child) {
        if (child == null) throw new IllegalArgumentException("child required");
        int idx = reservedBedIndexOf(child);
        if (idx < 0) idx = firstFreeBedIndex();
        if (idx < 0) return null;
        List<VillageBed> next = new ArrayList<>(beds);
        next.set(idx, beds.get(idx).withReservation(null).withOccupant(child));
        return withBeds(next);
    }

    public VillageRecord withMarket(VillageMarket newMarket) {
        return new VillageRecord(
                dataVersion, villageId, dimension,
                minX, minY, minZ, maxX, maxY, maxZ,
                audited, beds, workstations, newMarket, factionOwnerId, deaths);
    }

    public VillageRecord withFactionOwner(String newFactionOwnerId) {
        return new VillageRecord(
                dataVersion, villageId, dimension,
                minX, minY, minZ, maxX, maxY, maxZ,
                audited, beds, workstations, market, newFactionOwnerId, deaths);
    }
}
