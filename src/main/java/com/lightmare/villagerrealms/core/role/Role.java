package com.lightmare.villagerrealms.core.role;

import java.util.List;

/**
 * Data-driven role definition, loaded from datapack JSON (see
 * {@link RoleJson} for the format) with {@link RoleRegistry#defaults()} as
 * the code fallback.
 *
 * Production model is single-step: a worker, when "working", consumes
 * {@code consumes} and produces {@code produces} (either may be empty).
 * Multi-step chains (wheat -&gt; flour -&gt; bread) are explicitly v2.
 *
 * {@code workstationKind} names the vanilla POI the auditor matches this
 * role to (via RoleMapping). Empty means "no workstation": the auditor
 * sends such workers to the village centre, where they forage.
 */
public record Role(
        String id,
        boolean worker,
        List<RoleItemSpec> consumes,
        List<RoleItemSpec> produces,
        String workstationKind
) {
    public Role {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("id required");
        if (consumes == null) throw new IllegalArgumentException("consumes required");
        if (produces == null) throw new IllegalArgumentException("produces required");
        if (workstationKind == null) workstationKind = "";
        consumes = List.copyOf(consumes);
        produces = List.copyOf(produces);
    }

    public boolean producesAnything() { return !produces.isEmpty(); }
    public boolean consumesAnything() { return !consumes.isEmpty(); }
}
