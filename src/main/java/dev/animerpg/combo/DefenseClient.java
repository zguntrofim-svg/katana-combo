package dev.animerpg.combo;
import java.util.*;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.util.Identifier;
public final class DefenseClient {
    public interface AnimationListener {void update(UUID player,Identifier animation,long serverStart,long counterUntil);}
    public static final Event<AnimationListener> ANIMATION=EventFactory.createArrayBacked(AnimationListener.class,
        listeners->(player,animation,start,until)->{for(var listener:listeners)listener.update(player,animation,start,until);});
    private static final Map<UUID,Long> PARRY=new HashMap<>();
    public static void init(){
        ClientPlayNetworking.registerGlobalReceiver(DefenseState.ID,(state,context)->{
            if(state.phase()==2 && context.client().world!=null)PARRY.put(state.player(),context.client().world.getTime());
            String name=switch(state.phase()){case 1->"guard";case 2->"parry";case 3->"counter_ready";case 4->"counter";default->null;};
            ANIMATION.invoker().update(state.player(),name==null?null:Identifier.of("katana_combo",name),state.started(),state.counterUntil());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->PARRY.clear());
    }
    public static float impulse(UUID player,long now,float delta){
        Long time=PARRY.get(player);if(time==null)return 0;
        float age=now-time+delta;if(age>=6){PARRY.remove(player);return 0;}
        return (float)Math.sin(Math.max(0,age)/6*Math.PI);
    }
    private DefenseClient(){}
}
