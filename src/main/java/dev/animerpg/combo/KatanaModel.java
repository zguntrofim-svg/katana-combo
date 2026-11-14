package dev.animerpg.combo;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

/** Native baked quads: curved bevels and faceted details without a renderer or animation library. */
public final class KatanaModel implements BakedModel {
    private record Face(float[][] p,int tile,String group){}
    // The model loader discovers every models/*.json: custom geometry must live in a valid object.
    private record Mesh(Face[] millida_mesh){}
    private final BakedModel base;
    private final boolean storm;
    private final List<BakedQuad> quads;
    public static void init(){
        ModelLoadingPlugin.register(context->context.modifyModelAfterBake().register((model,bake)->{
            if(model!=null && !(model instanceof KatanaModel)
                    && (Identifier.of("katana_combo","item/kurotsuki").equals(bake.resourceId())
                    || (bake.topLevelId()!=null && Identifier.of("katana_combo","kurotsuki").equals(bake.topLevelId().id()))))return new KatanaModel(model);
            if(model!=null && !(model instanceof KatanaModel)
                && (Identifier.of("katana_combo","item/amakiri").equals(bake.resourceId())
                || (bake.topLevelId()!=null && Identifier.of("katana_combo","amakiri").equals(bake.topLevelId().id()))))
                return new KatanaModel(model,"/assets/katana_combo/models/item/amakiri_mesh.json",false);
            return model;
        }));
    }
    private KatanaModel(BakedModel base){this(base,"/assets/katana_combo/models/item/kurotsuki_mesh.json",true);}
    private KatanaModel(BakedModel base,String resource,boolean reverseEdge){
        this.base=base;storm=resource.endsWith("amakiri_mesh.json");
        List<BakedQuad> result=new ArrayList<>();
        try(var stream=KatanaModel.class.getResourceAsStream(resource)){
            if(stream==null)throw new IllegalStateException("Missing mesh "+resource);
            Face[] faces=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Mesh.class).millida_mesh();
            for(Face face:faces){
                // Half-turn around the canonical hilt-to-tip diagonal, not around the hand or camera.
                // Keep points on that axis fixed: only the cutting edge / spine and front / back swap.
                if(reverseEdge)for(float[] point:face.p){float x=point[0];point[0]=point[1];point[1]=x;point[2]=16-point[2];}
                result.add(bake(face,false));result.add(bake(face,true));
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Unable to load Kurotsuki mesh",e);}
        quads=List.copyOf(result);
    }
    private BakedQuad bake(Face face,boolean reverse){
        Sprite sprite=base.getParticleSprite();int[] data=new int[32];
        Vector3f a=new Vector3f(face.p[1]).sub(new Vector3f(face.p[0]));
        Vector3f b=new Vector3f(face.p[2]).sub(new Vector3f(face.p[0]));
        Vector3f normal=a.cross(b);
        if(normal.lengthSquared()<0.000001f)normal.set(0,0,1);else normal.normalize();
        if(reverse)normal.negate();
        int packed=((int)(normal.x*127)&255)|(((int)(normal.y*127)&255)<<8)|(((int)(normal.z*127)&255)<<16);
        for(int i=0;i<4;i++){
            int j=reverse?3-i:i,offset=i*8;
            for(int axis=0;axis<3;axis++)data[offset+axis]=Float.floatToRawIntBits(face.p[j][axis]/16);
            data[offset+3]=-1;
            float u=(face.tile*16+(j==0||j==3?1:15))/128f;
            float v=(j<2?1:15)/16f;
            data[offset+4]=Float.floatToRawIntBits(sprite.getFrameU(u));
            data[offset+5]=Float.floatToRawIntBits(sprite.getFrameV(v));
            data[offset+6]=storm && (face.tile==3 || face.tile==4)?15728880:0;data[offset+7]=packed;
        }
        return new BakedQuad(data,-1,Direction.getFacing(normal.x,normal.y,normal.z),sprite,face.tile!=3 && face.tile!=4);
    }
    public List<BakedQuad> getQuads(BlockState state,Direction face,Random random){return face==null?quads:List.of();}
    public boolean useAmbientOcclusion(){return false;}
    public boolean hasDepth(){return true;}
    public boolean isSideLit(){return false;}
    public boolean isBuiltin(){return false;}
    public Sprite getParticleSprite(){return base.getParticleSprite();}
    public ModelTransformation getTransformation(){return base.getTransformation();}
    public ModelOverrideList getOverrides(){return base.getOverrides();}
}
