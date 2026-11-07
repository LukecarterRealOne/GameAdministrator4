package com.gameadmin.mod.client;

import com.gameadmin.mod.Ability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Screen VFX. Everything here is drawn with plain rectangles, gradients and outlines on the HUD layer:
 * flashes, vignettes, streaks, expanding rings, scanlines and glitch bars. No particles anywhere.
 */
public final class Vfx {
    private Vfx() {}

    private static final class Active {
        final int ability;
        final long start;
        final int value;

        Active(int ability, long start, int value) {
            this.ability = ability;
            this.start = start;
            this.value = value;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static String toastText = null;
    private static int toastRgb = 0xFFFFFF;
    private static long toastStart = 0;
    private static final long TOAST_MS = 2200;

    // flight speed sampling
    private static long lastSampleMs = 0;
    private static double lastX, lastY, lastZ;
    private static double flightSpeed = 0;

    public static void start(int ability, int value) {
        ACTIVE.removeIf(a -> a.ability == ability);
        ACTIVE.add(new Active(ability, System.currentTimeMillis(), value));
        Ability ab = Ability.ALL[ability];
        toast(ab.label + describe(ability, value), ab.rgb);
    }

    public static void toast(String text, int rgb) {
        toastText = text;
        toastRgb = rgb;
        toastStart = System.currentTimeMillis();
    }

    private static String describe(int ability, int value) {
        return switch (Ability.ALL[ability]) {
            case SMITE -> value > 0 ? "  x" + value : "  (nothing to strike)";
            case PURGE -> "  " + value + " removed";
            case FREEZE -> "  " + value + " frozen";
            case WEATHER -> "  " + new String[]{"clear", "rain", "thunder"}[Math.floorMod(value, 3)];
            case SEND_NETHER, SEND_END, SEND_OVERWORLD -> value < 0 ? "  (you)" : "  " + value + " sent";
            case OMNISCIENCE -> "  " + value + " revealed";
            case ZERO_G -> "  " + value + " lifted";
            case BLACK_HOLE -> "  " + value + " caught";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ main hook

    public static void extract(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ACTIVE.clear();
            toastText = null;
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            Ability ab = Ability.ALL[a.ability];
            long elapsed = now - a.start;
            if (elapsed >= ab.vfxMs) {
                it.remove();
                continue;
            }
            float t = elapsed / (float) ab.vfxMs;
            switch (ab) {
                case SMITE -> smite(g, w, h, t, a.start);
                case BLINK -> blink(g, w, h, t);
                case TIME_WARP -> timeWarp(g, w, h, t);
                case WEATHER -> weather(g, w, h, t);
                case RESTORE -> restore(g, w, h, t);
                case GOD_MODE -> godMode(g, mc, w, h, t, ab.vfxMs - elapsed);
                case SKYWARD -> skyward(g, w, h, t);
                case FREEZE -> freeze(g, w, h, t);
                case PURGE -> purge(g, mc, w, h, t, elapsed, a.value);
                case TIME_STOP -> timeStop(g, mc, w, h, t, elapsed, ab.vfxMs - elapsed);
                case SEND_NETHER, SEND_END, SEND_OVERWORLD -> portal(g, w, h, t, ab.rgb);
                case OVERDRIVE -> overdrive(g, w, h, t);
                case OMNISCIENCE -> omniscience(g, w, h, t);
                case ZERO_G -> zeroG(g, w, h, t);
                case BLACK_HOLE -> blackHole(g, w, h, t);
            }
        }

        if (ClientState.flightOn) {
            flight(g, mc, w, h, now);
        } else {
            lastSampleMs = 0;
        }

        if (ClientState.adaptationActive()) {
            adaptHud(g, mc);
        }

        drawToast(g, mc, w, now);
    }

    private static void drawToast(GuiGraphics g, Minecraft mc, int w, long now) {
        if (toastText == null) return;
        long el = now - toastStart;
        if (el > TOAST_MS) {
            toastText = null;
            return;
        }
        float a = 1f;
        if (el < 150) a = el / 150f;
        else if (el > TOAST_MS - 400) a = (TOAST_MS - el) / 400f;

        int tw = mc.font.width(toastText);
        int x = (w - tw) / 2;
        int y = 22;
        box(g, x - 8, y - 4, x + tw + 8, y + 13, 0x000000, 0.65f * a);
        box(g, x - 8, y + 12, x + tw + 8, y + 13, toastRgb, a);
        g.drawString(mc.font, toastText, x, y, col(toastRgb, a), true);
    }

    // ------------------------------------------------------------------ the nine effects

    /** Flash of white, a jagged bolt tearing down the screen, violet edge glow. */
    private static void smite(GuiGraphics g, int w, int h, float t, long seed) {
        float inv = 1f - t;
        box(g, 0, 0, w, h, 0xFFFFFF, 0.85f * inv * inv * inv);
        vignette(g, w, h, 0x8A6BFF, 0.55f * inv, h / 5);

        if (t < 0.7f) {
            float bolt = 1f - t / 0.7f;
            int x = w / 2 + (int) ((rnd((int) seed) - 0.5f) * w * 0.4f);
            int segs = 14;
            int segH = h / segs + 1;
            for (int i = 0; i < segs; i++) {
                x += (int) ((rnd(i * 31 + (int) (seed & 0xFFFF)) - 0.5f) * 36f);
                int y = i * segH;
                box(g, x - 7, y, x + 8, y + segH, 0x9FB4FF, 0.30f * bolt);
                box(g, x - 2, y, x + 3, y + segH, 0xFFFFFF, 0.95f * bolt);
            }
        }
    }

    /** Cyan speed lines rush in from both edges while a tunnel vignette pulses. */
    private static void blink(GuiGraphics g, int w, int h, float t) {
        float inv = 1f - t;
        float ease = 1f - (1f - t) * (1f - t);
        vignette(g, w, h, 0x55E6FF, 0.75f * inv, h / 3);
        for (int i = 0; i < 34; i++) {
            int y = (int) (rnd(i * 7 + 1) * h);
            int len = (int) ((0.12f + 0.38f * rnd(i * 13 + 5)) * w * ease);
            int th = 1 + (i % 3 == 0 ? 1 : 0);
            box(g, 0, y, len, y + th, 0xBFF6FF, 0.85f * inv);
            box(g, w - len, y, w, y + th, 0xBFF6FF, 0.85f * inv);
        }
        box(g, 0, 0, w, h, 0xFFFFFF, 0.25f * inv * inv);
    }

    /** Gold tint, expanding clock rings and two spinning clock hands. */
    private static void timeWarp(GuiGraphics g, int w, int h, float t) {
        float fade = fadeInOut(t, 0.15f, 0.35f);
        g.fillGradient(0, 0, w, h, col(0xFFD36B, 0.30f * fade), col(0xFF8A00, 0.30f * fade));

        int cx = w / 2, cy = h / 2;
        int reach = Math.max(w, h) / 2;
        for (int i = 0; i < 5; i++) {
            float p = frac(t * 1.2f + i * 0.2f);
            int hw = (int) (p * reach);
            int hh = (int) (p * reach * 0.62f);
            float a = (1f - p) * fade;
            g.renderOutline(cx - hw, cy - hh, hw * 2, hh * 2, col(0xFFE08A, a));
            g.renderOutline(cx - hw - 1, cy - hh - 1, hw * 2 + 2, hh * 2 + 2, col(0xFFC94D, a * 0.6f));
        }
        hand(g, cx, cy, 60, t * 40f, 0xFFF3C4, fade);
        hand(g, cx, cy, 38, t * 3.3f, 0xFFC94D, fade);
        box(g, cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFF, fade);
    }

    /** Cold grey-blue wash, a sweeping band and streaks falling across the screen. */
    private static void weather(GuiGraphics g, int w, int h, float t) {
        float fade = fadeInOut(t, 0.2f, 0.4f);
        g.fillGradient(0, 0, w, h, col(0x4A5E78, 0.45f * fade), col(0x9DB6D4, 0.10f * fade));

        int bandY = (int) (frac(t * 1.4f) * h);
        g.fillGradient(0, Math.max(0, bandY - 50), w, bandY, col(0xBFD6F2, 0f), col(0xBFD6F2, 0.28f * fade));
        box(g, 0, bandY, w, bandY + 2, 0xE6F2FF, 0.6f * fade);

        for (int i = 0; i < 70; i++) {
            int x = (int) (rnd(i * 5 + 3) * w);
            float speed = 1.6f + 1.8f * rnd(i * 11 + 2);
            int y = (int) (frac(t * speed + rnd(i * 17 + 9)) * (h + 40)) - 20;
            int len = 10 + (int) (rnd(i * 3 + 8) * 14);
            box(g, x, y, x + 1, y + len, 0xCFE4FF, 0.55f * fade);
        }
    }

    /** Green healing wave rising from the bottom plus a swelling cross. */
    private static void restore(GuiGraphics g, int w, int h, float t) {
        float inv = 1f - t;
        float rise = 1f - (1f - t) * (1f - t);
        int top = (int) (h * (1f - rise));
        g.fillGradient(0, top, w, h, col(0x4DFF88, 0f), col(0x4DFF88, 0.55f * inv));
        box(g, 0, top, w, top + 2, 0xC8FFD9, 0.9f * inv);
        vignette(g, w, h, 0x4DFF88, 0.55f * inv, h / 4);

        int cx = w / 2, cy = h / 2;
        int arm = (int) (14 + 60 * rise);
        int th = (int) (4 + 6 * rise);
        float a = 0.9f * inv;
        box(g, cx - arm, cy - th, cx + arm, cy + th, 0xDFFFEA, a);
        box(g, cx - th, cy - arm, cx + th, cy + arm, 0xDFFFEA, a);
    }

    /** Pulsing golden frame for the whole 20 seconds, with a live countdown. */
    private static void godMode(GuiGraphics g, Minecraft mc, int w, int h, float t, long remainingMs) {
        float intro = Math.max(0f, 1f - t * 40f); // first ~0.5 s flash
        float fadeOut = remainingMs < 1500 ? remainingMs / 1500f : 1f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);

        box(g, 0, 0, w, h, 0xFFE680, 0.55f * intro);
        vignette(g, w, h, 0xFFD700, (0.28f + 0.22f * pulse) * fadeOut, h / 4);
        g.renderOutline(4, 4, w - 8, h - 8, col(0xFFE680, (0.35f + 0.35f * pulse) * fadeOut));

        String text = "GOD MODE  " + ((remainingMs + 999) / 1000) + "s";
        int tw = mc.font.width(text);
        box(g, w / 2 - tw / 2 - 6, h - 58, w / 2 + tw / 2 + 6, h - 44, 0x000000, 0.55f * fadeOut);
        g.drawString(mc.font, text, w / 2 - tw / 2, h - 54, col(0xFFE680, fadeOut), true);
    }

    /** White streaks racing upward and a growing white-out at the top of the screen. */
    private static void skyward(GuiGraphics g, int w, int h, float t) {
        float fade = fadeInOut(t, 0.1f, 0.5f);
        for (int i = 0; i < 46; i++) {
            int x = (int) (rnd(i * 9 + 4) * w);
            float speed = 2.2f + 2.2f * rnd(i * 5 + 1);
            int len = 22 + (int) (rnd(i * 7 + 6) * 60);
            int y = h - (int) (frac(t * speed + rnd(i * 3 + 2)) * (h + len)) ;
            box(g, x, y, x + 2, y + len, 0xFFFFFF, 0.7f * fade);
        }
        g.fillGradient(0, 0, w, h / 2, col(0xFFFFFF, 0.65f * t), col(0xFFFFFF, 0f));
        vignette(g, w, h, 0xBFD8FF, 0.45f * fade, h / 6);
    }

    /** Frosted corners and icy edges that slowly thaw. */
    private static void freeze(GuiGraphics g, int w, int h, float t) {
        float fade = t < 0.1f ? t / 0.1f : 1f - (t - 0.1f) / 0.9f;
        fade = Math.max(0f, fade);
        box(g, 0, 0, w, h, 0x9FE8FF, 0.16f * fade);
        vignette(g, w, h, 0x8FEFFF, 0.85f * fade, h / 3);

        int size = Math.min(w, h) / 3;
        for (int k = 0; k < size; k += 2) {
            int len = (size - k) * 2;
            float a = 0.55f * fade * (1f - k / (float) size);
            box(g, 0, k, len, k + 2, 0xE8FBFF, a);                 // top-left
            box(g, w - len, k, w, k + 2, 0xE8FBFF, a);             // top-right
            box(g, 0, h - k - 2, len, h - k, 0xE8FBFF, a);         // bottom-left
            box(g, w - len, h - k - 2, w, h - k, 0xE8FBFF, a);     // bottom-right
        }
        // a few crack lines
        for (int i = 0; i < 6; i++) {
            int x = (int) (rnd(i * 4 + 1) * w);
            int y0 = (int) (rnd(i * 6 + 2) * h * 0.3f);
            int len = (int) (h * 0.15f + rnd(i * 8 + 3) * h * 0.2f * Math.min(1f, t * 4f));
            box(g, x, y0, x + 1, y0 + len, 0xFFFFFF, 0.5f * fade);
        }
    }

    /** Red flicker, a descending scanline and glitch bars. */
    private static void purge(GuiGraphics g, Minecraft mc, int w, int h, float t, long elapsed, int count) {
        float inv = 1f - t;
        int frame = (int) (elapsed / 55);
        float flicker = 0.12f + 0.12f * rnd(frame * 3 + 1);
        box(g, 0, 0, w, h, 0xFF1A1A, flicker * inv * 2f);
        vignette(g, w, h, 0xFF2A2A, 0.6f * inv, h / 4);

        int y = (int) (Math.min(1.1f, t * 1.25f) * h);
        g.fillGradient(0, Math.max(0, y - 70), w, y, col(0xFF3030, 0f), col(0xFF3030, 0.35f * inv));
        box(g, 0, y, w, y + 3, 0xFFB0B0, 0.85f * inv);

        for (int i = 0; i < 10; i++) {
            float r = rnd(frame * 31 + i);
            int by = (int) (rnd(frame * 17 + i * 3) * h);
            int bh = 2 + (int) (rnd(frame * 5 + i) * 10);
            int bx = (int) (rnd(frame * 11 + i) * w * 0.55f);
            int bw = (int) (w * (0.15f + 0.55f * rnd(frame * 19 + i)));
            int rgb = r < 0.5f ? 0xFF2020 : (r < 0.8f ? 0xFFFFFF : 0x000000);
            box(g, bx, by, bx + bw, by + bh, rgb, 0.5f * inv);
        }

        String text = "HOSTILES PURGED: " + count;
        int jx = (int) ((rnd(frame * 7 + 2) - 0.5f) * 8f);
        int jy = (int) ((rnd(frame * 9 + 4) - 0.5f) * 4f);
        int tw = mc.font.width(text);
        g.drawString(mc.font, text, w / 2 - tw / 2 + jx + 1, h / 2 + jy + 1, col(0x00FFFF, 0.6f * inv), false);
        g.drawString(mc.font, text, w / 2 - tw / 2 + jx, h / 2 + jy, col(0xFF4040, inv), true);
    }


    /** Everything stops: washed-out screen, a frozen clock face and a ring that keeps contracting. */
    private static void timeStop(GuiGraphics g, Minecraft mc, int w, int h, float t, long elapsed, long remainingMs) {
        float fade = fadeInOut(t, 0.03f, 0.10f);
        box(g, 0, 0, w, h, 0xC4CCE0, 0.20f * fade);
        vignette(g, w, h, 0x0A0A1E, 0.8f * fade, h / 3);

        int cx = w / 2, cy = h / 2;
        int radius = Math.min(w, h) / 4;
        circle(g, cx, cy, radius, 0xFFFFFF, 0.7f * fade, 72);
        circle(g, cx, cy, radius + 6, 0xAAB4D0, 0.35f * fade, 72);
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI / 6.0;
            int x = cx + (int) (Math.cos(ang) * (radius - 8));
            int y = cy + (int) (Math.sin(ang) * (radius - 8));
            box(g, x - 2, y - 2, x + 3, y + 3, 0xFFFFFF, 0.9f * fade);
        }
        // frozen hands: they never move
        hand(g, cx, cy, radius - 16, -1.15f, 0xFFFFFF, fade);
        hand(g, cx, cy, radius - 34, 0.55f, 0xFFFFFF, fade);
        box(g, cx - 3, cy - 3, cx + 4, cy + 4, 0xFFFFFF, fade);

        // contracting pulse, once per second
        float p = frac(elapsed / 1000f);
        int pr = (int) ((1f - p) * radius * 1.7f) + 4;
        circle(g, cx, cy, pr, 0xDDE6FF, 0.5f * p * fade, 64);

        String text = "TIME STOPPED  " + ((remainingMs + 999) / 1000) + "s";
        int tw = mc.font.width(text);
        box(g, cx - tw / 2 - 6, cy + radius + 18, cx + tw / 2 + 6, cy + radius + 32, 0x000000, 0.55f * fade);
        g.drawString(mc.font, text, cx - tw / 2, cy + radius + 22, col(0xFFFFFF, fade), true);
    }

