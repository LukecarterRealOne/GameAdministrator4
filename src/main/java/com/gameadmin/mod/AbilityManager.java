package com.gameadmin.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server side brain: cooldowns + what every ability does, plus the world-visible VFX (see WorldVfx) and sounds.
 * Abilities affect EVERY living thing near you (animals, villagers, monsters, other players), never yourself.
 */
public final class AbilityManager {
    private AbilityManager() {}

    private static final Map<UUID, long[]> READY_AT = new HashMap<>();
    private static boolean nextIsNight = true;
    private static int weatherStep = 0;

    private static final double HOLE_RADIUS = 26.0;
    private static final int HOLE_PULSES = 60;   // 60 x 100 ms = 6 seconds

    public static void handleCast(ServerPlayer player, int id) {
        if (id < 0 || id >= Ability.ALL.length) return;
        Ability ability = Ability.ALL[id];

        long now = System.currentTimeMillis();
        long[] ready = READY_AT.computeIfAbsent(player.getUUID(), k -> new long[Ability.ALL.length]);
        if (now < ready[id]) {
            send(player, id, Net.Feedback.COOLDOWN, (int) (ready[id] - now));
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        if (!Gate.allowed(player, server)) {
            Gate.deny(player);
            return;
        }

        int affected = 0;
        try {
            affected = switch (ability) {
                case SMITE -> smite(player, level, server);
                case BLINK -> blink(player, level, server);
                case TIME_WARP -> timeWarp(player, server);
                case WEATHER -> weather(player, server);
                case RESTORE -> restore(player, server);
                case GOD_MODE -> godMode(player, server);
                case SKYWARD -> skyward(player, server);
                case FREEZE -> freeze(player, level, server);
                case PURGE -> purge(player, level, server);
                case TIME_STOP -> timeStop(player, server);
                case SEND_NETHER -> sendToDimension(player, level, server, "minecraft:the_nether");
                case SEND_END -> sendToDimension(player, level, server, "minecraft:the_end");
                case SEND_OVERWORLD -> sendToDimension(player, level, server, "minecraft:overworld");
                case OVERDRIVE -> overdrive(player, server);
                case OMNISCIENCE -> omniscience(player, level, server);
                case ZERO_G -> zeroG(player, level, server);
                case BLACK_HOLE -> blackHole(player, level, server);
            };
        } catch (Exception ex) {
            GameAdminMod.LOGGER.warn("Ability {} failed", ability, ex);
        }

        ready[id] = now + ability.cooldownMs;
        send(player, id, Net.Feedback.CAST, affected);
    }

    private static void send(ServerPlayer player, int ability, int status, int value) {
        Net.send(player, new Net.Feedback(ability, status, value));
    }

    // ------------------------------------------------------------------ abilities

    private static int smite(ServerPlayer player, ServerLevel level, MinecraftServer server) {
        UUID me = player.getUUID();
        WorldVfx.sound(server, me, "minecraft:entity.lightning_bolt.thunder", 1.6f, 0.9f);
        int strikes = 0;
        for (LivingEntity e : targets(player, level, 28.0)) {
            if (e.distanceTo(player) < 3.5f) continue; // never zap yourself by standing next to the target
            if (strikes >= 14) break;
            runAt(server, e, "summon minecraft:lightning_bolt ~ ~ ~");
            if (strikes < 10) {
                UUID t = e.getUUID();
                WorldVfx.beam(server, t, "white_concrete", 0.8, 45, 8);
                WorldVfx.ring(server, t, "yellow_stained_glass", 1.2, 5.0, 0.05, 8);
            }
            strikes++;
        }
        if (strikes == 0) {
            double[] p = rayEnd(player, level, 48.0);
            double dx = p[0] - player.getX(), dz = p[2] - player.getZ();
            if (dx * dx + dz * dz > 16.0) {
                run(server, "execute as " + me + " at @s run summon minecraft:lightning_bolt " + xyz(p[0], p[1], p[2]));
                strikes = 1;
            }
            WorldVfx.ring(server, me, "light_blue_stained_glass", 1.2, 6.0, 0.05, 8);
        }
        return strikes;
    }

    private static int blink(ServerPlayer player, ServerLevel level, MinecraftServer server) {
        UUID me = player.getUUID();
        WorldVfx.column(server, me, "cyan_stained_glass", 1.6, 2.4, 0.1, 3.4, 8, 8);
        WorldVfx.ring(server, me, "light_blue_stained_glass", 2.5, 0.5, 0.05, 8);
        WorldVfx.sound(server, me, "minecraft:entity.enderman.teleport", 1.0f, 1.0f);

        double[] p = rayEnd(player, level, 40.0);
        run(server, "tp " + me + " " + xyz(p[0], p[1], p[2]));

        // arrival burst at the new spot (the anchor is the player, so it follows them there)
        Scheduler.after(150, () -> {
            WorldVfx.column(server, me, "cyan_stained_glass", 0.5, 1.5, 1.8, 3.0, 6, 6);
            WorldVfx.ring(server, me, "light_blue_stained_glass", 1.2, 3.5, 0.05, 8);
            WorldVfx.sound(server, me, "minecraft:entity.enderman.teleport", 1.0f, 1.3f);
        });
        return 1;
    }

    private static int timeWarp(ServerPlayer player, MinecraftServer server) {
        UUID me = player.getUUID();
        boolean toNight = nextIsNight;
        String block = toNight ? "purple_stained_glass" : "yellow_stained_glass";
        run(server, toNight ? "time set night" : "time set day");
        nextIsNight = !nextIsNight;

        WorldVfx.ring(server, me, block, 1.2, 12.0, 0.05, 14);
        Scheduler.after(250, () -> WorldVfx.ring(server, me, block, 1.2, 9.0, 0.05, 12));
        WorldVfx.column(server, me, block, 1.2, 1.5, 0.5, 6.0, 12, 12);
        WorldVfx.sound(server, me, "minecraft:block.bell.use", 1.5f, toNight ? 0.6f : 1.2f);
        return 1;
    }

    private static int weather(ServerPlayer player, MinecraftServer server) {
        UUID me = player.getUUID();
        String[] steps = {"clear", "rain", "thunder"};
        weatherStep = (weatherStep + 1) % steps.length;
        run(server, "weather " + steps[weatherStep]);

        String cloud = switch (weatherStep) {
            case 0 -> "white_stained_glass";
            case 1 -> "light_gray_stained_glass";
            default -> "gray_stained_glass";
        };
        WorldVfx.cloud(server, me, cloud, 12.0, 14.0, 12, 60);
        if (weatherStep == 0) {
            WorldVfx.ring(server, me, "yellow_stained_glass", 1.2, 8.0, 0.05, 12);
        } else {
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0;
                WorldVfx.streak(server, me, "light_blue_stained_glass",
                        Math.cos(ang) * 4.5, Math.sin(ang) * 4.5, 14.0, 10 + (i % 3) * 3);
            }
        }
        WorldVfx.sound(server, me, weatherStep == 2
                ? "minecraft:entity.lightning_bolt.thunder" : "minecraft:block.beacon.activate", 1.0f, 1.0f);
        return weatherStep;
    }

