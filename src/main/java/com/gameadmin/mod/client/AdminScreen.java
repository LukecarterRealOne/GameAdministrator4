package com.gameadmin.mod.client;

import com.gameadmin.mod.Ability;
import com.gameadmin.mod.Net;
import com.gameadmin.mod.Passives;
import com.gameadmin.mod.Powers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The Game Administrator console: Abilities, Powers (modes / rules / command tools) and Passives. */
public class AdminScreen extends Screen {
    private static final int COLS = 4;
    private static final int BTN_W = 100;
    private static final int BTN_H = 28;
    private static final int VGAP = 4;
    private static final int GAP = 6;
    private static final int PAD = 16;
    private static final int CONTENT_H = 160;

    private static final String[] TAB_NAMES = {"Abilities", "Powers", "Passives"};
    private static int page = 0;

    private record Label(int x, int y, String text, int rgb) {}

    private final List<Button> abilityButtons = new ArrayList<>();
    private final List<Button> passiveButtons = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final Button[] tabButtons = new Button[TAB_NAMES.length];
    private Button flyButton;

    private int panelX, panelY, panelW, panelH;

    public AdminScreen() {
        super(Component.literal("Game Administrator"));
        // ask the server for the current passive state so the toggles show the truth
        Net.sendToServer(new Net.PassiveToggle(-1));
    }

    @Override
    protected void init() {
        abilityButtons.clear();
        passiveButtons.clear();
        labels.clear();

        int gridW = COLS * BTN_W + (COLS - 1) * GAP;
        panelW = gridW + PAD * 2;
        panelH = 54 + CONTENT_H + 34;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int gx = panelX + PAD;
        int y0 = panelY + 54;

        // tabs
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int tab = i;
            tabButtons[i] = Button.builder(Component.literal(TAB_NAMES[i]), b -> {
                        page = tab;
                        this.rebuildWidgets();
                    })
                    .bounds(gx + i * (BTN_W + GAP), panelY + 29, BTN_W, 20).build();
            this.addRenderableWidget(tabButtons[i]);
        }

        switch (page) {
            case 0 -> buildAbilities(gx, y0);
            case 1 -> buildPowers(gx, y0, gridW);
            default -> buildPassives(gx, y0);
        }

