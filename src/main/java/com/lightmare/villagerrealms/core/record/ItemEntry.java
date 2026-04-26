package com.lightmare.villagerrealms.core.record;

public record ItemEntry(
        String itemId,
        int count,
        Provenance provenance,
        long acquiredAtTick
) {
    public ItemEntry {
        if (itemId == null) throw new IllegalArgumentException("itemId required");
        if (count <= 0) throw new IllegalArgumentException("count must be > 0, got " + count);
        if (provenance == null) throw new IllegalArgumentException("provenance required");
    }
}
