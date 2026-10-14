package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.consequence.Consequences;
import com.lightmare.villagerrealms.core.consequence.DeathCause;
import com.lightmare.villagerrealms.core.consequence.Refs;
import com.lightmare.villagerrealms.core.consequence.Standing;
import com.lightmare.villagerrealms.core.economy.MarketOps;
import com.lightmare.villagerrealms.core.persist.store.FactionStore;
import com.lightmare.villagerrealms.core.persist.store.NPCRegistry;
import com.lightmare.villagerrealms.core.persist.store.VillageStore;
import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.DeathRecord;
import com.lightmare.villagerrealms.core.record.Faction;
import com.lightmare.villagerrealms.core.record.ItemEntry;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.entity.NPCEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 step 9: the consequence layer's server-side glue. Vanilla events
 * on {@link NPCEntity} are translated into pure record operations
 * ({@link Consequences}, {@link Standing}) and the one irreversible
 * operation — death — is centralised in {@link #handleDeath} so the
 * entity path (LivingDeathEvent), the Tier 2 path (abstract starvation) and
 * the catch-up path (Reconciler) all bury an NPC the same way.
 *
 * Death is permanent: the record leaves the registry, the home village
 * ledgers it and frees the bed, village-mates remember it, the killer's
 * standing with the village faction drops, and a faction whose villages
 * are all empty is dissolved.
 */
public final class ConsequenceEvents {

    private static final Logger LOG = LoggerFactory.getLogger(ConsequenceEvents.class);

    /** Repeated hits by the same attacker within this window count once for standing. */
    public static final int ATTACK_STANDING_WINDOW_TICKS = 60;

    /** (victim uuid + attacker ref) → last tick a standing delta was applied. */
    private static final Map<String, Long> lastStandingHit = new HashMap<>();

    private ConsequenceEvents() {}

    public static void reset() {
        lastStandingHit.clear();
    }

    // -------- damage ----------------------------------------------------

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof NPCEntity npc)) return;
        if (npc.level().isClientSide()) return;
        if (event.getNewDamage() <= 0f) return;
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;
        NPCRecord rec = svc.registry().get(npc.getUUID()).orElse(null);
        if (rec == null) return;

        String attackerRef = refOf(event.getSource());
        if (attackerRef == null) return; // environment: nothing to hold a grudge against
        long tick = npc.level().getGameTime();

        // Being hit is an event worth re-evaluating on (IAUS notes): drop
        // whatever the NPC was doing — including a locked pathfind — so the
        // next scheduler pass is free to pick flee.
        NPCRecord hit = Consequences.attacked(rec, attackerRef, event.getNewDamage(), tick);
        svc.registry().put(withAction(hit, ActionState.IDLE));
        ActiveStepRuntime.forget(npc.getUUID());

        String key = npc.getUUID() + "|" + attackerRef;
        Long last = lastStandingHit.get(key);
        if (last == null || tick - last >= ATTACK_STANDING_WINDOW_TICKS) {
            lastStandingHit.put(key, tick);
            adjustStanding(svc.factions(), rec.factionId(), attackerRef, Standing.ATTACK_DELTA);
        }
    }

    // -------- death -----------------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof NPCEntity npc)) return;
        if (!(npc.level() instanceof ServerLevel level)) return;
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;
        NPCRecord rec = svc.registry().get(npc.getUUID()).orElse(null);
        if (rec == null) return;

        String killerRef = refOf(event.getSource());
        String cause = causeOf(event.getSource(), killerRef);
        handleDeath(svc, rec, level.getGameTime(), cause, killerRef, level);
    }

    /** Tier 2: the abstract tick drained a dormant NPC to zero health. */
    public static void onAbstractStarvation(PersistenceService svc, NPCRecord rec, long tick) {
        handleDeath(svc, rec, tick, DeathCause.STARVATION, null, null);
    }

    /**
     * Bury an NPC. Idempotent on the registry (a second call for the same
     * uuid finds no record and returns). {@code level} is non-null only on
     * the entity path, where the inventory is dropped at the death
     * position; without a level the goods fold into the village market.
     */
    public static void handleDeath(PersistenceService svc, NPCRecord rec, long tick,
                                   String cause, String killerRef, ServerLevel level) {
        UUID id = rec.identity().uuid();
        NPCRegistry registry = svc.registry();
        if (registry.get(id).isEmpty()) return;

        registry.remove(id);
        AIService ai = AIService.getOrNull();
        if (ai != null) {
            ai.abstractScheduler().forget(id);
            ai.cache().invalidate(id);
        }
        ActiveStepRuntime.forget(id);

        DeathRecord death = DeathRecord.of(rec, tick, cause, killerRef);
        String villageId = rec.location().homeVillageId();
        VillageRecord village = villageId == null ? null : svc.villages().get(villageId).orElse(null);

        if (village != null) {
            svc.villages().put(village.recordDeath(death));
            for (NPCRecord mate : registry.byVillage(villageId)) {
                registry.put(Consequences.witnessedDeath(mate, death, tick));
            }
        }

        if (killerRef != null) {
            adjustStanding(svc.factions(), rec.factionId(), killerRef, Standing.KILL_DELTA);
        }

        dropInventory(svc, rec, level, villageId);
        dissolveIfEmpty(svc, rec.factionId());

        LOG.info("[vr] {} ({}) of {} died: {}{}", rec.identity().name(), id, villageId, cause,
                killerRef == null ? "" : " by " + killerRef);
    }

    private static void dropInventory(PersistenceService svc, NPCRecord rec, ServerLevel level, String villageId) {
        if (rec.inventory() == null || rec.inventory().items().isEmpty()) return;
        if (level != null) {
            double x = rec.location().x(), y = rec.location().y(), z = rec.location().z();
            for (ItemEntry e : rec.inventory().items()) {
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(e.itemId()));
                if (item.isEmpty()) continue;
                Containers.dropItemStack(level, x, y, z, new ItemStack(item.get(), e.count()));
            }
            return;
        }
        if (villageId == null) return;
        VillageRecord v = svc.villages().get(villageId).orElse(null);
        if (v == null) return;
        var market = v.market();
        for (ItemEntry e : rec.inventory().items()) {
            market = MarketOps.addStock(market, e.itemId(), e.count());
        }
        svc.villages().put(v.withMarket(market));
    }

    /** A faction with no living members in any claimed village is wiped out. */
    private static void dissolveIfEmpty(PersistenceService svc, String factionId) {
        if (factionId == null || factionId.isEmpty()) return;
        FactionStore factions = svc.factions();
        Faction faction = factions.get(factionId).orElse(null);
        if (faction == null) return;
        for (String villageId : faction.claimedVillageIds()) {
            if (!svc.registry().byVillage(villageId).isEmpty()) return;
        }
        factions.remove(factionId);
        for (String villageId : faction.claimedVillageIds()) {
            svc.villages().get(villageId).ifPresent(v -> {
                if (factionId.equals(v.factionOwnerId())) svc.villages().put(v.withFactionOwner(null));
            });
        }
        LOG.warn("[vr] faction {} ({}) has been wiped out", factionId, faction.name());
    }

    // -------- player interaction ----------------------------------------

    /**
     * Right-click dispatch (main hand only, server side):
     *   - empty hand → the NPC's trade menu (chat);
     *   - sneaking with an item → sell one unit to the market through the NPC;
     *   - otherwise with an item → gift one unit.
     * Gifts are always accepted — they're how standing is repaired — but
     * trade is refused by hostile NPCs (see {@link TradeService}).
     */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof NPCEntity npc)) return;
        if (!(npc.level() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) return;
        NPCRecord rec = svc.registry().get(npc.getUUID()).orElse(null);
        if (rec == null) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            TradeService.showMenu(svc, player, npc, rec);
        } else if (player.isShiftKeyDown()) {
            TradeService.sellHeld(svc, player, npc, rec, held);
        } else {
            gift(svc, level, player, rec, held);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void gift(PersistenceService svc, ServerLevel level, Player player, NPCRecord rec, ItemStack held) {
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        long tick = level.getGameTime();
        svc.registry().put(Consequences.giftReceived(rec, Refs.player(player.getUUID()), itemId, 1, tick));
        adjustStanding(svc.factions(), rec.factionId(), Refs.player(player.getUUID()), Standing.GIFT_DELTA);
        if (!player.getAbilities().instabuild) held.shrink(1);
        player.sendSystemMessage(Component.literal(rec.identity().name() + " accepts your " + held.getHoverName().getString() + "."));
    }

    static void adjustStandingFor(FactionStore factions, String factionId, String target, int delta) {
        adjustStanding(factions, factionId, target, delta);
    }

    private static NPCRecord withAction(NPCRecord r, ActionState a) {
        return new NPCRecord(
                r.dataVersion(), r.identity(), r.location(), r.vitals(), r.inventory(),
                r.economy(), r.role(), r.factionId(), r.relationships(), r.memory(), a);
    }

    // -------- helpers ---------------------------------------------------

    private static void adjustStanding(FactionStore factions, String factionId, String target, int delta) {
        if (factionId == null || factionId.isEmpty()) return;
        factions.setRelations(Standing.adjust(factions.relations(), factionId, target, delta));
    }

    /** Ref for whoever is behind a damage source; null for environmental sources. */
    static String refOf(DamageSource source) {
        if (source == null) return null;
        Entity e = source.getEntity();
        if (e == null) return null;
        if (e instanceof Player p) return Refs.player(p.getUUID());
        if (e instanceof NPCEntity n) return Refs.npc(n.getUUID());
        return Refs.entity(EntityType.getKey(e.getType()).toString());
    }

    static String causeOf(DamageSource source, String killerRef) {
        if (Refs.isPlayer(killerRef)) return DeathCause.PLAYER;
        if (killerRef != null) return DeathCause.ENTITY;
        if (source != null && source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return DeathCause.ENVIRONMENT;
        if (source != null && "starve".equals(source.getMsgId())) return DeathCause.STARVATION;
        return DeathCause.ENVIRONMENT;
    }
}
