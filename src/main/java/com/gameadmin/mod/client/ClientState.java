package com.gameadmin.mod.client;

import com.gameadmin.mod.Ability;
import com.gameadmin.mod.Passives;

/** Client-side memory: when each ability is ready again, what was cast last, flight and passive state. */
public final class ClientState {
    private ClientState() {}

    public static final long[] COOLDOWN_END = new long[Ability.ALL.length];
    public static int lastCast = -1;
    public static boolean flightOn = false;
    /** Whether the server says this player may use the console. Starts true; the server corrects it on join. */
    public static boolean access = true;

    /** Bitmask of enabled passives, as last sent by the server. */
    public static int passiveMask = 0;
    /** The thing you are adapting to (a mob id like "zombie" or a damage kind like "lava"), and how far along (0 to 5). */
    public static String adaptMob = "";
    public static int adaptLevel = 0;

    public static long remainingMs(int ability) {
        return Math.max(0L, COOLDOWN_END[ability] - System.currentTimeMillis());
    }

    public static boolean passiveOn(int passive) {
        return (passiveMask & (1 << passive)) != 0;
    }

    public static boolean adaptationActive() {
        return passiveOn(Passives.ADAPT);
    }
}
