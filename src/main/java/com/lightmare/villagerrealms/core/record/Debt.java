package com.lightmare.villagerrealms.core.record;

import java.util.UUID;

public record Debt(UUID creditor, long amount, long incurredAtTick) {
    public Debt {
        if (creditor == null) throw new IllegalArgumentException("creditor required");
    }
}
