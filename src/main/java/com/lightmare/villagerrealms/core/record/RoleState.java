package com.lightmare.villagerrealms.core.record;

public record RoleState(
        String roleId,
        String workplaceRef,
        long scheduleOffsetTicks
) {
    public RoleState {
        if (roleId == null) throw new IllegalArgumentException("roleId required");
    }
}
