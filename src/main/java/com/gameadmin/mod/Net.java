package com.gameadmin.mod;

import com.gameadmin.mod.client.ClientHooks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Every network message of the mod. */
public final class Net {
    private Net() {}

    // ------------------------------------------------------------ client -> server

    /** Cast ability #n. */
    public record Cast(int ability) implements CustomPacketPayload {
        public static final Type<Cast> TYPE = new Type<>(GameAdminMod.id("cast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, Cast::ability, Cast::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Turn flight on or off. */
    public record FlightToggle(boolean enable) implements CustomPacketPayload {
        public static final Type<FlightToggle> TYPE = new Type<>(GameAdminMod.id("flight_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FlightToggle> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, FlightToggle::enable, FlightToggle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Game mode (0 survival, 1 creative, 2 adventure, 3 spectator) or tool (4 = give command blocks). */
    public record Power(int id) implements CustomPacketPayload {
        public static final Type<Power> TYPE = new Type<>(GameAdminMod.id("power"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Power> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, Power::id, Power::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Set a game rule (index into Powers.RULES) on or off. */
    public record Rule(int rule, boolean value) implements CustomPacketPayload {
        public static final Type<Rule> TYPE = new Type<>(GameAdminMod.id("rule"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rule> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, Rule::rule, ByteBufCodecs.BOOL, Rule::value, Rule::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Run any command from the command line. */
    public record RunCommand(String command) implements CustomPacketPayload {
        public static final Type<RunCommand> TYPE = new Type<>(GameAdminMod.id("run_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RunCommand> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RunCommand::command, RunCommand::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Edit a command block from survival. flags: bits 0-1 type, bit 2 conditional, bit 3 always active. */
    public record CommandBlockEdit(int x, int y, int z, String command, int flags) implements CustomPacketPayload {
        public static final Type<CommandBlockEdit> TYPE = new Type<>(GameAdminMod.id("command_block_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CommandBlockEdit> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, CommandBlockEdit::x,
                        ByteBufCodecs.INT, CommandBlockEdit::y,
                        ByteBufCodecs.INT, CommandBlockEdit::z,
                        ByteBufCodecs.STRING_UTF8, CommandBlockEdit::command,
                        ByteBufCodecs.INT, CommandBlockEdit::flags,
                        CommandBlockEdit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Toggle passive 0..4. -1 just asks the server to send the current state. */
    public record PassiveToggle(int id) implements CustomPacketPayload {
        public static final Type<PassiveToggle> TYPE = new Type<>(GameAdminMod.id("passive_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PassiveToggle> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, PassiveToggle::id, PassiveToggle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------ server -> client

    /** Result of a cast. status 0 = cast (value = how many things it affected), 1 = on cooldown (value = ms left). */
    public record Feedback(int ability, int status, int value) implements CustomPacketPayload {
        public static final int CAST = 0;
        public static final int COOLDOWN = 1;

        public static final Type<Feedback> TYPE = new Type<>(GameAdminMod.id("feedback"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Feedback> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT, Feedback::ability,
                        ByteBufCodecs.INT, Feedback::status,
                        ByteBufCodecs.INT, Feedback::value,
                        Feedback::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Flight is now on or off. */
    public record FlightState(boolean on) implements CustomPacketPayload {
        public static final Type<FlightState> TYPE = new Type<>(GameAdminMod.id("flight_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FlightState> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, FlightState::on, FlightState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Bitmask of the passives that are on. */
    public record PassiveState(int mask) implements CustomPacketPayload {
        public static final Type<PassiveState> TYPE = new Type<>(GameAdminMod.id("passive_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PassiveState> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, PassiveState::mask, PassiveState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** What you are adapting to (a mob id or a damage kind like "lava") and how far along (0 to 5). */
    public record Adapt(String mob, int level) implements CustomPacketPayload {
        public static final Type<Adapt> TYPE = new Type<>(GameAdminMod.id("adapt"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Adapt> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Adapt::mob, ByteBufCodecs.INT, Adapt::level, Adapt::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Whether this player may use the admin console / powers (sent on join and whenever it changes). */
    public record Access(boolean allowed) implements CustomPacketPayload {
        public static final Type<Access> TYPE = new Type<>(GameAdminMod.id("access"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Access> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Access::allowed, Access::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A short message for the player's screen. */
    public record Notice(String text, int rgb) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = new Type<>(GameAdminMod.id("notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Notice::text, ByteBufCodecs.INT, Notice::rgb, Notice::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------ registration

    /** Mod-bus event: tells NeoForge about every message and who handles it. Handlers run on the main thread. */
    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): players WITHOUT the mod can still join a server that has it (they just cannot use the console).
        PayloadRegistrar r = event.registrar("1").optional();

        r.playToServer(Cast.TYPE, Cast.CODEC,
                (m, ctx) -> AbilityManager.handleCast((ServerPlayer) ctx.player(), m.ability()));
        r.playToServer(FlightToggle.TYPE, FlightToggle.CODEC,
                (m, ctx) -> FlightManager.set((ServerPlayer) ctx.player(), m.enable()));
        r.playToServer(Power.TYPE, Power.CODEC,
                (m, ctx) -> Powers.power((ServerPlayer) ctx.player(), m.id()));
        r.playToServer(Rule.TYPE, Rule.CODEC,
                (m, ctx) -> Powers.rule((ServerPlayer) ctx.player(), m.rule(), m.value()));
        r.playToServer(RunCommand.TYPE, RunCommand.CODEC,
                (m, ctx) -> Powers.command((ServerPlayer) ctx.player(), m.command()));
        r.playToServer(CommandBlockEdit.TYPE, CommandBlockEdit.CODEC,
                (m, ctx) -> Powers.editCommandBlock((ServerPlayer) ctx.player(), m.x(), m.y(), m.z(), m.command(), m.flags()));
        r.playToServer(PassiveToggle.TYPE, PassiveToggle.CODEC,
                (m, ctx) -> Passives.toggle((ServerPlayer) ctx.player(), m.id()));

        r.playToClient(Feedback.TYPE, Feedback.CODEC,
                (m, ctx) -> ClientHooks.feedback(m.ability(), m.status(), m.value()));
        r.playToClient(FlightState.TYPE, FlightState.CODEC,
                (m, ctx) -> ClientHooks.flight(m.on()));
        r.playToClient(PassiveState.TYPE, PassiveState.CODEC,
                (m, ctx) -> ClientHooks.passives(m.mask()));
        r.playToClient(Adapt.TYPE, Adapt.CODEC,
                (m, ctx) -> ClientHooks.adapt(m.mob(), m.level()));
        r.playToClient(Access.TYPE, Access.CODEC,
                (m, ctx) -> ClientHooks.access(m.allowed()));
        r.playToClient(Notice.TYPE, Notice.CODEC,
                (m, ctx) -> ClientHooks.notice(m.text(), m.rgb()));
    }

    // ------------------------------------------------------------ helpers

    /** Send a message from the server to one player. */
    public static void send(ServerPlayer player, CustomPacketPayload message) {
        // players without the mod have no channel for it: skip instead of erroring
        if (player.connection.hasChannel(message.type())) PacketDistributor.sendToPlayer(player, message);
    }

    /** Send a message from the client to the server. */
    public static void sendToServer(CustomPacketPayload message) {
        PacketDistributor.sendToServer(message);
    }
}
