package com.lightmare.villagerrealms.core.consequence;

import com.lightmare.villagerrealms.core.record.FactionRelations;

/**
 * Event-driven deltas to the faction opinion matrix. The target may be a
 * faction id or any {@link Refs} ref — a player's standing with a faction is
 * the matrix entry (faction → {@code player:<uuid>}). Pure functions.
 */
public final class Standing {

    /** A faction member was hit by the target. */
    public static final int ATTACK_DELTA = -5;
    /** A faction member was killed by the target. */
    public static final int KILL_DELTA = -25;
    /** A faction member received a gift from the target. */
    public static final int GIFT_DELTA = 2;

    private Standing() {}

    public static int of(FactionRelations relations, String viewerFaction, String target) {
        return relations == null ? 0 : relations.opinion(viewerFaction, target);
    }

    /** Apply {@code delta} to (viewerFaction → target), clamped to the opinion scale. */
    public static FactionRelations adjust(FactionRelations relations, String viewerFaction,
                                          String target, int delta) {
        if (relations == null) relations = FactionRelations.EMPTY;
        if (viewerFaction == null || viewerFaction.isEmpty() || target == null || target.isEmpty()) {
            return relations;
        }
        if (delta == 0) return relations;
        int next = Opinions.clamp(relations.opinion(viewerFaction, target) + delta);
        return relations.withOpinion(viewerFaction, target, next);
    }
}
