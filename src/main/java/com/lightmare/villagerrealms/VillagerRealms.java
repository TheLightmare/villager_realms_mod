package com.lightmare.villagerrealms;

import com.lightmare.villagerrealms.command.VRCommand;
import com.lightmare.villagerrealms.entity.NPCEntityType;
import com.lightmare.villagerrealms.entity.client.NPCClientRegistration;
import com.lightmare.villagerrealms.server.ActiveStepRuntime;
import com.lightmare.villagerrealms.server.Reconciler;
import com.lightmare.villagerrealms.server.ServerLifecycle;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(VillagerRealms.MOD_ID)
public final class VillagerRealms {

    public static final String MOD_ID = "villager_realms";
    public static final Logger LOG = LogUtils.getLogger();

    public VillagerRealms(IEventBus modBus) {
        NPCEntityType.register(modBus);

        NeoForge.EVENT_BUS.register(ServerLifecycle.class);
        NeoForge.EVENT_BUS.register(Reconciler.class);
        NeoForge.EVENT_BUS.register(VRCommand.class);
        NeoForge.EVENT_BUS.register(ActiveStepRuntime.class);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NPCClientRegistration.register(modBus);
        }

        LOG.info("Villager Realms loaded.");
    }
}