    /** A swirling tunnel in the destination's colour that ends in a white flash. */
    private static void portal(GuiGraphics g, int w, int h, float t, int rgb) {
        float fade = fadeInOut(t, 0.12f, 0.15f);
        int cx = w / 2, cy = h / 2;
        box(g, 0, 0, w, h, rgb, 0.22f * fade);
        vignette(g, w, h, rgb, 0.85f * fade, h / 2);

        for (int i = 0; i < 9; i++) {
            float p = frac(t * 2.2f + i / 9f);
            float size = 1f - p;                       // rings shrink into the centre
            int hw = (int) (size * w * 0.5f);
            int hh = (int) (size * h * 0.5f);
            float a = p * 0.95f * fade;
            g.renderOutline(cx - hw, cy - hh, hw * 2, hh * 2, col(rgb, a));
            g.renderOutline(cx - hw + 2, cy - hh + 2, Math.max(0, hw * 2 - 4), Math.max(0, hh * 2 - 4), col(0xFFFFFF, a * 0.25f));
        }
        // swirling spokes
        for (int i = 0; i < 8; i++) {
            double ang = t * 6.0 + i * Math.PI / 4.0;
            hand(g, cx, cy, Math.min(w, h) / 3, (float) ang, rgb, 0.6f * fade);
        }
        if (t > 0.82f) {
            box(g, 0, 0, w, h, 0xFFFFFF, (t - 0.82f) / 0.18f * 0.95f);
        }
    }

