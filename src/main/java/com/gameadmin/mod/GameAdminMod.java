package com.gameadmin.mod;

import com.gameadmin.mod.client.ClientBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** You are the game administrator. Press G for the Admin Console. */
@Mod(GameAdminMod.MOD_ID)
public class GameAdminMod {
    public static final String MOD_ID = "gameadmin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public GameAdminMod(IEventBus modBus) {
        modBus.addListener(Net::register);

        // server-side hooks
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(AdminCommands::register);
        NeoForge.EVENT_BUS.addListener(AdminCommands::onLogin);

        // client-only hooks (keys, HUD, screens). Never touched on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init(modBus);
        }

        LOGGER.info("Game Administrator loaded. You are in charge.");
    }
}
