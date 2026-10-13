package com.lightmare.villagerrealms.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.lightmare.villagerrealms.core.role.Role;
import com.lightmare.villagerrealms.core.role.RoleJson;
import com.lightmare.villagerrealms.core.role.RoleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack role loader. Scans {@code data/<namespace>/villager_realms/roles/*.json}
 * on every server data reload (world load and {@code /reload}) and replaces
 * the {@link RoleRegistry} contents. Parsing is delegated to the
 * Minecraft-free {@link RoleJson} so the format is unit-tested without a
 * server.
 *
 * Role ids are the file name without namespace ({@code farmer.json} →
 * {@code "farmer"}), matching the plain string ids stored in NPC records.
 * If two namespaces define the same id the later one in iteration order
 * wins and a warning is logged. Addon mods therefore extend or override the
 * built-in set just by shipping files.
 *
 * If no files load at all (broken data pack), the built-in defaults are
 * restored rather than leaving the registry empty.
 */
public final class RoleLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOG = LoggerFactory.getLogger(RoleLoader.class);
    private static final Gson GSON = new GsonBuilder().create();

    public static final String DIRECTORY = "villager_realms/roles";

    public RoleLoader() {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new RoleLoader());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, ResourceLocation> seen = new HashMap<>();
        List<Role> roles = new ArrayList<>(files.size());
        int rejected = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation file = entry.getKey();
            String id = file.getPath();
            ResourceLocation prior = seen.put(id, file);
            if (prior != null) {
                LOG.warn("[vr] role '{}' defined by both {} and {}; using {}", id, prior, file, file);
                roles.removeIf(r -> r.id().equals(id));
            }
            try {
                roles.add(RoleJson.parse(id, entry.getValue()));
            } catch (RuntimeException ex) {
                rejected++;
                LOG.error("[vr] rejected role file {}: {}", file, ex.getMessage());
            }
        }

        if (roles.isEmpty()) {
            LOG.warn("[vr] no roles loaded from data packs ({} rejected); keeping built-in defaults", rejected);
            RoleRegistry.resetToDefaults();
            return;
        }

        RoleRegistry.replaceAll(roles);
        List<String> ids = new ArrayList<>(roles.size());
        for (Role r : roles) ids.add(r.id());
        ids.sort(null);
        LOG.info("[vr] loaded {} roles from data packs ({} rejected): {}", roles.size(), rejected, ids);
    }
}
