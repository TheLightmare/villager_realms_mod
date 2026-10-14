package com.lightmare.villagerrealms.core.birth;

import com.lightmare.villagerrealms.core.record.NPCRecord;

import java.util.Collection;
import java.util.UUID;

/**
 * Picks a partner for an NPC that has decided to have a child: another
 * eligible adult of the same village whom they don't dislike and who
 * doesn't dislike them (sparse personal opinions only; faction standing is
 * irrelevant inside one village). Prefers the warmest mutual opinion and
 * breaks ties by uuid so the choice is stable across evaluations.
 * Gender-agnostic: nothing assigns gender in v1.
 */
public final class Partners {

    private Partners() {}

    public static NPCRecord choose(NPCRecord mother, Collection<NPCRecord> villagers, long now) {
        if (mother == null) return null;
        UUID me = mother.identity().uuid();
        String village = mother.location().homeVillageId();

        NPCRecord best = null;
        int bestScore = Integer.MIN_VALUE;
        for (NPCRecord c : villagers) {
            UUID id = c.identity().uuid();
            if (id.equals(me)) continue;
            if (village != null && !village.equals(c.location().homeVillageId())) continue;
            if (!Readiness.isEligibleParent(c, now)) continue;

            int mine = mother.relationships().opinions().getOrDefault(id, 0);
            int theirs = c.relationships().opinions().getOrDefault(me, 0);
            if (mine < 0 || theirs < 0) continue;

            int score = mine + theirs;
            if (score > bestScore || (score == bestScore && id.toString().compareTo(best.identity().uuid().toString()) < 0)) {
                best = c;
                bestScore = score;
            }
        }
        return best;
    }
}
