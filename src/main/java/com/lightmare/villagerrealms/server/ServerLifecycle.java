package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.economy.MarketOps;
import com.lightmare.villagerrealms.core.persist.store.VillageStore;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.entity.NPCEntity;
import com.lightmare.villagerrealms.entity.Projector;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;

public final class ServerLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ServerLifecycle.class);

    private static final int LIVE_SYNC_INTERVAL_TICKS = 60;
    private static final int AUTOSAVE_INTERVAL_TICKS = 6000;
    /** Supply/demand retained per autosave interval: ~0.9^6 ≈ 53% after half an hour. */
    private static final float MARKET_DECAY_KEEP = 0.9f;

    private static int tickCounter;
    private static long worldTick;

    private ServerLifecycle() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        try {
            PersistenceService.start(event.getServer());
        } catch (IOException e) {
            LOG.error("Failed to start PersistenceService: {}", e.toString(), e);
            throw new RuntimeException(e);
        }
        AIService.start(PersistenceService.get().villages());
        tickCounter = 0;
        worldTick = 0L;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;
        extractAllLoaded(event.getServer());
        try {
            svc.flushAll();
        } catch (IOException e) {
            LOG.error("Final flushDirty failed: {}", e.toString(), e);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TierManager.reset();
        ActiveStepRuntime.reset();
        ConsequenceEvents.reset();
        BirthService.reset();
        AIService.stop();
        PersistenceService.stop();
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;
        tickCounter++;
        // gameTime drives scheduling (monotonic; never frozen by /gamerule).
        // dayTime drives time-of-day considerations — it's what vanilla beds
        // check. They differ when /time set is used or doDaylightCycle is off.
        net.minecraft.server.level.ServerLevel overworld = event.getServer().overworld();
        worldTick = overworld.getGameTime();
        long dayTime = overworld.getDayTime();

        AIService ai = AIService.getOrNull();
        if (ai != null) {
            ai.tick(worldTick, dayTime, svc.registry());
        }

        if (tickCounter % LIVE_SYNC_INTERVAL_TICKS == 0) {
            extractAllLoaded(event.getServer());
        }
        if (tickCounter % AUTOSAVE_INTERVAL_TICKS == 0) {
            decayMarkets(svc.villages());
            try {
                svc.flushAll();
            } catch (IOException e) {
                LOG.error("Auto-save flush failed: {}", e.toString(), e);
            }
        }
    }

    /**
     * Let each market's supply/demand counters forget the past so price
     * tracks recent flow rather than lifetime totals. Runs on the autosave
     * cadence for every village regardless of tier — Tier 2 villages trade
     * too. Only villages with a live signal are written back.
     */
    private static void decayMarkets(VillageStore villages) {
        for (VillageRecord v : new ArrayList<>(villages.all())) {
            VillageMarket m = v.market();
            if (m.supply().isEmpty() && m.demand().isEmpty()) continue;
            VillageMarket next = MarketOps.decay(m, MARKET_DECAY_KEEP);
            if (!next.equals(m)) villages.put(v.withMarket(next));
        }
    }

    private static void extractAllLoaded(net.minecraft.server.MinecraftServer server) {
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof NPCEntity npc)) continue;
                NPCRecord prior = svc.registry().get(npc.getUUID()).orElse(null);
                if (prior == null) continue;
                NPCRecord updated = Projector.extract(prior, npc);
                if (!updated.equals(prior)) {
                    svc.registry().put(updated);
                }
            }
        }
    }
}
