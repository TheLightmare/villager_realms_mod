package com.lightmare.villagerrealms.core.ai.cache;

import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.Curves;
import com.lightmare.villagerrealms.core.ai.EvalContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Per-NPC, per-consideration TTL cache. Lives in transient runtime state
 * (never persisted, never on NPCRecord). Single-threaded — same model as
 * the registry.
 *
 * A consideration with ttlTicks() == 0 always recomputes; the cache short
 * circuits without writing. Larger TTLs are cheap reads after the first
 * computation; they expire on the tick boundary, not by wall clock.
 *
 * Eviction is opportunistic: stale entries get overwritten in place, and
 * {@link #pruneStale} sweeps unused entries — call it on autosave.
 */
public final class ConsiderationCache {

    private record Entry(float value, long expiresAtTick) {}

    private final Map<UUID, Map<String, Entry>> byNpc = new HashMap<>();

    public float scoreCached(Consideration c, EvalContext ctx) {
        if (c.ttlTicks() <= 0L) {
            return Curves.clamp01(c.score(ctx));
        }
        UUID id = ctx.npc().identity().uuid();
        Map<String, Entry> m = byNpc.get(id);
        if (m != null) {
            Entry e = m.get(c.id());
            if (e != null && e.expiresAtTick > ctx.tick()) {
                return e.value;
            }
        }
        float v = Curves.clamp01(c.score(ctx));
        Map<String, Entry> dst = byNpc.computeIfAbsent(id, k -> new HashMap<>());
        dst.put(c.id(), new Entry(v, ctx.tick() + c.ttlTicks()));
        return v;
    }

    public void invalidate(UUID npcId) {
        byNpc.remove(npcId);
    }

    public void invalidate(UUID npcId, String considerationId) {
        Map<String, Entry> m = byNpc.get(npcId);
        if (m != null) m.remove(considerationId);
    }

    public void clear() { byNpc.clear(); }

    /** Drop entries whose expiresAtTick is at or before {@code now}. */
    public int pruneStale(long now) {
        int removed = 0;
        Iterator<Map.Entry<UUID, Map<String, Entry>>> outer = byNpc.entrySet().iterator();
        while (outer.hasNext()) {
            Map.Entry<UUID, Map<String, Entry>> npcEntry = outer.next();
            Map<String, Entry> m = npcEntry.getValue();
            Iterator<Map.Entry<String, Entry>> inner = m.entrySet().iterator();
            while (inner.hasNext()) {
                Map.Entry<String, Entry> e = inner.next();
                if (e.getValue().expiresAtTick <= now) {
                    inner.remove();
                    removed++;
                }
            }
            if (m.isEmpty()) outer.remove();
        }
        return removed;
    }

    public int trackedNpcCount() { return byNpc.size(); }
}
