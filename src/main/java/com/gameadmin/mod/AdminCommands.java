package com.gameadmin.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Collection;

/**
 * Operator tools:
 *   /gameadmin grant <player>   give a player the console + powers
 *   /gameadmin revoke <player>  take them away again
 *   /gameadmin list             who has access
 *   /gameadmin status           do I have access?
 */
public final class AdminCommands {
    private AdminCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("gameadmin")
                .then(Commands.literal("grant").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", GameProfileArgument.gameProfile()).executes(c -> {
                            int n = 0;
                            for (GameProfile p : GameProfileArgument.getGameProfiles(c, "players")) {
                                boolean added = Gate.grant(p.getName());
                                n++;
                                ServerPlayer online = c.getSource().getServer().getPlayerList().getPlayer(p.getId());
                                if (online != null) {
                                    Gate.sendAccess(online);
                                    if (added) Gate.notice(online, "You were given the Admin Console (press G)", 0xA6FF9E);
                                }
                                final String name = p.getName();
                                c.getSource().sendSuccess(() -> Component.literal(
                                        (added ? "Granted Game Administrator access to " : "Already had access: ") + name), true);
                            }
                            return n;
                        })))
                .then(Commands.literal("revoke").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", GameProfileArgument.gameProfile()).executes(c -> {
                            int n = 0;
                            for (GameProfile p : GameProfileArgument.getGameProfiles(c, "players")) {
                                boolean removed = Gate.revoke(p.getName());
                                n++;
                                ServerPlayer online = c.getSource().getServer().getPlayerList().getPlayer(p.getId());
                                if (online != null) {
                                    Gate.sendAccess(online);
                                    if (removed) Gate.notice(online, "Your Admin Console access was removed", 0xFF5555);
                                }
                                final String name = p.getName();
                                c.getSource().sendSuccess(() -> Component.literal(
                                        (removed ? "Removed access from " : "Was not on the list: ") + name), true);
                            }
                            return n;
                        })))
                .then(Commands.literal("list").requires(s -> s.hasPermission(2)).executes(c -> {
                    var names = Gate.list();
                    c.getSource().sendSuccess(() -> Component.literal(names.isEmpty()
                            ? "No one was granted access (server owners and the single player host always have it)."
                            : "Game Administrators: " + String.join(", ", names)), false);
                    return names.size();
                }))
                .then(Commands.literal("status").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    boolean ok = Gate.allowed(p, c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal(ok
                            ? "You have Game Administrator access. Press G."
                            : "You do not have access. Ask an operator to run /gameadmin grant " + p.getGameProfile().getName()), false);
                    return ok ? 1 : 0;
                })));
    }

    /** Tell each joining client whether its console is unlocked. */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Gate.sendAccess(p);
    }
}
