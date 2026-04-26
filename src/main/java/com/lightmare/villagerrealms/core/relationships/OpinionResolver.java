package com.lightmare.villagerrealms.core.relationships;

import com.lightmare.villagerrealms.core.record.FactionRelations;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Relationships;

import java.util.UUID;

/**
 * Pure function. Resolves the viewer's effective opinion of a target by
 * walking the precedence chain:
 *
 * <ol>
 *   <li>Sparse {@code Relationships.opinions} entry for the target's UUID
 *       — a personal override (you killed their brother, they owe you).</li>
 *   <li>Sparse {@code Relationships.factionOpinions} entry for the target's
 *       faction — a personal override of how this NPC feels about that
 *       faction, distinct from their faction's stance.</li>
 *   <li>The faction-faction {@link FactionRelations} matrix entry for
 *       (viewer's faction, target's faction).</li>
 *   <li>0 (neutral).</li>
 * </ol>
 *
 * NPCs without a faction (NO_FACTION) skip the faction lookups and fall
 * straight through to neutral.
 */
public final class OpinionResolver {

    private OpinionResolver() {}

    public static int effective(NPCRecord viewer, NPCRecord target, FactionRelations relations) {
        if (viewer == null || target == null) return 0;
        Relationships rel = viewer.relationships();
        UUID targetId = target.identity().uuid();

        Integer perNpc = rel.opinions().get(targetId);
        if (perNpc != null) return perNpc;

        String targetFaction = target.factionId();
        if (targetFaction != null && !targetFaction.isEmpty()) {
            Integer perFaction = rel.factionOpinions().get(targetFaction);
            if (perFaction != null) return perFaction;

            String viewerFaction = viewer.factionId();
            if (viewerFaction != null && !viewerFaction.isEmpty() && relations != null) {
                return relations.opinion(viewerFaction, targetFaction);
            }
        }
        return 0;
    }
}
