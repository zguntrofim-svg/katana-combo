package dev.animerpg.combo;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class ComboPackets {
    public record Request() implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("katana_combo", "attack"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.unit(new Request());
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    /** step -1 cancels an animation. UUID prevents entity-ID reuse glitches. */
    public record State(java.util.UUID player, int step, long started) implements CustomPayload {
        public static final Id<State> ID = new Id<>(Identifier.of("katana_combo", "state"));
        public static final PacketCodec<RegistryByteBuf, State> CODEC = new PacketCodec<>() {
            public State decode(RegistryByteBuf buf) { return new State(buf.readUuid(), buf.readVarInt(), buf.readLong()); }
            public void encode(RegistryByteBuf buf, State state) { buf.writeUuid(state.player); buf.writeVarInt(state.step); buf.writeLong(state.started); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    private ComboPackets() {}
}