    /** Orange heat: fast streaks, rolling heat bands and a pounding edge glow. */
    private static void overdrive(GuiGraphics g, int w, int h, float t) {
        float fade = fadeInOut(t, 0.08f, 0.35f);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 40f);
        vignette(g, w, h, 0xFF7A1A, (0.45f + 0.3f * pulse) * fade, h / 3);

        for (int i = 0; i < 44; i++) {
            int y = (int) (rnd(i * 5 + 11) * h);
            float speed = 3f + 3f * rnd(i * 3 + 7);
            int len = 40 + (int) (rnd(i * 9 + 2) * 120);
            int x = (int) (frac(t * speed + rnd(i * 13 + 5)) * (w + len)) - len;
            box(g, x, y, x + len, y + 2, 0xFFC35A, 0.75f * fade);
        }
        for (int i = 0; i < 10; i++) {
            int y = (int) (h - frac(t * 1.6f + i / 10f) * (h + 30));
            box(g, 0, y, w, y + 6, 0xFF5A00, 0.10f * fade);
        }
        box(g, 0, 0, w, h, 0xFFD27F, 0.30f * (1f - t) * (1f - t));
    }

    /** Green radar: scanlines, range rings and a sweeping beam. */
    private static void omniscience(GuiGraphics g, int w, int h, float t) {
        float fade = fadeInOut(t, 0.10f, 0.30f);
        box(g, 0, 0, w, h, 0x00FF99, 0.07f * fade);
        for (int y = 0; y < h; y += 6) {
            box(g, 0, y, w, y + 1, 0x7CFFC4, 0.12f * fade);
        }
        vignette(g, w, h, 0x3CFFA0, 0.55f * fade, h / 4);

        int cx = w / 2, cy = h / 2;
        int max = Math.min(w, h) / 2 - 10;
        for (int i = 1; i <= 3; i++) {
            circle(g, cx, cy, max * i / 3, 0x7CFFC4, 0.35f * fade, 72);
        }
        float grow = frac(t * 1.5f);
        circle(g, cx, cy, (int) (grow * max * 1.2f), 0xCFFFE8, 0.6f * (1f - grow) * fade, 80);

        float sweep = t * 14f;
        for (int k = 0; k < 6; k++) {   // beam with a fading tail
            hand(g, cx, cy, max, sweep - k * 0.07f, 0xBFFFE0, (0.8f - k * 0.13f) * fade);
        }
        box(g, cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFF, fade);
    }

