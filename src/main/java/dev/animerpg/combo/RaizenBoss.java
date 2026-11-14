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

public final class RaizenBoss extends HostileEntity {
    public static final EntityType<RaizenBoss> TYPE=Registry.register(Registries.ENTITY_TYPE,Identifier.of("katana_combo","raizen"),
        EntityType.Builder.create(RaizenBoss::new,SpawnGroup.MONSTER).dimensions(1.45f,3.5f).eyeHeight(3.05f)
            .maxTrackingRange(12).trackingTickInterval(2).build("katana_combo:raizen"));
    public static final Item AMAKIRI=Registry.register(Registries.ITEM,Identifier.of("katana_combo","amakiri"),
        new SwordItem(ToolMaterials.NETHERITE,new Item.Settings().fireproof().rarity(net.minecraft.util.Rarity.EPIC)
            .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE,3,-2.6f))));
    public static final Item EGG=Registry.register(Registries.ITEM,Identifier.of("katana_combo","raizen_spawn_egg"),
        new SpawnEggItem(TYPE,0x181725,0x68d9ff,new Item.Settings()));
    private static final TrackedData<Long> ATTACK_START=DataTracker.registerData(RaizenBoss.class,TrackedDataHandlerRegistry.LONG);
    private final ServerBossBar bar=new ServerBossBar(Text.translatable("entity.katana_combo.raizen"),BossBar.Color.BLUE,BossBar.Style.NOTCHED_10);
    private int cooldown;
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
    @Override protected void initDataTracker(DataTracker.Builder builder){super.initDataTracker(builder);builder.add(ATTACK_START,-1L);}
    public float attackAge(float delta){long start=dataTracker.get(ATTACK_START);return start<0?-1:Math.max(0,getWorld().getTime()-start+delta);}
    @Override protected void initGoals(){
        goalSelector.add(0,new SwimGoal(this));
        goalSelector.add(2,new Goal(){
            {setControls(EnumSet.of(Control.MOVE,Control.LOOK));}
            public boolean canStart(){return getTarget()!=null && getTarget().isAlive();}
            public boolean shouldRunEveryTick(){return true;}
            public void tick(){
                var target=getTarget();if(target==null)return;
                getLookControl().lookAt(target,18,18);
                if(attackAge(0)>=0){getNavigation().stop();return;}
                getNavigation().startMovingTo(target,1);
                if(cooldown<=0 && squaredDistanceTo(target)<=16 && canSee(target)){
                    dataTracker.set(ATTACK_START,getWorld().getTime());cooldown=42;getNavigation().stop();
                }
            }
            public void stop(){getNavigation().stop();}
        });
        goalSelector.add(6,new WanderAroundFarGoal(this,.65));
        goalSelector.add(7,new LookAtEntityGoal(this,PlayerEntity.class,12));
        targetSelector.add(1,new RevengeGoal(this));
        targetSelector.add(2,new ActiveTargetGoal<>(this,PlayerEntity.class,true));
    }
    @Override public void tick(){
        super.tick();
        if(!getWorld().isClient){
            if(cooldown>0)cooldown--;
            bar.setPercent(getHealth()/getMaxHealth());
            if(hasCustomName())bar.setName(getDisplayName());
            float age=attackAge(0);
            if(age==12){
                var target=getTarget();
                if(target!=null && target.isAlive() && squaredDistanceTo(target)<=16 && canSee(target)){
                    var offset=target.getPos().subtract(getPos()).multiply(1,0,1).normalize();
                    if(getRotationVec(1).multiply(1,0,1).normalize().dotProduct(offset)>.25)tryAttack(target);
                }
            }
            if(age>=24)dataTracker.set(ATTACK_START,-1L);
        }
    }
    @Override public void onStartedTrackingBy(ServerPlayerEntity player){super.onStartedTrackingBy(player);bar.addPlayer(player);}
    @Override public void onStoppedTrackingBy(ServerPlayerEntity player){super.onStoppedTrackingBy(player);bar.removePlayer(player);}
    @Override public boolean cannotDespawn(){return true;}
}
