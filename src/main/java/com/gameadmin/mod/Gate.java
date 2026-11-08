package com.gameadmin.mod;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Who may use the admin powers (GUI, abilities, flight, command line...)?
 * - The host of a single player / LAN world: always.
 * - Server owners (permission level 4): always.
 * - Anyone an operator granted with  /gameadmin grant <player>  (saved in config/gameadmin-admins.txt, one name per line).
 * Everybody else sees "Admins only".
 */
public final class Gate {
    private Gate() {}

    private static Set<String> admins = new HashSet<>();
    private static long lastLoad = 0;

    public static MinecraftServer server(ServerPlayer player) {
        return player.level() instanceof ServerLevel level ? level.getServer() : null;
    }

    public static boolean allowed(ServerPlayer player, MinecraftServer server) {
        if (server.isSingleplayerOwner(player.getGameProfile())) return true;
        if (player.hasPermissions(4)) return true;
        return loadAdmins().contains(key(player.getGameProfile().getName()));
    }

    public static void notice(ServerPlayer player, String text, int rgb) {
        Net.send(player, new Net.Notice(text, rgb));
    }

    public static void deny(ServerPlayer player) {
        notice(player, "Admins only. Ask an operator: /gameadmin grant " + player.getGameProfile().getName(), 0xFF5555);
        sendAccess(player);
    }

    /** Tells the player's client whether the console is unlocked (so G can show a lock message instead of an empty screen). */
    public static void sendAccess(ServerPlayer player) {
        MinecraftServer server = server(player);
        if (server != null) Net.send(player, new Net.Access(allowed(player, server)));
    }

    // ------------------------------------------------------------ operator commands

    public static synchronized boolean grant(String name) {
        loadAdmins(true);
        boolean added = admins.add(key(name));
        if (added) save();
        return added;
    }

    public static synchronized boolean revoke(String name) {
        loadAdmins(true);
        boolean removed = admins.remove(key(name));
        if (removed) save();
        return removed;
    }

    public static synchronized List<String> list() {
        loadAdmins(true);
        List<String> out = new ArrayList<>(admins);
        Collections.sort(out);
        return out;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("gameadmin-admins.txt");
    }

    private static void save() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# Game Administrator: one player name per line. These players can use the mod's powers.");
            lines.add("# Manage it in game with /gameadmin grant|revoke|list (operators only).");
            List<String> sorted = new ArrayList<>(admins);
            Collections.sort(sorted);
            lines.addAll(sorted);
            Files.write(file(), lines);
            lastLoad = System.currentTimeMillis();
        } catch (IOException ex) {
            GameAdminMod.LOGGER.warn("Could not write admin list", ex);
        }
    }

    private static Set<String> loadAdmins() {
        return loadAdmins(false);
    }

    private static synchronized Set<String> loadAdmins(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastLoad < 5_000) return admins;
        lastLoad = now;
        Path file = file();
        try {
            if (!Files.exists(file)) {
                Files.writeString(file, "# Game Administrator: one player name per line. These players can use the mod's powers.\n"
                        + "# Manage it in game with /gameadmin grant|revoke|list (operators only).\n");
            }
            Set<String> out = new HashSet<>();
            for (String line : Files.readAllLines(file)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                out.add(key(t));
            }
            admins = out;
        } catch (IOException ex) {
            GameAdminMod.LOGGER.warn("Could not read admin list", ex);
        }
        return admins;
    }
}
