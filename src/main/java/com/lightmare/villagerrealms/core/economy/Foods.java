package com.lightmare.villagerrealms.core.economy;

import java.util.HashMap;
import java.util.Map;

/**
 * Food registry: which item ids are food and how much hunger each restores.
 * Built-in defaults cover the v1 staples; datapack overrides come later.
 *
 * Hunger is on a 0..20 scale (Minecraft food convention). One unit of bread
 * restores 5 hunger; meat restores more, fruit a bit less.
 */
public final class Foods {

    private static final Map<String, Float> RESTORE = new HashMap<>();

    static {
        RESTORE.put("minecraft:bread",            5f);
        RESTORE.put("minecraft:apple",            4f);
        RESTORE.put("minecraft:carrot",           3f);
        RESTORE.put("minecraft:potato",           1f);
        RESTORE.put("minecraft:baked_potato",     5f);
        RESTORE.put("minecraft:cooked_beef",      8f);
        RESTORE.put("minecraft:cooked_chicken",   6f);
        RESTORE.put("minecraft:cooked_porkchop",  8f);
        RESTORE.put("minecraft:cooked_cod",       5f);
        RESTORE.put("minecraft:cooked_salmon",    6f);
    }

    private Foods() {}

    public static boolean isFood(String itemId) {
        return itemId != null && RESTORE.containsKey(itemId);
    }

    public static float restoreFor(String itemId) {
        Float v = RESTORE.get(itemId);
        return v == null ? 0f : v;
    }

    public static void register(String itemId, float restore) {
        if (itemId == null) throw new IllegalArgumentException("itemId required");
        if (restore <= 0f) throw new IllegalArgumentException("restore must be > 0");
        RESTORE.put(itemId, restore);
    }
}
