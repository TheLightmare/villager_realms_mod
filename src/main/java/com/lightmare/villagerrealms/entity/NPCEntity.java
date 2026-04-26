package com.lightmare.villagerrealms.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/**
 * Placeholder NPC entity. Behavior-bearing data lives in NPCRecord; this
 * class is the puppet.
 *
 * Goals here are intentionally limited to *mechanical execution* of
 * pathfinding (opening doors, floating in water). Behavioral decisions
 * — what to do, where to go, when to eat — come from the IAUS evaluator
 * and the active step runtime, never from the goal selector.
 */
public class NPCEntity extends PathfinderMob {

    public NPCEntity(EntityType<? extends NPCEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation nav = new GroundPathNavigation(this, level);
        nav.setCanOpenDoors(true);
        nav.setCanPassDoors(true);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        // Mechanical, not behavioral: lets the path planner route through
        // doors; the goal opens the door when the entity reaches it.
        this.goalSelector.addGoal(0, new OpenDoorGoal(this, false));
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        // NPCs persist with chunks, never despawn from distance.
        return false;
    }
}
