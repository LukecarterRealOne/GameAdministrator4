package com.gameadmin.mod.client;

import com.gameadmin.mod.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Edits a command block from survival mode. Applying it sends the new settings to the server. */
public class CommandBlockScreen extends Screen {
    private static final String[] TYPES = {"Impulse", "Chain", "Repeat"};
    private static final int W = 340, H = 140;

    private final BlockPos pos;
    private int type = 0;
    private boolean conditional = false;
    private boolean auto = false;

    private EditBox box;
    private Button typeButton, condButton, autoButton;
    private int px, py;

    public CommandBlockScreen(BlockPos pos) {
        super(Component.literal("Command Block"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;

        box = new EditBox(this.font, px + 10, py + 36, W - 20, 20, Component.literal("Command"));
        box.setMaxLength(256);
        this.addRenderableWidget(box);
        this.setInitialFocus(box);

        int y = py + 64;
        typeButton = Button.builder(Component.literal(""), b -> {
            type = (type + 1) % TYPES.length;
            refresh();
        }).bounds(px + 10, y, 100, 20).build();
        condButton = Button.builder(Component.literal(""), b -> {
            conditional = !conditional;
            refresh();
        }).bounds(px + 120, y, 100, 20).build();
        autoButton = Button.builder(Component.literal(""), b -> {
            auto = !auto;
            refresh();
        }).bounds(px + 230, y, 100, 20).build();
        this.addRenderableWidget(typeButton);
        this.addRenderableWidget(condButton);
        this.addRenderableWidget(autoButton);
        refresh();

        this.addRenderableWidget(Button.builder(Component.literal("Apply"), b -> {
                    int flags = type | (conditional ? 4 : 0) | (auto ? 8 : 0);
                    Net.sendToServer(new Net.CommandBlockEdit(
                            pos.getX(), pos.getY(), pos.getZ(), box.getValue(), flags));
                    this.onClose();
                })
                .bounds(px + W / 2 - 106, py + H - 28, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(px + W / 2 + 6, py + H - 28, 100, 20).build());
    }

    private void refresh() {
        typeButton.setMessage(Component.literal(TYPES[type]));
        condButton.setMessage(Component.literal(conditional ? "Conditional" : "Unconditional"));
        autoButton.setMessage(Component.literal(auto ? "Always Active" : "Needs Redstone"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(px, py, px + W, py + H, 0xE60B0B14);
        g.renderOutline(px, py, W, H, 0xFFFF9A3C);
        g.fill(px + 2, py + 2, px + W - 2, py + 22, 0xFF2A1A0A);
        String title = "COMMAND BLOCK  (survival editor)";
        g.drawString(this.font, title, px + (W - this.font.width(title)) / 2, py + 8, 0xFFFFE2BF, true);
        g.drawString(this.font, "Type the command this block should run:", px + 10, py + 25, 0xFFB09070, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
