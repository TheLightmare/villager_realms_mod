package com.lightmare.villagerrealms.core.role;

public record RoleItemSpec(String itemId, int count) {
    public RoleItemSpec {
        if (itemId == null || itemId.isEmpty()) throw new IllegalArgumentException("itemId required");
        if (count <= 0) throw new IllegalArgumentException("count must be > 0, got " + count);
    }
}
