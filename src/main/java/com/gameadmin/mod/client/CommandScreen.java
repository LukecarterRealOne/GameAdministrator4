package com.gameadmin.mod.client;

import com.gameadmin.mod.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A command line that works in survival, with or without cheats. Commands run as you, with server permissions. */
public class CommandScreen extends Screen {
    private static final int W = 330, H = 100;

    private EditBox box;
    private int px, py;

    public CommandScreen() {
        super(Component.literal("Command Line"));
    }

    @Override
    protected void init() {
        px = (this.width - W) / 2;
        py = (this.height - H) / 2;

        box = new EditBox(this.font, px + 10, py + 36, W - 20, 20, Component.literal("Command"));
        box.setMaxLength(256);
        box.setValue("");
        this.addRenderableWidget(box);
        this.setInitialFocus(box);

        this.addRenderableWidget(Button.builder(Component.literal("Run"), b -> {
                    String text = box.getValue();
                    if (!text.isBlank()) {
                        Net.sendToServer(new Net.RunCommand(text));
                    }
                    this.onClose();
                })
                .bounds(px + W / 2 - 106, py + H - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(px + W / 2 + 6, py + H - 28, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(px, py, px + W, py + H, 0xE60B0B14);
        g.renderOutline(px, py, W, H, 0xFF7C4DFF);
        g.fill(px + 2, py + 2, px + W - 2, py + 22, 0xFF1A1030);
        String title = "COMMAND LINE";
        g.drawString(this.font, title, px + (W - this.font.width(title)) / 2, py + 8, 0xFFE9DDFF, true);
        g.drawString(this.font, "Type a command (the / is optional). @s means you.", px + 10, py + 25, 0xFF8F86AA, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
