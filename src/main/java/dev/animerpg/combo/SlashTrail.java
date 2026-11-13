package dev.animerpg.combo;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import java.util.List;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Analytically sampled blade ribbon: soft cyan fill, narrow white edge, fading over four ticks. */
public final class SlashTrail {
    private static final class TrailLayer extends RenderLayer {
        private static final List<RenderPhase> PHASES=List.of(LIGHTNING_PROGRAM,LIGHTNING_TRANSPARENCY,
            LEQUAL_DEPTH_TEST,DISABLE_CULLING,COLOR_MASK);
        private static final RenderLayer INSTANCE=new TrailLayer();
        private TrailLayer(){super("katana_combo_trail",VertexFormats.POSITION_COLOR,VertexFormat.DrawMode.QUADS,
            8192,false,true,()->PHASES.forEach(RenderPhase::startDrawing),()->PHASES.forEach(RenderPhase::endDrawing));}
    }
    public static void render(LivingEntity player,ComboAnimation.Pose current,Matrix4f root,
                              VertexConsumerProvider vertices,boolean camera){
        if(player.isInvisible() || current.step()<0)return;
        var state=ComboClient.animationState(player.getUuid());if(state==null)return;
        float age=current.age(),cutStart=ComboTiming.HIT_START[state.step()]-0.8f;
        float cutEnd=ComboTiming.HIT_END[state.step()]+0.5f;
        float head=Math.min(age,cutEnd),tail=Math.max(cutStart,age-6f);
        if(head<=tail)return;
        float birth=MathHelper.clamp((age-cutStart)/0.65f,0,1);
        float fade=1-MathHelper.clamp((age-cutEnd)/6,0,1);fade=fade*fade*(3-2*fade);
        float alpha=birth*fade*(camera?0.95f:1f);
        float sign=player.getMainArm()==Arm.LEFT?-1:1;
        // Built-in additive layer: depth tested, emissive vertex colors, no shader/library dependency.
        VertexConsumer buffer=vertices.getBuffer(TrailLayer.INSTANCE);
        int count=44;
        for(int i=0;i<count;i++){
            float a=(float)i/count,b=(float)(i+1)/count;
            var pa=ComboAnimation.sampleAge(state.step(),MathHelper.lerp(a,tail,head));
            var pb=ComboAnimation.sampleAge(state.step(),MathHelper.lerp(b,tail,head));
            if(pa==null||pb==null)continue;
            float aa=alpha*a,ab=alpha*b;
            // Width and intensity taper into the oldest portion of the arc.
            float innerA=1.1f-0.5f*a,innerB=1.1f-0.5f*b;
            band(buffer,root,pa,pb,innerA,innerB,1.38f,1.38f,aa,ab,0.08f,0.62f,80,195,255,sign,camera);
            band(buffer,root,pa,pb,1.38f,1.38f,1.43f,1.43f,aa,ab,0.95f,1f,235,252,255,sign,camera);
            band(buffer,root,pa,pb,1.43f,1.43f,1.54f,1.54f,aa,ab,0.38f,0,110,215,255,sign,camera);
            // The geometric cut plane can be edge-on, especially for the vertical finisher.
            // A narrow view-facing edge keeps all three trails readable without disabling scene depth.
            Vector3f tipA=root.transformPosition(point(pa,1.40f,sign,camera));
            Vector3f tipB=root.transformPosition(point(pb,1.40f,sign,camera));
            facingBand(buffer,tipA,tipB,aa,ab,camera?0.055f:0.085f,80,190,255,0.48f);
            facingBand(buffer,tipA,tipB,aa,ab,camera?0.018f:0.028f,235,252,255,0.9f);
        }
    }
    private static Vector3f point(ComboAnimation.Pose pose,float radius,float sign,boolean camera){
        Vector3f grip=pose.grip(),dir=pose.blade();
        if(camera){
            Vector3f p=new Vector3f(-grip.x*sign/22,-0.3f-grip.y/32,-0.75f)
                .fma(radius*0.78f,new Vector3f(-dir.x*sign,-dir.y,dir.z));
            return AnimationMatrices.cameraMotion(pose,sign).transformPosition(p);
        }
        return new Vector3f(grip.x*sign/16,grip.y/16,grip.z/16).fma(radius,new Vector3f(dir.x*sign,dir.y,dir.z));
    }
    private static void band(VertexConsumer out,Matrix4f root,ComboAnimation.Pose a,ComboAnimation.Pose b,
                             float innerA,float innerB,float outerA,float outerB,float aa,float ab,
                             float innerAlpha,float outerAlpha,int red,int green,int blue,float sign,boolean camera){
        vertex(out,root,point(a,innerA,sign,camera),red,green,blue,aa*innerAlpha);
        vertex(out,root,point(b,innerB,sign,camera),red,green,blue,ab*innerAlpha);
        vertex(out,root,point(b,outerB,sign,camera),red,green,blue,ab*outerAlpha);
        vertex(out,root,point(a,outerA,sign,camera),red,green,blue,aa*outerAlpha);
    }
    private static void vertex(VertexConsumer out,Matrix4f root,Vector3f p,int r,int g,int b,float alpha){
        out.vertex(root,p.x,p.y,p.z).color(r,g,b,(int)(255*MathHelper.clamp(alpha,0,1)));
    }
    private static void facingBand(VertexConsumer out,Vector3f a,Vector3f b,float aa,float ab,float width,
                                   int red,int green,int blue,float opacity){
        Vector3f tangent=new Vector3f(b).sub(a);
        Vector3f view=new Vector3f(a).add(b).mul(-0.5f);
        Vector3f side=tangent.cross(view);
        if(side.lengthSquared()<0.00000001f)return;
        side.normalize().mul(width);
        Matrix4f identity=new Matrix4f();
        vertex(out,identity,new Vector3f(a).sub(side),red,green,blue,aa*opacity);
        vertex(out,identity,new Vector3f(b).sub(side),red,green,blue,ab*opacity);
        vertex(out,identity,new Vector3f(b).add(side),red,green,blue,ab*opacity);
        vertex(out,identity,new Vector3f(a).add(side),red,green,blue,aa*opacity);
    }
    private SlashTrail(){}
}
