# NPC Overhaul Mod — Design Document & Claude Code Prompt

## Project Context

You are helping build a **Minecraft mod for 1.21.1 NeoForge** that overhauls NPCs into a Kenshi-inspired living-world simulation. The mod is **standalone** (not built on MCA Reborn or any other NPC mod) and is being designed from the ground up with **server performance as a day-one constraint**.

The author has prior modding experience, including a previous mod implementing IAUS (Infinite Axis Utility System) for a Skynet-style central AI controlling drones. They are comfortable rolling their own AI architecture rather than using vanilla's Brain system.

This document captures the architectural decisions made during design. **Treat these as committed decisions, not suggestions.** Where v1 scope is specified, do not expand it without explicit instruction.

---

## Design Goals

- NPCs feel alive: they have hunger, sleep, work, social needs, inventories, and money.
- NPCs persist and "live" even when far from the player (background simulation).
- The world has consequence: NPCs can die permanently, factions can be wiped out, NPCs remember significant events.
- Built for **server performance** — tiered simulation, capped per-tick budgets, shared evaluation logic between fidelity levels.
- **Extensibility first**: roles, factions, and economic primitives are data-driven so deeper simulation can layer in later without re-architecting.

---

## Core Architectural Decisions

### 1. NPC Identity: Record-as-NPC, Entity-as-Puppet

- The **NPCRecord** is the source of truth. It is a plain data object (POJO/record), no behavior, no Minecraft world references.
- Minecraft entities are **thin projections** of records — they handle rendering, physics, and collision, but do not own state.
- On entity spawn: project relevant fields from record onto entity.
- On entity despawn or chunk unload: extract changes from entity back to record.
- Live sync (e.g., position) is **batched**, not reactive per-field.
- Behavior systems (IAUS evaluator, abstract simulator, projector) operate **on records**, not on entities.

### 2. Tiered Simulation

Every NPC exists at one of four tiers, determined by chunk-load state and player proximity to the NPC's home village (NPCs are tier-pinned to their village's loaded state, not their personal position — this keeps groups coherent):

- **Tier 0 (Active)**: Full IAUS evaluation, full pathfinding, full interactions. Entity is projected. Runs on entity tick (with re-evaluation cadence ~20 ticks or on event, not every tick).
- **Tier 1 (Nearby/Loaded)**: Chunk loaded, no player close. Stripped IAUS evaluator (only important considerations: hunger, sleep, danger; only important actions). Reduced cadence.
- **Tier 2 (Dormant)**: Chunk unloaded. No entity. Abstract simulation runs every **100 ticks** (~5s real-time), staggered across NPCs by UUID hash to avoid synchronized spikes. Per-tick budget cap; overflow defers to next real tick.
- **Tier 3 (Cold)**: Untouched for a long time. State frozen, only updates when queried.

**Critical**: Considerations are **pure functions over NPC state**, shared between Tier 0 and Tier 1 evaluators. Tuning one tunes both. Tier 2 does **not** run IAUS — it runs aggregate rules that produce summaries (food consumed, gold delta, location change, relationships modified).

**Tier transitions catch up state**: When an NPC transitions Tier 2 → Tier 0, run any overdue abstract ticks immediately before projecting the entity. Don't project a stale record.

**Abstract simulation goal: plausible, not bit-identical.** Do not chase parity with active simulation.

### 3. Persistence: Custom Sharded Database with ORM Layer

- **Not** using Minecraft's `SavedData` (whole-file rewrite cost, NBT overhead).
- Sharded flat-file approach: one file per region (or per village) plus global state files. Custom binary format.
- **ORM layer** maps records ↔ binary. Reflection or codec-based; do not write per-record manual serialization (ages badly past ~5 record types).
- **Schema versioning from day one**: every persisted type has a `dataVersion` field and a migration chain, even if v1→v1 is a no-op.

**Persistence files (logical separation):**

- **NPCRegistry** — master `UUID → NPCRecord` store. Sharded by region.
- **FactionStore** — global. Faction definitions, faction-faction relationship matrix, leadership, territory claims.
- **VillageStore** — per-village state: bounds, bed registry (with role assignments and current occupant UUIDs), workstation registry, audit results, market state, shared resources, faction ownership.
- **SimulationScheduler** — persisted task queue: which Tier 2 NPCs are due next, pending births, scheduled events.

**Required properties:**

- **Atomic writes**: write to `*.tmp`, fsync, rename. No exceptions.
- **Backup-on-corrupt**: failed parse on load → rename to `*.corrupt-<timestamp>`, start fresh for that shard, log loudly. Never crash the world.
- **Save off main thread**: snapshot on main thread, write on worker thread.
- **Indices are derived, not stored**: faction → NPC list, village → NPC list, role → NPC list — built in memory on load.
- **No cross-shard transactions**: design shards so each is independently consistent. If a logical write spans shards, redesign the shard boundary.

