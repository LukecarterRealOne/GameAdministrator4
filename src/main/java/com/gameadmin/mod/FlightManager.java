package com.gameadmin.mod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server side flight: grants/removes the player's ability to fly and gives them a glowing aura. */
public final class FlightManager {
    private FlightManager() {}

    private static final float FLY_SPEED = 0.12f;      // vanilla creative flight is 0.05
    private static final float DEFAULT_SPEED = 0.05f;

    private static final Set<UUID> FLYING = new HashSet<>();

    /** After a game mode change vanilla resets the player's abilities. If flight was on, switch it back on. */
    public static void reapply(ServerPlayer player) {
        if (!FLYING.contains(player.getUUID())) return;
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed(FLY_SPEED);
        player.onUpdateAbilities();
    }

    public static void set(ServerPlayer player, boolean on) {
        MinecraftServer gateServer = Gate.server(player);
        if (gateServer != null && !Gate.allowed(player, gateServer)) {
            Gate.deny(player);
            return;
        }
        Abilities abilities = player.getAbilities();
        if (on) {
            FLYING.add(player.getUUID());
            abilities.mayfly = true;
            abilities.flying = true;
            abilities.setFlyingSpeed(FLY_SPEED);
        } else {
            FLYING.remove(player.getUUID());
            boolean keep = player.isCreative() || player.isSpectator();
            abilities.mayfly = keep;
            abilities.flying = keep && abilities.flying;
            abilities.setFlyingSpeed(DEFAULT_SPEED);
        }
        player.onUpdateAbilities();

        if (player.level() instanceof ServerLevel level) {
            MinecraftServer server = level.getServer();
            String u = player.getUUID().toString();
            java.util.UUID me = player.getUUID();
            try {
                if (on) {
                    run(server, "effect give " + u + " minecraft:glowing infinite 0 true");
                    WorldVfx.column(server, me, "cyan_stained_glass", 1.4, 0.1, 0.5, 4.0, 10, 10);
                    WorldVfx.ring(server, me, "light_blue_stained_glass", 0.5, 4.0, 0.05, 10);
                    WorldVfx.sound(server, me, "minecraft:item.elytra.flying", 1.0f, 1.2f);
                } else {
                    WorldVfx.ring(server, me, "white_stained_glass", 2.5, 0.5, 0.05, 8);
                    WorldVfx.sound(server, me, "minecraft:entity.phantom.flap", 1.0f, 0.8f);
                    run(server, "effect clear " + u + " minecraft:glowing");
                    run(server, "effect give " + u + " minecraft:slow_falling 8 0 true");
                }
            } catch (Exception ex) {
                GameAdminMod.LOGGER.warn("Flight aura failed", ex);
            }
        }
        Net.send(player, new Net.FlightState(on));
    }

    private static void run(MinecraftServer server, String command) {
        Cmd.run(server, command);
    }
}
