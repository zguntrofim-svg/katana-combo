package dev.animerpg.combo;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
public final class EffectNetwork {
    public record Choice(int style) implements CustomPayload {
        public static final Id<Choice> ID=new Id<>(Identifier.of("katana_combo","effect_choice"));
        public static final PacketCodec<RegistryByteBuf,Choice> CODEC=new PacketCodec<>(){
            public Choice decode(RegistryByteBuf b){return new Choice(b.readVarInt());}
            public void encode(RegistryByteBuf b,Choice p){b.writeVarInt(p.style);}
        };
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record State(UUID player,int style) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("katana_combo","effect_state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=new PacketCodec<>(){
            public State decode(RegistryByteBuf b){return new State(b.readUuid(),b.readVarInt());}
            public void encode(RegistryByteBuf b,State p){b.writeUuid(p.player);b.writeVarInt(p.style);}
        };
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final Map<UUID,Integer> STYLES=new HashMap<>();
    public static void init(){
        PayloadTypeRegistry.playC2S().register(Choice.ID,Choice.CODEC);PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Choice.ID,(packet,context)->{
            if(packet.style<0 || packet.style>=EffectStyle.values().length)return;
            var p=context.player();Integer before=STYLES.put(p.getUuid(),packet.style);
            if(before!=null && before==packet.style)return;
            Set<ServerPlayerEntity> viewers=new HashSet<>(PlayerLookup.tracking(p));viewers.add(p);
            for(var viewer:viewers)send(viewer,p.getUuid(),packet.style);
        });
        EntityTrackingEvents.START_TRACKING.register((entity,viewer)->{if(entity instanceof ServerPlayerEntity)send(viewer,entity.getUuid(),STYLES.getOrDefault(entity.getUuid(),3));});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->STYLES.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->STYLES.clear());
    }
    private static void send(ServerPlayerEntity viewer,UUID id,int style){if(ServerPlayNetworking.canSend(viewer,State.ID))ServerPlayNetworking.send(viewer,new State(id,style));}
    private EffectNetwork(){}
}