### 4. Threading Model: Main Server Thread

All simulation runs on the **main server thread**. No worker threads for AI/simulation logic. The DB write thread is the only background thread. Do not assume thread safety in records, registry, or AI systems.

### 5. Determinism: Not Required

Same seed + same actions need not produce identical outcomes. Random rolls in abstract ticks can use any RNG. Do not pay engineering cost for determinism.

### 6. NPC Record Schema (v1)

Each `NPCRecord` contains:

- **Identity**: UUID, name, age, gender, traits/personality vector
- **Location**: home village ID, last known position, dimension, current tier
- **Vitals**: health, hunger, energy/sleep, mood
- **Inventory**: items + provenance metadata (where each item came from: crafted/looted/bought/gifted — not used in v1 mechanics, but stored for v2 trade/theft systems)
- **Economic state**: gold, debts, owned property references
- **Role**: current role ID, workplace reference, schedule offset
- **Relationships**: sparse `UUID → opinion modifier` map (only stores deviations from faction defaults)
- **Memory**: capped ring buffer of significant events ("attacked by player X", "received gift from Y")
- **Action state**: current action, sub-step progress, interruption-safe checkpoint

### 7. Faction & Relationship Model

- NPCs hold opinions of **factions**, not of every other NPC.
- Individual NPC-to-NPC relationships are **sparse exceptions** (you killed their brother, they owe you a debt) stored only when they exist.
- Per-NPC opinion of every other NPC is O(n²) and forbidden.

### 8. Roles: Data-Driven

- Roles defined in JSON/datapack, not hardcoded classes.
- A role describes: daily schedule template, needed items, produced items, workplace requirements, social standing.
- This is what makes the mod extensible — addon mods can register new roles.
- **Children are a role**, not a separate NPC class. Aging-up is a role transition.

---

## Village Generation & Population (v1 Scope)

- Vanilla village structures retained for v1. **No worldgen overhaul.**
- After village generation, run a **post-generation audit pass** (worldgen-time, not chunk-discovery-time):
  1. Count beds → target population N.
  2. Audit production capacity: farm tiles, workstations by type, water access.
  3. If below minimums, augment: place additional fields, add missing critical buildings from a template pool. In extreme cases mark village as "outpost" tier with reduced population.
- **Audit assigns roles to beds**, not just to NPCs. Greedy spatial algorithm: for each workstation, find nearest unassigned bed, assign that role. Leftover beds become generic roles (laborer, child, elder).
- NPCs spawn one-per-bed.
- **Existing-world compatibility is explicitly not a goal.** Worldgen-time audit only.

---

## Entity Spawning Behavior

- Entities are **persisted in chunks like vanilla** (deserialized on chunk load, serialized on chunk unload). No burst-spawn-on-player-approach.
- Entity NBT contains UUID pointing at the record database.
- **Reconciliation pass on `ChunkEvent.Load`**:
  - Entity exists, no record → log, despawn orphan. Don't crash. Don't synthesize a placeholder.
  - Record claims to be in this chunk, no entity → spawn entity from record (self-healing).
  - Both exist → verify consistency, fix mismatches.

---

## Births (deferred to Phase 3)

Births were originally step 7 of the v1 plan. They are now deferred behind the Phase 2 substrate work (see Implementation Order). The mechanics below are still the committed design — they're gated, not redesigned.

- NPCs choose to have children based on IAUS considerations, gated by **bed availability** in the home village.
- **Bed reservation on conception**: when conception happens, the bed is reserved immediately for the future child to prevent races between couples.
- Pregnancy is a slow timer; gives a hook for future complications/miscarriage if desired.
- Births are a Tier 2 mechanic — most pregnancies elapse while chunks are unloaded. Predicated on Phase 2 step 7 (Tier 2 abstract simulator) being in place.
- **No migration.** NPCs do not travel between villages. (Deferred because handling long-distance travel through unloaded chunks is its own project.)

---

## Economy (v1 Scope)

Three mechanics. Anything beyond this is v2.

1. **NPCs have inventories and consume from them.** Hunger ticks down food. No food → seek food (buy/forage/steal/beg, depending on personality + role).
2. **Production and consumption roles.** Baker produces bread (consumes wheat). Farmer produces wheat (consumes time + field). Trade is surplus → deficit. Prices flat at first.
3. **Per-village market state.** Simple supply/demand number per item category. Prices float around a base value.

**Items track provenance metadata** (crafted/looted/bought/gifted) from v1 even though it's unused — this is the seed for v2 stolen-goods detection, faction trade reputation, etc.

**Explicitly out of scope for v1:**

- Inter-village exchange rates
- Currency inflation
- Multi-step production chains (wheat → flour → bread → sandwich)
- Caravans

---

