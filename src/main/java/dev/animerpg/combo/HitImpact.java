package dev.animerpg.combo;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Cosmetic server-confirmed contact; never accepted from a client. */
public record HitImpact(Identifier dimension,int style,double x,double y,double z,
                        float nx,float ny,float nz,boolean finisher,long seed) implements CustomPayload {
    public static final Id<HitImpact> ID=new Id<>(Identifier.of("katana_combo","hit_impact"));
    public static final PacketCodec<RegistryByteBuf,HitImpact> CODEC=new PacketCodec<>(){
        public HitImpact decode(RegistryByteBuf b){return new HitImpact(b.readIdentifier(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readLong());}
        public void encode(RegistryByteBuf b,HitImpact p){b.writeIdentifier(p.dimension);b.writeVarInt(p.style);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(p.nx);b.writeFloat(p.ny);b.writeFloat(p.nz);b.writeBoolean(p.finisher);b.writeLong(p.seed);}
    };
    public Id<? extends CustomPayload> getId(){return ID;}
    public static void init(){PayloadTypeRegistry.playS2C().register(ID,CODEC);}
    public static void emit(ServerPlayerEntity attacker,LivingEntity target,Vec3d contact,boolean finisher){
        Vec3d normal=attacker.getEyePos().subtract(contact).normalize();
        if(normal.lengthSquared()<0.01)normal=attacker.getRotationVec(1).negate();
        var world=attacker.getServerWorld();
        HitImpact packet=new HitImpact(world.getRegistryKey().getValue(),EffectNetwork.styleFor(attacker.getUuid()),
            contact.x,contact.y,contact.z,(float)normal.x,(float)normal.y,(float)normal.z,finisher,world.getRandom().nextLong());
        // Every observer in this dimension gets the same confirmed point, style and animation seed.
        for(ServerPlayerEntity viewer:world.getPlayers())if(ServerPlayNetworking.canSend(viewer,ID))ServerPlayNetworking.send(viewer,packet);
    }
}