    /** Violet gravity inversion: bands and chevrons drifting up, rings collapsing outward-in. */
    private static void zeroG(GuiGraphics g, int w, int h, float t) {
        float fade = fadeInOut(t, 0.10f, 0.30f);
        vignette(g, w, h, 0x9A4DFF, 0.6f * fade, h / 3);
        g.fillGradient(0, 0, w, h, col(0xB88CFF, 0.0f), col(0x6A2DD0, 0.22f * fade));

        for (int i = 0; i < 14; i++) {
            int y = (int) (h - frac(t * 1.3f + i / 14f) * (h + 40));
            int th = 3 + (i % 3) * 3;
            box(g, 0, y, w, y + th, 0xD6B8FF, 0.20f * fade);
        }
        int cx = w / 2;
        for (int j = 0; j < 4; j++) {
            int y = (int) (h - frac(t * 1.1f + j / 4f) * (h + 60)) + 20;
            for (int k = 0; k <= 16; k++) {
                box(g, cx - k * 2 - 2, y + k, cx - k * 2, y + k + 3, 0xE6D4FF, 0.55f * fade);
                box(g, cx + k * 2, y + k, cx + k * 2 + 2, y + k + 3, 0xE6D4FF, 0.55f * fade);
            }
        }
        for (int i = 0; i < 3; i++) {
            float p = frac(t * 1.4f + i / 3f);
            circle(g, w / 2, h / 2, (int) ((1f - p) * Math.min(w, h) * 0.5f) + 6, 0xCFA8FF, 0.5f * p * fade, 64);
        }
    }


