package com.gameadmin.mod;

import net.minecraft.server.MinecraftServer;

import java.util.Locale;
import java.util.UUID;

/**
 * Server-side visual effects that EVERY nearby player can see. No particles are used.
 *
 * Each effect is a handful of glowing block display entities that are summoned, animated by interpolating their
 * size/position, and removed again a moment later. Everything is done with vanilla commands, run as the server.
 * "at" is always the UUID of the entity (player or mob) the effect is centred on.
 *
 * Every effect STARTS at a clearly visible size, so even if the animation step were ever skipped it still shows.
 */
public final class WorldVfx {
    private WorldVfx() {}

    private static int counter = 0;

    /** One size, rotation and position of a block display. */
    private record Xf(double sx, double sy, double sz, double tx, double ty, double tz, double yaw) {
        Xf(double sx, double sy, double sz, double tx, double ty, double tz) {
            this(sx, sy, sz, tx, ty, tz, 0.0);
        }

        String snbt() {
            double qy = Math.sin(yaw / 2.0), qw = Math.cos(yaw / 2.0);
            return String.format(Locale.ROOT,
                    "{left_rotation:[0f,%.4ff,0f,%.4ff],right_rotation:[0f,0f,0f,1f],"
                            + "translation:[%.3ff,%.3ff,%.3ff],scale:[%.3ff,%.3ff,%.3ff]}",
                    qy, qw, tx, ty, tz, sx, sy, sz);
        }
    }

    private static String at(UUID id) {
        return "execute as " + id + " at @s run ";
    }

    private static String atPos(UUID id, double x, double y, double z) {
        return String.format(Locale.ROOT, "execute as %s at @s positioned %.2f %.2f %.2f run ", id, x, y, z);
    }

    // ------------------------------------------------------------------ primitives

    /**
     * Summon one block display, then animate it from one shape to another and remove it later.
     * @param prefix an "execute as ... at @s [positioned ...] run " prefix that decides where it appears
     * @return the unique tag of the spawned entity (so callers can keep updating it)
     */
    private static String anim(MinecraftServer s, String prefix, String block, Xf from, Xf to, int ticks, int lifeTicks) {
        int id = ++counter;
        String tag = "gameadmin_fx_" + id;
        String head = "{Tags:[\"gameadmin_vfx\",\"" + tag + "\"],block_state:{Name:\"minecraft:" + block + "\"},";
        String shape = "transformation:" + from.snbt() + "}";
        String full = head + "brightness:{sky:15,block:15},view_range:3.0f," + shape;

        if (!Cmd.run(s, prefix + "summon minecraft:block_display ~ ~ ~ " + full)) {
            // fallback with the bare minimum, in case one of the optional fields was the problem
            Cmd.run(s, prefix + "summon minecraft:block_display ~ ~ ~ " + head + shape);
        }

        // give clients a moment to receive the starting shape, then interpolate to the end shape
        Scheduler.after(100, () -> Cmd.run(s, "data merge entity @e[tag=" + tag + ",limit=1] "
                + "{start_interpolation:0,interpolation_duration:" + ticks + ",transformation:" + to.snbt() + "}"));
        Scheduler.after(Math.max(ticks, lifeTicks) * 50L + 500L, () -> Cmd.run(s, "kill @e[tag=" + tag + "]"));
        return tag;
    }

    private static void update(MinecraftServer s, String tag, Xf to, int ticks) {
        Cmd.run(s, "data merge entity @e[tag=" + tag + ",limit=1] "
                + "{start_interpolation:0,interpolation_duration:" + ticks + ",transformation:" + to.snbt() + "}");
    }

    public static void sound(MinecraftServer s, UUID at, String sound, float volume, float pitch) {
        Cmd.run(s, String.format(Locale.ROOT, "execute as %s at @s run playsound %s master @a ~ ~ ~ %.2f %.2f",
                at, sound, volume, pitch));
    }

    public static void soundAt(MinecraftServer s, UUID dimensionOf, double x, double y, double z,
                               String sound, float volume, float pitch) {
        Cmd.run(s, String.format(Locale.ROOT, "execute as %s at @s run playsound %s master @a %.2f %.2f %.2f %.2f %.2f",
                dimensionOf, sound, x, y, z, volume, pitch));
    }

    // ------------------------------------------------------------------ shapes

