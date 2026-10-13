package com.lightmare.villagerrealms.core.role;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Process-global registry of {@link Role} definitions.
 *
 * Roles are data-driven: the authoritative set is loaded from datapack JSON
 * ({@code data/<namespace>/villager_realms/roles/<id>.json}) by the
 * server-side role loader, which calls {@link #replaceAll}. The built-in
 * defaults below are the fallback when no datapack roles load, and the
 * shipped JSON mirrors them exactly (guarded by a parity test).
 *
 * Economic shape of the v1 defaults:
 *   - Producers with a vanilla workstation: farmer (wheat), butcher,
 *     fisherman, shepherd (cooked meats).
 *   - Baker: the only input-consuming role (wheat → bread). Vanilla has no
 *     baker workstation, so the auditor derives bakers from leftover beds.
 *   - Foragers: laborer and the vanilla professions with no food output
 *     (librarian, toolsmith, …). They have no workstation kind, so the
 *     auditor sends them to the village centre, where they gather
 *     {@link #FORAGE_ITEM} into the market. This is the income source for
 *     every NPC that doesn't own a food workstation; without it they could
 *     never buy food.
 *
 * Single-threaded. Mutated on server start / datapack reload, read everywhere else.
 */
public final class RoleRegistry {

    public static final String LABORER = "laborer";

    /** What foragers (laborer and the non-food vanilla professions) produce. */
    public static final String FORAGE_ITEM = "minecraft:carrot";

    private static final Map<String, Role> ROLES = new HashMap<>();

    static { resetToDefaults(); }

    private RoleRegistry() {}

    /** The built-in role set. Also what the shipped datapack JSON must equal. */
    public static List<Role> defaults() {
        return List.of(
                producer("farmer",    "minecraft:wheat",           "farmer"),
                new Role("baker", true,
                        List.of(new RoleItemSpec("minecraft:wheat", 1)),
                        List.of(new RoleItemSpec("minecraft:bread", 1)),
                        "baker"),
                producer("butcher",   "minecraft:cooked_porkchop", "butcher"),
                producer("fisherman", "minecraft:cooked_cod",      "fisherman"),
                producer("shepherd",  "minecraft:cooked_chicken",  "shepherd"),
                forager(LABORER),
                forager("librarian"),
                forager("toolsmith"),
                forager("mason"),
                forager("cartographer"),
                forager("cleric"),
                forager("armorer"),
                forager("fletcher"),
                forager("leatherworker"),
                forager("weaponsmith"));
    }

    public static synchronized void resetToDefaults() {
        ROLES.clear();
        for (Role r : defaults()) register(r);
    }

    /**
     * Replace the whole registry with {@code roles} (datapack reload). The
     * laborer fallback is always kept: if {@code roles} doesn't define it,
     * the default laborer is retained so {@link #getOrLaborer} can't return
     * null.
     */
    public static synchronized void replaceAll(Collection<Role> roles) {
        Role fallback = ROLES.get(LABORER);
        ROLES.clear();
        for (Role r : roles) register(r);
        if (!ROLES.containsKey(LABORER)) {
            ROLES.put(LABORER, fallback != null ? fallback : forager(LABORER));
        }
    }

    public static synchronized void register(Role role) {
        ROLES.put(role.id(), role);
    }

    public static Optional<Role> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(ROLES.get(id));
    }

    /** Falls back to the laborer role if the id is unknown. */
    public static Role getOrLaborer(String id) {
        Role r = ROLES.get(id);
        if (r != null) return r;
        return ROLES.get(LABORER);
    }

    public static Collection<Role> all() {
        return Collections.unmodifiableCollection(ROLES.values());
    }

    public static int size() {
        return ROLES.size();
    }

    private static Role producer(String id, String itemId, String workstationKind) {
        return new Role(id, true, List.of(), List.of(new RoleItemSpec(itemId, 1)), workstationKind);
    }

    private static Role forager(String id) {
        return new Role(id, true, List.of(), List.of(new RoleItemSpec(FORAGE_ITEM, 1)), "");
    }
}