    /** Everything is dragged to a black core ringed by an orange and purple accretion disk, then it collapses. */
    private static void blackHole(GuiGraphics g, int w, int h, float t) {
        float fade = fadeInOut(t, 0.08f, 0.10f);
        vignette(g, w, h, 0x000000, 0.9f * fade, h / 2);
        vignette(g, w, h, 0x7A2CFF, 0.35f * fade, h / 3);

        int cx = w / 2, cy = h / 2;
        float grow = 1f - (1f - Math.min(1f, t * 1.4f)) * (1f - Math.min(1f, t * 1.4f));
        int r = (int) (8 + 46 * grow);

        // the black core (a filled disc)
        for (int dy = -r; dy <= r; dy += 2) {
            int hw = (int) Math.sqrt((double) r * r - (double) dy * dy);
            box(g, cx - hw, cy + dy, cx + hw, cy + dy + 2, 0x000000, 0.95f * fade);
        }

        // spiral arms that wind inward
        for (int arm = 0; arm < 4; arm++) {
            for (int k = 0; k < 44; k++) {
                double ang = arm * Math.PI / 2.0 + k * 0.20 - t * 16.0;
                double rad = r + 6 + k * 5.5;
                int x = cx + (int) (Math.cos(ang) * rad);
                int y = cy + (int) (Math.sin(ang) * rad * 0.62);
                float a = (1f - k / 44f) * 0.85f * fade;
                box(g, x - 1, y - 1, x + 2, y + 2, (k % 2 == 0) ? 0xFFA040 : 0xB070FF, a);
            }
        }

        circle(g, cx, cy, r + 8, 0xFFC070, 0.9f * fade, 96);
        circle(g, cx, cy, r + 14, 0xB070FF, 0.6f * fade, 96);

        // collapse flash at the very end
        if (t > 0.90f) {
            box(g, 0, 0, w, h, 0xFFFFFF, (t - 0.90f) / 0.10f * 0.95f);
        }
    }

    // ------------------------------------------------------------------ flight look

