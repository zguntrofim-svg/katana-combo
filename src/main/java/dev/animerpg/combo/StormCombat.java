package dev.animerpg.combo;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Three server-authoritative techniques layered on the existing weapon/combo, not a second melee loop. */
public final class StormCombat {
    public static final int[] DURATION={28,40,64}, RELEASE={12,18,50}, COOLDOWN={100,180,300};
    private static final Map<UUID,Technique> ACTIVE=new HashMap<>();
    private static final Map<UUID,long[]> READY=new HashMap<>();
    private static final Map<UUID,Long> REQUEST_AFTER=new HashMap<>();
    private static final class Technique {
        final ServerPlayerEntity owner;final ServerWorld world;final int ability;final long started;
        final net.minecraft.item.ItemStack weapon;final Set<UUID> hits=new HashSet<>();
        Vec3d point,direction;UUID target;float yaw;boolean waveStopped;
        Technique(ServerPlayerEntity p,int ability){
            owner=p;world=p.getServerWorld();this.ability=ability;started=world.getTime();weapon=p.getMainHandStack();
            point=p.getPos();yaw=p.getYaw();direction=horizontal(p);
        }
        StormPackets.State packet(int kind){return new StormPackets.State(owner.getUuid(),world.getRegistryKey().getValue(),kind,started,point.x,point.y,point.z,yaw);}
    }
    private static long clock(ServerPlayerEntity p){return p.getServer().getOverworld().getTime();}
    public static boolean isCasting(ServerPlayerEntity p){return ACTIVE.containsKey(p.getUuid());}
    private static Vec3d horizontal(ServerPlayerEntity p){return new Vec3d(-Math.sin(Math.toRadians(p.getYaw())),0,Math.cos(Math.toRadians(p.getYaw())));}
    public static void init(){
        PayloadTypeRegistry.playC2S().register(StormPackets.Cast.ID,StormPackets.Cast.CODEC);
        PayloadTypeRegistry.playS2C().register(StormPackets.State.ID,StormPackets.State.CODEC);
        PayloadTypeRegistry.playS2C().register(StormPackets.Cooldowns.ID,StormPackets.Cooldowns.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StormPackets.Cast.ID,(packet,context)->request(context.player(),packet.ability()));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var iterator=ACTIVE.values().iterator();iterator.hasNext();){
                Technique t=iterator.next();ServerPlayerEntity p=t.owner;
                if(!p.isAlive() || p.isSpectator() || p.getServerWorld()!=t.world || p.getMainHandStack()!=t.weapon
                        || !t.weapon.isOf(RaizenBoss.AMAKIRI) || p.isUsingItem() || p.hasVehicle() || p.isFallFlying() || p.isSwimming()){
                    broadcast(t,-1);iterator.remove();continue;
                }
                int age=(int)(t.world.getTime()-t.started);
                if(age>=DURATION[t.ability]){iterator.remove();continue;}
                if(t.ability==2 && age<=RELEASE[2]){
                    var marked=t.target==null?null:t.world.getEntity(t.target);
                    if(marked instanceof LivingEntity e && e.isAlive() && p.squaredDistanceTo(e)<=32*32)t.point=e.getPos();
                    if(age%5==0)broadcast(t,t.ability);
                }
                if(age==RELEASE[t.ability]){
                    if(t.ability!=2){
                        t.point=p.getPos();t.direction=horizontal(p);t.yaw=p.getYaw();
                        if(t.ability==1){
                            var floor=t.world.raycast(new RaycastContext(p.getPos().add(0,1,0),p.getPos().add(0,-6,0),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
                            if(floor.getType()==HitResult.Type.MISS){t.waveStopped=true;broadcast(t,-1);}else t.point=floor.getPos().add(0,.025,0);
                        }
                        if(!t.waveStopped)broadcast(t,t.ability);
                    }
                    t.world.playSound(null,t.point.x,t.point.y,t.point.z,SoundEvents.ITEM_TRIDENT_THUNDER,SoundCategory.PLAYERS,.9f,t.ability==0?1.35f:.85f);
                    if(t.ability==2)area(t,5,26,true);
                    t.weapon.damage(1,p,EquipmentSlot.MAINHAND);
                }
                if(t.ability==0 && age>=12 && age<=25 && !t.waveStopped)arc(t,age-12);
                if(t.ability==1 && !t.waveStopped && age>=18 && age<=30)area(t,Math.min(8,.7+(age-18)*.62),18,false);
            }
            if(server.getTicks()%20==0)for(var p:server.getPlayerManager().getPlayerList())if(p.getMainHandStack().isOf(RaizenBoss.AMAKIRI))sync(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            Technique t=ACTIVE.remove(handler.player.getUuid());if(t!=null)broadcast(t,-1);
            REQUEST_AFTER.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{ACTIVE.clear();READY.clear();REQUEST_AFTER.clear();});
        EntityTrackingEvents.START_TRACKING.register((entity,viewer)->{
            Technique t=ACTIVE.get(entity.getUuid());if(t!=null && ServerPlayNetworking.canSend(viewer,StormPackets.State.ID))ServerPlayNetworking.send(viewer,t.packet(t.ability));
        });
    }
    private static void request(ServerPlayerEntity p,int ability){
        if(ability<0 || ability>2 || !p.isAlive() || p.isSpectator() || !p.getMainHandStack().isOf(RaizenBoss.AMAKIRI)
                || p.isUsingItem() || p.hasVehicle() || p.isFallFlying() || p.isSwimming())return;
        long now=clock(p);
        if(now<REQUEST_AFTER.getOrDefault(p.getUuid(),0L))return;REQUEST_AFTER.put(p.getUuid(),now+4);
        if(isCasting(p) || ComboMod.isAttacking(p)){p.sendMessage(Text.literal("Сначала заверши текущую атаку."),true);return;}
        long[] ready=READY.computeIfAbsent(p.getUuid(),id->new long[3]);
        if(now<ready[ability]){sync(p);p.sendMessage(Text.literal("Способность восстанавливается: "+(int)Math.ceil((ready[ability]-now)/20.0)+" с"),true);return;}
        Technique t=new Technique(p,ability);
        if(ability==2 && !mark(t)){p.sendMessage(Text.literal("Наведи прицел на врага или поверхность не дальше 24 блоков."),true);return;}
        ready[ability]=now+COOLDOWN[ability];ACTIVE.put(p.getUuid(),t);sync(p);broadcast(t,ability);
    }
    private static boolean mark(Technique t){
        ServerPlayerEntity p=t.owner;Vec3d eye=p.getEyePos(),look=p.getRotationVec(1),end=eye.add(look.multiply(24));
        var wall=t.world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
        double closest=wall.getType()==HitResult.Type.MISS?24:eye.distanceTo(wall.getPos());LivingEntity chosen=null;
        for(var e:t.world.getEntitiesByClass(LivingEntity.class,p.getBoundingBox().expand(24),entity->ComboMod.canHit(p,entity))){
            var intersection=e.getBoundingBox().expand(.25).raycast(eye,end);
            if(intersection.isPresent() && eye.distanceTo(intersection.get())<closest){closest=eye.distanceTo(intersection.get());chosen=e;}
        }
        if(chosen!=null){t.target=chosen.getUuid();t.point=chosen.getPos();return true;}
        if(wall.getType()!=HitResult.Type.MISS){t.point=wall.getPos().add(0,.03,0);return true;}
        return false;
    }
    private static void arc(Technique t,int tick){
        Vec3d from=t.point.add(0,1.1,0).add(t.direction.multiply(tick)),to=from.add(t.direction);
        var wall=t.world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,t.owner));
        if(wall.getType()!=HitResult.Type.MISS){t.waveStopped=true;broadcast(t,-1);return;}
        Vec3d right=new Vec3d(t.direction.z,0,-t.direction.x),center=from.lerp(to,.5);
        for(var target:t.world.getEntitiesByClass(LivingEntity.class,new Box(from,to).expand(3.5,2,3.5),e->ComboMod.canHit(t.owner,e))){
            Vec3d d=target.getBoundingBox().getCenter().subtract(center);
            if(Math.abs(d.dotProduct(t.direction))>1.0+target.getWidth()/2 || Math.abs(d.dotProduct(right))>3.5+target.getWidth()/2 || Math.abs(d.y)>2)continue;
            damage(t,target,12,false);
        }
    }
    private static void area(Technique t,double radius,float damage,boolean judgment){
        for(var target:t.world.getEntitiesByClass(LivingEntity.class,new Box(t.point,t.point).expand(radius,4,radius),e->ComboMod.canHit(t.owner,e))){
            Vec3d d=target.getPos().subtract(t.point);
            if(d.x*d.x+d.z*d.z>radius*radius || Math.abs(d.y)>3.5)continue;
            damage(t,target,damage,judgment);
        }
    }
    private static void damage(Technique t,LivingEntity target,float amount,boolean strong){
        if(!t.hits.add(target.getUuid()))return;
        if(target.damage(t.owner.getDamageSources().playerAttack(t.owner),amount)){
            HitImpact.emitStyle(t.owner,target,target.getBoundingBox().getCenter(),strong,EffectStyle.STORM.ordinal());
            target.takeKnockback(strong?.55:.3,t.owner.getX()-target.getX(),t.owner.getZ()-target.getZ());
        }
    }
    private static void sync(ServerPlayerEntity p){
        if(!ServerPlayNetworking.canSend(p,StormPackets.Cooldowns.ID))return;
        long now=clock(p);long[] r=READY.getOrDefault(p.getUuid(),new long[3]);
        ServerPlayNetworking.send(p,new StormPackets.Cooldowns((int)Math.max(0,r[0]-now),(int)Math.max(0,r[1]-now),(int)Math.max(0,r[2]-now)));
    }
    private static void broadcast(Technique t,int ability){
        var packet=t.packet(ability);
        for(var viewer:t.world.getPlayers())if((viewer==t.owner || viewer.squaredDistanceTo(t.point)<160*160)
                && ServerPlayNetworking.canSend(viewer,StormPackets.State.ID))ServerPlayNetworking.send(viewer,packet);
    }
    private StormCombat(){}
}