    private static int restore(ServerPlayer player, MinecraftServer server) {
        UUID me = player.getUUID();
        player.setHealth(player.getMaxHealth());
        String u = me.toString();
        run(server, "effect give " + u + " minecraft:saturation 2 20 true");
        run(server, "effect give " + u + " minecraft:fire_resistance 5 0 true");
        for (String bad : new String[]{"poison", "wither", "hunger", "weakness", "slowness", "nausea",
                "blindness", "mining_fatigue", "darkness", "levitation"}) {
            run(server, "effect clear " + u + " minecraft:" + bad);
        }
        WorldVfx.column(server, me, "lime_stained_glass", 0.6, 0.6, 1.8, 3.2, 14, 10);
        WorldVfx.ring(server, me, "lime_stained_glass", 1.2, 4.0, 0.05, 10);
        WorldVfx.sound(server, me, "minecraft:entity.player.levelup", 1.2f, 1.0f);
        return 1;
    }

    private static int godMode(ServerPlayer player, MinecraftServer server) {
        UUID me = player.getUUID();
        String u = me.toString();
        run(server, "effect give " + u + " minecraft:resistance 20 4 true");
        run(server, "effect give " + u + " minecraft:regeneration 20 2 true");
        run(server, "effect give " + u + " minecraft:absorption 20 4 true");
        run(server, "effect give " + u + " minecraft:fire_resistance 20 0 true");

        WorldVfx.shell(server, me, "yellow_stained_glass", 1.4, 3.4, 8, 12);
        WorldVfx.sound(server, me, "minecraft:block.beacon.activate", 1.5f, 1.0f);
        // a golden pulse at their feet every 2 seconds for the full 20 seconds
        for (int i = 0; i < 10; i++) {
            Scheduler.after(i * 2000L, () -> WorldVfx.ring(server, me, "yellow_stained_glass", 1.2, 3.4, 0.05, 14));
        }
        return 1;
    }

