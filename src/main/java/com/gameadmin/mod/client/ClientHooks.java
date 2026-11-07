package com.gameadmin.mod.client;

import com.gameadmin.mod.Ability;

/** Called (on the client only) when a message from the server arrives. */
public final class ClientHooks {
    private ClientHooks() {}

    private static final int CAST = 0;

    public static void feedback(int ability, int status, int value) {
        if (ability < 0 || ability >= Ability.ALL.length) return;
        Ability a = Ability.ALL[ability];
        long now = System.currentTimeMillis();
        if (status == CAST) {
            ClientState.COOLDOWN_END[ability] = now + a.cooldownMs;
            Vfx.start(ability, value);
        } else {
            ClientState.COOLDOWN_END[ability] = now + value;
            Vfx.toast(a.label + " is recharging", a.rgb);
        }
    }

    public static void flight(boolean on) {
        ClientState.flightOn = on;
        Vfx.toast(on ? "Flight engaged" : "Flight off", 0x7FE8FF);
    }

    public static void passives(int mask) {
        ClientState.passiveMask = mask;
    }

    public static void adapt(String mob, int level) {
        ClientState.adaptMob = mob;
        ClientState.adaptLevel = level;
    }

    public static void access(boolean allowed) {
        ClientState.access = allowed;
    }

    public static void notice(String text, int rgb) {
        Vfx.toast(text, rgb);
    }
}
