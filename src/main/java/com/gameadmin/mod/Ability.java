package com.gameadmin.mod;

/** The nine administrator abilities. Pure data, shared by the server logic, the GUI and the VFX. */
public enum Ability {
    SMITE("Smite",
            "Strike every living thing within 28 blocks (except you) with lightning. If nothing is near, strikes where you look.",
            8_000, 0xB9C7FF, 900),
    BLINK("Blink",
            "Teleport to the spot you are looking at (up to 40 blocks, stops before walls).",
            3_000, 0x55E6FF, 650),
    TIME_WARP("Time Warp",
            "Flip the world between day and night.",
            5_000, 0xFFC94D, 1_600),
    WEATHER("Weather",
            "Cycle the weather: clear, then rain, then thunderstorm.",
            5_000, 0x7FA6C9, 1_700),
    RESTORE("Full Restore",
            "Heal to full, refill hunger and clear bad potion effects.",
            10_000, 0x4DFF88, 1_400),
    GOD_MODE("God Mode",
            "20 seconds of invulnerability, regeneration and absorption.",
            45_000, 0xFFD700, 20_000),
    SKYWARD("Skyward",
            "Launch high into the sky, then glide back down safely.",
            6_000, 0xFFFFFF, 1_700),
    FREEZE("Freeze Field",
            "Freeze and highlight every living thing within 24 blocks (except you) for 10 seconds.",
            20_000, 0x8FEFFF, 3_500),
    PURGE("Purge",
            "Wipe out everything alive within 48 blocks (except you), bosses included. Players are killed, mobs are deleted.",
            30_000, 0xFF3B3B, 1_500),
    TIME_STOP("Time Stop",
            "Stop time for 8 seconds. Everything freezes except players.",
            45_000, 0xC8D0E8, 8_000),
    SEND_NETHER("Send: Nether",
            "Send other players within 64 blocks and every creature within 24 to the Nether. If nobody is near, sends YOU.",
            15_000, 0xFF4A1A, 1_800),
    SEND_END("Send: End",
            "Send other players within 64 blocks and every creature within 24 to the End. If nobody is near, sends YOU.",
            15_000, 0xB070FF, 1_800),
    SEND_OVERWORLD("Send: Overworld",
            "Send other players within 64 blocks and every creature within 24 to the Overworld. If nobody is near, sends YOU.",
            15_000, 0x5AD66B, 1_800),
    OVERDRIVE("Overdrive",
            "30 seconds of super speed, strength, haste and jump boost.",
            40_000, 0xFF8A1F, 2_200),
    OMNISCIENCE("Omniscience",
            "See everything: highlights every creature within 64 blocks for 15s and gives night vision for 60s.",
            25_000, 0x3CFFA0, 2_600),
    ZERO_G("Zero-G",
            "Everything within 24 blocks (except you) floats up for 4 seconds, then crashes down.",
            20_000, 0xB88CFF, 3_000),
    BLACK_HOLE("Black Hole",
            "Opens a black hole where you look. For 6 seconds it drags in every living thing nearby (except you), "
                    + "crushes what reaches the core, then collapses in a blast.",
            40_000, 0x7A2CFF, 6_500);

    public final String label;
    public final String description;
    /** Cooldown in milliseconds (real time). */
    public final int cooldownMs;
    /** Accent colour (RGB) used by the GUI and the VFX. */
    public final int rgb;
    /** How long the client-side screen VFX lasts, in milliseconds. */
    public final int vfxMs;

    Ability(String label, String description, int cooldownMs, int rgb, int vfxMs) {
        this.label = label;
        this.description = description;
        this.cooldownMs = cooldownMs;
        this.rgb = rgb;
        this.vfxMs = vfxMs;
    }

    public static final Ability[] ALL = values();
}