    /** Winged HUD while flying: feathered wings that flap, speed streaks, cyan edge glow and a flight readout. */
    private static void flight(GuiGraphics g, Minecraft mc, int w, int h, long now) {
        if (mc.player == null) return;

        // measure speed (blocks per second) from position changes
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        if (lastSampleMs != 0 && now - lastSampleMs >= 50) {
            double dt = (now - lastSampleMs) / 1000.0;
            double d = Math.sqrt((px - lastX) * (px - lastX) + (py - lastY) * (py - lastY) + (pz - lastZ) * (pz - lastZ));
            flightSpeed = flightSpeed * 0.8 + (d / dt) * 0.2;
            lastSampleMs = now; lastX = px; lastY = py; lastZ = pz;
        } else if (lastSampleMs == 0) {
            lastSampleMs = now; lastX = px; lastY = py; lastZ = pz;
        }

        float speedF = (float) Math.min(1.0, flightSpeed / 18.0);
        float flap = (float) Math.sin(now / 260.0);

        vignette(g, w, h, 0x7FE8FF, 0.16f + 0.22f * speedF, h / 5);

        // wings
        for (int side = 0; side < 2; side++) {
            for (int f = 0; f < 7; f++) {
                int baseY = (int) (h * 0.62f + f * 11 + flap * (4 + f));
                int len = 120 - f * 11 + (int) (flap * 6);
                float slope = 0.10f + f * 0.07f;
                int rgb = (f % 2 == 0) ? 0xDFFBFF : 0x8FE3FF;
                for (int k = 0; k < len; k += 2) {
                    int y = baseY - (int) (k * slope);
                    float a = (1f - k / (float) len) * 0.55f;
                    int x = side == 0 ? k : w - k - 2;
                    box(g, x, y, x + 2, y + 3, rgb, a);
                }
            }
        }

        // speed streaks near the edges
        int streaks = 6 + (int) (speedF * 28);
        float tt = now / 1000f;
        for (int i = 0; i < streaks; i++) {
            boolean left = (i % 2 == 0);
            int x = left ? (int) (rnd(i * 7 + 3) * w * 0.22f) : w - (int) (rnd(i * 7 + 3) * w * 0.22f);
            float sp = 1.2f + 1.8f * rnd(i * 5 + 1) + speedF * 2f;
            int len = 12 + (int) (rnd(i * 3 + 9) * 26);
            int y = (int) (frac(tt * sp + rnd(i * 11 + 4)) * (h + len)) - len;
            box(g, x, y, x + 1, y + len, 0xE8FCFF, 0.35f + 0.3f * speedF);
        }

        // readout
        String text = String.format(java.util.Locale.ROOT, "FLIGHT   ALT %d   SPD %.1f", (int) py, flightSpeed);
        int tw = mc.font.width(text);
        box(g, w / 2 - tw / 2 - 6, 4, w / 2 + tw / 2 + 6, 17, 0x000000, 0.5f);
        box(g, w / 2 - tw / 2 - 6, 16, w / 2 + tw / 2 + 6, 17, 0x7FE8FF, 0.9f);
        g.drawString(mc.font, text, w / 2 - tw / 2, 7, col(0xBFF3FF, 1f), true);
    }


    // ------------------------------------------------------------------ adaptation HUD (top-left)

    /**
     * Badge in the top-left corner: a pixel-art Minecraft block emblem, the face of the mob type you are
     * adapting to, its name and a 5-step adaptation bar (each step = 20% dodge, so step 5 = 100%).
     */
    private static void adaptHud(GuiGraphics g, Minecraft mc) {
        int x = 6, y = 6;
        String mob = ClientState.adaptMob;
        int level = ClientState.adaptLevel;
        boolean has = mob != null && !mob.isEmpty();

        int panelW = 150, panelH = 50;
        box(g, x, y, x + panelW, y + panelH, 0x070C07, 0.75f);
        g.renderOutline(x, y, panelW, panelH, col(has ? 0x7CFF7C : 0x5A7A5A, 0.95f));
        box(g, x + 1, y + 1, x + panelW - 1, y + 2, 0x7CFF7C, has ? 0.35f : 0.12f);

        cube(g, x + 16, y + 9);

        int fx = x + 34, fy = y + 8;
        if (has) {
            drawFace(g, fx, fy, 4, mob);
            g.renderOutline(fx - 1, fy - 1, 34, 34, col(0xFFFFFF, 0.55f));
        } else {
            box(g, fx, fy, fx + 32, fy + 32, 0x1E261E, 0.9f);
            g.drawString(mc.font, "?", fx + 13, fy + 12, col(0x88AA88, 1f), true);
        }

        int tx = x + 74;
        g.drawString(mc.font, "ADAPTING TO", tx, y + 6, col(0x9BB59B, 1f), false);
        g.drawString(mc.font, has ? prettyName(mob) : "waiting...", tx, y + 17, col(has ? 0xFFFFFF : 0xAAAAAA, 1f), true);

        for (int i = 0; i < 5; i++) {
            int sx = tx + i * 14;
            boolean filled = i < level;
            int rgb = i < 2 ? 0xA8E060 : (i < 4 ? 0xE8D040 : 0xFF7A3A);
            box(g, sx, y + 30, sx + 12, y + 36, filled ? rgb : 0x2A332A, filled ? 1f : 0.9f);
        }
        String dodge = level > 0 ? "dodge " + (level * 20) + "%" : "dodge 0%";
        g.drawString(mc.font, dodge, tx, y + 39, col(0xBFD6BF, 1f), false);
    }

