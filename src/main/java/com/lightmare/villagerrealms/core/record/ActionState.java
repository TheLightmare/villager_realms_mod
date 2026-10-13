package com.lightmare.villagerrealms.core.record;

public record ActionState(
        String actionId,
        int subStep,
        long startedAtTick,
        String checkpointToken
) {
    public static final ActionState IDLE = new ActionState("idle", 0, 0L, "");

    /**
     * Checkpoint set by the active runtime while the entity is walking a
     * {@code pathfind} substep and cleared on arrival, skip, or timeout. The
     * scheduler never replaces an action carrying this token — CLAUDE.md:
     * "re-evaluation between sub-steps only, never mid-pathfind".
     */
    public static final String CKPT_PATHFINDING = "pathfinding";

    public boolean isPathfinding() {
        return CKPT_PATHFINDING.equals(checkpointToken);
    }

    public ActionState withCheckpoint(String token) {
        return new ActionState(actionId, subStep, startedAtTick, token);
    }

    public ActionState withSubStep(int newSubStep, String token) {
        return new ActionState(actionId, newSubStep, startedAtTick, token);
    }

    public ActionState {
        if (actionId == null) throw new IllegalArgumentException("actionId required");
        if (checkpointToken == null) checkpointToken = "";
    }
}
