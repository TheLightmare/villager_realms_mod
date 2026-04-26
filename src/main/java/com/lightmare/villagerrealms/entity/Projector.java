package com.lightmare.villagerrealms.entity;

import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Vitals;
import net.minecraft.network.chat.Component;

/**
 * Translates between NPCRecord (source of truth) and NPCEntity (puppet).
 * Pure functions: callers decide cadence (batched, not reactive per-field).
 */
public final class Projector {

    private Projector() {}

    public static void project(NPCRecord record, NPCEntity entity) {
        entity.setUUID(record.identity().uuid());

        Location loc = record.location();
        entity.moveTo(loc.x(), loc.y(), loc.z(), 0.0f, 0.0f);

        entity.setCustomName(Component.literal(record.identity().name()));
        entity.setCustomNameVisible(true);

        float health = Math.min(record.vitals().health(), entity.getMaxHealth());
        entity.setHealth(Math.max(health, 0.001f));
    }

    public static NPCRecord extract(NPCRecord prior, NPCEntity entity) {
        Location oldLoc = prior.location();
        String dim = entity.level().dimension().location().toString();
        Location newLoc = new Location(
                oldLoc.homeVillageId(),
                entity.getX(), entity.getY(), entity.getZ(),
                dim,
                oldLoc.tier());

        Vitals oldVitals = prior.vitals();
        Vitals newVitals = new Vitals(
                entity.getHealth(),
                oldVitals.hunger(),
                oldVitals.energy(),
                oldVitals.mood());

        return new NPCRecord(
                prior.dataVersion(),
                prior.identity(),
                newLoc,
                newVitals,
                prior.inventory(),
                prior.economy(),
                prior.role(),
                prior.factionId(),
                prior.relationships(),
                prior.memory(),
                prior.action());
    }
}
