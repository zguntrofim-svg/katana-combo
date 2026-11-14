package dev.animerpg.combo;

import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class StormPackets {
    public record Cast(int ability) implements CustomPayload {
        public static final Id<Cast> ID=new Id<>(Identifier.of("katana_combo","storm_cast"));
        public static final PacketCodec<RegistryByteBuf,Cast> CODEC=new PacketCodec<>(){
            public Cast decode(RegistryByteBuf b){return new Cast(b.readVarInt());}
            public void encode(RegistryByteBuf b,Cast p){b.writeVarInt(p.ability);}
        };
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record State(UUID player,Identifier dimension,int ability,long started,double x,double y,double z,float yaw) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("katana_combo","storm_state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=new PacketCodec<>(){
            public State decode(RegistryByteBuf b){return new State(b.readUuid(),b.readIdentifier(),b.readVarInt(),b.readLong(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat());}
            public void encode(RegistryByteBuf b,State p){b.writeUuid(p.player);b.writeIdentifier(p.dimension);b.writeVarInt(p.ability);b.writeLong(p.started);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(p.yaw);}
        };
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Cooldowns(int arc,int slam,int judgment) implements CustomPayload {
        public static final Id<Cooldowns> ID=new Id<>(Identifier.of("katana_combo","storm_cooldowns"));
        public static final PacketCodec<RegistryByteBuf,Cooldowns> CODEC=new PacketCodec<>(){
            public Cooldowns decode(RegistryByteBuf b){return new Cooldowns(b.readVarInt(),b.readVarInt(),b.readVarInt());}
            public void encode(RegistryByteBuf b,Cooldowns p){b.writeVarInt(p.arc);b.writeVarInt(p.slam);b.writeVarInt(p.judgment);}
        };
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private StormPackets(){}
}
