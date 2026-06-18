package com.lightmare.villagerrealms.command;

import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.EconomicState;
import com.lightmare.villagerrealms.core.record.Faction;
import com.lightmare.villagerrealms.core.record.Gender;
import com.lightmare.villagerrealms.core.record.Identity;
import com.lightmare.villagerrealms.core.record.ItemEntry;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.MemoryEvent;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.Relationships;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.Traits;
import com.lightmare.villagerrealms.core.record.Vitals;
import com.lightmare.villagerrealms.entity.NPCEntity;
import com.lightmare.villagerrealms.server.PersistenceService;
import com.lightmare.villagerrealms.server.Reconciler;
import com.lightmare.villagerrealms.server.TierManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.util.UUID;

public final class VRCommand {

    private VRCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("vr")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("spawn").executes(VRCommand::spawn))
                        .then(Commands.literal("count").executes(VRCommand::count))
                        .then(Commands.literal("tiers").executes(VRCommand::tiers))
                        .then(Commands.literal("list").executes(VRCommand::list))
                        .then(Commands.literal("flush").executes(VRCommand::flush))
                        .then(Commands.literal("respawn").executes(VRCommand::respawn))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("uuid", UuidArgument.uuid())
                                        .executes(VRCommand::remove)))
                        .then(Commands.literal("info")
                                .then(Commands.argument("uuid", UuidArgument.uuid())
                                        .executes(VRCommand::info)))
                        .then(Commands.literal("faction")
                                .then(Commands.literal("list").executes(VRCommand::factionList))));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            src.sendFailure(Component.literal("[vr] PersistenceService not started"));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        UUID id = UUID.randomUUID();
        String name = "NPC-" + id.toString().substring(0, 6);

        NPCRecord rec = new NPCRecord(
                NPCRecord.CURRENT_VERSION,
                new Identity(id, name, 30, Gender.OTHER, Traits.NEUTRAL),
                new Location("debug", pos.x, pos.y, pos.z,
                        level.dimension().location().toString(), Tier.ACTIVE),
                Vitals.FRESH,
                NPCInventory.EMPTY,
                EconomicState.ZERO,
                new RoleState("laborer", null, 0L),
                NPCRecord.NO_FACTION,
                Relationships.EMPTY,
                MemoryLog.empty(),
                ActionState.IDLE);

        svc.registry().put(rec);
        NPCEntity entity = Reconciler.spawnFromRecord(level, rec);
        if (entity == null) {
            svc.registry().remove(id);
            src.sendFailure(Component.literal("[vr] Failed to spawn entity"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[vr] Spawned " + name + " (" + id + ")"), true);
        return 1;
    }

    private static int count(CommandContext<CommandSourceStack> ctx) {
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            ctx.getSource().sendFailure(Component.literal("[vr] PersistenceService not started"));
            return 0;
        }
        int n = svc.registry().size();
        int shards = svc.registry().shardCount();
        int dirty = svc.registry().dirtyShards().size();
        ctx.getSource().sendSuccess(
                () -> Component.literal("[vr] " + n + " NPCs across " + shards + " shards (" + dirty + " dirty)"),
                false);
        return n;
    }

    private static int tiers(CommandContext<CommandSourceStack> ctx) {
        int[] counts = TierManager.tierCounts();
        ctx.getSource().sendSuccess(
                () -> Component.literal("[vr] tiers (last sweep): active="
                        + counts[Tier.ACTIVE.ordinal()]
                        + " nearby=" + counts[Tier.NEARBY.ordinal()]
                        + " dormant=" + counts[Tier.DORMANT.ordinal()]
                        + " cold=" + counts[Tier.COLD.ordinal()]),
                false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            ctx.getSource().sendFailure(Component.literal("[vr] PersistenceService not started"));
            return 0;
        }
        CommandSourceStack src = ctx.getSource();
        for (NPCRecord rec : svc.registry().all()) {
            Location loc = rec.location();
            String line = String.format("%s %s @ %d,%d,%d role=%s tier=%s",
                    rec.identity().name(),
                    rec.identity().uuid(),
                    (int) loc.x(), (int) loc.y(), (int) loc.z(),
                    rec.role().roleId(),
                    loc.tier());
            src.sendSuccess(() -> Component.literal("[vr] " + line), false);
        }
        return svc.registry().size();
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        UUID id = UuidArgument.getUuid(ctx, "uuid");
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            ctx.getSource().sendFailure(Component.literal("[vr] PersistenceService not started"));
            return 0;
        }
        boolean had = svc.registry().get(id).isPresent();
        svc.registry().remove(id);
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof NPCEntity npc) {
                npc.discard();
                break;
            }
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("[vr] Removed " + id + (had ? "" : " (not in registry)")),
                true);
        return had ? 1 : 0;
    }

    private static int respawn(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            src.sendFailure(Component.literal("[vr] PersistenceService not started"));
            return 0;
        }
        int spawned = 0;
        int alreadyAlive = 0;
        int failed = 0;
        int dimSkipped = 0;
        int chunkUnloaded = 0;
        ServerLevel commandLevel = src.getLevel();
        String commandDim = commandLevel.dimension().location().toString();

        for (NPCRecord rec : svc.registry().all()) {
            String dim = rec.location().dimension();
            if (!dim.equals(commandDim)) {
                dimSkipped++;
                continue;
            }
            int cx = (int) Math.floor(rec.location().x()) >> 4;
            int cz = (int) Math.floor(rec.location().z()) >> 4;
            if (!commandLevel.hasChunk(cx, cz)) {
                chunkUnloaded++;
                continue;
            }
            Entity existing = commandLevel.getEntity(rec.identity().uuid());
            if (existing != null) {
                alreadyAlive++;
                continue;
            }
            NPCEntity entity = Reconciler.spawnFromRecord(commandLevel, rec);
            if (entity == null || entity.isRemoved()) {
                failed++;
            } else {
                spawned++;
            }
        }
        final int s = spawned, a = alreadyAlive, f = failed, d = dimSkipped, u = chunkUnloaded;
        src.sendSuccess(() -> Component.literal(String.format(
                "[vr] respawn: spawned=%d alreadyAlive=%d failed=%d otherDim=%d chunkUnloaded=%d",
                s, a, f, d, u)), true);
        return spawned;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        UUID id = UuidArgument.getUuid(ctx, "uuid");
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            src.sendFailure(Component.literal("[vr] PersistenceService not started"));
            return 0;
        }
        NPCRecord rec = svc.registry().get(id).orElse(null);
        if (rec == null) {
            src.sendFailure(Component.literal("[vr] No NPC with uuid " + id));
            return 0;
        }

        Identity ident = rec.identity();
        Location loc = rec.location();
        Vitals v = rec.vitals();
        RoleState role = rec.role();
        EconomicState econ = rec.economy();
        ActionState act = rec.action();

        Entity live = null;
        for (ServerLevel level : src.getServer().getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null) { live = e; break; }
        }
        String liveStatus = live == null ? "no" : "yes (" + live.level().dimension().location() + ")";

        line(src, "[vr] " + ident.name() + " (" + ident.uuid() + ")");
        line(src, "  age=" + ident.age() + " gender=" + ident.gender() + " live=" + liveStatus);
        line(src, "  village=" + loc.homeVillageId()
                + " pos=" + fmt(loc.x()) + "," + fmt(loc.y()) + "," + fmt(loc.z())
                + " dim=" + loc.dimension() + " tier=" + loc.tier());
        line(src, "  vitals: hp=" + fmt(v.health()) + " hunger=" + fmt(v.hunger())
                + " energy=" + fmt(v.energy()) + " mood=" + fmt(v.mood()));
        line(src, "  role=" + role.roleId()
                + " workplace=" + (role.workplaceRef() == null ? "<none>" : role.workplaceRef())
                + " offset=" + role.scheduleOffsetTicks() + "t");
        line(src, "  faction=" + (rec.factionId().isEmpty() ? "<none>" : rec.factionId()));
        line(src, "  economy: gold=" + econ.gold()
                + " debts=" + econ.debts().size()
                + " property=" + econ.ownedProperty().size());
        line(src, "  action: " + act.actionId() + " step=" + act.subStep()
                + " startedAt=" + act.startedAtTick()
                + (act.checkpointToken().isEmpty() ? "" : " ckpt=" + act.checkpointToken()));

        NPCInventory inv = rec.inventory();
        if (inv.items().isEmpty()) {
            line(src, "  inventory: <empty>");
        } else {
            line(src, "  inventory:");
            for (ItemEntry e : inv.items()) {
                line(src, "    " + e.count() + "x " + e.itemId()
                        + " (" + e.provenance() + ", t=" + e.acquiredAtTick() + ")");
            }
        }

        Relationships rel = rec.relationships();
        line(src, "  relationships: "
                + rel.opinions().size() + " npc, "
                + rel.factionOpinions().size() + " faction overrides");

        MemoryLog mem = rec.memory();
        if (mem.events().isEmpty()) {
            line(src, "  memory: <empty> (cap=" + mem.capacity() + ")");
        } else {
            line(src, "  memory: " + mem.events().size() + "/" + mem.capacity());
            int show = Math.min(5, mem.events().size());
            for (int i = mem.events().size() - show; i < mem.events().size(); i++) {
                MemoryEvent ev = mem.events().get(i);
                line(src, "    t=" + ev.tick() + " " + ev.kind()
                        + (ev.subjectRef() == null ? "" : " subj=" + ev.subjectRef())
                        + (ev.detail() == null ? "" : " detail=" + ev.detail()));
            }
        }
        return 1;
    }

    private static void line(CommandSourceStack src, String s) {
        src.sendSuccess(() -> Component.literal(s), false);
    }

    private static String fmt(double d) {
        return String.format("%.2f", d);
    }

    private static String fmt(float f) {
        return String.format("%.2f", f);
    }

    private static int factionList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            src.sendFailure(Component.literal("[vr] PersistenceService not started"));
            return 0;
        }
        var factions = svc.factions();
        if (factions.size() == 0) {
            src.sendSuccess(() -> Component.literal("[vr] no factions registered"), false);
            return 0;
        }
        for (Faction f : factions.all()) {
            String leader = f.leaderUuid() == null ? "<none>" : f.leaderUuid().toString();
            String claims = String.join(",", f.claimedVillageIds());
            src.sendSuccess(() -> Component.literal(
                    "[vr] " + f.id() + " name=\"" + f.name() + "\""
                            + " leader=" + leader
                            + " villages=[" + claims + "]"), false);
        }
        int relationRows = factions.relations().by().size();
        src.sendSuccess(() -> Component.literal(
                "[vr] " + factions.size() + " factions, " + relationRows + " relation rows"), false);
        return factions.size();
    }

    private static int flush(CommandContext<CommandSourceStack> ctx) {
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            ctx.getSource().sendFailure(Component.literal("[vr] PersistenceService not started"));
            return 0;
        }
        try {
            svc.registry().flushDirty();
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("[vr] flush failed: " + e.getMessage()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[vr] flushed"), true);
        return 1;
    }
}