    private static int skyward(ServerPlayer player, MinecraftServer server) {
        UUID me = player.getUUID();
        String u = me.toString();
        WorldVfx.beam(server, me, "cyan_stained_glass", 1.8, 80.0, 14);
        WorldVfx.ring(server, me, "white_stained_glass", 1.2, 5.0, 0.05, 10);
        WorldVfx.sound(server, me, "minecraft:entity.firework_rocket.launch", 1.5f, 0.8f);
        run(server, "effect give " + u + " minecraft:levitation 2 20 true");
        run(server, "effect give " + u + " minecraft:slow_falling 30 0 true");
        return 1;
    }

    private static int freeze(ServerPlayer player, ServerLevel level, MinecraftServer server) {
        UUID me = player.getUUID();
        int count = 0;
        for (LivingEntity e : targets(player, level, 24.0)) {
            runAs(server, e, "effect give @s minecraft:slowness 10 10 true");
            runAs(server, e, "effect give @s minecraft:weakness 10 3 true");
            runAs(server, e, "effect give @s minecraft:glowing 10 0 true");
            if (count < 12) {
                // an ice block around each frozen creature, for the whole freeze
                WorldVfx.shell(server, e.getUUID(), "light_blue_stained_glass", 1.0, 1.8, 6, 200);
            }
            count++;
        }
        WorldVfx.ring(server, me, "light_blue_stained_glass", 1.2, 24.0, 0.05, 14);
        WorldVfx.sound(server, me, "minecraft:entity.player.hurt_freeze", 1.4f, 0.8f);
        Scheduler.after(10_000, () -> WorldVfx.sound(server, me, "minecraft:block.glass.break", 1.5f, 0.8f));
        return count;
    }

    private static int purge(ServerPlayer player, ServerLevel level, MinecraftServer server) {
        UUID me = player.getUUID();
        int count = 0;
        for (LivingEntity e : targets(player, level, 48.0)) {
            if (count < 12) {
                WorldVfx.beam(server, e.getUUID(), "red_concrete", 0.9, 30.0, 8);   // while it still exists
            }
            if (e instanceof Player) {
                run(server, "kill " + e.getUUID());   // players must die properly, not be deleted
            } else {
                e.discard();
            }
            count++;
        }
        WorldVfx.ring(server, me, "red_stained_glass", 1.2, 40.0, 0.05, 14);
        Scheduler.after(200, () -> WorldVfx.ring(server, me, "red_stained_glass", 1.2, 30.0, 0.05, 12));
        WorldVfx.sound(server, me, "minecraft:entity.generic.explode", 1.4f, 0.6f);
        WorldVfx.sound(server, me, "minecraft:entity.wither.break_block", 1.2f, 0.8f);
        return count;
    }

