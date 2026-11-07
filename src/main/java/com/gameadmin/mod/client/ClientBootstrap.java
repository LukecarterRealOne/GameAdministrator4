package com.gameadmin.mod.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Registers every client-only listener. Only ever called when running on the client. */
public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientModEvents::onRegisterKeys);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onGui);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRightClickBlock);
    }
}
