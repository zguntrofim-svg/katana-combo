package dev.animerpg.combo.temple;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.structure.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.*;

/** Native structure starts/pieces are saved by Minecraft; generation is clipped to the current chunk. */
public final class ThunderTempleStructure extends Structure {
    public static final MapCodec<ThunderTempleStructure> CODEC=createCodec(ThunderTempleStructure::new);
    public static final StructureType<ThunderTempleStructure> TYPE=()->CODEC;
    public static final StructurePieceType PIECE=Piece::new;
    public static void init(){
        Registry.register(Registries.STRUCTURE_TYPE,Identifier.of("millida","thunder_temple"),TYPE);
        Registry.register(Registries.STRUCTURE_PIECE,Identifier.of("millida","thunder_temple"),PIECE);
        // Build the read-only plan once during startup, not in a running world's generation task.
        TempleBlueprint.COLUMNS.size();
        TemplePlacement.init();
        TempleSearch.init();
    }
    public ThunderTempleStructure(Config config){super(config);}
    @Override protected Optional<StructurePosition> getStructurePosition(Context context){
        int x=context.chunkPos().getCenterX(),z=context.chunkPos().getCenterZ(),low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        // Reject other biomes before costly height/noise queries (also used by locate).
        if(!context.biomePredicate().test(context.biomeSource().getBiome(x>>2,40,z>>2,context.noiseConfig().getMultiNoiseSampler())))return Optional.empty();
        int center=context.chunkGenerator().getHeightOnGround(x,z,Heightmap.Type.WORLD_SURFACE_WG,context.world(),context.noiseConfig());
        if(center<110 || center+TempleBlueprint.HEIGHT+3>=context.world().getTopY())return Optional.empty();
        low=high=center;
        for(int dx:new int[]{-42,42})for(int dz:new int[]{-42,42}){
            int h=context.chunkGenerator().getHeightOnGround(x+dx,z+dz,Heightmap.Type.WORLD_SURFACE_WG,context.world(),context.noiseConfig());
            low=Math.min(low,h);high=Math.max(high,h);
        }
        // Restrict to reasonably broad mountain summits; never cut buildings off at the world ceiling.
        if(high<110 || high-low>64 || high+TempleBlueprint.HEIGHT+3>=context.world().getTopY())return Optional.empty();
        BlockPos origin=new BlockPos(x,high+2,z);
        return Optional.of(new StructurePosition(origin,collector->collector.addPiece(new Piece(origin,context.world().getBottomY()))));
    }
    @Override public StructureType<?> getType(){return TYPE;}
    public static final class Piece extends StructurePiece {
        private final BlockPos origin;
        Piece(BlockPos origin,int bottom){
            super(PIECE,0,new BlockBox(origin.getX()-60,bottom,origin.getZ()-60,origin.getX()+59,origin.getY()+TempleBlueprint.HEIGHT,origin.getZ()+59));
            this.origin=origin;
        }
        Piece(StructureContext context,NbtCompound nbt){super(PIECE,nbt);origin=new BlockPos(nbt.getInt("TempleX"),nbt.getInt("TempleY"),nbt.getInt("TempleZ"));}
        @Override protected void writeNbt(StructureContext context,NbtCompound nbt){nbt.putInt("TempleX",origin.getX());nbt.putInt("TempleY",origin.getY());nbt.putInt("TempleZ",origin.getZ());}
        @Override public void generate(StructureWorldAccess world,StructureAccessor accessor,ChunkGenerator generator,Random random,BlockBox bounds,ChunkPos chunk,BlockPos pivot){
            for(var column:TempleBlueprint.COLUMNS){
                int x=origin.getX()+column.x(),z=origin.getZ()+column.z();
                if(x<bounds.getMinX() || x>bounds.getMaxX() || z<bounds.getMinZ() || z>bounds.getMaxZ())continue;
                if(column.support()){
                    BlockPos.Mutable p=new BlockPos.Mutable(x,origin.getY()-1,z);
                    while(p.getY()>world.getBottomY()){
                        var state=world.getBlockState(p);
                        if(state.isSolidBlock(world,p) && state.getFluidState().isEmpty())break;
                        world.setBlockState(p,TempleBlueprint.foundation(x,p.getY(),z),Block.NOTIFY_LISTENERS);p.move(Direction.DOWN);
                    }
                }
                if(TempleBlueprint.footprint(column.x(),column.z())){
                    // Local summit spikes between the height samples must not bury the open courtyards.
                    BlockPos.Mutable p=new BlockPos.Mutable(x,origin.getY()+column.floor()+1,z);
                    while(p.getY()<Math.min(world.getTopY(),origin.getY()+TempleBlueprint.HEIGHT)){
                        if(!world.getBlockState(p).isAir())world.setBlockState(p,net.minecraft.block.Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
                        p.move(Direction.UP);
                    }
                }
                for(var voxel:column.blocks()){
                    BlockPos p=new BlockPos(x,origin.getY()+voxel.y(),z);
                    if(bounds.contains(p))world.setBlockState(p,voxel.state(),Block.NOTIFY_LISTENERS);
                }
            }
        }
    }
}
