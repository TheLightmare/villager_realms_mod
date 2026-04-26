package com.lightmare.villagerrealms.core.role;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Process-global registry of {@link Role} definitions. Built-in defaults
 * cover the v1 economy (farmer, baker, butcher, fisherman, laborer);
 * datapack overrides land in a later step.
 *
 * Single-threaded. Mutated only at server start, read everywhere else.
 *
 * The {@code BAKER} entry is the only multi-input role in v1 — it consumes
 * one wheat to produce one bread. Per CLAUDE.md, multi-step chains
 * (wheat -&gt; flour -&gt; bread) are out of scope; "wheat -&gt; bread" counts as
 * a single step and is fine.
 */
public final class RoleRegistry {

    public static final String LABORER = "laborer";

    private static final Map<String, Role> ROLES = new HashMap<>();

    static { resetToDefaults(); }

    private RoleRegistry() {}

    public static synchronized void resetToDefaults() {
        ROLES.clear();
        register(new Role(
                "farmer", true,
                List.of(),
                List.of(new RoleItemSpec("minecraft:wheat", 1)),
                "farmer"));
        register(new Role(
                "baker", true,
                List.of(new RoleItemSpec("minecraft:wheat", 1)),
                List.of(new RoleItemSpec("minecraft:bread", 1)),
                "baker"));
        register(new Role(
                "butcher", true,
                List.of(),
                List.of(new RoleItemSpec("minecraft:cooked_porkchop", 1)),
                "butcher"));
        register(new Role(
                "fisherman", true,
                List.of(),
                List.of(new RoleItemSpec("minecraft:cooked_cod", 1)),
                "fisherman"));
        register(new Role(
                "shepherd", true,
                List.of(),
                List.of(new RoleItemSpec("minecraft:cooked_chicken", 1)),
                "shepherd"));
        register(new Role(
                LABORER, false, List.of(), List.of(), ""));
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
}
