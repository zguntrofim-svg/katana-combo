package dev.animerpg.combo.temple;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.structure.Structure;
import static net.minecraft.server.command.CommandManager.literal;

/** Locate this large, rare structure incrementally; never run vanilla's entire region search in one tick. */
public final class TempleSearch {
    private static final Identifier ID=Identifier.of("millida","thunder_temple");
    private static final Map<ServerWorld,Search> SEARCHES=new HashMap<>();
    private static final ChunkTicketType<ChunkPos> TICKET=ChunkTicketType.create("millida_temple_search",Comparator.comparingLong(ChunkPos::toLong));
    public static void init(){
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            dispatcher.register(literal("millida").then(literal("temple").requires(s->s.hasPermissionLevel(2))
                .then(literal("locate").executes(c->start(c.getSource())))));
            // This literal takes precedence over vanilla's generic <structure> argument only for our ID.
            dispatcher.register(literal("locate").requires(s->s.hasPermissionLevel(2)).then(literal("structure")
                .then(literal("millida:thunder_temple").executes(c->start(c.getSource())))));
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            var iterator=SEARCHES.values().iterator();
            while(iterator.hasNext()){
                Search search=iterator.next();
                try{if(search.tick()){search.close();iterator.remove();}}
                catch(RuntimeException error){
                    org.slf4j.LoggerFactory.getLogger("MillidaTemple").error("Temple search failed",error);
                    search.source.sendError(Text.literal("Поиск храма остановлен из-за ошибки; подробности в журнале."));
                    search.close();iterator.remove();
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{SEARCHES.values().forEach(Search::close);SEARCHES.clear();});
    }
    static int cancel(ServerCommandSource source){
        Search search=SEARCHES.remove(source.getWorld());
        if(search==null){source.sendError(Text.literal("Нет активного размещения или поиска храма."));return 0;}
        search.close();source.sendFeedback(()->Text.literal("Поиск храма остановлен."),false);return 1;
    }
    private static int start(ServerCommandSource source){
        ServerWorld world=source.getWorld();
        if(world.getRegistryKey()!=World.OVERWORLD || !world.getStructureAccessor().shouldGenerateStructures()){
            source.sendError(Text.literal("Поиск доступен в обычном мире с включённой генерацией структур."));return 0;
        }
        if(SEARCHES.containsKey(world)){source.sendError(Text.literal("Поиск уже идёт. /millida temple cancel — остановить."));return 0;}
        Structure structure=world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(ID);
        var set=world.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(ID);
        if(!(structure instanceof ThunderTempleStructure) || set==null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)){
            source.sendError(Text.literal("Не найдены совместимые настройки генерации храма в datapack."));return 0;
        }
        SEARCHES.put(world,new Search(source,structure,placement));
        source.sendFeedback(()->Text.literal("Ищу храм постепенно, без длительной блокировки тика. Поиск ограничен 90 секундами; /millida temple cancel — остановить."),false);
        return 1;
    }
    private static final class Search {
        final ServerWorld world;final ServerCommandSource source;final Structure structure;
        final RandomSpreadStructurePlacement placement;final List<ChunkPos> candidates=new ArrayList<>();
        final long deadline=System.nanoTime()+90_000_000_000L;
        int index;ChunkPos requested;CompletableFuture<OptionalChunk<Chunk>> pending;
        Search(ServerCommandSource source,Structure structure,RandomSpreadStructurePlacement placement){
            this.source=source;this.world=source.getWorld();this.structure=structure;this.placement=placement;
            BlockPos p=BlockPos.ofFloored(source.getPosition());int spacing=placement.getSpacing();
            int rx=Math.floorDiv(p.getX()>>4,spacing),rz=Math.floorDiv(p.getZ()>>4,spacing);
            for(int dx=-24;dx<=24;dx++)for(int dz=-24;dz<=24;dz++){
                ChunkPos candidate=placement.getStartChunk(world.getSeed(),(rx+dx)*spacing,(rz+dz)*spacing);
                if(world.getWorldBorder().contains(candidate.getCenterAtY(64)))candidates.add(candidate);
            }
            candidates.sort(Comparator.comparingDouble(c->{double dx=c.getCenterX()-source.getPosition().x,dz=c.getCenterZ()-source.getPosition().z;return dx*dx+dz*dz;}));
        }
        void close(){
            if(requested!=null)world.getChunkManager().removeTicket(TICKET,requested,0,requested);
            requested=null;pending=null;
        }
        boolean tick(){
            if(System.nanoTime()>deadline || index>=candidates.size() && pending==null){
                source.sendFeedback(()->Text.literal("За ограниченное время храм не найден. Перейди в другой горный район и повтори поиск или используй /millida temple spawn."),false);return true;
            }
            if(pending!=null){
                if(!pending.isDone())return false;
                var result=pending.getNow(null);
                if(result!=null && result.isPresent()){
                    var start=result.orElse(null).getStructureStart(structure);
                    if(start!=null && start.hasChildren()){
                        var box=start.getBoundingBox();BlockPos p=new BlockPos(box.getMinX()+60,box.getMaxY()-TempleBlueprint.HEIGHT+6,box.getMinZ()+65);
                        source.sendFeedback(()->Text.literal("Храм найден. Центр арены: "+p.toShortString()+". Телепортация: /tp @s "+p.getX()+" "+p.getY()+" "+p.getZ()),false);return true;
                    }
                }
                close();return false;
            }
            // At most ONE candidate (five height columns after the biome filter) per server tick.
            ChunkPos pos=candidates.get(index++);var manager=world.getChunkManager();var generator=manager.getChunkGenerator();
            if(!placement.shouldGenerate(manager.getStructurePlacementCalculator(),pos.x,pos.z))return false;
            var context=new Structure.Context(world.getRegistryManager(),generator,generator.getBiomeSource(),manager.getNoiseConfig(),
                world.getStructureTemplateManager(),world.getSeed(),pos,world,structure.getValidBiomes()::contains);
            if(structure.getValidStructurePosition(context).isEmpty())return false;
            // Verify the actual saved/generated start asynchronously: old chunks may predate this mod.
            requested=pos;manager.addTicket(TICKET,pos,0,pos);
            pending=manager.getChunkFutureSyncOnMainThread(pos.x,pos.z,ChunkStatus.FULL,true);
            return false;
        }
    }
    private TempleSearch(){}
}
