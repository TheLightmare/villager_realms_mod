package com.lightmare.villagerrealms.core.ai.sched;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.ai.Decision;
import com.lightmare.villagerrealms.core.ai.actions.IdleAction;
import com.lightmare.villagerrealms.core.ai.actions.SleepAction;
import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StepExecutorTest {

    private static NPCRecord withAction(NPCRecord r, ActionState a) {
        return new NPCRecord(r.dataVersion(), r.identity(), r.location(),
                r.vitals(), r.inventory(), r.economy(), r.role(),
                r.factionId(), r.relationships(), r.memory(), a);
    }

    private static Decision pick(com.lightmare.villagerrealms.core.ai.Action a) {
        return new Decision(a, 0.5f, List.of());
    }

    @Test
    void samePickLeavesRecordUntouched() {
        NPCRecord npc = withAction(Fixtures.npc(UUID.randomUUID(), "v", 0, 0),
                new ActionState("sleep", 1, 100L, ""));
        assertSame(npc, new StepExecutor().apply(npc, pick(new SleepAction()), 200L));
    }

    @Test
    void differentPickStartsFreshActionState() {
        NPCRecord npc = withAction(Fixtures.npc(UUID.randomUUID(), "v", 0, 0),
                new ActionState("sleep", 1, 100L, ""));
        NPCRecord out = new StepExecutor().apply(npc, pick(new IdleAction()), 200L);
        assertEquals(new ActionState("idle", 0, 200L, ""), out.action());
    }

    @Test
    void neverSwitchesMidPathfind() {
        ActionState walking = new ActionState("sleep", 0, 100L, ActionState.CKPT_PATHFINDING);
        NPCRecord npc = withAction(Fixtures.npc(UUID.randomUUID(), "v", 0, 0), walking);
        assertSame(npc, new StepExecutor().apply(npc, pick(new IdleAction()), 200L),
                "a pick must be ignored while the prior action is mid-pathfind");
    }

    @Test
    void switchesAgainOnceCheckpointCleared() {
        ActionState arrived = new ActionState("sleep", 1, 100L, "");
        NPCRecord npc = withAction(Fixtures.npc(UUID.randomUUID(), "v", 0, 0), arrived);
        NPCRecord out = new StepExecutor().apply(npc, pick(new IdleAction()), 300L);
        assertEquals("idle", out.action().actionId());
    }
}
