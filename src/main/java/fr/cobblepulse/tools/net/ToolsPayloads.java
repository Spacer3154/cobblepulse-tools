package fr.cobblepulse.tools.net;

import fr.cobblepulse.tools.CobblePulseTools;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Deux paquets generiques (type + JSON) entre l'editeur client et le serveur. */
public final class ToolsPayloads {
    private static final StreamCodec<ByteBuf, String> BIG_STRING = ByteBufCodecs.stringUtf8(1 << 21);

    private ToolsPayloads() {}

    /** Client -> serveur : action + arguments JSON. */
    public record Action(String action, String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(CobblePulseTools.id("action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::action, BIG_STRING, Action::json, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Serveur -> client : donnees (config, ouverture de l'editeur, resultat). */
    public record Data(String kind, String json) implements CustomPacketPayload {
        public static final Type<Data> TYPE = new Type<>(CobblePulseTools.id("data"));
        public static final StreamCodec<ByteBuf, Data> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Data::kind, BIG_STRING, Data::json, Data::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
