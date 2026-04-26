package com.lightmare.villagerrealms.core.ai.sched;

/**
 * Per-tick scheduler counters. Plain data — read by debug commands and
 * used in tests to assert budget enforcement.
 */
public record SchedulerStats(
        int evaluated,
        int deferredAdded,
        int deferredDrained,
        int overflowSize) {}
