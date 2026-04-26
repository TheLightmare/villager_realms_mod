package com.lightmare.villagerrealms.core.ai.cache;

import com.lightmare.villagerrealms.core.Fixtures;
import com.lightmare.villagerrealms.core.ai.Consideration;
import com.lightmare.villagerrealms.core.ai.EvalContext;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsiderationCacheTest {

    private static class CountingConsideration implements Consideration {
        final AtomicInteger calls = new AtomicInteger();
        private final long ttl;
        CountingConsideration(long ttl) { this.ttl = ttl; }
        @Override public String id() { return "counter"; }
        @Override public boolean essential() { return true; }
        @Override public long ttlTicks() { return ttl; }
        @Override public float score(EvalContext ctx) { calls.incrementAndGet(); return 0.5f; }
    }

    @Test
    void zeroTtlAlwaysRecomputes() {
        var cache = new ConsiderationCache();
        var c = new CountingConsideration(0L);
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        cache.scoreCached(c, EvalContext.of(npc, 100L));
        cache.scoreCached(c, EvalContext.of(npc, 100L));
        cache.scoreCached(c, EvalContext.of(npc, 101L));
        assertEquals(3, c.calls.get());
    }

    @Test
    void positiveTtlReusesWithinWindow() {
        var cache = new ConsiderationCache();
        var c = new CountingConsideration(50L);
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        cache.scoreCached(c, EvalContext.of(npc, 100L));
        cache.scoreCached(c, EvalContext.of(npc, 130L));
        cache.scoreCached(c, EvalContext.of(npc, 149L));
        assertEquals(1, c.calls.get());
    }

    @Test
    void recomputesAfterExpiry() {
        var cache = new ConsiderationCache();
        var c = new CountingConsideration(50L);
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        cache.scoreCached(c, EvalContext.of(npc, 100L));
        cache.scoreCached(c, EvalContext.of(npc, 160L));
        assertEquals(2, c.calls.get());
    }

    @Test
    void invalidateForcesRecompute() {
        var cache = new ConsiderationCache();
        var c = new CountingConsideration(1000L);
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        cache.scoreCached(c, EvalContext.of(npc, 100L));
        cache.invalidate(npc.identity().uuid());
        cache.scoreCached(c, EvalContext.of(npc, 110L));
        assertEquals(2, c.calls.get());
    }

    @Test
    void pruneStaleRemovesExpired() {
        var cache = new ConsiderationCache();
        var c = new CountingConsideration(50L);
        NPCRecord npc = Fixtures.npc(UUID.randomUUID(), "v", 0, 0);
        cache.scoreCached(c, EvalContext.of(npc, 100L));
        int dropped = cache.pruneStale(200L);
        assertEquals(1, dropped);
        assertEquals(0, cache.trackedNpcCount());
    }
}
