package com.gameadmin.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Mod-bus client event: the key bindings. */
public class ClientModEvents {
    public static final KeyMapping OPEN = new KeyMapping("key.gameadmin.open",
            InputConstants.Type.KEYSYM, 71, "key.categories.gameadmin");     // 71 = G
    public static final KeyMapping REPEAT = new KeyMapping("key.gameadmin.repeat",
            InputConstants.Type.KEYSYM, 86, "key.categories.gameadmin");     // 86 = V
    public static final KeyMapping FLY = new KeyMapping("key.gameadmin.fly",
            InputConstants.Type.KEYSYM, 72, "key.categories.gameadmin");     // 72 = H

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
        event.register(REPEAT);
        event.register(FLY);
    }
}