## IAUS Evaluator Notes

Author has prior IAUS experience but only for a centralized evaluator (one big think). Per-agent evaluation needs the following discipline:

- **Consideration caching with TTLs**: hunger is current-tick; "is there food in this village" cached ~30s; "regional threat level" cached several minutes. Background system refreshes caches on their own cadence.
- **Re-evaluation on event or slow cadence (~20 ticks)**, not every tick.
- **Two evaluators sharing considerations**: full (Tier 0) and stripped (Tier 1).
- **Action atomicity**: actions are sequences (pathfind → enter → buy → eat → leave). Re-evaluation between sub-steps only, never mid-pathfind.
- **Per-tick budget**: round-robin scheduler, only N NPCs evaluate per tick.

---

## Implementation Order

Resist building "fun" features before the foundation is solid.

**Phase 1 — done.** Foundation steps shipped:

1. ~~**Registry + ORM + persistence**~~. Sharded registry, codec-based ORM, atomic writes, corrupt-file quarantine, schema-versioned migrations.
2. ~~**Entity ↔ record projection layer + reconciliation pass**~~. Puppet model with `ChunkEvent.Load` reconciler.
3. ~~**Village audit + bed-based spawning**~~. POI-driven audit, deterministic bed UUIDs, role assignment.
4. ~~**IAUS evaluator**~~ with hunger / sleep / work. Tier-0 full and Tier-1 stripped evaluators sharing considerations. Caching with TTLs, per-tick budget, round-robin scheduler.
5. ~~**Economy v1**~~: inventories, role-based production/consumption, per-village market state with supply/demand-floated prices.
6. ~~**Factions + sparse relationships**~~. Per-village factions auto-created at audit, FactionStore + faction-faction matrix, OpinionResolver chain (UUID override → faction-opinion override → matrix → 0).

**Phase 2 — overhaul before more features.** The original step 7 (births) is deferred. Phase 1 produced a skeletal IAUS, a skeletal economy, and faction data with no behavior wired in — births layered onto that would be paint on plywood. Resist adding more roles, more considerations, more record fields until the substrate underneath them does real work.

The four substrate gaps, in priority order:

7. **Tier 2 abstract simulator**. The biggest hole. Currently nothing simulates while chunks are unloaded — NPCs don't "live" away from the player, which contradicts the core design goal. Implement the staggered-by-UUID 100-tick scheduler, the per-tick budget, the abstract aggregate rules (food consumed, gold delta, location change, relationships modified), and the catch-up pass that runs overdue ticks before re-projecting on Tier 2 → Tier 0 promotion. Anything that wants to happen offline (births included) plugs into this.
8. **Action depth + real economic flow**. Most actions are stubs. Close the loops: Work must actually push goods into the village market on a cadence; Sleep must restore energy through a coherent cycle; sub-step execution must be observable end-to-end in a running world. The market and roles already exist — wire production through them so the economy is *load-bearing*, not just persisted.
9. **Consequence layer**. No NPC can die. Memory events almost never fire from gameplay. Faction matrix never changes. Add: NPC death (with permanence), memory-event hooks on combat / theft / gifts / role changes, and event-driven faction-opinion deltas. Without this, "world has consequence" is aspirational.
10. **Dynamic tiering**. Tier assignment is currently essentially static and group-incoherent. Implement the doc's "tier-pinned to home village's loaded state" rule. (The catch-up half of this lands inside step 7; whatever doesn't fit there lives here.)

**Phase 3 — fun features.** Only after Phase 2:

11. **Births + child role.** Original step 7. Naturally a Tier 2 mechanic; gated on bed availability; pregnancy timer; child role transition.
12. Player-facing surface (dialog, trade UI, combat-with-NPCs).

Every "living-world" project that dies, dies because someone built the fun parts on top of a shaky simulation core. Foundation first, depth second, features third.

---

## Non-Goals (Explicit)

- Backward compatibility with existing worlds.
- Building on top of MCA Reborn or any existing NPC mod.
- Determinism.
- Migration between villages (v1).
- Worldgen overhaul (v1).
- Multi-step production chains (v1).
- Per-NPC opinion matrices (ever).
- Bit-identical abstract vs. active simulation (ever).

---

## Working Style for Claude Code

- When a decision in this doc conflicts with a "best practice" from training data, **the doc wins**. These were deliberate choices.
- When something is ambiguous or not specified, ask before assuming.
- When implementing, surface trade-offs explicitly rather than making silent choices about perf/correctness/scope.
- Prefer small, testable units. The simulation core needs to be debuggable in isolation from Minecraft (records are POJOs, considerations are pure functions — exploit this for unit tests).
- NeoForge 1.21.1 specifics matter. Verify event names, registry APIs, and lifecycle hooks against actual NeoForge 1.21.1 docs rather than relying on memory of older Forge versions.
