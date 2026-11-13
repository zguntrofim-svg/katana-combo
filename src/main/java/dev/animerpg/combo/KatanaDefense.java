package dev.animerpg.combo;

import java.util.*;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

public final class KatanaDefense {
    public static final int PARRY_TICKS=4, COUNTER_TICKS=20;
    private static final Map<UUID,Guard> GUARDS=new HashMap<>();
    private static final class Guard {
        long started,lastStart=Long.MIN_VALUE/2,counterUntil,lastEffect=Long.MIN_VALUE/2;
        boolean active,perfect,spent;
        ItemStack weapon;
        net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension;
    }
    public static boolean holding(PlayerEntity p){
        return ComboMod.eligible(p) && p.isAlive() && p.getOffHandStack().isEmpty() && p.isUsingItem()
            && p.getActiveHand()==Hand.MAIN_HAND && p.getActiveItem()==p.getMainHandStack();
    }
    public static void init(){
        PayloadTypeRegistry.playS2C().register(DefenseState.ID,DefenseState.CODEC);
        UseItemCallback.EVENT.register((player,world,hand)->{
            ItemStack stack=player.getStackInHand(hand);
            if(hand!=Hand.MAIN_HAND || !ComboMod.eligible(player) || !player.getOffHandStack().isEmpty()
                    || player.isUsingItem())return TypedActionResult.pass(stack);
            if(!world.isClient && ComboMod.isAttacking(player))return TypedActionResult.fail(stack);
            player.setCurrentHand(hand);
            if(player instanceof ServerPlayerEntity server){
                Guard g=GUARDS.computeIfAbsent(player.getUuid(),id->new Guard());
                long now=world.getTime();
                g.perfect=now-g.lastStart>=10;g.started=now;g.lastStart=now;g.active=true;g.spent=false;
                g.weapon=stack;g.dimension=world.getRegistryKey();
                publish(server,g,1);
            }
            return TypedActionResult.consume(stack);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{
            if(entity instanceof ServerPlayerEntity p && amount>0 && canBlock(p,source)){
                Guard g=GUARDS.get(p.getUuid());long now=p.getWorld().getTime();
                if(perfect(g,now)){
                    g.spent=true;g.counterUntil=now+COUNTER_TICKS;
                    collision(p,g,true);publish(p,g,2);
                    return false;
                }
            }
            return true;
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(ServerPlayerEntity p:server.getPlayerManager().getPlayerList()){
                Guard g=GUARDS.get(p.getUuid());if(g==null)continue;
                if(!p.isAlive() || !ComboMod.eligible(p) || p.getMainHandStack()!=g.weapon
                        || p.getWorld().getRegistryKey()!=g.dimension){
                    publish(p,g,0);GUARDS.remove(p.getUuid());continue;
                }
                if(g.active && !holding(p)){g.active=false;publish(p,g,g.counterUntil>p.getWorld().getTime()?3:0);}
                if(g.counterUntil>0 && p.getWorld().getTime()>=g.counterUntil){g.counterUntil=0;publish(p,g,g.active?1:0);}
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->GUARDS.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->GUARDS.clear());
        EntityTrackingEvents.START_TRACKING.register((entity,observer)->{
            Guard g=GUARDS.get(entity.getUuid());
            if(entity instanceof ServerPlayerEntity p && g!=null)send(observer,p,g,g.active?1:0);
        });
    }
    private static boolean perfect(Guard g,long now){return g!=null&&g.active&&g.perfect&&!g.spent&&now-g.started>=0&&now-g.started<PARRY_TICKS;}
    public static boolean canBlock(ServerPlayerEntity p,DamageSource source){
        Guard g=GUARDS.get(p.getUuid());
        if(g==null || !g.active || !holding(p) || g.weapon!=p.getMainHandStack()
                || g.dimension!=p.getWorld().getRegistryKey() || ComboMod.isAttacking(p))return false;
        // Only direct, visible frontal melee contacts. Environmental damage, explosions and projectiles pass through.
        if(!(source.getAttacker() instanceof LivingEntity attacker) || source.getSource()!=attacker
                || source.isIn(DamageTypeTags.BYPASSES_SHIELD) || source.isIn(DamageTypeTags.IS_PROJECTILE)
                || source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY))return false;
        if(attacker==p || !p.canSee(attacker))return false;
        var offset=attacker.getPos().subtract(p.getPos()).multiply(1,0,1);
        var facing=p.getRotationVec(1).multiply(1,0,1);
        return offset.lengthSquared()>0.0001 && facing.normalize().dotProduct(offset.normalize())>=0.5;
    }
    public static float reduce(LivingEntity target,DamageSource source,float amount){
        if(amount>0 && target instanceof ServerPlayerEntity p && canBlock(p,source)){
            Guard g=GUARDS.get(p.getUuid());
            if(perfect(g,p.getWorld().getTime()))return amount; // ALLOW_DAMAGE fully cancels this hit.
            collision(p,g,false);return amount*0.4f;
        }
        return amount;
    }
    private static void collision(ServerPlayerEntity p,Guard g,boolean parry){
        long now=p.getWorld().getTime();if(!parry && now-g.lastEffect<4)return;g.lastEffect=now;
        var point=p.getEyePos().add(p.getRotationVec(1).multiply(0.75)).add(0,-0.35,0);
        p.getServerWorld().playSound(null,p.getX(),p.getY(),p.getZ(),parry?SoundEvents.ITEM_TRIDENT_HIT:SoundEvents.ITEM_SHIELD_BLOCK,
            SoundCategory.PLAYERS,parry?0.85f:0.55f,parry?1.55f:1.25f);
        p.getServerWorld().spawnParticles(ParticleTypes.ELECTRIC_SPARK,point.x,point.y,point.z,parry?7:3,0.12,0.12,0.12,0.05);
    }
    public static boolean consumeCounter(ServerPlayerEntity p){
        Guard g=GUARDS.get(p.getUuid());
        if(g==null || g.counterUntil<=p.getWorld().getTime() || g.weapon!=p.getMainHandStack()
                || g.dimension!=p.getWorld().getRegistryKey())return false;
        g.counterUntil=0;publish(p,g,4);return true;
    }
    private static void publish(ServerPlayerEntity p,Guard g,int phase){
        Set<ServerPlayerEntity> viewers=new HashSet<>(PlayerLookup.tracking(p));viewers.add(p);
        for(var viewer:viewers)send(viewer,p,g,phase);
    }
    private static void send(ServerPlayerEntity viewer,ServerPlayerEntity p,Guard g,int phase){
        if(ServerPlayNetworking.canSend(viewer,DefenseState.ID))ServerPlayNetworking.send(viewer,new DefenseState(p.getUuid(),phase,g.started,g.counterUntil));
    }
    private KatanaDefense(){}
}
