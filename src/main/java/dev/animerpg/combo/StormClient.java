package dev.animerpg.combo;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Native remappable controls and the adapter from ability timing to the existing three-hit animations. */
public final class StormClient {
    static final Map<UUID,StormPackets.State> ACTIVE=new HashMap<>();
    private static final KeyBinding[] KEYS=new KeyBinding[3];
    private static final long[] READY=new long[3];
    public static void init(){
        int[] defaults={GLFW.GLFW_KEY_Z,GLFW.GLFW_KEY_X,GLFW.GLFW_KEY_C};
        for(int i=0;i<3;i++)KEYS[i]=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.katana_combo.storm_"+i,InputUtil.Type.KEYSYM,defaults[i],"category.katana_combo.storm"));
        ClientPlayNetworking.registerGlobalReceiver(StormPackets.State.ID,(state,context)->{
            var world=context.client().world;
            if(world==null || !world.getRegistryKey().getValue().equals(state.dimension()))return;
            if(state.ability()<0){ACTIVE.remove(state.player());return;}
            if(state.ability()>2 || !Double.isFinite(state.x()) || !Double.isFinite(state.y()) || !Double.isFinite(state.z()) || !Float.isFinite(state.yaw()))return;
            if(ACTIVE.size()>=128 && !ACTIVE.containsKey(state.player()))ACTIVE.remove(ACTIVE.keySet().iterator().next());
            ACTIVE.put(state.player(),state);
        });
        ClientPlayNetworking.registerGlobalReceiver(StormPackets.Cooldowns.ID,(status,context)->{
            if(context.client().world==null)return;long now=context.client().world.getTime();
            READY[0]=now+status.arc();READY[1]=now+status.slam();READY[2]=now+status.judgment();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{ACTIVE.clear();Arrays.fill(READY,0);});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.world==null){ACTIVE.clear();Arrays.fill(READY,0);return;}
            long now=client.world.getTime();
            ACTIVE.values().removeIf(s->!s.dimension().equals(client.world.getRegistryKey().getValue()) || now-s.started()>StormCombat.DURATION[s.ability()]+16);
            for(int i=0;i<3;i++)while(KEYS[i].wasPressed()){
                if(client.currentScreen==null && client.player!=null && !client.player.isSpectator()
                        && client.player.getMainHandStack().isOf(RaizenBoss.AMAKIRI) && ClientPlayNetworking.canSend(StormPackets.Cast.ID))
                    ClientPlayNetworking.send(new StormPackets.Cast(i));
            }
        });
        HudRenderCallback.EVENT.register((draw,tick)->{
            var client=MinecraftClient.getInstance();
            if(client.player==null || client.world==null || client.options.hudHidden || client.player.isSpectator() || !client.player.getMainHandStack().isOf(RaizenBoss.AMAKIRI))return;
            float now=client.world.getTime()+tick.getTickDelta(false);int x=draw.getScaledWindowWidth()/2-116,y=draw.getScaledWindowHeight()-65;
            for(int i=0;i<3;i++){
                int xx=x+i*79;float remaining=Math.max(0,READY[i]-now),ready=1-remaining/StormCombat.COOLDOWN[i];
                draw.fill(xx-1,y-1,xx+77,y+24,0x7034ceff);draw.fill(xx,y,xx+76,y+23,0xe5101427);
                draw.fill(xx+2,y+20,xx+74,y+22,0xff26344d);draw.fill(xx+2,y+20,xx+2+(int)(72*ready),y+22,remaining>0?0xff2e81b7:0xff70eaff);
                String key=KEYS[i].getBoundKeyLocalizedText().getString();String name=Text.translatable("storm.katana_combo.short_"+i).getString();
                draw.drawTextWithShadow(client.textRenderer,client.textRenderer.trimToWidth(key+" · "+name,70),xx+3,y+3,remaining>0?0xff8296b4:0xffd8faff);
                draw.drawTextWithShadow(client.textRenderer,remaining>0?String.format(java.util.Locale.ROOT,"%.1f s",remaining/20):Text.translatable("storm.katana_combo.ready").getString(),xx+3,y+12,0xff75cfff);
            }
        });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack,context,type,lines)->{
            if(stack.isOf(RaizenBoss.AMAKIRI)){
                for(int i=0;i<3;i++)lines.add(Text.literal(KEYS[i].getBoundKeyLocalizedText().getString()+" — ").append(Text.translatable("key.katana_combo.storm_"+i)).formatted(net.minecraft.util.Formatting.AQUA));
                lines.add(Text.translatable("storm.katana_combo.two_handed").formatted(net.minecraft.util.Formatting.GRAY));
            }
        });
        StormVisuals.init();
    }
    public static boolean isCasting(UUID player){
        var world=MinecraftClient.getInstance().world;var s=ACTIVE.get(player);
        return world!=null && s!=null && world.getRegistryKey().getValue().equals(s.dimension()) && world.getTime()-s.started()<StormCombat.DURATION[s.ability()];
    }
    public static ComboAnimation.Pose pose(PlayerEntity player,float delta){
        var world=MinecraftClient.getInstance().world;var s=ACTIVE.get(player.getUuid());
        if(world==null || s==null || !world.getRegistryKey().getValue().equals(s.dimension()) || !player.getMainHandStack().isOf(RaizenBoss.AMAKIRI))return null;
        float age=world.getTime()-s.started()+delta;int skill=s.ability(),duration=StormCombat.DURATION[skill],step=skill==0?0:2;
        if(age<0 || age>=duration)return null;
        float[] destination=skill==0?new float[]{0,9,12,18,24,28}:skill==1?new float[]{0,14,18,24,33,40}:new float[]{0,12,50,54,57,64};
        float[] source={0,ComboTiming.HIT_START[step]-1,ComboTiming.HIT_START[step]+.75f,ComboTiming.HIT_END[step]+2,ComboTiming.DURATION[step],ComboTiming.DURATION[step]+ComboClient.RECOVERY_TICKS-.05f};
        int segment=0;while(segment<4 && age>destination[segment+1])segment++;
        float u=MathHelper.clamp((age-destination[segment])/(destination[segment+1]-destination[segment]),0,1);
        var pose=ComboAnimation.sampleAge(step,MathHelper.lerp(u,source[segment],source[segment+1]));if(pose==null)return null;
        float[] v=pose.values().clone();
        float hold=skill==2 && age>=12 && age<58?1-smooth((age-50)/8):0;
        if(hold>0){
            v[0]=MathHelper.lerp(hold,v[0],0);v[1]=MathHelper.lerp(hold,v[1],2);v[2]=MathHelper.lerp(hold,v[2],-6);
            v[3]=MathHelper.lerp(hold,v[3],-10);v[4]=MathHelper.lerp(hold,v[4],85);v[5]=MathHelper.lerp(hold,v[5],-8);v[6]=MathHelper.lerp(hold,v[6],0);
        }
        var idle=ComboAnimation.amakiriIdle();float recover=smooth((age-duration+7)/7);
        for(int i=0;i<v.length;i++)v[i]=MathHelper.lerp(recover,v[i],idle.values()[i]);
        Vector3f edge=new Vector3f(pose.cuttingDirection()).lerp(new Vector3f(0,0,-1),hold).lerp(idle.cuttingDirection(),recover);
        float start=smooth(age/2);
        for(int i=0;i<v.length;i++)v[i]=MathHelper.lerp(start,idle.values()[i],v[i]);
        edge=new Vector3f(idle.cuttingDirection()).lerp(edge,start);
        if(edge.lengthSquared()<.00001f)edge.set(0,0,-1);else edge.normalize();
        return new ComboAnimation.Pose(v,1,pose.age(),edge,step);
    }
    private static float smooth(float value){value=MathHelper.clamp(value,0,1);return value*value*(3-2*value);}
    private StormClient(){}
}
