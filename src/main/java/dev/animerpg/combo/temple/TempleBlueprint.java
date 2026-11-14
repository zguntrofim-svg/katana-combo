package dev.animerpg.combo.temple;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** One deterministic, immutable plan shared by native world generation and the surveyed command. */
public final class TempleBlueprint {
    public static final int MIN=-60, MAX=59, HEIGHT=66;
    public record Voxel(int y,BlockState state){}
    public record Column(int x,int z,int floor,boolean support,List<Voxel> blocks){}
    private static final BlockState STONE=Blocks.STONE_BRICKS.getDefaultState(), TILE=Blocks.DEEPSLATE_TILES.getDefaultState(),
        DARK=Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), WOOD=Blocks.DARK_OAK_PLANKS.getDefaultState(),
        RED=Blocks.RED_NETHER_BRICKS.getDefaultState(), SILVER=Blocks.POLISHED_ANDESITE.getDefaultState(),
        LIGHT=Blocks.SEA_LANTERN.getDefaultState(), GLASS=Blocks.CYAN_STAINED_GLASS.getDefaultState();
    private final Map<Integer,TreeMap<Integer,BlockState>> cells=new HashMap<>();
    public static final List<Column> COLUMNS=new TempleBlueprint().build();
    public static boolean footprint(int x,int z){
        if(Math.abs(x)<=5 && z>=36 && z<=59)return true;
        if(Math.abs(x)<=46 && z>=-51 && z<=-26)return true;
        double ripple=Math.sin(x*.24)*.025+Math.sin(z*.31)*.02;
        return x*x/(58.0*58)+(z+6.0)*(z+6)/(57.0*57)<=1+ripple;
    }
    public static int floor(int z){
        if(z>=42)return 1;
        if(z>=35)return 3;
        if(z>=26)return 4;
        if(z>=-15)return 5;
        if(z>=-23)return 5+(-15-z);
        return 13;
    }
    private void put(int x,int y,int z,BlockState block){
        if(x<MIN || x>MAX || z<MIN || z>MAX || y<0 || y>=HEIGHT)return;
        cells.computeIfAbsent((x-MIN)*120+z-MIN,k->new TreeMap<>()).put(y,block);
    }
    private void block(int x,int y,int z,Block b){put(x,y,z,b.getDefaultState());}
    private void box(int x0,int y0,int z0,int x1,int y1,int z1,BlockState b){
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)for(int y=y0;y<=y1;y++)put(x,y,z,b);
    }
    private void stairs(int x,int y,int z,Block b,Direction facing){put(x,y,z,b.getDefaultState().with(Properties.HORIZONTAL_FACING,facing));}
    private void lamp(int x,int y,int z){
        block(x,y,z,Blocks.CHISELED_STONE_BRICKS);block(x,y+1,z,Blocks.STONE_BRICK_WALL);
        block(x,y+2,z,Blocks.SHROOMLIGHT);block(x,y+3,z,Blocks.DEEPSLATE_TILE_SLAB);
        for(Direction d:Direction.Type.HORIZONTAL)put(x+d.getOffsetX(),y+2,z+d.getOffsetZ(),Blocks.DARK_OAK_TRAPDOOR.getDefaultState().with(Properties.HORIZONTAL_FACING,d).with(Properties.OPEN,true));
    }
    private void pillar(int x,int y,int z,int height){
        block(x,y,z,Blocks.CHISELED_POLISHED_BLACKSTONE);
        box(x,y+1,z,x,y+height,z,Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState());put(x,y+height+1,z,RED);
    }
    private void roof(int cx,int y,int cz,int halfX,int halfZ,boolean ruined){
        // Solid paired slopes, rising ridge, and double lifted eaves; all walkable real stair blocks.
        for(int x=-halfX;x<=halfX;x++)for(int z=-halfZ;z<=halfZ;z++){
            if(ruined && x>halfX/3 && z<-halfZ/3 && (x*7-z*3)%9!=0)continue;
            int rise=(halfZ-Math.abs(z))/2;
            int up=Math.abs(x)>halfX-3?1:0;
            if(Math.abs(z)>=halfZ-1)up++;
            int yy=y+rise+up;
            stairs(cx+x,yy,cz+z,Blocks.DEEPSLATE_TILE_STAIRS,z<=0?Direction.SOUTH:Direction.NORTH);
            if(Math.abs(z)==halfZ || Math.abs(x)==halfX)put(cx+x,yy-1,cz+z,WOOD);
            if(z==0)block(cx+x,yy+1,cz,Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        }
        for(int side:new int[]{-1,1}){
            int xx=cx+side*halfX;
            for(int j=0;j<3;j++)block(xx-side*j,y+halfZ/2+2+j/2,cz,Blocks.GILDED_BLACKSTONE);
            block(xx,y+halfZ/2+3,cz,Blocks.LIGHTNING_ROD);
        }
    }
    private void hall(int cx,int y,int cz,int hx,int hz,boolean ruin){
        box(cx-hx,y,cz-hz,cx+hx,y,cz+hz,WOOD);
        box(cx-hx+1,y+1,cz-hz+1,cx+hx-1,y+8,cz+hz-1,Blocks.AIR.getDefaultState());
        for(int dx=-hx;dx<=hx;dx+=Math.max(1,hx/2))for(int dz:new int[]{-hz,hz}){
            if(!(ruin && dx>0 && dz<0))pillar(cx+dx,y+1,cz+dz,7);
        }
        for(int dz=-hz;dz<=hz;dz+=Math.max(1,hz))for(int dx:new int[]{-hx,hx})pillar(cx+dx,y+1,cz+dz,7);
        for(int x=-hx;x<=hx;x++)for(int z=-hz;z<=hz;z++){
            if(Math.abs(x)!=hx && Math.abs(z)!=hz)continue;
            if(Math.abs(x)<=2 && z==hz)continue;
            for(int h=1;h<=5;h++){
                if(ruin && x>0 && z<0 && h>2)continue;
                put(cx+x,y+h,cz+z,h==1 || h==5?RED:h%2==0?WOOD:Blocks.DARK_OAK_FENCE.getDefaultState());
            }
        }
        box(cx-hx,y+8,cz-hz,cx+hx,y+8,cz-hz,RED);
        box(cx-hx,y+8,cz+hz,cx+hx,y+8,cz+hz,RED);
        roof(cx,y+9,cz,hx+3,hz+3,ruin);
        for(int dx:new int[]{-hx+1,hx-1})block(cx+dx,y+6,cz+hz+1,Blocks.LANTERN);
        // Roofed side balconies with rail openings toward the central approach.
        for(int dx:new int[]{-hx-1,hx+1})for(int z=-hz;z<=hz;z++)block(cx+dx,y+1,cz+z,Blocks.DARK_OAK_FENCE);
    }
    private void torii(int cx,int y,int cz,int half){
        for(int s:new int[]{-1,1}){
            int x=cx+s*half;box(x-1,y,cz-1,x+1,y+1,cz+1,DARK);
            box(x,y+2,cz,x,y+11,cz,RED);
            box(x-1,y+8,cz-1,x+1,y+9,cz+1,RED);
        }
        box(cx-half-2,y+8,cz,cx+half+2,y+8,cz,RED);
        box(cx-half-3,y+12,cz-1,cx+half+3,y+12,cz+1,RED);
        for(int x=-half-4;x<=half+4;x++){
            int rise=Math.abs(x)>half+1?1:0;
            block(cx+x,y+13+rise,cz,Blocks.DEEPSLATE_TILE_SLAB);
        }
        box(cx-1,y+8,cz,cx+1,y+11,cz,Blocks.WARPED_PLANKS.getDefaultState());
        block(cx,y+10,cz+1,Blocks.SEA_LANTERN);
        block(cx,y+9,cz+1,Blocks.LIGHT_BLUE_STAINED_GLASS);
    }
    private void crystal(int x,int y,int z,int height){
        box(x-1,y,z-1,x+1,y+1,z+1,DARK);
        for(int i=2;i<height;i++){
            block(x,y+i,z,Blocks.SEA_LANTERN);block(x+1,y+i,z,Blocks.CYAN_STAINED_GLASS);
            if(i<height-2)block(x-1,y+i,z,Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        block(x,y+height,z,Blocks.LIGHT_BLUE_STAINED_GLASS);
    }
    private void altar(int x,int y,int z,boolean main){
        int r=main?5:3;
        box(x-r-1,y,z-r-1,x+r+1,y,z+r+1,DARK);
        box(x-r,y+1,z-r,x+r,y+1,z+r,SILVER);
        box(x-2,y+2,z-2,x+2,y+2,z+2,TILE);
        put(x,y+3,z,LIGHT);put(x,y+4,z,GLASS);
        for(int dx:new int[]{-r,r})for(int dz:new int[]{-r,r}){
            pillar(x+dx,y+1,z+dz,3);block(x+dx,y+5,z+dz,Blocks.SOUL_LANTERN);
        }
        for(int i=-r;i<=r;i++){put(x+i,y+1,z,LIGHT);put(x,y+1,z+i,LIGHT);}
        if(!main)roof(x,y+7,z,r+2,r+1,false);
    }
    private void statue(int x,int y,int z,boolean broken){
        box(x-4,y,z-3,x+4,y+1,z+3,DARK);
        box(x-3,y+2,z-2,x-1,y+6,z+2,SILVER);box(x+1,y+2,z-2,x+3,y+6,z+2,SILVER);
        box(x-3,y+7,z-2,x+3,y+12,z+2,DARK);
        for(int h:new int[]{8,10,12})box(x-4,y+h,z-3,x+4,y+h,z+3,SILVER);
        box(x-5,y+10,z-2,x-4,y+12,z+2,DARK);
        if(!broken)box(x+4,y+9,z-2,x+5,y+12,z+2,DARK);
        box(x-2,y+13,z-2,x+2,y+16,z+2,DARK);
        box(x-1,y+14,z+3,x+1,y+15,z+3,Blocks.SMOOTH_QUARTZ.getDefaultState());
        block(x-1,y+15,z+4,Blocks.SEA_LANTERN);block(x+1,y+15,z+4,Blocks.SEA_LANTERN);
        box(x-3,y+16,z-3,x+3,y+16,z+3,SILVER);
        block(x-3,y+17,z,Blocks.QUARTZ_STAIRS);
        if(!broken)block(x+3,y+17,z,Blocks.QUARTZ_STAIRS);
        box(x-6,y+2,z+1,x-6,y+16,z+1,Blocks.POLISHED_BASALT.getDefaultState());
        box(x-6,y+16,z+1,x-6,y+20,z+1,SILVER);
        if(broken){box(x+5,y+2,z+3,x+7,y+3,z+5,SILVER);block(x+6,y+4,z+4,Blocks.COBBLESTONE_SLAB);}
    }
    private void cherry(int x,int y,int z,int height){
        box(x,y,z,x,y+height,z,Blocks.CHERRY_LOG.getDefaultState());
        for(int s:new int[]{-1,1}){
            box(Math.min(x,x+s*2),y+height-2,z,Math.max(x,x+s*2),y+height-2,z,Blocks.CHERRY_LOG.getDefaultState());
            block(x+s*2,y+height-1,z,Blocks.CHERRY_LOG);
        }
        for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=0;dy<3;dy++){
            if(dx*dx+dz*dz>19-dy*3 || (dx*17+dz*13+dy*7)%11==0)continue;
            put(x+dx,y+height+dy,z+dz,Blocks.CHERRY_LEAVES.getDefaultState().with(Properties.PERSISTENT,true));
        }
        for(int i=0;i<8;i++)block(x+(i*7%9)-4,y,z+(i*5%9)-4,Blocks.PINK_PETALS);
    }
    private List<Column> build(){
        for(int x=MIN;x<=MAX;x++)for(int z=MIN;z<=MAX;z++)if(footprint(x,z)){
            int f=floor(z),hash=Math.floorMod(x*7349+z*9151,31);
            for(int y=0;y<f;y++)put(x,y,z,y<f-2?DARK:STONE);
            BlockState paving=hash<3?Blocks.CRACKED_STONE_BRICKS.getDefaultState():hash<5?Blocks.MOSSY_STONE_BRICKS.getDefaultState():STONE;
            if(Math.abs(x)<5)paving=hash%3==0?SILVER:TILE;
            put(x,f,z,paving);
            if(z<42 && (!footprint(x+1,z) || !footprint(x-1,z) || !footprint(x,z+1) || !footprint(x,z-1))){
                block(x,f+1,z,Blocks.COBBLED_DEEPSLATE_WALL);
                if(hash<4)crystal(x,f+1,z,4+hash);
            }
        }
        // Continuous six-block-wide processional stairs between each terrace, never across the arena.
        for(int z=-23;z<=-16;z++)for(int x=-5;x<=5;x++)stairs(x,floor(z),z,Blocks.STONE_BRICK_STAIRS,Direction.NORTH);
        for(int z:new int[]{26,35,42})for(int x=-5;x<=5;x++)stairs(x,floor(z)+1,z,Blocks.STONE_BRICK_STAIRS,Direction.NORTH);
        // Entrance bridge: exposed dark beams, illuminated balustrades, and stone piers below the deck.
        for(int z=42;z<=59;z++)for(int x:new int[]{-5,5}){
            block(x,2,z,Blocks.DARK_OAK_FENCE);put(x,0,z,WOOD);
            if(z%8==2)lamp(x,2,z);
        }
        torii(0,4,36,7);
        for(int z:new int[]{29,39})for(int x:new int[]{-7,7})lamp(x,floor(z)+1,z);
        // Forty-one block circle: unbroken fighting floor and recessed luminous thunder sigil.
        for(int x=-20;x<=20;x++)for(int dz=-20;dz<=20;dz++){
            double r=Math.sqrt(x*x+dz*dz);if(r>20.5)continue;int z=5+dz;
            BlockState b=r>19?SILVER:r>17.7?DARK:TILE;
            boolean rune=(r>13.9 && r<14.7) || (Math.abs(x)<=1 && Math.abs(dz)<=11)
                || (Math.abs(dz)<=1 && Math.abs(x)<=11) || (Math.abs(x-dz/2)<=1 && dz>=-9 && dz<=9);
            if(rune){b=GLASS;put(x,4,z,LIGHT);}
            put(x,5,z,b);
            // Reset floor inlay only, leaving the arena obstacle-free.
            if(rune && Math.abs(x)%4==0)put(x,5,z,LIGHT);
        }
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4;int x=(int)Math.round(Math.sin(a)*22),z=5+(int)Math.round(Math.cos(a)*22);
            if(Math.abs(x)<7)continue;
            pillar(x,6,z,5);block(x,12,z,Blocks.SOUL_LANTERN);
        }
        statue(-29,6,19,false);statue(29,6,19,true);
        hall(0,14,-38,21,11,false);
        hall(0,31,-39,14,8,false);
        hall(0,46,-40,7,5,false);
        for(int dx:new int[]{-14,14})for(int dz:new int[]{-47,-31})pillar(dx,22,dz,8);
        for(int dx:new int[]{-7,7})for(int dz:new int[]{-45,-35})pillar(dx,37,dz,8);
        // Two switchback flights reach the upper sanctuary through explicit roof/floor openings.
        for(int step=0;step<17;step++){
            int xx=step<9?-17:-12,zz=step<9?-31-step:-39+(step-9),yy=15+step;
            for(int dx=0;dx<3;dx++){
                box(xx+dx,yy+1,zz,xx+dx,yy+3,zz,Blocks.AIR.getDefaultState());
                stairs(xx+dx,yy,zz,Blocks.DARK_OAK_STAIRS,step<9?Direction.NORTH:Direction.SOUTH);
            }
        }
        box(-17,23,-40,-10,23,-40,WOOD);
        // A second internal switchback opens into the third, smaller shrine storey.
        for(int step=0;step<15;step++){
            int xx=step<8?9:3,zz=step<8?-33-step:-40+(step-8),yy=32+step;
            for(int dx=0;dx<3;dx++){
                box(xx+dx,yy+1,zz,xx+dx,yy+3,zz,Blocks.AIR.getDefaultState());
                stairs(xx+dx,yy,zz,Blocks.DARK_OAK_STAIRS,step<8?Direction.NORTH:Direction.SOUTH);
            }
        }
        box(3,39,-41,11,39,-41,WOOD);
        box(3,46,-35,5,50,-35,Blocks.AIR.getDefaultState());
        // Broken southwest eave; the central sanctuary and its route stay intact.
        box(-24,23,-25,-19,28,-24,Blocks.AIR.getDefaultState());
        box(-25,14,-24,-22,15,-23,TILE);
        // Stairs into the main hall, central doorway and a future quest / sanctuary space.
        for(int z=-25;z<=-23;z++)for(int x=-5;x<=5;x++)stairs(x,14,z,Blocks.STONE_BRICK_STAIRS,Direction.NORTH);
        box(-3,15,-27,3,21,-26,Blocks.AIR.getDefaultState());
        altar(0,15,-42,true);
        altar(-40,6,-1,false);altar(40,6,-1,false);
        hall(-34,14,-34,8,7,true);hall(34,14,-34,8,7,true);
        hall(-32,5,34,7,5,true);hall(32,5,34,7,5,true);
        // Garden paths, side courtyards, shrines and ruined colonnades.
        for(int side:new int[]{-1,1}){
            for(int z=-17;z<=28;z++)for(int dx=25;dx<=27;dx++)put(side*dx,5,z,SILVER);
            for(int z:new int[]{-17,-7,7,27})lamp(side*25,6,z);
            for(int z:new int[]{-12,11,29})cherry(side*43,floor(z)+1,z,5+(z&1));
            for(int z:new int[]{-47,-18,21})if(footprint(side*49,z))crystal(side*49,floor(z)+1,z,6);
            for(int z=0;z<5;z++){
                int x=side*(34+z),zz=25+z;
                box(Math.min(x,x+side*2),5,zz,Math.max(x,x+side*2),6,zz+2,Blocks.MOSSY_COBBLESTONE.getDefaultState());
                if(z%2==0)block(x,7,zz,Blocks.COBBLESTONE_SLAB);
            }
            // Reserved NPC plinth facing the entrance court, deliberately no spawned NPCs yet.
            box(side*13-2,4,30,side*13+2,4,34,WOOD);
        }
        // Keep public routes clear through terrace rails and the central torii opening.
        box(-4,5,35,4,11,36,Blocks.AIR.getDefaultState());
        // One floor-level boss socket, surrounded by the thunder inlay, no automatic boss spawning.
        block(0,5,5,Blocks.CHISELED_POLISHED_BLACKSTONE);
        List<Column> result=new ArrayList<>();
        for(var entry:new TreeMap<>(cells).entrySet()){
            int x=entry.getKey()/120+MIN,z=entry.getKey()%120+MIN;
            List<Voxel> blocks=entry.getValue().entrySet().stream().map(e->new Voxel(e.getKey(),e.getValue())).toList();
            boolean support=footprint(x,z) && (z<42 || z%8==2 || z==59);
            result.add(new Column(x,z,floor(z),support,blocks));
        }
        return List.copyOf(result);
    }
    public static BlockState foundation(int x,int y,int z){
        int h=Math.floorMod(x*31+y*17+z*13,19);
        return (h<2?Blocks.MOSSY_COBBLESTONE:h<6?Blocks.TUFF:h<9?Blocks.COBBLED_DEEPSLATE:Blocks.STONE).getDefaultState();
    }
    private TempleBlueprint(){}
}
