package com.lightmare.villagerrealms.entity.client;

import com.lightmare.villagerrealms.entity.NPCEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder renderer using the vanilla villager model + texture so NPCs are
 * visible in dev. The model accepts any {@code Entity} subtype; we drop the
 * profession/head/unhappy layers because none of them apply to NPCEntity yet.
 */
public final class NPCRenderer extends MobRenderer<NPCEntity, VillagerModel<NPCEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/villager/villager.png");

    public NPCRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(NPCEntity entity) {
        return TEXTURE;
    }
}