    /** The classic isometric grass block, drawn with rectangles. cx = horizontal centre, y0 = top. */
    private static void cube(GuiGraphics g, int cx, int y0) {
        for (int dy = 0; dy < 12; dy++) {
            int hw = dy < 6 ? (dy + 1) * 2 : (12 - dy) * 2;
            box(g, cx - hw, y0 + dy, cx + hw, y0 + dy + 1, 0x62BE4A, 1f);
        }
        for (int dx = 0; dx < 12; dx++) {
            int xl = cx - 12 + dx;
            int topL = 6 + dx / 2;
            box(g, xl, y0 + topL, xl + 1, y0 + topL + 12, 0x8B5A2B, 1f);
            box(g, xl, y0 + topL, xl + 1, y0 + topL + 3, 0x4E9A38, 1f);

            int xr = cx + dx;
            int topR = 11 - dx / 2;
            box(g, xr, y0 + topR, xr + 1, y0 + topR + 12, 0x6B4220, 1f);
            box(g, xr, y0 + topR, xr + 1, y0 + topR + 3, 0x3F7F2E, 1f);
        }
    }

    /** An 8x8 pixel-art face for a mob id, using known colours for the common mobs and a hashed colour for the rest. */
    private static void drawFace(GuiGraphics g, int fx, int fy, int px, String id) {
        int base = switch (id) {
            case "zombie" -> 0x5E9A4E;
            case "husk" -> 0xA89868;
            case "drowned" -> 0x4E9A94;
            case "skeleton" -> 0xD4D4CC;
            case "stray" -> 0xB6C8D4;
            case "creeper" -> 0x4FB43C;
            case "spider" -> 0x4A3A34;
            case "cave_spider" -> 0x2A5A58;
            case "enderman" -> 0x1C1C26;
            case "blaze" -> 0xE8B22A;
            case "witch" -> 0x6A4A7A;
            case "pillager", "vindicator", "evoker" -> 0x7A7A72;
            case "slime" -> 0x6ADA6A;
            case "magma_cube" -> 0x9A3A1A;
            case "ghast" -> 0xF0F0F0;
            case "piglin", "piglin_brute" -> 0xC88A6A;
            case "zombified_piglin" -> 0x9AB070;
            case "wither_skeleton" -> 0x2A2A2A;
            case "phantom" -> 0x4A5A9A;
            case "warden" -> 0x0A3A4A;
            case "player" -> 0xC89A7A;
            case "lava" -> 0xE0561A;
            case "inFire", "onFire" -> 0xFF8A1F;
            case "hotFloor" -> 0xB8441A;
            case "fall", "flyIntoWall" -> 0xBFAF9A;
            case "drown" -> 0x2F6FD6;
            case "cactus", "sweetBerryBush" -> 0x2E8B3A;
            case "explosion", "explosion.player" -> 0xFFB040;
            case "lightningBolt" -> 0xE8E8FF;
            case "freeze" -> 0xA8E6FF;
            case "starve" -> 0x8A5A3A;
            case "magic", "indirectMagic" -> 0xB060FF;
            case "outOfWorld" -> 0x120A24;
            default -> {
                int h = id.hashCode();
                int r = 90 + (int) (rnd(h) * 130);
                int gg = 90 + (int) (rnd(h + 1) * 130);
                int b = 90 + (int) (rnd(h + 2) * 130);
                yield (r << 16) | (gg << 8) | b;
            }
        };
        int seed = id.hashCode() * 31;
        for (int gy = 0; gy < 8; gy++) {
            for (int gx = 0; gx < 8; gx++) {
                float shade = 0.80f + 0.32f * rnd(seed + gy * 8 + gx);
                int r = Math.min(255, (int) (((base >> 16) & 0xFF) * shade));
                int gr = Math.min(255, (int) (((base >> 8) & 0xFF) * shade));
                int b = Math.min(255, (int) ((base & 0xFF) * shade));
                int x1 = fx + gx * px, y1 = fy + gy * px;
                box(g, x1, y1, x1 + px, y1 + px, (r << 16) | (gr << 8) | b, 1f);
            }
        }

        if (HAZARDS.containsKey(id)) {
            // a damage kind has no face: draw a bright diagonal flash instead
            for (int i = 0; i < 8; i++) {
                cell(g, fx, fy, px, i, 7 - i, 1, 1, 0xFFFFFF);
                if (i < 7) cell(g, fx, fy, px, i + 1, 7 - i, 1, 1, 0xFFE8B0);
            }
            return;
        }

        int eye = switch (id) {
            case "enderman" -> 0xE070FF;
            case "spider", "cave_spider" -> 0xFF2020;
            case "blaze" -> 0x222222;
            default -> 0x101010;
        };
        if (id.equals("creeper")) {
            cell(g, fx, fy, px, 1, 2, 2, 2, 0x101010);
            cell(g, fx, fy, px, 5, 2, 2, 2, 0x101010);
            cell(g, fx, fy, px, 3, 4, 2, 2, 0x101010);
            cell(g, fx, fy, px, 2, 5, 1, 2, 0x101010);
            cell(g, fx, fy, px, 5, 5, 1, 2, 0x101010);
        } else {
            cell(g, fx, fy, px, 1, 3, 2, 2, eye);
            cell(g, fx, fy, px, 5, 3, 2, 2, eye);
            cell(g, fx, fy, px, 2, 6, 4, 1, 0x20140E);
        }
    }

