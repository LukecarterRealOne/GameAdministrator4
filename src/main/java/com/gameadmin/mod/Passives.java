package com.gameadmin.mod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Always-on abilities you can switch on and off from the Passives tab. */
public final class Passives {
    private Passives() {}

    public static final int IMMUNITY = 0, ADAPT = 1, NIGHT = 2, FOOD = 3, REGEN = 4;

    public static final String[] NAMES = {"Hazard Immunity", "Adaptation", "Night Eyes", "Never Hungry", "Regeneration"};
    public static final String[] DESCRIPTIONS = {
            "Immune to fire, lava, drowning, falls, blasts, cactus...",
            "Learns from ANY damage (mobs, lava, fire, falls...) up to 100% dodge",
            "Permanent night vision",
            "Hunger never drops",
            "Constant slow healing",
    };

    /** Each hit of a damage kind adds a level (max 5). Every level gives 20% dodge, so level 5 is a 100% dodge. */
    private static final int MAX_LEVEL = 5;
    private static final float DODGE_PER_LEVEL = 0.20f;

    private static final Map<UUID, Integer> MASK = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> LEVELS = new HashMap<>();
    private static final Map<UUID, String> LAST_MOB = new HashMap<>();
    private static int counter = 0;

    public static boolean has(UUID id, int passive) {
        return (MASK.getOrDefault(id, 0) & (1 << passive)) != 0;
    }

    // ------------------------------------------------------------ toggling

    public static void toggle(ServerPlayer player, int id) {
        MinecraftServer server = Gate.server(player);
        if (server == null) return;

        if (id >= 0 && id < NAMES.length) {
            if (!Gate.allowed(player, server)) {
                Gate.deny(player);
                return;
            }
            UUID uuid = player.getUUID();
            int mask = MASK.getOrDefault(uuid, 0) ^ (1 << id);
            MASK.put(uuid, mask);
            if ((mask & (1 << id)) == 0) cleanup(server, player, id);
            else applyNow(server, player);
            Gate.notice(player, NAMES[id] + ((mask & (1 << id)) != 0 ? ": ON" : ": OFF"), 0xA6FF9E);
        }
        Gate.sendAccess(player);
        sync(player);
    }

    /** Sends the passive bitmask and the current adaptation target to the client. */
    public static void sync(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Net.send(player, new Net.PassiveState(MASK.getOrDefault(uuid, 0)));
        String mob = LAST_MOB.get(uuid);
        int level = 0;
        if (mob != null) level = LEVELS.getOrDefault(uuid, Map.of()).getOrDefault(mob, 0);
        Net.send(player, new Net.Adapt(mob == null ? "" : mob, level));
    }

    private static void cleanup(MinecraftServer server, ServerPlayer p, int id) {
        String u = p.getUUID().toString();
        switch (id) {
            case IMMUNITY -> {
                run(server, "effect clear " + u + " minecraft:fire_resistance");
                run(server, "effect clear " + u + " minecraft:water_breathing");
            }
            case NIGHT -> run(server, "effect clear " + u + " minecraft:night_vision");
            case REGEN -> run(server, "effect clear " + u + " minecraft:regeneration");
            default -> { }
        }
    }

    // ------------------------------------------------------------ once per second

    public static void tick(MinecraftServer server) {
        if (MASK.isEmpty()) return;
        counter++;
        if (counter % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (MASK.getOrDefault(p.getUUID(), 0) != 0) applyNow(server, p);
        }
    }

    private static void applyNow(MinecraftServer server, ServerPlayer p) {
        UUID id = p.getUUID();
        String u = id.toString();
        if (has(id, IMMUNITY)) {
            run(server, "effect give " + u + " minecraft:fire_resistance 10 0 true");
            run(server, "effect give " + u + " minecraft:water_breathing 10 0 true");
            for (String bad : new String[]{"poison", "wither", "nausea", "blindness", "darkness", "hunger"}) {
                run(server, "effect clear " + u + " minecraft:" + bad);
            }
        }
        if (has(id, NIGHT)) run(server, "effect give " + u + " minecraft:night_vision 30 0 true");
        if (has(id, FOOD)) run(server, "effect give " + u + " minecraft:saturation 2 4 true");
        if (has(id, REGEN)) run(server, "effect give " + u + " minecraft:regeneration 3 1 true");
    }

    // ------------------------------------------------------------ damage hook

    /** Called for every hit on a player: return false to cancel the damage. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player)) return true;
        UUID id = player.getUUID();
        int mask = MASK.getOrDefault(id, 0);
        if (mask == 0) return true;

        // 1) Adaptation learns from ANY damage: mobs, other players, lava, fire, falling, drowning, void...
        boolean dodge = false;
        if ((mask & (1 << ADAPT)) != 0) {
            String kind = adaptKey(source);
            Map<String, Integer> levels = LEVELS.computeIfAbsent(id, k -> new HashMap<>());
            int level = levels.getOrDefault(kind, 0);

            // level 5 = 100% (nextFloat is always below 1.0)
            dodge = level > 0 && player.getRandom().nextFloat() < level * DODGE_PER_LEVEL;

            int newLevel = Math.min(MAX_LEVEL, level + 1);
            boolean changed = newLevel != level || !kind.equals(LAST_MOB.get(id));
            levels.put(kind, newLevel);
            LAST_MOB.put(id, kind);
            if (changed) Net.send(player, new Net.Adapt(kind, newLevel));
        }

        if (dodge) {
            MinecraftServer server = Gate.server(player);
            if (server != null) {
                WorldVfx.ring(server, id, "white_stained_glass", 1.2, 2.4, 0.05, 6);
                WorldVfx.sound(server, id, "minecraft:item.shield.block", 0.8f, 1.4f);
            }
            return false;
        }

        // 2) Hazard Immunity blocks the dangerous stuff outright
        if ((mask & (1 << IMMUNITY)) != 0 && isHazard(source)) return false;
        return true;
    }

    /** What we adapt to: the attacker's entity type if something attacked, otherwise the kind of damage (lava, fall...). */
    private static String adaptKey(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker != null) return EntityType.getKey(attacker.getType()).getPath();
        return source.getMsgId();
    }

    private static boolean isHazard(DamageSource s) {
        return s.is(DamageTypeTags.IS_FIRE)
                || s.is(DamageTypeTags.IS_FALL)
                || s.is(DamageTypeTags.IS_DROWNING)
                || s.is(DamageTypeTags.IS_EXPLOSION)
                || s.is(DamageTypeTags.IS_LIGHTNING)
                || s.is(DamageTypeTags.IS_FREEZING)
                || s.is(DamageTypes.CACTUS)
                || s.is(DamageTypes.SWEET_BERRY_BUSH)
                || s.is(DamageTypes.IN_WALL)
                || s.is(DamageTypes.CRAMMING)
                || s.is(DamageTypes.STALAGMITE)
                || s.is(DamageTypes.FLY_INTO_WALL)
                || s.is(DamageTypes.WITHER);
    }

    private static void run(MinecraftServer server, String command) {
        Cmd.run(server, command);
    }
}
