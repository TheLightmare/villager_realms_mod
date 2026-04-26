package com.lightmare.villagerrealms.entity;

import com.lightmare.villagerrealms.VillagerRealms;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class NPCEntityType {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, VillagerRealms.MOD_ID);

    public static final Supplier<EntityType<NPCEntity>> NPC = ENTITY_TYPES.register(
            "npc",
            () -> EntityType.Builder.<NPCEntity>of(NPCEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build(VillagerRealms.MOD_ID + ":npc"));

    private NPCEntityType() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(NPCEntityType::onAttributesCreate);
    }

    @SubscribeEvent
    public static void onAttributesCreate(EntityAttributeCreationEvent event) {
        event.put(NPC.get(), NPCEntity.createAttributes().build());
    }
}
