package com.gameadmin.mod;

import com.gameadmin.mod.client.ClientHooks;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Every network message of the mod. */
public final class Net {
    private Net() {}

    private static final String PROTOCOL = "1";
    // acceptMissingOr: players WITHOUT the mod can still join a server that has it (they just cannot use the console).
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("gameadmin", "main"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    // ------------------------------------------------------------ client -> server

    /** Cast ability #n. */
    public record Cast(int ability) {}

    /** Turn flight on or off. */
    public record FlightToggle(boolean enable) {}

    /** Game mode (0 survival, 1 creative, 2 adventure, 3 spectator) or tool (4 = give command blocks). */
    public record Power(int id) {}

    /** Set a game rule (index into Powers.RULES) on or off. */
    public record Rule(int rule, boolean value) {}

    /** Run any command from the command line. */
    public record RunCommand(String command) {}

    /** Edit a command block from survival. flags: bits 0-1 type, bit 2 conditional, bit 3 always active. */
    public record CommandBlockEdit(int x, int y, int z, String command, int flags) {}

    /** Toggle passive 0..4. -1 just asks the server to send the current state. */
    public record PassiveToggle(int id) {}

    // ------------------------------------------------------------ server -> client

    /** Result of a cast. status 0 = cast (value = how many things it affected), 1 = on cooldown (value = ms left). */
    public record Feedback(int ability, int status, int value) {
        public static final int CAST = 0;
        public static final int COOLDOWN = 1;
    }

    /** Flight is now on or off. */
    public record FlightState(boolean on) {}

    /** Bitmask of the passives that are on. */
    public record PassiveState(int mask) {}

    /** What you are adapting to (a mob id or a damage kind like "lava") and how far along (0 to 5). */
    public record Adapt(String mob, int level) {}

    /** Whether this player may use the admin console / powers (sent on join and whenever it changes). */
    public record Access(boolean allowed) {}

    /** A short message for the player's screen. */
    public record Notice(String text, int rgb) {}

    // ------------------------------------------------------------ registration

    private static <T> void toServer(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
                                     BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.registerMessage(id, type, enc, dec, (m, ctx) -> {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer p = c.getSender();
                if (p != null) handler.accept(m, p);
            });
            c.setPacketHandled(true);
        });
    }

    private static <T> void toClient(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
                                     Consumer1<T> handler) {
        CHANNEL.registerMessage(id, type, enc, dec, (m, ctx) -> {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handler.accept(m)));
            c.setPacketHandled(true);
        });
    }

    /** Tiny functional interface so the client handler lambda stays serialisable-free and dist safe. */
    public interface Consumer1<T> {
        void accept(T t);
    }

    /** Call once from the mod constructor. */
    public static void register() {
        int i = 0;
        toServer(i++, Cast.class, (m, b) -> b.writeInt(m.ability()), b -> new Cast(b.readInt()),
                (m, p) -> AbilityManager.handleCast(p, m.ability()));
        toServer(i++, FlightToggle.class, (m, b) -> b.writeBoolean(m.enable()), b -> new FlightToggle(b.readBoolean()),
                (m, p) -> FlightManager.set(p, m.enable()));
        toServer(i++, Power.class, (m, b) -> b.writeInt(m.id()), b -> new Power(b.readInt()),
                (m, p) -> Powers.power(p, m.id()));
        toServer(i++, Rule.class, (m, b) -> { b.writeInt(m.rule()); b.writeBoolean(m.value()); }, b -> new Rule(b.readInt(), b.readBoolean()),
                (m, p) -> Powers.rule(p, m.rule(), m.value()));
        toServer(i++, RunCommand.class, (m, b) -> b.writeUtf(m.command(), 512), b -> new RunCommand(b.readUtf(512)),
                (m, p) -> Powers.command(p, m.command()));
        toServer(i++, CommandBlockEdit.class, (m, b) -> {
            b.writeInt(m.x()); b.writeInt(m.y()); b.writeInt(m.z()); b.writeUtf(m.command(), 512); b.writeInt(m.flags());
        }, b -> new CommandBlockEdit(b.readInt(), b.readInt(), b.readInt(), b.readUtf(512), b.readInt()),
                (m, p) -> Powers.editCommandBlock(p, m.x(), m.y(), m.z(), m.command(), m.flags()));
        toServer(i++, PassiveToggle.class, (m, b) -> b.writeInt(m.id()), b -> new PassiveToggle(b.readInt()),
                (m, p) -> Passives.toggle(p, m.id()));

        toClient(i++, Feedback.class, (m, b) -> { b.writeInt(m.ability()); b.writeInt(m.status()); b.writeInt(m.value()); },
                b -> new Feedback(b.readInt(), b.readInt(), b.readInt()),
                m -> ClientHooks.feedback(m.ability(), m.status(), m.value()));
        toClient(i++, FlightState.class, (m, b) -> b.writeBoolean(m.on()), b -> new FlightState(b.readBoolean()),
                m -> ClientHooks.flight(m.on()));
        toClient(i++, PassiveState.class, (m, b) -> b.writeInt(m.mask()), b -> new PassiveState(b.readInt()),
                m -> ClientHooks.passives(m.mask()));
        toClient(i++, Adapt.class, (m, b) -> { b.writeUtf(m.mob(), 128); b.writeInt(m.level()); }, b -> new Adapt(b.readUtf(128), b.readInt()),
                m -> ClientHooks.adapt(m.mob(), m.level()));
        toClient(i++, Access.class, (m, b) -> b.writeBoolean(m.allowed()), b -> new Access(b.readBoolean()),
                m -> ClientHooks.access(m.allowed()));
        toClient(i++, Notice.class, (m, b) -> { b.writeUtf(m.text(), 256); b.writeInt(m.rgb()); }, b -> new Notice(b.readUtf(256), b.readInt()),
                m -> ClientHooks.notice(m.text(), m.rgb()));
    }

    // ------------------------------------------------------------ helpers

    /** Send a message from the server to one player. Players without the mod are skipped instead of erroring. */
    public static void send(ServerPlayer player, Object message) {
        if (player.connection != null && CHANNEL.isRemotePresent(player.connection.connection)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    /** Send a message from the client to the server. */
    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
