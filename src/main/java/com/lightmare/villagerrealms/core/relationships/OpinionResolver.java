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
        return effectiveTowards(viewer, target.factionId(), target.identity().uuid(), relations);
    }

    /**
     * Same chain for a target that need not be an NPC. {@code targetGroup}
     * is what the faction-level lookups key on: another NPC's faction id, or
     * a {@link com.lightmare.villagerrealms.core.consequence.Refs} ref such as
     * {@code player:<uuid>} (players are not factions; their standing is a
     * matrix row against their ref). {@code targetUuid} drives the personal
     * override and may be null.
     */
    public static int effectiveTowards(NPCRecord viewer, String targetGroup, UUID targetUuid,
                                       FactionRelations relations) {
        if (viewer == null) return 0;
        Relationships rel = viewer.relationships();

        if (targetUuid != null) {
            Integer perNpc = rel.opinions().get(targetUuid);
            if (perNpc != null) return perNpc;
        }

        if (targetGroup != null && !targetGroup.isEmpty()) {
            Integer perFaction = rel.factionOpinions().get(targetGroup);
            if (perFaction != null) return perFaction;

            String viewerFaction = viewer.factionId();
            if (viewerFaction != null && !viewerFaction.isEmpty() && relations != null) {
                return relations.opinion(viewerFaction, targetGroup);
            }
        }
        return 0;
    }
}