    private static int timeStop(ServerPlayer player, MinecraftServer server) {
        UUID me = player.getUUID();
        // Show the bubble first. While the world is frozen the effect entities cannot animate,
        // so the freeze starts after the bubble has finished growing.
        WorldVfx.shell(server, me, "white_stained_glass", 2.0, 16.0, 10, 160);
        WorldVfx.ring(server, me, "light_blue_stained_glass", 1.2, 14.0, 0.05, 12);
        WorldVfx.sound(server, me, "minecraft:block.beacon.deactivate", 1.5f, 0.6f);

        Scheduler.after(800, () -> TimeStop.start(server, me, 7_200));
        // Always thaw the world again, no matter what happens.
        Scheduler.after(8_000, () -> {
            TimeStop.stop(server);
            WorldVfx.sound(server, me, "minecraft:block.beacon.activate", 1.5f, 1.0f);
        });
        return 8;
    }

    /**
     * Sends other players (64 blocks) and every other creature (24 blocks) to a dimension.
     * If nobody else is around, the caster goes instead. Returns how many were sent, or -1 for "you".
     */
    private static int sendToDimension(ServerPlayer player, ServerLevel level, MinecraftServer server, String dimension) {
        List<Entity> found = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != player && other.level() == level && other.distanceTo(player) <= 64f) {
                found.add(other);
            }
        }
        for (LivingEntity e : targets(player, level, 24.0)) {
            if (!(e instanceof Player)) found.add(e);
        }

        boolean self = found.isEmpty();
        if (self) found.add(player);

        String color = switch (dimension) {
            case "minecraft:the_nether" -> "orange_stained_glass";
            case "minecraft:the_end" -> "purple_stained_glass";
            default -> "lime_stained_glass";
        };

        boolean fromNether = level.dimension() == Level.NETHER;
        int vfx = 0;
        for (Entity e : found) {
            UUID id = e.getUUID();
            if (vfx++ < 12) {
                WorldVfx.column(server, id, color, 1.6, 3.2, 0.1, 4.5, 8, 8);
                WorldVfx.ring(server, id, color, 2.5, 0.5, 0.05, 8);
                WorldVfx.sound(server, id, "minecraft:block.portal.travel", 0.5f, 1.2f);
                // arrival effect, once they have landed in the new dimension
                Scheduler.after(300, () -> {
                    WorldVfx.ring(server, id, color, 1.2, 4.0, 0.05, 10);
                    WorldVfx.column(server, id, color, 0.5, 1.5, 1.6, 3.4, 6, 6);
                });
            }

            double x = e.getX(), z = e.getZ();
            double tx, tz;
            switch (dimension) {
                case "minecraft:the_nether" -> {
                    tx = fromNether ? x : x / 8.0;
                    tz = fromNether ? z : z / 8.0;
                }
                case "minecraft:the_end" -> {
                    tx = 0.0;
                    tz = 0.0;
                }
                default -> {
                    tx = fromNether ? x * 8.0 : x;
                    tz = fromNether ? z * 8.0 : z;
                }
            }
            // spreadplayers drops them on a safe surface (under the roof in the Nether, on the main island in the End)
            String under = dimension.equals("minecraft:the_nether") ? "under 120 " : "";
            run(server, String.format(Locale.ROOT, "execute in %s run spreadplayers %.1f %.1f 4 48 %sfalse %s",
                    dimension, tx, tz, under, id));
            if (e instanceof ServerPlayer sp) {
                String u = sp.getUUID().toString();
                run(server, "effect give " + u + " minecraft:fire_resistance 20 0 true");
                run(server, "effect give " + u + " minecraft:resistance 6 4 true");
                run(server, "effect give " + u + " minecraft:slow_falling 10 0 true");
            }
        }
        return self ? -1 : found.size();
    }

    private static int overdrive(ServerPlayer player, MinecraftServer server) {
        UUID me = player.getUUID();
        String u = me.toString();
        run(server, "effect give " + u + " minecraft:speed 30 3 true");
        run(server, "effect give " + u + " minecraft:strength 30 3 true");
        run(server, "effect give " + u + " minecraft:haste 30 3 true");
        run(server, "effect give " + u + " minecraft:jump_boost 30 2 true");

        // three orange rings spiral inward as they rise
        double[] heights = {0.05, 1.1, 2.1};
        for (int i = 0; i < heights.length; i++) {
            final double y = heights[i];
            Scheduler.after(i * 130L, () -> WorldVfx.ring(server, me, "orange_stained_glass", 3.2, 1.2, y, 10));
        }
        WorldVfx.column(server, me, "orange_stained_glass", 1.4, 1.5, 0.6, 3.2, 8, 8);
        WorldVfx.sound(server, me, "minecraft:item.firecharge.use", 1.4f, 0.7f);
        return 1;
    }

    private static int omniscience(ServerPlayer player, ServerLevel level, MinecraftServer server) {
        UUID me = player.getUUID();
        run(server, "effect give " + me + " minecraft:night_vision 60 0 true");
        int count = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64.0))) {
            if (e == player) continue;
            runAs(server, e, "effect give @s minecraft:glowing 15 0 true");
            count++;
        }
        WorldVfx.ring(server, me, "lime_stained_glass", 1.2, 64.0, 0.05, 24);
        Scheduler.after(300, () -> WorldVfx.ring(server, me, "lime_stained_glass", 1.2, 48.0, 0.05, 20));
        WorldVfx.sound(server, me, "minecraft:block.beacon.power_select", 1.5f, 1.2f);
        return count;
    }

    private static int zeroG(ServerPlayer player, ServerLevel level, MinecraftServer server) {
        UUID me = player.getUUID();
        int count = 0;
        for (LivingEntity e : targets(player, level, 24.0)) {
            runAs(server, e, "effect give @s minecraft:levitation 4 6 true");
            if (count < 10) {
                WorldVfx.column(server, e.getUUID(), "magenta_stained_glass", 1.0, 1.5, 0.5, 6.0, 10, 10);
            }
            count++;
        }
        WorldVfx.ring(server, me, "purple_stained_glass", 1.2, 24.0, 0.05, 14);
        WorldVfx.sound(server, me, "minecraft:block.respawn_anchor.charge", 1.4f, 0.6f);
        return count;
    }

    // ------------------------------------------------------------------ black hole

    /**
     * Opens a black hole where the caster looks. For 6 seconds, every 100 ms, it drags every living thing
     * toward the core (except the caster), hurts whatever reaches the centre, and finally collapses in a blast.
     * Returns how many creatures were inside the pull radius when it opened.
     */
    private static int blackHole(ServerPlayer player, ServerLevel level, MinecraftServer server) {
        UUID me = player.getUUID();
        double[] p = rayEnd(player, level, 22.0);
        double cx = p[0], cy = p[1] + 2.5, cz = p[2];

        WorldVfx.Hole hole = WorldVfx.openBlackHole(server, me, cx, cy, cz);
        WorldVfx.soundAt(server, me, cx, cy, cz, "minecraft:entity.wither.spawn", 1.6f, 0.5f);

        int caught = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, holeBox(cx, cy, cz))) {
            if (!e.getUUID().equals(me) && e.getType() != EntityType.ARMOR_STAND) caught++;
        }

        pulse(server, level, me, cx, cy, cz, hole, 0);
        return caught;
    }

    private static AABB holeBox(double cx, double cy, double cz) {
        return new AABB(cx - HOLE_RADIUS, cy - HOLE_RADIUS, cz - HOLE_RADIUS,
                cx + HOLE_RADIUS, cy + HOLE_RADIUS, cz + HOLE_RADIUS);
    }

    private static void pulse(MinecraftServer server, ServerLevel level, UUID me,
                              double cx, double cy, double cz, WorldVfx.Hole hole, int n) {
        if (n >= HOLE_PULSES) {
            hole.collapse();
            WorldVfx.soundAt(server, me, cx, cy, cz, "minecraft:entity.generic.explode", 2.0f, 0.5f);
            WorldVfx.soundAt(server, me, cx, cy, cz, "minecraft:entity.ender_dragon.death", 1.2f, 0.7f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, holeBox(cx, cy, cz))) {
                if (e.getUUID().equals(me) || e.getType() == EntityType.ARMOR_STAND) continue;
                double d = dist(e, cx, cy, cz);
                if (d <= 9.0) runAs(server, e, "damage @s 40 minecraft:explosion");
            }
            return;
        }

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, holeBox(cx, cy, cz))) {
            if (e.getUUID().equals(me) || e.getType() == EntityType.ARMOR_STAND) continue;
            double d = dist(e, cx, cy, cz);
            if (d > HOLE_RADIUS) continue;
            if (d < 2.4) {
                runAs(server, e, "damage @s 4 minecraft:magic");       // the core crushes whatever reaches it
            } else {
                double step = Math.max(0.5, Math.min(2.0, d * 0.15));   // faster when far, gentler up close
                run(server, String.format(Locale.ROOT,
                        "execute as %s at @s facing %.2f %.2f %.2f run tp @s ^ ^ ^%.2f",
                        e.getUUID(), cx, cy, cz, step));
            }
        }
        hole.update(n);
        Scheduler.after(100, () -> pulse(server, level, me, cx, cy, cz, hole, n + 1));
    }

    private static double dist(Entity e, double cx, double cy, double cz) {
        double dx = cx - e.getX(), dy = cy - e.getY(), dz = cz - e.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ helpers

    /** Every living thing near the player except the player: animals, villagers, monsters and other players. */
    private static List<LivingEntity> targets(ServerPlayer player, ServerLevel level, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius))) {
            if (e == player) continue;
            if (e.getType() == EntityType.ARMOR_STAND) continue;   // leave decorations alone
            out.add(e);
        }
        return out;
    }

    /**
     * Marches along the player's look direction and returns {x, y(feet), z} of the last spot a player
     * could stand in before hitting something solid.
     */
    private static double[] rayEnd(ServerPlayer player, ServerLevel level, double maxDistance) {
        double yaw = Math.toRadians(player.getYRot());
        double pitch = Math.toRadians(player.getXRot());
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);

        double ex = player.getX(), ey = player.getEyeY(), ez = player.getZ();
        double bx = player.getX(), by = player.getY(), bz = player.getZ();

        for (double s = 0.5; s <= maxDistance; s += 0.5) {
            double fx = ex + dx * s, fy = ey + dy * s, fz = ez + dz * s;
            double feetY = fy - 1.62;
            BlockPos feet = BlockPos.containing(fx, feetY, fz);
            BlockPos head = feet.above();
            if (blocked(level, feet) || blocked(level, head)) break;
            bx = fx;
            by = feetY;
            bz = fz;
        }
        return new double[]{bx, by, bz};
    }

    private static boolean blocked(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static String xyz(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", x, y, z);
    }

    private static void run(MinecraftServer server, String command) {
        Cmd.run(server, command);
    }

    /** Run a command as an entity (@s = that entity). */
    private static void runAs(MinecraftServer server, LivingEntity e, String command) {
        run(server, "execute as " + e.getUUID() + " run " + command);
    }

    private static void runAt(MinecraftServer server, LivingEntity e, String command) {
        run(server, "execute as " + e.getUUID() + " at @s run " + command);
    }
}