    /** A flat square ring lying on the ground that grows (or shrinks) from radius r0 to r1. */
    public static void ring(MinecraftServer s, UUID at, String block, double r0, double r1, double y, int ticks) {
        ring0(s, at(at), block, r0, r1, y, ticks);
    }

    /** The same ring, but centred on an exact point in the world (same dimension as "dimensionOf"). */
    public static void ringAt(MinecraftServer s, UUID dimensionOf, double x, double yy, double z,
                              String block, double r0, double r1, int ticks) {
        ring0(s, atPos(dimensionOf, x, yy, z), block, r0, r1, 0.0, ticks);
    }

    private static void ring0(MinecraftServer s, String prefix, String block, double r0, double r1, double y, int ticks) {
        // never start smaller than a visible ring
        double a = Math.max(r0, 1.2);
        double h = 0.3;
        double t0 = Math.max(0.2, a * 0.10), t1 = Math.max(0.2, r1 * 0.05);
        // north and south bars
        anim(s, prefix, block, new Xf(2 * a, h, t0, -a, y, -a), new Xf(2 * r1, h, t1, -r1, y, -r1), ticks, ticks);
        anim(s, prefix, block, new Xf(2 * a, h, t0, -a, y, a - t0), new Xf(2 * r1, h, t1, -r1, y, r1 - t1), ticks, ticks);
        // west and east bars
        anim(s, prefix, block, new Xf(t0, h, 2 * a - 2 * t0, -a, y, -a + t0),
                new Xf(t1, h, 2 * r1 - 2 * t1, -r1, y, -r1 + t1), ticks, ticks);
        anim(s, prefix, block, new Xf(t0, h, 2 * a - 2 * t0, a - t0, y, -a + t0),
                new Xf(t1, h, 2 * r1 - 2 * t1, r1 - t1, y, -r1 + t1), ticks, ticks);
    }

    /** A column standing on the anchor's feet that changes from w0 x h0 to w1 x h1. */
    public static void column(MinecraftServer s, UUID at, String block, double w0, double h0, double w1, double h1,
                              int ticks, int lifeTicks) {
        double sw = Math.max(w0, 0.5), sh = Math.max(h0, 1.5);   // always visible at the start
        anim(s, at(at), block, new Xf(sw, sh, sw, -sw / 2, 0.0, -sw / 2),
                new Xf(w1, h1, w1, -w1 / 2, 0.0, -w1 / 2), ticks, lifeTicks);
    }

    /** A column that starts at full size and thins out to nothing, like a beam from the sky. */
    public static void beam(MinecraftServer s, UUID at, String block, double width, double height, int ticks) {
        anim(s, at(at), block, new Xf(width, height, width, -width / 2, 0.0, -width / 2),
                new Xf(0.04, height, 0.04, -0.02, 0.0, -0.02), ticks, ticks);
    }

    /** A cube of glass centred on the anchor's body that grows from size0 to size1 and stays for lifeTicks. */
    public static void shell(MinecraftServer s, UUID at, String block, double size0, double size1, int ticks, int lifeTicks) {
        double a = Math.max(size0, 1.0);
        anim(s, at(at), block,
                new Xf(a, a, a, -a / 2, -a / 2 + 0.9, -a / 2),
                new Xf(size1, size1, size1, -size1 / 2, -size1 / 2 + 0.9, -size1 / 2), ticks, lifeTicks);
    }

    /** A slab floating above the anchor. */
    public static void cloud(MinecraftServer s, UUID at, String block, double size, double height, int ticks, int lifeTicks) {
        double a = Math.max(size * 0.4, 2.0);
        anim(s, at(at), block,
                new Xf(a, 0.6, a, -a / 2, height, -a / 2),
                new Xf(size, 0.8, size, -size / 2, height, -size / 2), ticks, lifeTicks);
    }

    /** A thin streak that falls from a height down to the ground at an offset from the anchor. */
    public static void streak(MinecraftServer s, UUID at, String block, double dx, double dz, double height, int ticks) {
        String prefix = String.format(Locale.ROOT, "execute as %s at @s positioned ~%.2f ~ ~%.2f run ", at, dx, dz);
        anim(s, prefix, block, new Xf(0.1, 3.0, 0.1, -0.05, height - 3.0, -0.05),
                new Xf(0.1, height, 0.1, -0.05, 0.0, -0.05), ticks, ticks);
    }

