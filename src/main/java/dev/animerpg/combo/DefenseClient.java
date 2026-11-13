package dev.animerpg.combo;
import java.util.*;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.item.SwordItem;
public final class DefenseClient {
    public interface AnimationListener {void update(UUID player,Identifier animation,long serverStart,long counterUntil);}
    public static final Event<AnimationListener> ANIMATION=EventFactory.createArrayBacked(AnimationListener.class,
        listeners->(player,animation,start,until)->{for(var listener:listeners)listener.update(player,animation,start,until);});
    private static final Map<UUID,Long> PARRY=new HashMap<>();
    private static long counterUntil,flashUntil;
    public static void init(){
        HudRenderCallback.EVENT.register((draw,tickCounter)->{
            var client=MinecraftClient.getInstance();
            if(client.player==null || client.world==null || client.options.hudHidden || client.player.isSpectator())return;
            float now=client.world.getTime()+tickCounter.getTickDelta(false);
            boolean ready=counterUntil>now,flash=flashUntil>now;
            if(!(client.player.getMainHandStack().getItem() instanceof SwordItem) && !ready && !flash)return;
            int width=58,x=Math.min(draw.getScaledWindowWidth()/2+100,draw.getScaledWindowWidth()-width-4);
            int y=draw.getScaledWindowHeight()-17;
            draw.fill(x-2,y-2,x+width+2,y+7,0xc0181828);
            draw.fill(x,y,x+width,y+5,0xff34384c);
            if(ready || flash){
                float progress=ready?MathHelper.clamp((counterUntil-now)/KatanaDefense.COUNTER_TICKS,0,1):1;
                int fill=(int)Math.ceil(width*progress);
                for(int i=0;i<fill;i++){
                    int color=0xff000000|MathHelper.hsvToRgb((now*0.045f+(float)i/width)%1,0.75f,1);
                    draw.fill(x+i,y-1,x+i+1,y+6,0x35000000|(color&0xffffff));
                    draw.fill(x+i,y,x+i+1,y+5,color);
                }
                String label=ready?"КОНТРУДАР":"ПАРИРОВАНИЕ";
                int labelX=Math.max(4,x+width-client.textRenderer.getWidth(label));
                draw.drawTextWithShadow(client.textRenderer,label,labelX,y-11,0xfff8f4ff);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(DefenseState.ID,(state,context)->{
            if(state.phase()==2 && context.client().world!=null)PARRY.put(state.player(),context.client().world.getTime());
            if(context.client().player!=null && context.client().player.getUuid().equals(state.player())){
                counterUntil=state.counterUntil();
                if(state.phase()==2 && context.client().world!=null)flashUntil=context.client().world.getTime()+8;
                if(state.phase()==0)flashUntil=0;
            }
            String name=switch(state.phase()){case 1->"guard";case 2->"parry";case 3->"counter_ready";case 4->"counter";default->null;};
            ANIMATION.invoker().update(state.player(),name==null?null:Identifier.of("katana_combo",name),state.started(),state.counterUntil());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{PARRY.clear();counterUntil=0;flashUntil=0;});
    }
    public static float impulse(UUID player,long now,float delta){
        Long time=PARRY.get(player);if(time==null)return 0;
        float age=now-time+delta;if(age>=6){PARRY.remove(player);return 0;}
        return (float)Math.sin(Math.max(0,age)/6*Math.PI);
    }
    private DefenseClient(){}
}
