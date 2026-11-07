package com.gameadmin.mod;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge event hooks that run on the server: the scheduler, passives and damage. */
public final class ServerEvents {
    private ServerEvents() {}

    private static boolean cleaned = false;

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        if (!cleaned) {          // remove effect entities left behind by a crash or a stopped server
            cleaned = true;
            WorldVfx.cleanup(server);
        }
        Scheduler.tick(server);
        Passives.tick(server);
    }

    /** Adaptation and Hazard Immunity: cancel the hit before any damage is taken. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!Passives.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
            event.setCanceled(true);
        }
    }
}
