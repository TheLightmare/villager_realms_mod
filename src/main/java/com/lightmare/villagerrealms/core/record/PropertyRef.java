package com.lightmare.villagerrealms.core.record;

public record PropertyRef(String villageId, String key) {
    public PropertyRef {
        if (villageId == null) throw new IllegalArgumentException("villageId required");
        if (key == null) throw new IllegalArgumentException("key required");
    }
}