    private static void cell(GuiGraphics g, int fx, int fy, int px, int gx, int gy, int gw, int gh, int rgb) {
        box(g, fx + gx * px, fy + gy * px, fx + (gx + gw) * px, fy + (gy + gh) * px, rgb, 1f);
    }

    /** Damage kinds (not creatures) that Adaptation can learn from, with their display names. */
    private static final Map<String, String> HAZARDS = Map.ofEntries(
            Map.entry("lava", "Lava"), Map.entry("inFire", "Fire"), Map.entry("onFire", "Burning"),
            Map.entry("hotFloor", "Magma"), Map.entry("fall", "Falling"), Map.entry("flyIntoWall", "Impact"),
            Map.entry("drown", "Drowning"), Map.entry("cactus", "Cactus"), Map.entry("sweetBerryBush", "Berry Bush"),
            Map.entry("explosion", "Explosion"), Map.entry("explosion.player", "Explosion"),
            Map.entry("lightningBolt", "Lightning"), Map.entry("freeze", "Freezing"), Map.entry("starve", "Hunger"),
            Map.entry("wither", "Wither"), Map.entry("magic", "Magic"), Map.entry("indirectMagic", "Magic"),
            Map.entry("outOfWorld", "The Void"), Map.entry("genericKill", "Kill"), Map.entry("inWall", "Suffocation"),
            Map.entry("cramming", "Cramming"), Map.entry("fallingBlock", "Falling Block"),
            Map.entry("anvil", "Anvil"), Map.entry("fallingStalactite", "Stalactite"),
            Map.entry("stalagmite", "Stalagmite"), Map.entry("dragonBreath", "Dragon Breath"),
            Map.entry("dryout", "Drying Out"), Map.entry("thorns", "Thorns"), Map.entry("generic", "Damage"));

    /** "zombie_villager" becomes "Zombie Villager"; damage kinds like "lava" use their friendly name. */
    private static String prettyName(String id) {
        String known = HAZARDS.get(id);
        if (known != null) return known;
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ drawing helpers

    /** ARGB from an RGB colour and a 0..1 alpha. */
    private static int col(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** Filled rectangle that quietly skips invisible or empty boxes. */
    private static void box(GuiGraphics g, int x1, int y1, int x2, int y2, int rgb, float alpha) {
        if (alpha < 0.004f || x2 <= x1 || y2 <= y1) return;
        g.fill(x1, y1, x2, y2, col(rgb, alpha));
    }

    /** Soft coloured frame around the screen edges, strongest at the very edge. */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha, int thick) {
        int layers = 10;
        for (int k = 0; k < layers; k++) {
            float f = 1f - k / (float) layers;
            float a = alpha * f * f;
            int t0 = thick * k / layers;
            int t1 = thick * (k + 1) / layers;
            if (t1 <= t0) continue;
            box(g, 0, t0, w, t1, rgb, a);              // top
            box(g, 0, h - t1, w, h - t0, rgb, a);      // bottom
            box(g, t0, t1, t1, h - t1, rgb, a);        // left
            box(g, w - t1, t1, w - t0, h - t1, rgb, a);// right
        }
    }

    /** A ring made of small dots. */
    private static void circle(GuiGraphics g, int cx, int cy, int radius, int rgb, float alpha, int dots) {
        if (radius < 2 || alpha < 0.004f) return;
        for (int i = 0; i < dots; i++) {
            double ang = i * (Math.PI * 2.0) / dots;
            int x = cx + (int) (Math.cos(ang) * radius);
            int y = cy + (int) (Math.sin(ang) * radius * 0.62);   // slightly squashed to read as a ring on wide screens
            box(g, x - 1, y - 1, x + 2, y + 2, rgb, alpha);
        }
    }

    /** A clock hand drawn as a short dotted line. */
    private static void hand(GuiGraphics g, int cx, int cy, int len, float angle, int rgb, float alpha) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        for (int r = 4; r <= len; r += 2) {
            int x = cx + (int) (cos * r);
            int y = cy + (int) (sin * r);
            box(g, x - 1, y - 1, x + 1, y + 1, rgb, alpha);
        }
    }

    private static float fadeInOut(float t, float in, float out) {
        if (t < in) return t / in;
        if (t > 1f - out) return Math.max(0f, (1f - t) / out);
        return 1f;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** Cheap deterministic pseudo random number in [0,1) from an integer. */
    private static float rnd(int n) {
        int h = n * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
