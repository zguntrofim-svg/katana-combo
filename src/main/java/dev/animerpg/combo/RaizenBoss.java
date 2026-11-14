package dev.animerpg.combo;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.*;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.math.Box;

public final class RaizenBoss extends HostileEntity {
    // Mod entities have no vanilla DFU schema; null follows FabricEntityTypeBuilder and preserves saving.
    public static final EntityType<RaizenBoss> TYPE=Registry.register(Registries.ENTITY_TYPE,Identifier.of("katana_combo","raizen"),
        EntityType.Builder.create(RaizenBoss::new,SpawnGroup.MONSTER).dimensions(1.45f,3.5f).eyeHeight(3.05f)
            .maxTrackingRange(12).trackingTickInterval(2).build(null));
    public static final Item AMAKIRI=Registry.register(Registries.ITEM,Identifier.of("katana_combo","amakiri"),
        new SwordItem(ToolMaterials.NETHERITE,new Item.Settings().fireproof().rarity(net.minecraft.util.Rarity.EPIC)
            .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE,3,-2.6f))));
    public static final Item EGG=Registry.register(Registries.ITEM,Identifier.of("katana_combo","raizen_spawn_egg"),
        new SpawnEggItem(TYPE,0x181725,0x68d9ff,new Item.Settings()));
    private static final TrackedData<Long> ATTACK_START=DataTracker.registerData(RaizenBoss.class,TrackedDataHandlerRegistry.LONG);
    private final ServerBossBar bar=new ServerBossBar(Text.translatable("entity.katana_combo.raizen"),BossBar.Color.BLUE,BossBar.Style.NOTCHED_10);
    private static final TrackedData<Integer> ACTION=DataTracker.registerData(RaizenBoss.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> PHASE_TWO=DataTracker.registerData(RaizenBoss.class,TrackedDataHandlerRegistry.BOOLEAN);
    private int cooldown,sequence;
    private boolean transitionUsed;
    private final Set<UUID> dashHits=new HashSet<>();
    private float actionYaw;
    public static void init(){
        FabricDefaultAttributeRegistry.register(TYPE,HostileEntity.createHostileAttributes()
            .add(EntityAttributes.GENERIC_MAX_HEALTH,360).add(EntityAttributes.GENERIC_ARMOR,18)
            .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,6).add(EntityAttributes.GENERIC_ATTACK_DAMAGE,6)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED,0.22).add(EntityAttributes.GENERIC_FOLLOW_RANGE,40)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,0.9));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries->entries.add(AMAKIRI));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries->entries.add(EGG));
    }
    public RaizenBoss(EntityType<? extends HostileEntity> type,World world){
        super(type,world);experiencePoints=80;setPersistent();
        equipStack(EquipmentSlot.MAINHAND,new ItemStack(AMAKIRI));setEquipmentDropChance(EquipmentSlot.MAINHAND,0);
    }
    @Override protected void initDataTracker(DataTracker.Builder builder){super.initDataTracker(builder);builder.add(ATTACK_START,-1L);builder.add(ACTION,0);builder.add(PHASE_TWO,false);}
    public float attackAge(float delta){long start=dataTracker.get(ATTACK_START);return start<0?-1:Math.max(0,getWorld().getTime()-start+delta);}
    public int action(){return dataTracker.get(ACTION);}
    public boolean phaseTwo(){return dataTracker.get(PHASE_TWO);}
    private void beginAction(int type){
        dashHits.clear();
        dataTracker.set(ACTION,type);dataTracker.set(ATTACK_START,getWorld().getTime());
        var target=getTarget();
        if(target!=null){var d=target.getPos().subtract(getPos());actionYaw=(float)(Math.atan2(-d.x,d.z)*180/Math.PI);}else actionYaw=getYaw();
        setYaw(actionYaw);setBodyYaw(actionYaw);getNavigation().stop();
        cooldown=type==RaizenMotion.TRANSITION?68:(int)((RaizenMotion.duration(type)+18)*(phaseTwo()?.8f:1));
    }
    private void enterPhaseTwo(){
        dataTracker.set(PHASE_TWO,true);transitionUsed=true;
        getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(.26);
        getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(8);
        bar.setColor(BossBar.Color.PURPLE);
    }
    @Override public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt){super.writeCustomDataToNbt(nbt);nbt.putBoolean("StormPhaseTwo",phaseTwo());}
    @Override public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt){super.readCustomDataFromNbt(nbt);if(nbt.getBoolean("StormPhaseTwo"))enterPhaseTwo();}
    @Override protected void initGoals(){
        goalSelector.add(0,new SwimGoal(this));
        goalSelector.add(2,new Goal(){
            {setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
            public boolean canStart(){return getTarget()!=null && getTarget().isAlive();}
            public boolean shouldRunEveryTick(){return true;}
            public void tick(){
                var target=getTarget();if(target==null)return;
                if(attackAge(0)>=0){getNavigation().stop();return;}
                getLookControl().lookAt(target,18,18);
                getNavigation().startMovingTo(target,1);
                if(cooldown<=0 && canSee(target)){
                    double distance=squaredDistanceTo(target);
                    if(distance>17.64 && distance<=70.56)beginAction(RaizenMotion.DASH);
                    else if(distance<=17.64)beginAction(sequence++%2==0?RaizenMotion.HORIZONTAL:RaizenMotion.SLAM);
                }
            }
            public void stop(){getNavigation().stop();}
        });
        goalSelector.add(6,new WanderAroundFarGoal(this,.65));
        goalSelector.add(7,new LookAtEntityGoal(this,PlayerEntity.class,12));
        targetSelector.add(1,new RevengeGoal(this));
        targetSelector.add(2,new ActiveTargetGoal<>(this,PlayerEntity.class,true));
    }
    private boolean damageTarget(LivingEntity target){
        return target!=this && target.isAlive() && (target==getTarget() || target instanceof PlayerEntity)
            && !(target instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))
            && canTarget(target) && canSee(target);
    }
    private void dashContacts(Box before){
        // Sweep both bodies for this tick: approaching players can cross the charge before its final slash.
        Box sweep=before.union(getBoundingBox()).expand(.4,.15,.4);
        for(var target:getWorld().getEntitiesByClass(LivingEntity.class,sweep.expand(2),this::damageTarget)){
            Box targetSweep=target.getBoundingBox().union(target.getBoundingBox().offset(
                target.prevX-target.getX(),target.prevY-target.getY(),target.prevZ-target.getZ()));
            if(sweep.intersects(targetSweep) && dashHits.add(target.getUuid()))tryAttack(target);
        }
    }
    @Override public void tick(){
        Box before=getBoundingBox();
        super.tick();
        if(!getWorld().isClient){
            if(cooldown>0)cooldown--;
            bar.setPercent(getHealth()/getMaxHealth());
            if(hasCustomName())bar.setName(getDisplayName());
            if(!isAlive()){dataTracker.set(ACTION,0);dataTracker.set(ATTACK_START,-1L);getNavigation().stop();return;}
            if(!transitionUsed && isAlive() && getHealth()<=getMaxHealth()*.5f){transitionUsed=true;beginAction(RaizenMotion.TRANSITION);}
            float age=attackAge(0);int type=action();
            if(age>=0){
                setYaw(actionYaw);setBodyYaw(actionYaw);getNavigation().stop();
                if(type==RaizenMotion.DASH && age>=7 && age<=13 && !isTouchingWater() && isOnGround()){
                    double angle=Math.toRadians(actionYaw),speed=phaseTwo()?.68:.56;
                    move(MovementType.SELF,new net.minecraft.util.math.Vec3d(-Math.sin(angle)*speed,0,Math.cos(angle)*speed));
                }
                if(type==RaizenMotion.DASH && age>=7 && age<=RaizenMotion.hit(type))dashContacts(before);
                if(age==RaizenMotion.hit(type)){
                    double reach=type==RaizenMotion.HORIZONTAL?4.5:4.2;
                    for(var target:getWorld().getEntitiesByClass(net.minecraft.entity.LivingEntity.class,getBoundingBox().expand(reach,1.5,reach),
                            e->e!=this && e.isAlive() && (e==getTarget() || e instanceof PlayerEntity))){
                        if(target instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))continue;
                        if(squaredDistanceTo(target)>reach*reach || !canSee(target) || !canTarget(target))continue;
                        var offset=target.getPos().subtract(getPos()).multiply(1,0,1).normalize();
                        double cone=type==RaizenMotion.HORIZONTAL?-.15:.45;
                        if(getRotationVec(1).multiply(1,0,1).normalize().dotProduct(offset)>cone
                                && (type!=RaizenMotion.DASH || dashHits.add(target.getUuid())))tryAttack(target);
                    }
                }
                if(age>=RaizenMotion.duration(type)){
                    if(type==RaizenMotion.TRANSITION){enterPhaseTwo();cooldown=18;}
                    dataTracker.set(ACTION,0);dataTracker.set(ATTACK_START,-1L);
                }
            }
        }
    }
    @Override public void onStartedTrackingBy(ServerPlayerEntity player){super.onStartedTrackingBy(player);bar.addPlayer(player);}
    @Override public void onStoppedTrackingBy(ServerPlayerEntity player){super.onStoppedTrackingBy(player);bar.removePlayer(player);}
    @Override public boolean cannotDespawn(){return true;}
}
