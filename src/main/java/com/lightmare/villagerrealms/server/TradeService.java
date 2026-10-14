package com.lightmare.villagerrealms.server;

import com.lightmare.villagerrealms.core.birth.Births;
import com.lightmare.villagerrealms.core.consequence.Attitude;
import com.lightmare.villagerrealms.core.consequence.Consequences;
import com.lightmare.villagerrealms.core.consequence.MemoryKinds;
import com.lightmare.villagerrealms.core.consequence.Refs;
import com.lightmare.villagerrealms.core.economy.Trade;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.entity.NPCEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Phase 3 step 12: player ↔ village-market trade through an NPC, presented
 * as chat (clickable lines) rather than a screen — zero client code, and
 * the same methods a GUI would call. Terms come from the pure
 * {@link Trade}; the NPC's {@link Attitude} to the player sets the margin
 * and hostile NPCs refuse outright.
 *
 * Players have no gold stat: one gold is one emerald in the player's
 * inventory, both ways.
 */
public final class TradeService {

    /** The NPC must be this close for a trade command to go through. */
    public static final double MAX_TRADE_DIST_SQ = 8.0 * 8.0;
    public static final int MAX_MENU_LINES = 24;
    /** Personal opinion nudge per completed trade. */
    public static final int TRADE_PERSONAL_DELTA = 1;

    private TradeService() {}

    // -------- menu --------------------------------------------------------

