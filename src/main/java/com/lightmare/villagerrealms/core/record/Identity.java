package com.lightmare.villagerrealms.core.record;

import java.util.UUID;

public record Identity(
        UUID uuid,
        String name,
        int age,
        Gender gender,
        Traits traits
) {
    public Identity {
        if (uuid == null) throw new IllegalArgumentException("uuid required");
        if (name == null) throw new IllegalArgumentException("name required");
        if (gender == null) throw new IllegalArgumentException("gender required");
        if (traits == null) throw new IllegalArgumentException("traits required");
    }
}
