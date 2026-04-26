package com.lightmare.villagerrealms.village;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Workstation -> role id. Vanilla profession mapping for v1. Roles are plain
 * strings; the actual role data (schedule, needed items) will be a datapack-
 * driven JSON in a later step.
 *
 * Two indices are kept in sync:
 *   - {@link #BY_BLOCK} for code that has a {@link Block} in hand.
 *   - {@link #BY_POI}   for code that has a vanilla {@link PoiType} holder
 *                       (i.e. queries against {@code PoiManager}).
 */
public final class RoleMapping {

    private static final Map<Block, String> BY_BLOCK = new HashMap<>();
    private static final Map<ResourceKey<PoiType>, String> BY_POI = new HashMap<>();

    static {
        register(Blocks.COMPOSTER,         PoiTypes.FARMER,        "farmer");
        register(Blocks.SMOKER,            PoiTypes.BUTCHER,       "butcher");
        register(Blocks.LECTERN,           PoiTypes.LIBRARIAN,     "librarian");
        register(Blocks.SMITHING_TABLE,    PoiTypes.TOOLSMITH,     "toolsmith");
        register(Blocks.STONECUTTER,       PoiTypes.MASON,         "mason");
        register(Blocks.CARTOGRAPHY_TABLE, PoiTypes.CARTOGRAPHER,  "cartographer");
        register(Blocks.BREWING_STAND,     PoiTypes.CLERIC,        "cleric");
        register(Blocks.BLAST_FURNACE,     PoiTypes.ARMORER,       "armorer");
        register(Blocks.BARREL,            PoiTypes.FISHERMAN,     "fisherman");
        register(Blocks.FLETCHING_TABLE,   PoiTypes.FLETCHER,      "fletcher");
        register(Blocks.CAULDRON,          PoiTypes.LEATHERWORKER, "leatherworker");
        register(Blocks.LOOM,              PoiTypes.SHEPHERD,      "shepherd");
        register(Blocks.GRINDSTONE,        PoiTypes.WEAPONSMITH,   "weaponsmith");
    }

    private RoleMapping() {}

    private static void register(Block block, ResourceKey<PoiType> poi, String role) {
        BY_BLOCK.put(block, role);
        BY_POI.put(poi, role);
    }

    public static String roleFor(Block block) {
        return BY_BLOCK.get(block);
    }

    public static String roleForPoi(Holder<PoiType> poiHolder) {
        return poiHolder.unwrapKey().map(BY_POI::get).orElse(null);
    }

    public static boolean isWorkstation(Block block) {
        return BY_BLOCK.containsKey(block);
    }
}
