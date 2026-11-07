package com.gameadmin.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Game modes, game rules, the free-form command line and survival command block editing. */
public final class Powers {
    private Powers() {}

    public static final String[] MODES = {"survival", "creative", "adventure", "spectator"};
    private static final GameType[] GAME_TYPES = {
            GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR};

    /** {label shown in the GUI, game rule name}. Minecraft 1.20.1 uses camelCase rule names. */
    public static final String[][] RULES = {
            {"Keep Inv.", "keepInventory"},
            {"Mob Grief", "mobGriefing"},
            {"Day Cycle", "doDaylightCycle"},
            {"Weather", "doWeatherCycle"},
            {"Fall Dmg", "fallDamage"},
            {"Fire Dmg", "fireDamage"},
            {"Insta-Respawn", "doImmediateRespawn"},
            {"Mob Spawn", "doMobSpawning"},
    };

    private static final String[] BLOCK_IDS = {"command_block", "chain_command_block", "repeating_command_block"};

    // id 0..3 = game mode, id 4 = give command blocks
    public static void power(ServerPlayer player, int id) {
        MinecraftServer server = Gate.server(player);
        if (server == null) return;
        if (!Gate.allowed(player, server)) {
            Gate.deny(player);
            return;
        }
        String u = player.getUUID().toString();
        if (id >= 0 && id < MODES.length) {
            // set it directly instead of through the /gamemode command
            player.setGameMode(GAME_TYPES[id]);
            // switching mode resets the player's abilities, so put flight back if it was on
            FlightManager.reapply(player);
            Gate.notice(player, "Game mode: " + MODES[id], 0xFFD36B);
        } else if (id == 4) {
            run(server, "give " + u + " minecraft:command_block");
            run(server, "give " + u + " minecraft:chain_command_block");
            run(server, "give " + u + " minecraft:repeating_command_block");
            Gate.notice(player, "Command blocks added. Right-click one to edit it, even in survival.", 0x7FE8FF);
        }
    }

    public static void rule(ServerPlayer player, int rule, boolean value) {
        MinecraftServer server = Gate.server(player);
        if (server == null) return;
        if (!Gate.allowed(player, server)) {
            Gate.deny(player);
            return;
        }
        if (rule < 0 || rule >= RULES.length) return;
        run(server, "gamerule " + RULES[rule][1] + " " + value);
        Gate.notice(player, RULES[rule][1] + " = " + value, 0xFFD36B);
    }

    public static void command(ServerPlayer player, String raw) {
        MinecraftServer server = Gate.server(player);
        if (server == null) return;
        if (!Gate.allowed(player, server)) {
            Gate.deny(player);
            return;
        }
        String cmd = clean(raw);
        if (cmd.isEmpty()) return;
        // @s, ~ ~ ~ and friends all refer to the player who typed it
        run(server, "execute as " + player.getUUID() + " at @s run " + cmd);
        Gate.notice(player, "Ran /" + cmd, 0x7FE8FF);
    }

    public static void editCommandBlock(ServerPlayer player, int x, int y, int z, String raw, int flags) {
        MinecraftServer server = Gate.server(player);
        if (server == null || !(player.level() instanceof ServerLevel level)) return;
        if (!Gate.allowed(player, server)) {
            Gate.deny(player);
            return;
        }
        if (player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) > 64.0) {
            Gate.notice(player, "Too far from that command block", 0xFF5555);
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CommandBlock)) {
            Gate.notice(player, "That is not a command block", 0xFF5555);
            return;
        }

        int type = Math.max(0, Math.min(2, flags & 3));
        boolean conditional = (flags & 4) != 0;
        boolean auto = (flags & 8) != 0;
        String facing = state.getValue(CommandBlock.FACING).getSerializedName();
        String snbt = clean(raw).replace("\\", "\\\\").replace("\"", "\\\"");

        String at = "execute as " + player.getUUID() + " at @s run ";
        String p = x + " " + y + " " + z;
        run(server, at + "setblock " + p + " minecraft:" + BLOCK_IDS[type]
                + "[facing=" + facing + ",conditional=" + conditional + "] replace");
        run(server, at + "data merge block " + p + " {Command:\"" + snbt + "\",auto:" + (auto ? "1b" : "0b") + "}");
        Gate.notice(player, "Command block updated", 0x7FE8FF);
    }

    /** Trim, drop a leading slash and newlines, and cap the length. */
    private static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\n", " ").replace("\r", " ").trim();
        if (s.startsWith("/")) s = s.substring(1).trim();
        if (s.length() > 256) s = s.substring(0, 256);
        return s;
    }

    private static void run(MinecraftServer server, String command) {
        Cmd.run(server, command);
    }
}
