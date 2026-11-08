package com.gameadmin.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Time Stop for 1.20.1 (there is no /tick freeze in this version): while active, every creature and projectile
 * around the caster (except players and the effect entities) is frozen in place, then released unchanged.
 */
public final class TimeStop {
    private TimeStop() {}

    private static long endsAt = 0;
    private static UUID caster = null;
    private static final double RADIUS = 96.0;
    /** frozen entity -> whether it already had no AI / no gravity before we touched it. */
    private static final Map<Entity, boolean[]> FROZEN = new HashMap<>();

    public static boolean active() {
        return System.currentTimeMillis() < endsAt;
    }

    public static void start(MinecraftServer server, UUID who, long millis) {
        caster = who;
        endsAt = System.currentTimeMillis() + millis;
    }

    public static void stop(MinecraftServer server) {
        endsAt = 0;
        release();
    }

    private static void release() {
        for (Map.Entry<Entity, boolean[]> e : new ArrayList<>(FROZEN.entrySet())) {
            Entity ent = e.getKey();
            if (ent.isRemoved()) continue;
            if (ent instanceof Mob mob) mob.setNoAi(e.getValue()[0]);
            ent.setNoGravity(e.getValue()[1]);
        }
        FROZEN.clear();
    }

    public static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty() && !active()) return;
        if (!active()) {
            release();
            return;
        }
        Player me = caster == null ? null : server.getPlayerList().getPlayer(caster);
        if (me == null || !(me.level() instanceof ServerLevel level)) return;
        List<Entity> near = level.getEntities((Entity) null, new AABB(me.position(), me.position()).inflate(RADIUS),
                e -> !(e instanceof Player) && !e.getTags().contains("gameadmin_vfx") && e.isAlive());
        for (Entity e : near) {
            boolean[] old = FROZEN.computeIfAbsent(e, k -> new boolean[]{k instanceof Mob m && m.isNoAi(), k.isNoGravity()});
            if (e instanceof Mob mob) mob.setNoAi(true);
            e.setNoGravity(true);
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
        }
    }
}
