package dev.animerpg.combo;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Server phases: 0 stop, 1 block, 2 parry contact, 3 counter ready, 4 counter attack. */
public record DefenseState(UUID player,int phase,long started,long counterUntil) implements CustomPayload {
    public static final Id<DefenseState> ID=new Id<>(Identifier.of("katana_combo","defense"));
    public static final PacketCodec<RegistryByteBuf,DefenseState> CODEC=new PacketCodec<>(){
        public DefenseState decode(RegistryByteBuf b){return new DefenseState(b.readUuid(),b.readVarInt(),b.readLong(),b.readLong());}
        public void encode(RegistryByteBuf b,DefenseState s){b.writeUuid(s.player);b.writeVarInt(s.phase);b.writeLong(s.started);b.writeLong(s.counterUntil);}
    };
    public Id<? extends CustomPayload> getId(){return ID;}
}
