package dev.animerpg.combo.temple;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.server.command.*;
import net.minecraft.server.world.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Survey first, then place with a per-tick operation and time budget. No /fill, force-placement or join(). */
public final class TemplePlacement {
    private static final Map<ServerWorld,Job> JOBS=new HashMap<>();
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("millida_temple",Comparator.comparingLong(ChunkPos::toLong));
    private static final Set<Block> NATURAL=Set.of(Blocks.STONE,Blocks.DEEPSLATE,Blocks.TUFF,Blocks.CALCITE,
        Blocks.GRANITE,Blocks.DIORITE,Blocks.ANDESITE,Blocks.GRAVEL,Blocks.DIRT,Blocks.GRASS_BLOCK,Blocks.COARSE_DIRT,
        Blocks.ROOTED_DIRT,Blocks.PODZOL,Blocks.MOSS_BLOCK,Blocks.MOSS_CARPET,Blocks.SNOW,Blocks.SNOW_BLOCK,
        Blocks.POWDER_SNOW,Blocks.ICE,Blocks.PACKED_ICE,Blocks.BLUE_ICE,Blocks.SHORT_GRASS,Blocks.TALL_GRASS,
        Blocks.FERN,Blocks.LARGE_FERN,Blocks.DEAD_BUSH,Blocks.DANDELION,Blocks.POPPY,Blocks.BROWN_MUSHROOM,Blocks.RED_MUSHROOM,
        Blocks.COAL_ORE,Blocks.DEEPSLATE_COAL_ORE,Blocks.IRON_ORE,Blocks.DEEPSLATE_IRON_ORE,
        Blocks.COPPER_ORE,Blocks.DEEPSLATE_COPPER_ORE,Blocks.GOLD_ORE,Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.EMERALD_ORE,Blocks.DEEPSLATE_EMERALD_ORE,Blocks.LAPIS_ORE,Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.REDSTONE_ORE,Blocks.DEEPSLATE_REDSTONE_ORE,Blocks.DIAMOND_ORE,Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.RAW_IRON_BLOCK,Blocks.RAW_COPPER_BLOCK);
    private static boolean safe(BlockState state){return !state.hasBlockEntity() && (state.isAir() || NATURAL.contains(state.getBlock()));}
    public static void init(){
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("millida")
            .then(literal("temple").requires(source->source.hasPermissionLevel(2))
                .then(literal("spawn").executes(context->start(context.getSource())))
                .then(literal("cancel").executes(context->{
                    var job=JOBS.remove(context.getSource().getWorld());
                    if(job==null)return TempleSearch.cancel(context.getSource());
                    job.close();job.message("Размещение остановлено. Уже поставленные блоки сохранены.");return 1;
                })))));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var iterator=JOBS.values().iterator();
            while(iterator.hasNext()){
                Job job=iterator.next();
                try{if(job.tick()){job.close();iterator.remove();}}
                catch(RuntimeException error){
                    org.slf4j.LoggerFactory.getLogger("MillidaTemple").error("Temple placement interrupted",error);
                    job.message("Размещение прервано из-за ошибки; подробности в журнале сервера. Уже поставленные блоки сохранены.");
                    job.close();iterator.remove();
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{JOBS.values().forEach(Job::close);JOBS.clear();});
    }
    private static int start(ServerCommandSource source){
        ServerWorld world=source.getWorld();
        if(world.getRegistryKey()!=World.OVERWORLD){source.sendError(Text.literal("Храм размещается только в обычном мире."));return 0;}
        if(JOBS.containsKey(world)){source.sendError(Text.literal("Обследование или строительство уже идёт. /millida temple cancel — остановить."));return 0;}
        BlockPos p=BlockPos.ofFloored(source.getPosition());
        int x=Math.floorDiv(p.getX(),16)*16+8,z=Math.floorDiv(p.getZ()-112,16)*16+8;
        if(!world.getWorldBorder().contains(new BlockPos(x-60,64,z-60)) || !world.getWorldBorder().contains(new BlockPos(x+59,64,z+59))){
            source.sendError(Text.literal("Участок выходит за границу мира."));return 0;
        }
        JOBS.put(world,new Job(world,source,x,z));
        source.sendFeedback(()->Text.literal("Обследую участок 120×120 к северу: "+x+", "+z+". До завершения обследования блоки не меняются."),false);
        return 1;
    }
    private static final class Job {
        final ServerWorld world;final ServerCommandSource source;final int x,z;
        final List<TempleBlueprint.Column> columns=TempleBlueprint.COLUMNS;
        final int[] surfaces=new int[columns.size()];
        final List<ChunkPos> chunks;final List<ChunkPos> tickets=new ArrayList<>();
        final Set<BlockBox> existingStructures=new HashSet<>();
        CompletableFuture<OptionalChunk<Chunk>> pending;
        int stage,chunkIndex,index,y=Integer.MIN_VALUE,voxelIndex,base,low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        int lastProgress=-1;boolean foundation;
        Job(ServerWorld world,ServerCommandSource source,int x,int z){
            this.world=world;this.source=source;this.x=x;this.z=z;
            Set<ChunkPos> set=new LinkedHashSet<>();
            for(var c:columns)set.add(new ChunkPos((x+c.x())>>4,(z+c.z())>>4));chunks=List.copyOf(set);
        }
        void message(String text){source.sendFeedback(()->Text.literal(text),false);}
        boolean fail(String text){source.sendError(Text.literal(text+" Переместись в другой участок гор и повтори команду."));return true;}
        void close(){for(var pos:tickets)world.getChunkManager().removeTicket(TICKET,pos,0,pos);tickets.clear();}
        void rememberStructures(Chunk chunk){
            for(var start:chunk.getStructureStarts().values())if(start.hasChildren())existingStructures.add(start.getBoundingBox());
        }
        boolean tick(){
            if(stage==0){
                // One asynchronous chunk request at a time. Keep surveyed chunks resident until completion.
                if(pending==null && chunkIndex<chunks.size()){
                    ChunkPos pos=chunks.get(chunkIndex);world.getChunkManager().addTicket(TICKET,pos,0,pos);tickets.add(pos);
                    pending=world.getChunkManager().getChunkFutureSyncOnMainThread(pos.x,pos.z,ChunkStatus.FULL,true);return false;
                }
                if(pending!=null){
                    if(!pending.isDone())return false;
                    var result=pending.getNow(null);
                    if(result==null || !result.isPresent())return fail("Не удалось загрузить участок.");
                    Chunk chunk=result.orElse(null);
                    rememberStructures(chunk);
                    // Read starts only if already loaded: a reference may point to a distant or underground structure.
                    for(var references:chunk.getStructureReferences().values())for(long packed:references){
                        ChunkPos start=new ChunkPos(packed);
                        var loaded=world.getChunkManager().getWorldChunk(start.x,start.z);
                        if(loaded!=null)rememberStructures(loaded);
                    }
                    pending=null;chunkIndex++;return false;
                }
                stage=1;message("Чанки загружены. Измеряю рельеф и проверяю существующие блоки.");
            }
            long deadline=System.nanoTime()+3_000_000L;
            int budget=stage==3?768:4096;
            while(budget-->0 && System.nanoTime()<deadline){
                if(index>=columns.size()){
                    if(stage==1){
                        base=high+2;
                        if(base+TempleBlueprint.HEIGHT>=world.getTopY() || base-low>96)return fail("Рельеф слишком крутой или храм не помещается по высоте.");
                        BlockBox affected=new BlockBox(x-60,Math.max(world.getBottomY(),low-4),z-60,x+59,base+TempleBlueprint.HEIGHT,z+59);
                        if(existingStructures.stream().anyMatch(box->box.intersects(affected)))
                            return fail("На участке есть структура, пересекающая объём строительства.");
                        stage=2;index=0;y=Integer.MIN_VALUE;continue;
                    }
                    if(stage==2){stage=3;index=0;y=Integer.MIN_VALUE;message("Участок свободен. Строю храм небольшими порциями; не ставь здесь блоки до завершения.");continue;}
                    message("TEMPLE OF THE THUNDER SHOGUN готов. Вход: "+x+" "+(base+3)+" "+(z+58)
                        +". Центр арены / место Райдзэна: "+x+" "+(base+6)+" "+(z+5)+". Босс автоматически не вызывается.");return true;
                }
                var column=columns.get(index);int wx=x+column.x(),wz=z+column.z();
                if(stage==1){
                    surfaces[index]=world.getTopY(Heightmap.Type.WORLD_SURFACE,wx,wz);
                    high=Math.max(high,surfaces[index]);low=Math.min(low,surfaces[index]);index++;continue;
                }
                if(stage==2){
                    if(y==Integer.MIN_VALUE)y=Math.max(world.getBottomY(),surfaces[index]-4);
                    if(y>=base+TempleBlueprint.HEIGHT){index++;y=Integer.MIN_VALUE;continue;}
                    BlockPos p=new BlockPos(wx,y++,wz);
                    if(!safe(world.getBlockState(p)))return fail("Защита построек: неподходящий блок в "+p.toShortString()+". Ничего не размещено.");
                    continue;
                }
                int progress=index*100/columns.size()/10;
                if(progress!=lastProgress){lastProgress=progress;message("Храм: "+(progress*10)+"%.");}
                if(y==Integer.MIN_VALUE){y=base-1;foundation=column.support();voxelIndex=0;}
                if(foundation){
                    BlockPos p=new BlockPos(wx,y,wz);BlockState state=world.getBlockState(p);
                    if(state.isSolidBlock(world,p) && state.getFluidState().isEmpty()){foundation=false;continue;}
                    if(y<=world.getBottomY())return fail("Под опорой не найдена порода; размещение остановлено.");
                    if(!safe(state))return fail("После обследования участок изменился в "+p.toShortString()+"; размещение остановлено без замены нового блока.");
                    world.setBlockState(p,TempleBlueprint.foundation(wx,y,wz),Block.NOTIFY_LISTENERS);y--;continue;
                }
                if(voxelIndex>=column.blocks().size()){index++;y=Integer.MIN_VALUE;continue;}
                var voxel=column.blocks().get(voxelIndex++);BlockPos p=new BlockPos(wx,base+voxel.y(),wz);
                if(!safe(world.getBlockState(p)))return fail("После обследования участок изменился в "+p.toShortString()+"; размещение остановлено без замены нового блока.");
                world.setBlockState(p,voxel.state(),Block.NOTIFY_LISTENERS);
            }
            return false;
        }
    }
    private TemplePlacement(){}
}
