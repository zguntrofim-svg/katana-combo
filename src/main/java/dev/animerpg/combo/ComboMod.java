package dev.animerpg.combo;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public final class ComboMod implements ModInitializer {
    private static final Map<UUID, Attack> ATTACKS = new HashMap<>();
    private static final class Attack {
        int step = -1;
        long started, ended;
        boolean active, queued, counter;
        ItemStack weapon;
        net.minecraft.registry.RegistryKey<net.minecraft.world.World> world;
        final Set<Integer> hit = new HashSet<>();
    }
    public static boolean eligible(net.minecraft.entity.player.PlayerEntity p) {
        return p.getMainHandStack().getItem() instanceof SwordItem && !p.isSpectator();
    }
    public static boolean isAttacking(net.minecraft.entity.player.PlayerEntity p){
        Attack a=ATTACKS.get(p.getUuid());return a!=null && a.active;
    }
    @Override public void onInitialize() {
        KatanaItems.init();
        EffectNetwork.init();
        KatanaDefense.init();
        PayloadTypeRegistry.playC2S().register(ComboPackets.Request.ID, ComboPackets.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(ComboPackets.State.ID, ComboPackets.State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ComboPackets.Request.ID, (packet, context) -> request(context.player()));
        // Cancel the vanilla entity attack, including packets from unmodified/malicious clients.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
            eligible(player) ? ActionResult.FAIL : ActionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) tick(p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ATTACKS.remove(handler.player.getUuid()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> ATTACKS.clear());
        EntityTrackingEvents.START_TRACKING.register((entity, observer) -> {
            Attack a = ATTACKS.get(entity.getUuid());
            if (a != null && a.active && ServerPlayNetworking.canSend(observer, ComboPackets.State.ID))
                ServerPlayNetworking.send(observer, new ComboPackets.State(entity.getUuid(), a.step, a.started));
        });
    }
    private static void request(ServerPlayerEntity p) {
        if (!eligible(p) || !p.isAlive() || p.isUsingItem()) return;
        Attack a = ATTACKS.computeIfAbsent(p.getUuid(), ignored -> new Attack());
        long now = p.getServerWorld().getTime();
        if (a.active) {
            if (ComboTiming.canQueue(a.step, now - a.started)) a.queued = true;
            return;
        }
        begin(p, a, now);
    }
    private static void begin(ServerPlayerEntity p, Attack a, long now) {
        a.counter=KatanaDefense.consumeCounter(p);
        a.step = a.counter?3:ComboTiming.next(a.step, now, a.ended);
        a.started = now; a.active = true; a.queued = false; a.hit.clear();
        a.weapon = p.getMainHandStack(); a.world = p.getWorld().getRegistryKey();
        broadcast(p, a.step, now);
        p.swingHand(Hand.MAIN_HAND, true);
    }
    private static void tick(ServerPlayerEntity p) {
        Attack a = ATTACKS.get(p.getUuid());
        if (a == null) return;
        long now = p.getServerWorld().getTime();
        if (!eligible(p) || !p.isAlive() || p.isUsingItem() || a.weapon != p.getMainHandStack()
                || a.world != p.getWorld().getRegistryKey()) {
            if (a.active) broadcast(p, -1, now);
            ATTACKS.remove(p.getUuid()); return;
        }
        if (!a.active) return;
        long age = now - a.started;
        if(a.counter && age==2 && p.isOnGround() && !p.isTouchingWater() && !p.hasVehicle()){
            Vec3d forward=p.getRotationVec(1).multiply(1,0,1).normalize();
            Vec3d velocity=p.getVelocity().add(forward.multiply(0.14));
            double speed=Math.sqrt(velocity.x*velocity.x+velocity.z*velocity.z);
            double limit=speed>0.32?0.32/speed:1;
            counterVelocity(p,new Vec3d(velocity.x*limit,0.42,velocity.z*limit));
        }
        if(a.counter && age==ComboTiming.HIT_START[a.step] && !p.isOnGround() && !p.isTouchingWater() && !p.hasVehicle()){
            Vec3d velocity=p.getVelocity();counterVelocity(p,new Vec3d(velocity.x,Math.min(velocity.y,-0.16),velocity.z));
        }
        if (age >= ComboTiming.DURATION[a.step]) {
            boolean queued = a.queued;
            a.active = false; a.ended = now;
            broadcast(p, -1, now);
            if (queued) begin(p, a, now);
        } else if (age >= ComboTiming.HIT_START[a.step] && age <= ComboTiming.HIT_END[a.step]) hit(p, a);
    }
    private static void counterVelocity(ServerPlayerEntity p,Vec3d velocity){
        p.setVelocity(velocity);p.velocityModified=true;
        p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(p));
    }
    private static void hit(ServerPlayerEntity p, Attack a) {
        double reach = Math.min(3.0, p.getEntityInteractionRange());
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        if(a.counter)look=new Vec3d(look.x,-0.55,look.z).normalize();
        for (LivingEntity target : p.getServerWorld().getEntitiesByClass(LivingEntity.class,
                p.getBoundingBox().expand(reach), e -> e != p && e.isAlive() && !e.isSpectator())) {
            if (a.hit.contains(target.getId()) || !p.canSee(target) || !p.canTarget(target)) continue;
            if (target instanceof ServerPlayerEntity other && !p.shouldDamagePlayer(other)) continue;
            Vec3d closest = new Vec3d(
                net.minecraft.util.math.MathHelper.clamp(eye.x, target.getBoundingBox().minX, target.getBoundingBox().maxX),
                net.minecraft.util.math.MathHelper.clamp(eye.y, target.getBoundingBox().minY, target.getBoundingBox().maxY),
                net.minecraft.util.math.MathHelper.clamp(eye.z, target.getBoundingBox().minZ, target.getBoundingBox().maxZ));
            Vec3d offset = closest.subtract(eye);
            if (offset.lengthSquared() > reach * reach || offset.normalize().dotProduct(look) < 0.5) continue;
            a.hit.add(target.getId());
            float damage = (float)p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * ComboTiming.DAMAGE[a.step] * (a.counter?1.5f:1f);
            if (target.damage(p.getDamageSources().playerAttack(p), damage)) {
                if (a.step == 2) target.takeKnockback(0.4, p.getX() - target.getX(), p.getZ() - target.getZ());
                p.getMainHandStack().postHit(target, p);
            }
        }
    }
    private static void broadcast(ServerPlayerEntity p, int step, long now) {
        Set<ServerPlayerEntity> players = new HashSet<>(PlayerLookup.tracking(p)); players.add(p);
        for (ServerPlayerEntity viewer : players)
            if (ServerPlayNetworking.canSend(viewer, ComboPackets.State.ID))
                ServerPlayNetworking.send(viewer, new ComboPackets.State(p.getUuid(), step, now));
    }
}
