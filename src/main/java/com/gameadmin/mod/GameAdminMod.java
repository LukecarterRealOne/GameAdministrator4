package com.gameadmin.mod;

import com.gameadmin.mod.client.ClientBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** You are the game administrator. Press G for the Admin Console. */
@Mod(GameAdminMod.MOD_ID)
public class GameAdminMod {
    public static final String MOD_ID = "gameadmin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public GameAdminMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Net.register();

        // server-side hooks
        MinecraftForge.EVENT_BUS.addListener(ServerEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ServerEvents::onIncomingDamage);
        MinecraftForge.EVENT_BUS.addListener(AdminCommands::register);
        MinecraftForge.EVENT_BUS.addListener(AdminCommands::onLogin);

        // client-only hooks (keys, HUD, screens). Never touched on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init(modBus);
        }

        LOGGER.info("Game Administrator loaded. You are in charge.");
    }
}
