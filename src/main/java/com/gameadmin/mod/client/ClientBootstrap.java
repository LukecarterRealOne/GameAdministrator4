package com.gameadmin.mod.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;

/** Registers every client-only listener. Only ever called when running on the client. */
public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientModEvents::onRegisterKeys);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onGui);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onRightClickBlock);
    }
}
