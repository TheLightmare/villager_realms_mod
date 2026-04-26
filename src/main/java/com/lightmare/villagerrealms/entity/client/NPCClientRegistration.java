package com.lightmare.villagerrealms.entity.client;

import com.lightmare.villagerrealms.entity.NPCEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class NPCClientRegistration {

    private NPCClientRegistration() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(NPCClientRegistration::onRegisterRenderers);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NPCEntityType.NPC.get(), NPCRenderer::new);
    }
}
