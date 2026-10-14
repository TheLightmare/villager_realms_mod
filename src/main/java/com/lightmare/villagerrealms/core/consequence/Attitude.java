package com.lightmare.villagerrealms.core.consequence;

import com.lightmare.villagerrealms.core.record.FactionRelations;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.relationships.OpinionResolver;

import java.util.UUID;

/**
 * How an NPC treats someone, derived from the effective opinion
 * ({@link OpinionResolver}). The bands are what every player-facing gate
 * reads — trade terms, threat detection, refusals — so standing has one
 * meaning everywhere.
 */
public enum Attitude {
    HOSTILE,
    WARY,
    NEUTRAL,
    FRIENDLY;

    /** Opinion at or below this: the NPC treats the subject as a threat and refuses interaction. */
    public static final int HOSTILE_AT = -30;
    /** Opinion at or above this: the NPC gives friendly terms. */
    public static final int FRIENDLY_AT = 20;

    public static Attitude of(int opinion) {
        if (opinion <= HOSTILE_AT) return HOSTILE;
        if (opinion < 0) return WARY;
        if (opinion >= FRIENDLY_AT) return FRIENDLY;
        return NEUTRAL;
    }

    /** The NPC's attitude towards a player. */
    public static Attitude towardsPlayer(NPCRecord npc, UUID player, FactionRelations relations) {
        return of(OpinionResolver.effectiveTowards(npc, Refs.player(player), player, relations));
    }

    public boolean hostile() { return this == HOSTILE; }
}
