package com.lightmare.villagerrealms.core.record;

public record ActionState(
        String actionId,
        int subStep,
        long startedAtTick,
        String checkpointToken
) {
    public static final ActionState IDLE = new ActionState("idle", 0, 0L, "");

    public ActionState {
        if (actionId == null) throw new IllegalArgumentException("actionId required");
        if (checkpointToken == null) checkpointToken = "";
    }
}