    /** A big translucent cube centred on an exact point that grows quickly (used for flashes and blasts). */
    public static void burstAt(MinecraftServer s, UUID dimensionOf, double x, double y, double z,
                               String block, double size0, double size1, int ticks) {
        anim(s, atPos(dimensionOf, x, y, z), block,
                new Xf(size0, size0, size0, -size0 / 2, -size0 / 2, -size0 / 2),
                new Xf(size1, size1, size1, -size1 / 2, -size1 / 2, -size1 / 2), ticks, ticks);
    }

    // ------------------------------------------------------------------ black hole

    /** Handle for a black hole that is being drawn: a dark core and two spinning accretion disks. */
    public static final class Hole {
        private final MinecraftServer s;
        private final UUID anchor;
        private final double x, y, z;
        private final String core, diskA, diskB;

        private Hole(MinecraftServer s, UUID anchor, double x, double y, double z, String core, String diskA, String diskB) {
            this.s = s;
            this.anchor = anchor;
            this.x = x;
            this.y = y;
            this.z = z;
            this.core = core;
            this.diskA = diskA;
            this.diskB = diskB;
        }

        /** Called every 100 ms while the hole is open. */
        public void update(int n) {
            double a = n * 0.22;
            double size = 3.0 + 0.5 * Math.sin(n * 0.5);
            WorldVfx.update(s, core, new Xf(size, size, size, -size / 2, -size / 2, -size / 2), 2);
            WorldVfx.update(s, diskA, disk(15.0, 0.2, a), 2);
            WorldVfx.update(s, diskB, disk(10.0, 0.3, -a * 1.6), 2);
            if (n % 12 == 0) {
                // a purple ring is sucked inward toward the core
                ring0(s, atPos(anchor, x, y, z), "purple_stained_glass", 26.0, 2.5, 0.0, 22);
            }
        }

        /** The final collapse: the core and disks vanish in a white flash and a huge ring. */
        public void collapse() {
            Cmd.run(s, "kill @e[tag=" + core + "]");
            Cmd.run(s, "kill @e[tag=" + diskA + "]");
            Cmd.run(s, "kill @e[tag=" + diskB + "]");
            burstAt(s, anchor, x, y, z, "white_stained_glass", 3.0, 36.0, 8);
            burstAt(s, anchor, x, y, z, "black_stained_glass", 2.0, 20.0, 6);
            ringAt(s, anchor, x, y - 1.0, z, "white_stained_glass", 2.0, 46.0, 14);
        }
    }

    /** Opens the visual black hole at an exact point. Returns a handle used to animate and close it. */
    public static Hole openBlackHole(MinecraftServer s, UUID anchor, double x, double y, double z) {
        String prefix = atPos(anchor, x, y, z);
        int life = 140; // ticks, comfortably longer than the 6 second effect; collapse() removes them earlier
        String core = anim(s, prefix, "black_concrete",
                new Xf(3.0, 3.0, 3.0, -1.5, -1.5, -1.5), new Xf(3.0, 3.0, 3.0, -1.5, -1.5, -1.5), 2, life);
        String diskA = anim(s, prefix, "purple_stained_glass", disk(15.0, 0.2, 0.0), disk(15.0, 0.2, 0.0), 2, life);
        String diskB = anim(s, prefix, "orange_stained_glass", disk(10.0, 0.3, 0.0), disk(10.0, 0.3, 0.0), 2, life);
        return new Hole(s, anchor, x, y, z, core, diskA, diskB);
    }

    /** A flat square disk of the given size, rotated by "yaw" radians about its own centre. */
    private static Xf disk(double size, double thickness, double yaw) {
        double cx = size / 2, cy = thickness / 2, cz = size / 2;
        double rx = cx * Math.cos(yaw) + cz * Math.sin(yaw);
        double rz = -cx * Math.sin(yaw) + cz * Math.cos(yaw);
        return new Xf(size, thickness, size, -rx, -cy, -rz, yaw);
    }

    // ------------------------------------------------------------------ cleanup

    /** Removes any effect entities left over from a crash or a stopped server. */
    public static void cleanup(MinecraftServer s) {
        Cmd.run(s, "kill @e[type=minecraft:block_display,tag=gameadmin_vfx]");
    }
}
