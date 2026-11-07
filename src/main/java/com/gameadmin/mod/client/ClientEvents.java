package com.gameadmin.mod.client;

import com.gameadmin.mod.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CommandBlock;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Game-bus client events: keys, the VFX overlay and the survival command block editor. */
public class ClientEvents {

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        while (ClientModEvents.OPEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                if (ClientState.access) {
                    mc.setScreen(new AdminScreen());
                } else {
                    Net.sendToServer(new Net.PassiveToggle(-1));   // ask again: an operator may have just granted access
                    Vfx.toast("Admins only. Ask an operator: /gameadmin grant " + mc.player.getGameProfile().getName(), 0xFF5555);
                }
            }
        }
        while (ClientModEvents.REPEAT.consumeClick()) {
            if (mc.player != null && mc.screen == null && ClientState.lastCast >= 0) {
                Net.sendToServer(new Net.Cast(ClientState.lastCast));
            }
        }
        while (ClientModEvents.FLY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                Net.sendToServer(new Net.FlightToggle(!ClientState.flightOn));
            }
        }
    }

    /** Screen VFX and the flight look are drawn over the game view. */
    public static void onGui(RenderGuiEvent.Post event) {
        Vfx.extract(event.getGuiGraphics());
    }

    /**
     * Survival command blocks: right-click one (not sneaking, not in creative) to open our own editor,
     * because vanilla only lets creative players open the command block screen.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        if (player.isCreative() || player.isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND) return;
        BlockPos pos = event.getPos();
        if (!(event.getLevel().getBlockState(pos).getBlock() instanceof CommandBlock)) return;

        Minecraft.getInstance().setScreen(new CommandBlockScreen(pos));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
