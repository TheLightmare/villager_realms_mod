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

7. ~~**Tier 2 abstract simulator**~~ — **substantially done.** `AbstractScheduler` runs DORMANT NPCs on a UUID-staggered 100-tick cadence with a per-tick budget (32) and overflow deferral; `AbstractTick` applies the aggregate rules via `Substeps` (hunger/energy drain, auto-eat, auto-buy from market, role work output into market, sleep restore); `Reconciler.applyCatchUp` runs overdue cycles before every entity projection. `lastAbstractTick` is in-memory by design (a restart loses at most one cycle — acceptable under "plausible, not bit-identical"); there is no persisted `SimulationScheduler` queue and none is needed until something must survive a restart.
   **Still open under this step:** abstract *location change* and *relationship modification* rules. Neither has a driver in v1 (no migration; relationship deltas arrive with step 9's events), so they're deferred until a consumer exists — do not add them speculatively.
8. ~~**Action depth + real economic flow**~~ — **done.** The action → substep → `Substeps` → market loop was already wired; what made the economy non-load-bearing was data and accounting, now fixed:
   - **Roles are datapack JSON** (`data/<ns>/villager_realms/roles/<id>.json`, format in `RoleJson`), loaded by `RoleLoader` on every server reload; `RoleRegistry.defaults()` is the code fallback and a parity test pins the shipped JSON to it. Addon mods add roles by shipping files.
   - **Every NPC has income.** Laborer and the nine non-food vanilla professions (librarian, toolsmith, …) are *foragers*: workers with no workstation kind who produce `RoleRegistry.FORAGE_ITEM` at the village centre. Without this, anyone not owning a food workstation started at 0 gold and could never buy.
   - **Bakers exist.** Vanilla has no baker POI, so `VillageAuditor` runs `RoleAssignment.deriveBakers` — one leftover bed per two farmers — giving wheat its consumer.
   - **Village-centre targets are at ground level** (`VillageRecord.groundY()` = median bed Y). The old bbox Y-midpoint was mid-air, so `buy` could never satisfy its arrival check.
   - **Prices track recent flow**: `MarketOps.decay(0.9)` runs on the autosave cadence (it was never called).
   - **Action atomicity enforced**: `ActiveStepRuntime` stamps `ActionState.CKPT_PATHFINDING` while walking and `StepExecutor` refuses to swap actions mid-pathfind; a 600-tick pathfind timeout bounds the lock.
   - **Observable in-game**: `/vr village list`, `/vr market <villageId>` (stock / supply / demand / price / ledger), and `/vr info` now shows the concrete substep.
   Known limits, by design: the market ledger may go negative (it prints money to pay producers); producers keep none of their own output; foragers all gather the same item.
9. ~~**Consequence layer**~~ — **done.** Nothing existed before this: `MemoryLog.append`, `Relationships.withOpinion`, `FactionStore.setRelations` and `OpinionResolver` had zero production callers, and vanilla damage *resurrected* NPCs (entity dies → record keeps hp≈0 → next chunk load projects it at 0.001 hp). Now:
   - **Death is permanent.** `ConsequenceEvents.handleDeath` is the single burial path for all three ways to die — `LivingDeathEvent` on the entity, Tier 2 abstract starvation (`AIService.commitAbstract`), and starvation during catch-up (`Reconciler.applyCatchUp`, which cancels the entity join). It removes the record from the registry, appends a `DeathRecord` (uuid, name, role, tick, cause, killer ref) to the home village's ledger and frees the bed (`VillageRecord.recordDeath`; VillageStore v3→v4), makes every living village-mate remember it, drops the killer's standing, and dissolves a faction whose claimed villages are all empty. The auditor consults the ledger so a dead NPC's bed is never repopulated. Inventory drops as items at a Tier 0 death and folds into the market at a Tier 2 death; gold is lost.
   - **Starvation kills.** At hunger 0, `Substeps.starve` drains 1 hp / 600 ticks (half a day from full). Tier 0/1 deals it as vanilla starvation damage (`ActiveStepRuntime`, 1 hp per 600 ticks via `hurt()` so it flows through `LivingDeathEvent`); Tier 2 drains the record in `AbstractTick` *after* the feed attempt. This is what makes the economy load-bearing: a village whose food loop breaks loses people.
   - **Memory fires from gameplay.** `core/consequence/Consequences` (pure): `attacked` (victim), `witnessedDeath` (every village-mate), `giftReceived` (right-click an NPC with an item in the main hand; one unit moves to the NPC with `GIFTED` provenance), `roleChanged` (hook for the step 11 age-up; no caller yet). Theft has no mechanic in v1, so no hook.
   - **Opinions move.** Subjects are `Refs` (`player:<uuid>`, `npc:<uuid>`, `entity:<type>`). Personal grudges go in the sparse `Relationships.opinions` map keyed by uuid (attack −10, witnessed kill −20, gift +5, clamped ±100). A faction's standing with a player is a row in the existing faction matrix against the player's ref (`Standing`: attack −5 rate-limited to once per 60 ticks per attacker, kill −25, gift +2) — players aren't factions, and this needs no new store. `OpinionResolver.effectiveTowards` resolves either kind of target through the same precedence chain.
   - **Observable:** `/vr deaths <village>`, `/vr faction relations` (the matrix, including player rows), and `/vr info` now prints opinion values.
   **Not yet consumed:** no consideration or action reads opinions — NPCs don't flee or refuse trade based on standing. That is the player-facing surface (step 12), and the data is now there for it.
10. ~~**Dynamic tiering**~~ — **done.** `TierManager` sweeps every 40 ticks and pins every NPC of a village to one tier from the village's center-chunk load state and nearest-player distance (ACTIVE ≤ 96 blocks, else NEARBY; unloaded → DORMANT, → COLD after 72k continuous unloaded ticks). On a live → offline demotion it stamps `AbstractScheduler.markCurrent` so abstract sim resumes from the demotion tick rather than replaying the live period. The ≤40-tick window where a freshly-loaded entity's record is still DORMANT is harmless: `AbstractTick.advance` over <100 ticks is a no-op.

**Phase 3 — fun features.** Only after Phase 2:

11. ~~**Births + child role**~~ — **done.** Original step 7.
   - **State lives in the persisted `ScheduleStore`** (`schedule.bin`, the "SimulationScheduler" file this doc lists): a queue of `ScheduledEvent`s (BIRTH, AGE_UP) keyed by id, pumped every 100 ticks by `BirthService`. No NPCRecord schema change.
   - **Bed reservation on conception.** `VillageBed.reservedFor` (VillageStore v4→v5; the shared bed codec was split into `BED_LEGACY`/`BED` so the frozen v1–v4 codecs keep decoding) holds the unborn child's pre-generated uuid. A bed is free only with no occupant *and* no reservation; `RecordVillageView.freeBeds` reports that, so two couples can't race for one bed.
   - **Shared readiness, both tiers.** `core/birth/Readiness` (adult 18–50, hunger ≥ 14, energy ≥ 0.5, free bed, village has food, not in cooldown) feeds `ConceiveAction` at Tier 0 (base weight 0.3 — wins only when nothing presses, e.g. rested at night) and the abstract sweep in `BirthService` for villages with no ACTIVE NPC (per-cycle roll, staggered by village hash). "Already expecting" is derived from the `CHILD_CONCEIVED` memory event: the cooldown (5 days) exceeds the pregnancy (3 days), so no flag is needed.
   - **Two parents, gender-agnostic.** `Partners.choose` picks another eligible adult of the village with non-negative mutual personal opinion, warmest first, uuid tie-break. Gender is still never assigned at audit; newborns get a random one.
   - **Delivery** (`Delivery.deliver`): claims the reserved bed (falls back to any free bed; drops the birth if none), builds the child (`child` role, age 0, 3 bread, up to 10 gold from each parent, family bonds +20 both ways, `BORN_TO`/`CHILD_BORN` memories), schedules AGE_UP at +10 days, and spawns the entity if the chunk is loaded. Either parent may be dead by then.
   - **Age-up inherits the bed's role** (`AgeUp.apply`): the audit's bed→role map is the source of truth, so a child born into a dead farmer's bed grows up a farmer with the nearest farmer station; roles without a station work at the centre. Goes through `Consequences.roleChanged`.
   - **Observable:** `/vr schedule`, `/vr conceive <uuid>` (forces one attempt and says why it failed), reserved/free bed counts in `/vr village list`.
   Known limits: no pregnancy complications (the timer is the hook); children don't earn and live on starting food/gold until age-up — a 10-day childhood with 3 bread means a child will usually need to buy food, so a village that can't feed itself loses its children first.
12. **Player-facing surface** — **reactions and trade done; dialog and GUI remain.**
   - **Danger is finally a consideration.** `DangerConsideration` (essential) = max(recently attacked — the newest `ATTACKED` memory fading over 600 ticks, pure over the record — and a hostile actor nearby via the new nullable `EvalContext.threatView`). `FleeAction` (essential, listed before the needs actions) runs the opposite way from a visible threat at 0.9 speed, or goes home on memory alone, so Tier 1 flees too. Being hit resets the NPC's action (an event-driven re-evaluation, per the IAUS notes) so a locked pathfind doesn't delay the flight.
   - **Threats are built server-side** (`Threats.view`): nearest non-creative, non-spectator player within 16 blocks whose `Attitude` towards the NPC is HOSTILE. Resolved per evaluation and per drive through `AIScheduler.ThreatViewResolver`; Tier 1 gets none.
   - **Standing has one meaning** — `Attitude.of(opinion)`: HOSTILE ≤ −30, WARY < 0, NEUTRAL, FRIENDLY ≥ 20 — and every player-facing gate reads it.
   - **Trade is with the village market through any adult NPC**, presented as clickable chat (`TradeService`, `/vrtrade buy <npc> <item> [count]`): empty-hand right-click opens the menu, sneak + right-click with an item sells one unit, right-click with an item is still a gift. Terms come from the pure `Trade`: buy markup FRIENDLY ×1.0 / NEUTRAL ×1.25 / WARY ×1.5, sell fraction 0.8 / 0.6 / 0.4, HOSTILE refuses. Purchases bump demand and pay the ledger, sales add supply and draw on it — the player is an ordinary market participant. **One gold = one emerald** in the player's inventory; players have no gold stat and don't need one. Each trade nudges the NPC's personal opinion +1 and writes a `TRADED` memory.
   - **Still open:** dialog (greeting reflecting standing/mood/memory) and a proper GUI (`Screen` + menu + payloads). The chat layer is the server logic a GUI would call; it was chosen so the step could be built and tested without compiling client code.
   Design limit: NPCs never fight back (flee only). Combat retaliation by aggression trait is a later slice.

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