    public static void showMenu(PersistenceService svc, ServerPlayer player, NPCEntity npc, NPCRecord rec) {
        Attitude attitude = Attitude.towardsPlayer(rec, player.getUUID(), svc.factions().relations());
        if (Births.CHILD_ROLE.equals(rec.role().roleId())) {
            say(player, rec, "is too young to trade.", ChatFormatting.GRAY);
            return;
        }
        if (!Trade.canTrade(attitude)) {
            say(player, rec, "wants nothing to do with you.", ChatFormatting.RED);
            return;
        }
        VillageRecord village = villageOf(svc, rec);
        if (village == null) {
            say(player, rec, "has nothing to sell.", ChatFormatting.GRAY);
            return;
        }
        VillageMarket m = village.market();

        player.sendSystemMessage(Component.literal("— " + rec.identity().name() + " (" + rec.role().roleId()
                + ") of " + village.villageId() + " — " + attitude.name().toLowerCase() + " —")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("You have " + countEmeralds(player) + " emeralds. "
                + "Sneak + right-click with an item to sell it.").withStyle(ChatFormatting.GRAY));

        TreeSet<String> items = new TreeSet<>(m.stockpile().keySet());
        int shown = 0;
        for (String itemId : items) {
            int stock = m.stockOf(itemId);
            if (stock <= 0) continue;
            if (shown++ >= MAX_MENU_LINES) {
                player.sendSystemMessage(Component.literal("  … and more").withStyle(ChatFormatting.GRAY));
                break;
            }
            long price = Trade.buyPrice(m, itemId, attitude);
            String cmd = "/vrtrade buy " + npc.getUUID() + " " + itemId + " 1";
            MutableComponent line = Component.literal("  [buy] ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(itemId + " ×" + stock + " — " + price + " emerald" + (price == 1 ? "" : "s") + " each")
                            .withStyle(ChatFormatting.WHITE));
            line.withStyle(s -> s
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Buy one " + itemId))));
            player.sendSystemMessage(line);
        }
        if (shown == 0) {
            player.sendSystemMessage(Component.literal("  (the market is empty)").withStyle(ChatFormatting.GRAY));
        }
    }

    // -------- buy ---------------------------------------------------------

    /** Returns true on a completed purchase. Messages the player either way. */
    public static boolean buy(PersistenceService svc, ServerPlayer player, UUID npcId, String itemId, int count) {
        NPCEntity npc = nearbyNpc(player, npcId);
        if (npc == null) {
            player.sendSystemMessage(Component.literal("That trader isn't near you.").withStyle(ChatFormatting.RED));
            return false;
        }
        NPCRecord rec = svc.registry().get(npcId).orElse(null);
        if (rec == null) return false;
        Attitude attitude = Attitude.towardsPlayer(rec, player.getUUID(), svc.factions().relations());
        if (!Trade.canTrade(attitude) || Births.CHILD_ROLE.equals(rec.role().roleId())) {
            say(player, rec, "won't trade with you.", ChatFormatting.RED);
            return false;
        }
        VillageRecord village = villageOf(svc, rec);
        if (village == null) return false;
        VillageMarket m = village.market();

        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(itemId));
        if (item.isEmpty() || count <= 0) {
            player.sendSystemMessage(Component.literal("No such item.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (m.stockOf(itemId) < count) {
            say(player, rec, "doesn't have that many.", ChatFormatting.RED);
            return false;
        }
        long unit = Trade.buyPrice(m, itemId, attitude);
        long total = unit * count;
        if (!player.getAbilities().instabuild && countEmeralds(player) < total) {
            say(player, rec, "wants " + total + " emeralds; you don't have that.", ChatFormatting.RED);
            return false;
        }

        if (!player.getAbilities().instabuild) takeEmeralds(player, (int) total);
        ItemStack goods = new ItemStack(item.get(), count);
        if (!player.getInventory().add(goods)) player.drop(goods, false);

        svc.villages().put(village.withMarket(Trade.buy(m, itemId, count, total)));
        remember(svc, rec, player, "sold " + count + "x " + itemId + " for " + total);
        say(player, rec, "sells you " + count + "× " + itemId + " for " + total + " emerald" + (total == 1 ? "" : "s") + ".", ChatFormatting.GREEN);
        return true;
    }

    // -------- sell --------------------------------------------------------

    /** Sell one unit of the held stack to the market through the NPC. */
    public static boolean sellHeld(PersistenceService svc, ServerPlayer player, NPCEntity npc, NPCRecord rec, ItemStack held) {
        Attitude attitude = Attitude.towardsPlayer(rec, player.getUUID(), svc.factions().relations());
        if (!Trade.canTrade(attitude) || Births.CHILD_ROLE.equals(rec.role().roleId())) {
            say(player, rec, "won't trade with you.", ChatFormatting.RED);
            return false;
        }
        VillageRecord village = villageOf(svc, rec);
        if (village == null) return false;
        VillageMarket m = village.market();
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        long price = Trade.sellPrice(m, itemId, attitude);
        if (price <= 0L) {
            say(player, rec, "isn't interested in " + itemId + ".", ChatFormatting.GRAY);
            return false;
        }

        held.shrink(1);
        ItemStack pay = new ItemStack(Items.EMERALD, (int) Math.min(price, 64));
        if (!player.getInventory().add(pay)) player.drop(pay, false);

        svc.villages().put(village.withMarket(Trade.sell(m, itemId, 1, price)));
        remember(svc, rec, player, "bought 1x " + itemId + " for " + price);
        say(player, rec, "buys your " + itemId + " for " + price + " emerald" + (price == 1 ? "" : "s") + ".", ChatFormatting.GREEN);
        return true;
    }

    // -------- helpers -----------------------------------------------------

    private static void remember(PersistenceService svc, NPCRecord rec, ServerPlayer player, String detail) {
        long tick = player.serverLevel().getGameTime();
        NPCRecord out = Consequences.remember(rec, new MemoryEvent(
                tick, MemoryKinds.TRADED, Refs.player(player.getUUID()), detail));
        out = Consequences.adjustPersonal(out, player.getUUID(), TRADE_PERSONAL_DELTA);
        svc.registry().put(out);
    }

    private static NPCEntity nearbyNpc(ServerPlayer player, UUID npcId) {
        Entity e = player.serverLevel().getEntity(npcId);
        if (!(e instanceof NPCEntity npc)) return null;
        return npc.distanceToSqr(player) <= MAX_TRADE_DIST_SQ ? npc : null;
    }

    private static VillageRecord villageOf(PersistenceService svc, NPCRecord rec) {
        String vid = rec.location().homeVillageId();
        if (vid == null || vid.isEmpty()) return null;
        return svc.villages().get(vid).orElse(null);
    }

    static int countEmeralds(ServerPlayer player) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(Items.EMERALD)) n += s.getCount();
        return n;
    }

    static void takeEmeralds(ServerPlayer player, int count) {
        for (ItemStack s : player.getInventory().items) {
            if (count <= 0) break;
            if (!s.is(Items.EMERALD)) continue;
            int take = Math.min(count, s.getCount());
            s.shrink(take);
            count -= take;
        }
    }

    private static void say(ServerPlayer player, NPCRecord rec, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(rec.identity().name() + " " + text).withStyle(color));
    }
}
