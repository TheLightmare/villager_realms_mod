package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.persist.store.FactionStore;
import com.lightmare.villagerrealms.core.persist.store.NPCRegistry;
import com.lightmare.villagerrealms.core.persist.store.VillageStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-server-instance owner of the NPCRegistry and PersistenceWorker.
 *
 * Lifecycle is driven by ServerLifecycle: start() on ServerStartingEvent,
 * stop() on ServerStoppedEvent. Access via {@link #get()} from systems that
 * run while the server is up.
 */
public final class PersistenceService {

    private static final Logger LOG = LoggerFactory.getLogger(PersistenceService.class);
    private static volatile PersistenceService current;

    private final MinecraftServer server;
    private final NPCRegistry registry;
    private final VillageStore villages;
    private final FactionStore factions;
    private final PersistenceWorker worker;

    private PersistenceService(MinecraftServer server, Path dataRoot) {
        this.server = server;
        this.worker = new PersistenceWorker("vr-persist");
        this.registry = new NPCRegistry(dataRoot.resolve("npcs"), worker);
        this.villages = new VillageStore(dataRoot, worker);
        this.factions = new FactionStore(dataRoot, worker);
    }

    public MinecraftServer server() { return server; }
    public NPCRegistry registry() { return registry; }
    public VillageStore villages() { return villages; }
    public FactionStore factions() { return factions; }
    public PersistenceWorker worker() { return worker; }

    public static PersistenceService get() {
        PersistenceService c = current;
        if (c == null) throw new IllegalStateException("PersistenceService not started");
        return c;
    }

    public static PersistenceService getOrNull() {
        return current;
    }

    static synchronized void start(MinecraftServer server) throws IOException {
        if (current != null) {
            LOG.warn("PersistenceService.start called but instance already exists; replacing");
            stop();
        }
        Path worldRoot = server.getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
        Path dataRoot = worldRoot.resolve("villager_realms");
        Files.createDirectories(dataRoot);

        PersistenceService svc = new PersistenceService(server, dataRoot);
        svc.worker.start();
        svc.registry.load();
        svc.villages.load();
        svc.factions.load();
        current = svc;
        LOG.info("PersistenceService started at {}", dataRoot);
    }

    static synchronized void stop() {
        PersistenceService svc = current;
        if (svc == null) return;
        try {
            svc.registry.flushDirty();
            svc.villages.flushDirty();
            svc.factions.flushDirty();
            svc.worker.awaitIdle(10_000);
        } catch (Exception e) {
            LOG.error("Error during final flush: {}", e.toString(), e);
        }
        svc.worker.shutdown(5_000);
        current = null;
        LOG.info("PersistenceService stopped");
    }
}
