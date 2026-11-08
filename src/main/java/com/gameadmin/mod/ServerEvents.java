package com.gameadmin.mod;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/** Forge event hooks that run on the server: the scheduler, passives, time stop and damage. */
public final class ServerEvents {
    private ServerEvents() {}

    private static boolean cleaned = false;

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        if (!cleaned) {          // remove effect entities left behind by a crash or a stopped server
            cleaned = true;
            WorldVfx.cleanup(server);
        }
        Scheduler.tick(server);
        Passives.tick(server);
        TimeStop.tick(server);
    }

    /** Adaptation and Hazard Immunity: cancel the hit before any damage is taken. */
    public static void onIncomingDamage(LivingAttackEvent event) {
        if (!Passives.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
            event.setCanceled(true);
        }
    }
}
