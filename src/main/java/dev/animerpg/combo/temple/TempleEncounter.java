package dev.animerpg.combo.temple;

import java.util.*;
import dev.animerpg.combo.RaizenBoss;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;

/** Server-only, persistent one encounter per temple. Never changes blocks or forces chunks to load. */
public final class TempleEncounter extends PersistentState {
    private static final Type<TempleEncounter> TYPE=new Type<>(TempleEncounter::new,TempleEncounter::read,null);
    private static final String SAVE="millida_temple_encounters";
    private static final class Site {
        final BlockPos origin;boolean awakened;UUID boss;
        Site(BlockPos origin){this.origin=origin;}
    }
    private final Map<BlockPos,Site> sites=new LinkedHashMap<>();
    private static TempleEncounter get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(TYPE,SAVE);}
    public static void register(ServerWorld world,BlockPos origin){
        TempleEncounter state=get(world);
        if(!state.sites.containsKey(origin)){state.sites.put(origin,new Site(origin.toImmutable()));state.index=null;state.markDirty();}
    }
    public static void init(){
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{
            for(var entry:chunk.getStructureStarts().entrySet()){
                if(!(entry.getKey() instanceof ThunderTempleStructure) || !entry.getValue().hasChildren())continue;
                var box=entry.getValue().getBoundingBox();
                register(world,new BlockPos(box.getMinX()+60,box.getMaxY()-TempleBlueprint.HEIGHT,box.getMinZ()+60));
            }
        });
        ServerTickEvents.END_WORLD_TICK.register(world->{if(world.getTime()%10==0)get(world).tick(world);});
    }
    private void tick(ServerWorld world){
        // Iterate players, not every temple in a world. Chunk-keyed nearby lookups cap work per player.
        for(var player:world.getPlayers()){
            if(!player.isAlive() || player.isSpectator())continue;
            int cx=player.getChunkPos().x,cz=player.getChunkPos().z;
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
                var nearby=byChunk().get(ChunkPos.toLong(cx+dx,cz+dz));if(nearby==null)continue;
                for(Site site:nearby){
                    BlockPos o=site.origin;
                    if(site.awakened || Math.abs(player.getX()-o.getX())>59 || player.getZ()<o.getZ()-59 || player.getZ()>o.getZ()+60
                            || player.getY()<o.getY()-1 || player.getY()>o.getY()+TempleBlueprint.HEIGHT)continue;
                    if(TempleBlueprint.footprint((int)Math.floor(player.getX())-o.getX(),(int)Math.floor(player.getZ())-o.getZ()))awaken(world,site);
                }
            }
        }
    }
    private Map<Long,List<Site>> index;
    private Map<Long,List<Site>> byChunk(){
        if(index==null){
            index=new HashMap<>();
            for(Site s:sites.values())index.computeIfAbsent(ChunkPos.toLong(s.origin.getX()>>4,s.origin.getZ()>>4),k->new ArrayList<>()).add(s);
        }
        return index;
    }
    private void awaken(ServerWorld world,Site site){
        if(world.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return;
        BlockPos feet=site.origin.add(0,6,5);
        if(world.getChunkManager().getWorldChunk(feet.getX()>>4,feet.getZ()>>4)==null
                || !world.getBlockState(feet.down()).isOf(Blocks.CHISELED_POLISHED_BLACKSTONE))return;
        var existing=world.getEntitiesByClass(RaizenBoss.class,new Box(feet).expand(60,66,60),e->e.isAlive());
        if(!existing.isEmpty()){site.boss=existing.getFirst().getUuid();site.awakened=true;markDirty();return;}
        RaizenBoss boss=RaizenBoss.TYPE.create(world);if(boss==null)return;
        boss.refreshPositionAndAngles(feet.getX()+.5,feet.getY(),feet.getZ()+.5,0,0);boss.setBodyYaw(0);
        if(!world.isSpaceEmpty(boss) || !world.spawnEntity(boss))return;
        site.awakened=true;site.boss=boss.getUuid();markDirty();
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,feet.getX()+.5,feet.getY()+1.5,feet.getZ()+.5,55,1.8,1.4,1.8,.12);
        world.spawnParticles(ParticleTypes.FLASH,feet.getX()+.5,feet.getY()+1,feet.getZ()+.5,1,0,0,0,0);
        world.playSound(null,feet,SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,SoundCategory.HOSTILE,.8f,1.25f);
        for(var p:world.getPlayers())if(p.squaredDistanceTo(boss)<100*100)p.sendMessage(Text.literal("RAIZEN — Грозовой сёгун пробуждается!"),false);
    }
    private static TempleEncounter read(NbtCompound nbt,RegistryWrapper.WrapperLookup registries){
        TempleEncounter state=new TempleEncounter();
        NbtList list=nbt.getList("Sites",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++){
            NbtCompound tag=list.getCompound(i);BlockPos origin=BlockPos.fromLong(tag.getLong("Origin"));
            Site s=new Site(origin);s.awakened=tag.getBoolean("Awakened");if(tag.containsUuid("Boss"))s.boss=tag.getUuid("Boss");
            state.sites.put(origin,s);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt,RegistryWrapper.WrapperLookup registries){
        NbtList list=new NbtList();
        for(Site s:sites.values()){
            NbtCompound tag=new NbtCompound();tag.putLong("Origin",s.origin.asLong());tag.putBoolean("Awakened",s.awakened);
            if(s.boss!=null)tag.putUuid("Boss",s.boss);list.add(tag);
        }
        nbt.put("Sites",list);return nbt;
    }
    private TempleEncounter(){}
}
