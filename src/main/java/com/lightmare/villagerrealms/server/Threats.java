package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.ai.ThreatView;
import com.lightmare.villagerrealms.core.ai.considerations.DangerConsideration;
import com.lightmare.villagerrealms.core.consequence.Attitude;
import com.lightmare.villagerrealms.core.consequence.Refs;
import com.lightmare.villagerrealms.core.record.FactionRelations;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Builds a {@link ThreatView} for a Tier 0 NPC: the nearest player within
 * {@link DangerConsideration#THREAT_RADIUS} whose standing with the NPC is
 * {@link Attitude#HOSTILE}. Spectators and creative-mode players are never
 * threats. O(players in level) per call; called once per evaluation and
 * once per drive, both already on a cadence.
 */
public final class Threats {

    private Threats() {}

    public static ThreatView view(ServerLevel level, NPCRecord npc, FactionRelations relations) {
        if (level == null || npc == null) return ThreatView.NONE;
        Location loc = npc.location();
        double r2 = DangerConsideration.THREAT_RADIUS * DangerConsideration.THREAT_RADIUS;

        ServerPlayer nearest = null;
        double nearestSq = r2;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative()) continue;
            double dx = p.getX() - loc.x(), dy = p.getY() - loc.y(), dz = p.getZ() - loc.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d > nearestSq) continue;
            if (!Attitude.towardsPlayer(npc, p.getUUID(), relations).hostile()) continue;
            nearest = p;
            nearestSq = d;
        }
        if (nearest == null) return ThreatView.NONE;
        return ThreatView.of(nearest.getX(), nearest.getY(), nearest.getZ(), nearestSq, Refs.player(nearest.getUUID()));
    }
}