        // footer
        int footerY = panelY + panelH - 28;
        flyButton = Button.builder(Component.literal("Flight"), b ->
                        Net.sendToServer(new Net.FlightToggle(!ClientState.flightOn)))
                .bounds(panelX + panelW / 2 - 106, footerY, 100, 20)
                .tooltip(Tooltip.create(Component.literal("Toggle flight (H). You glow while flying and get a winged HUD.")))
                .build();
        this.addRenderableWidget(flyButton);
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(panelX + panelW / 2 + 6, footerY, 100, 20).build());
    }

    // ------------------------------------------------------------------ pages

    private void buildAbilities(int gx, int y0) {
        for (int i = 0; i < Ability.ALL.length; i++) {
            final int index = i;
            final Ability ability = Ability.ALL[i];
            int bx = gx + (i % COLS) * (BTN_W + GAP);
            int by = y0 + (i / COLS) * (BTN_H + VGAP);

            Button button = Button.builder(Component.literal(ability.label), b -> {
                        ClientState.lastCast = index;
                        Net.sendToServer(new Net.Cast(index));
                        this.onClose();
                    })
                    .bounds(bx, by, BTN_W, BTN_H)
                    .tooltip(Tooltip.create(Component.literal(ability.description)))
                    .build();
            this.addRenderableWidget(button);
            abilityButtons.add(button);
        }
    }

    private void buildPowers(int gx, int y0, int gridW) {
        int y = y0;

        // game modes
        labels.add(new Label(gx, y, "GAME MODE", 0xFFFFD36B));
        y += 11;
        String[] modeNames = {"Survival", "Creative", "Adventure", "Spectator"};
        for (int i = 0; i < modeNames.length; i++) {
            final int mode = i;
            this.addRenderableWidget(Button.builder(Component.literal(modeNames[i]), b ->
                            Net.sendToServer(new Net.Power(mode)))
                    .bounds(gx + i * (BTN_W + GAP), y, BTN_W, 20).build());
        }
        y += 26;

        // game rules: 3 columns, each = label + ON + OFF
        labels.add(new Label(gx, y, "GAME RULES", 0xFFFFD36B));
        y += 11;
        int cell = gridW / 3;
        for (int i = 0; i < Powers.RULES.length; i++) {
            final int rule = i;
            int cx = gx + (i % 3) * cell;
            int cy = y + (i / 3) * 22;
            labels.add(new Label(cx, cy + 6, Powers.RULES[i][0], 0xFFE9DDFF));
            this.addRenderableWidget(Button.builder(Component.literal("ON"), b ->
                            Net.sendToServer(new Net.Rule(rule, true)))
                    .bounds(cx + 70, cy, 32, 20)
                    .tooltip(Tooltip.create(Component.literal(Powers.RULES[i][1] + " = true"))).build());
            this.addRenderableWidget(Button.builder(Component.literal("OFF"), b ->
                            Net.sendToServer(new Net.Rule(rule, false)))
                    .bounds(cx + 104, cy, 32, 20)
                    .tooltip(Tooltip.create(Component.literal(Powers.RULES[i][1] + " = false"))).build());
        }
        y += 3 * 22 + 4;

        // tools
        this.addRenderableWidget(Button.builder(Component.literal("Command Line"), b ->
                        this.minecraft.setScreen(new CommandScreen()))
                .bounds(gx, y, 140, 20)
                .tooltip(Tooltip.create(Component.literal("Run any command, even in survival without cheats.")))
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Get Command Blocks"), b ->
                        Net.sendToServer(new Net.Power(4)))
                .bounds(gx + 146, y, 140, 20)
                .tooltip(Tooltip.create(Component.literal("Gives all three command blocks. Right-click one in survival to edit it.")))
                .build());
    }

    private void buildPassives(int gx, int y0) {
        for (int i = 0; i < Passives.NAMES.length; i++) {
            final int id = i;
            int y = y0 + i * 30;
            Button b = Button.builder(Component.literal(Passives.NAMES[i]), btn ->
                            Net.sendToServer(new Net.PassiveToggle(id)))
                    .bounds(gx, y, 160, 22)
                    .tooltip(Tooltip.create(Component.literal(Passives.DESCRIPTIONS[i])))
                    .build();
            this.addRenderableWidget(b);
            passiveButtons.add(b);
            labels.add(new Label(gx + 170, y + 7, Passives.DESCRIPTIONS[i], 0xFFB6AED0));
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g) {
        super.renderBackground(g);

        // panel
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE60B0B14);
        g.renderOutline(panelX, panelY, panelW, panelH, 0xFF7C4DFF);
        g.renderOutline(panelX + 1, panelY + 1, panelW - 2, panelH - 2, 0x557C4DFF);

        // header bar
        g.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 24, 0xFF1A1030);
        g.fill(panelX + 2, panelY + 24, panelX + panelW - 2, panelY + 25, 0xFF7C4DFF);

        String title = "GAME ADMINISTRATOR";
        g.drawString(this.font, title, panelX + (panelW - this.font.width(title)) / 2, panelY + 9, 0xFFE9DDFF, true);

        String hint = "V repeat   H fly";
        g.drawString(this.font, hint, panelX + panelW - PAD - this.font.width(hint), panelY + 35, 0xFF8F86AA, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        // live button text
        for (int i = 0; i < tabButtons.length; i++) {
            if (tabButtons[i] != null) tabButtons[i].active = (i != page);
        }
        if (flyButton != null) {
            flyButton.setMessage(Component.literal(ClientState.flightOn ? "Flight: ON" : "Flight: OFF"));
        }
        for (int i = 0; i < passiveButtons.size(); i++) {
            boolean on = ClientState.passiveOn(i);
            passiveButtons.get(i).setMessage(Component.literal(Passives.NAMES[i] + (on ? ": ON" : ": OFF")));
        }
        for (int i = 0; i < abilityButtons.size(); i++) {
            Ability a = Ability.ALL[i];
            long rem = ClientState.remainingMs(i);
            abilityButtons.get(i).setMessage(Component.literal(rem > 0 ? a.label + "  " + ((rem + 999) / 1000) + "s" : a.label));
        }

        super.render(g, mouseX, mouseY, partialTick);

        for (Label l : labels) {
            g.drawString(this.font, l.text(), l.x(), l.y(), l.rgb(), false);
        }

        // passive on/off colour stripe
        for (int i = 0; i < passiveButtons.size(); i++) {
            Button b = passiveButtons.get(i);
            int rgb = ClientState.passiveOn(i) ? 0xFF55FF77 : 0xFF553333;
            g.fill(b.getX(), b.getY(), b.getX() + 3, b.getY() + b.getHeight(), rgb);
        }

        // ability accent stripe + cooldown bar
        for (int i = 0; i < abilityButtons.size(); i++) {
            Ability a = Ability.ALL[i];
            Button b = abilityButtons.get(i);
            int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
            g.fill(x, y, x + 3, y + h, 0xFF000000 | a.rgb);

            long rem = ClientState.remainingMs(i);
            if (rem > 0) {
                float frac = Math.min(1f, rem / (float) a.cooldownMs);
                g.fill(x, y + h - 3, x + w, y + h, 0xAA000000);
                g.fill(x, y + h - 3, x + (int) (w * frac), y + h, 0xFF000000 | a.rgb);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
