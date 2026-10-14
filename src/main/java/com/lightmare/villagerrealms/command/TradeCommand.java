package com.lightmare.villagerrealms.command;

import com.lightmare.villagerrealms.server.PersistenceService;
import com.lightmare.villagerrealms.server.TradeService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

/**
 * Player-level trade command, the target of the clickable lines in the NPC
 * trade menu. No permission level: any player may run it, but
 * {@link TradeService#buy} only completes when the named NPC is within
 * reach of the player.
 */
public final class TradeCommand {

    private TradeCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("vrtrade")
                        .then(Commands.literal("buy")
                                .then(Commands.argument("npc", UuidArgument.uuid())
                                        .then(Commands.argument("item", StringArgumentType.string())
                                                .executes(ctx -> buy(ctx, 1))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(ctx -> buy(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))));
    }

    private static int buy(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only players can trade."));
            return 0;
        }
        PersistenceService svc = PersistenceService.getOrNull();
        if (svc == null) {
            src.sendFailure(Component.literal("Villager Realms isn't running."));
            return 0;
        }
        UUID npc = UuidArgument.getUuid(ctx, "npc");
        String item = StringArgumentType.getString(ctx, "item");
        return TradeService.buy(svc, player, npc, item, count) ? 1 : 0;
    }
}
