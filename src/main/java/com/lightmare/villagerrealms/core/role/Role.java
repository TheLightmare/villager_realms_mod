package com.lightmare.villagerrealms.core.role;

import java.util.List;

/**
 * Data-driven role definition. Loaded from datapack JSON in a future step;
 * v1 ships built-in defaults via {@link RoleRegistry}.
 *
 * Production model is single-step: a worker, when "working", consumes
 * {@code consumes} and produces {@code produces} (either may be empty).
 * Multi-step chains (wheat -&gt; flour -&gt; bread) are explicitly v2.
 *
 * {@code workstationKind} mirrors the auditor-side role string and is kept
 * here so addon roles can self-describe; v1 still uses RoleMapping for the
 * audit-time POI scan.
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
