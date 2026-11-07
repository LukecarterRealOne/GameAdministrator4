package com.gameadmin.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/**
 * Runs a vanilla command as the server (full permissions, no chat spam).
 * Unlike a plain run, it checks the command parses first and writes the reason to the log when the game rejects it,
 * so a broken command shows up in latest.log as "Command rejected by the parser: ..." instead of failing silently.
 */
public final class Cmd {
    private Cmd() {}

    /** @return true if the game accepted and ran the command, false if it was rejected. */
    public static boolean run(MinecraftServer server, String command) {
        try {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
            ParseResults<CommandSourceStack> parsed = dispatcher.parse(command, source);
            if (parsed.getReader().canRead()) {
                String why = parsed.getExceptions().values().stream()
                        .map(Exception::getMessage).findFirst().orElse("unknown syntax problem");
                GameAdminMod.LOGGER.warn("Command rejected by the parser: /{}  -> {}", command, why);
                return false;
            }
            server.getCommands().performPrefixedCommand(source, command);
            return true;
        } catch (Exception ex) {
            GameAdminMod.LOGGER.warn("Command failed: /{}", command, ex);
            return false;
        }
    }
}
