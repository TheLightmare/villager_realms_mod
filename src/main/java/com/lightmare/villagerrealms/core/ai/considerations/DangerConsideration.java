package com.lightmare.villagerrealms.core.ai.considerations;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.Curves;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.ai.ThreatView;
import com.lightmare.villagerrealms.core.consequence.MemoryKinds;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.NPCRecord;

/**
 * "I am in danger." The essential consideration the IAUS notes always
 * assumed. Two inputs, max of both:
 *
 *   - Recently attacked: the newest {@code ATTACKED} memory event, fading
 *     linearly from 1 to 0 over {@link #RECENT_ATTACK_TICKS}. Pure over the
 *     record, so it works at Tier 1 with no threat view.
 *   - A hostile actor nearby ({@link ThreatView}): 1.0 on top of the NPC,
 *     {@link #MIN_PROXIMITY_SCORE} at the edge of the threat radius.
 */
public final class DangerConsideration implements Consideration {

    public static final String ID = "danger";

    /** How long being hit keeps an NPC scared. */
    public static final long RECENT_ATTACK_TICKS = 600L;
    /** Must match the radius the server-side threat resolver scans. */
    public static final double THREAT_RADIUS = 16.0;
    public static final float MIN_PROXIMITY_SCORE = 0.5f;

    @Override public String id() { return ID; }
    @Override public boolean essential() { return true; }
    @Override public long ttlTicks() { return 0L; }

    @Override
    public float score(EvalContext ctx) {
        return Math.max(recentAttack(ctx.npc(), ctx.tick()), proximity(ctx.threats()));
    }

    static float recentAttack(NPCRecord npc, long now) {
        if (npc.memory() == null) return 0f;
        long newest = Long.MIN_VALUE;
        for (MemoryEvent e : npc.memory().events()) {
            if (MemoryKinds.ATTACKED.equals(e.kind()) && e.tick() > newest) newest = e.tick();
        }
        if (newest == Long.MIN_VALUE) return 0f;
        long age = now - newest;
        if (age < 0) return 0f;
        return Curves.inverseRamp(age, 0f, RECENT_ATTACK_TICKS);
    }

    static float proximity(ThreatView t) {
        if (t == null || !t.present()) return 0f;
        float dist = (float) Math.sqrt(t.distSq());
        float near = Curves.inverseRamp(dist, 0f, (float) THREAT_RADIUS);
        return MIN_PROXIMITY_SCORE + (1f - MIN_PROXIMITY_SCORE) * near;
    }
}
