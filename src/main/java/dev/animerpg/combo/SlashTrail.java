package dev.animerpg.combo;

import java.util.List;
import net.minecraft.client.render.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A single connected, feathered ribbon following the blade tip; no overlapping hard-edged strips. */
public final class SlashTrail {
    private static final class TrailLayer extends RenderLayer {
        private static final List<RenderPhase> PHASES=List.of(LIGHTNING_PROGRAM,TRANSLUCENT_TRANSPARENCY,
            LEQUAL_DEPTH_TEST,DISABLE_CULLING,COLOR_MASK);
        private static final RenderLayer INSTANCE=new TrailLayer();
        private TrailLayer(){super("katana_combo_trail",VertexFormats.POSITION_COLOR,VertexFormat.DrawMode.QUADS,
            8192,false,true,()->PHASES.forEach(RenderPhase::startDrawing),()->PHASES.forEach(RenderPhase::endDrawing));}
    }
    private static final float[] ROWS={0,0.2f,0.6f,0.84f,0.93f,1};
    private static final float[] OPACITY={0,0.20f,0.48f,0.80f,0.92f,0};
    private static final Matrix4f IDENTITY=new Matrix4f();
    public static void render(LivingEntity player,ComboAnimation.Pose current,Matrix4f root,
                              VertexConsumerProvider vertices,boolean camera){
        if(player.isInvisible() || current.step()<0)return;
        var state=ComboClient.animationState(player.getUuid());if(state==null)return;
        EffectStyle style=EffectClient.forPlayer(player.getUuid());
        if(style==EffectStyle.PHANTOM){
            renderPass(player,current,root,vertices,camera,style,current.age()-2.6f,0.20f,0.16f);
            renderPass(player,current,root,vertices,camera,style,current.age()-1.3f,0.32f,0.08f);
        }
        renderPass(player,current,root,vertices,camera,style,current.age(),1,0);
    }
    private static void renderPass(LivingEntity player,ComboAnimation.Pose current,Matrix4f root,
                                   VertexConsumerProvider vertices,boolean camera,EffectStyle style,float age,float strength,float offset){
        var state=ComboClient.animationState(player.getUuid());if(state==null)return;
        float start=ComboTiming.HIT_START[state.step()]-0.5f;
        float end=ComboTiming.HIT_END[state.step()]+0.2f,lifetime=style.lifetime;
        float head=Math.min(age,end),tail=Math.max(start,age-lifetime);
        if(head-tail<0.025f)return;
        float birth=smooth((age-start)/0.9f),fade=1-smooth((age-end)/lifetime);
        float alpha=birth*fade*(camera?0.78f:0.90f)*strength;
        float sign=player.getMainArm()==Arm.LEFT?-1:1;
        int count=48;
        float tipRadius=player.getMainHandStack().getItem()==KatanaItems.KUROTSUKI?1.92f:1.08f;
        Vector3f[] outer=new Vector3f[count+1],inside=new Vector3f[count+1];
        float[] opacity=new float[count+1],widths=new float[count+1];
        for(int i=0;i<=count;i++){
            float u=(float)i/count;
            var pose=ComboAnimation.sampleAge(state.step(),MathHelper.lerp(u,tail,head));
            if(pose==null)return;
            // A pointed leading edge, gradually widening body, and a thin fading tail.
            float envelope=(float)Math.pow(Math.max(0,Math.sin(Math.PI*u)),0.65);
            float width=(camera?0.32f:0.42f)*envelope*style.width;
            if(style==EffectStyle.VOID)width*=0.83f+0.17f*(float)Math.sin(u*19-age*0.45f);
            outer[i]=root.transformPosition(point(pose,tipRadius+offset,sign,camera));
            inside[i]=root.transformPosition(point(pose,tipRadius+offset-width,sign,camera));
            widths[i]=width*(camera?0.78f:1);
            opacity[i]=alpha*smooth(u/0.7f);
        }
        Vector3f previousSide=null;
        for(int i=0;i<=count;i++){
            Vector3f tangent=new Vector3f(outer[Math.min(count,i+1)]).sub(outer[Math.max(0,i-1)]);
            Vector3f view=new Vector3f(outer[i]).negate();
            if(tangent.lengthSquared()<0.00000001f || view.lengthSquared()<0.00000001f)continue;
            view.normalize();
            Vector3f side=tangent.cross(view);
            if(side.lengthSquared()<0.00000001f)continue;
            side.normalize();
            Vector3f inward=new Vector3f(inside[i]).sub(outer[i]);
            if(previousSide!=null?side.dot(previousSide)<0:side.dot(inward)<0)side.negate();
            previousSide=new Vector3f(side);
            // Broaden only when viewed edge-on; keep a continuous orientation across adjacent segments.
            float projected=new Vector3f(inward).fma(-inward.dot(view),view).length();
            float readable=widths[i]*0.45f;
            float face=readable>0.00001f?smooth((readable-projected)/readable):0;
            inside[i]=new Vector3f(outer[i]).add(inward.mul(1-face)).fma(readable*face,side);
        }
        VertexConsumer buffer=vertices.getBuffer(TrailLayer.INSTANCE);
        ribbon(buffer,inside,outer,opacity,style,1);
        if(style==EffectStyle.CRIMSON){
            Vector3f center=new Vector3f(outer[count/2]);
            Vector3f[] crossOuter=new Vector3f[count+1],crossInner=new Vector3f[count+1];
            for(int i=0;i<=count;i++){
                crossOuter[i]=new Vector3f(outer[i]).sub(center).rotateZ(1.25f).add(center);
                crossInner[i]=new Vector3f(inside[i]).sub(center).rotateZ(1.25f).add(center);
            }
            ribbon(buffer,crossInner,crossOuter,opacity,style,0.62f);
        }
        if(style==EffectStyle.STORM){
            Vector3f previous=null;
            for(int i=4;i<count-2;i+=2){
                Vector3f p=new Vector3f(outer[i]);
                float jitter=(float)Math.sin(i*2.17+state.started()%99)*(camera?0.055f:0.09f);
                p.add(jitter,(float)Math.cos(i*1.71)*(camera?0.04f:0.07f),0);
                if(previous!=null)beam(buffer,previous,p,camera?0.008f:0.012f,0xc5e7ff,alpha*0.85f);
                previous=p;
            }
        }
        if(style==EffectStyle.MOON || style==EffectStyle.VOID){
            for(int i=8;i<count-3;i+=11){
                Vector3f p=new Vector3f(outer[i]).add((float)Math.sin(i)*0.07f,(age-start)*0.012f,0);
                diamond(buffer,p,camera?0.017f:0.025f,style==EffectStyle.MOON?style.edge:0x36114e,opacity[i]*0.7f);
            }
        }
    }
    private static void ribbon(VertexConsumer buffer,Vector3f[] inside,Vector3f[] outer,float[] opacity,EffectStyle style,float strength){
        for(int i=0;i<outer.length-1;i++)for(int row=0;row<ROWS.length-1;row++){
            int[] low=palette(style,ROWS[row]),high=palette(style,ROWS[row+1]);
            vertex(buffer,inside[i],outer[i],ROWS[row],low,opacity[i]*OPACITY[row]*strength);
            vertex(buffer,inside[i+1],outer[i+1],ROWS[row],low,opacity[i+1]*OPACITY[row]*strength);
            vertex(buffer,inside[i+1],outer[i+1],ROWS[row+1],high,opacity[i+1]*OPACITY[row+1]*strength);
            vertex(buffer,inside[i],outer[i],ROWS[row+1],high,opacity[i]*OPACITY[row+1]*strength);
        }
    }
    private static int[] palette(EffectStyle style,float t){
        float light=smooth((t-0.2f)/0.75f);
        int c=style.color,e=style.edge;
        return new int[]{(int)MathHelper.lerp(light,(c>>16)&255,(e>>16)&255),
            (int)MathHelper.lerp(light,(c>>8)&255,(e>>8)&255),(int)MathHelper.lerp(light,c&255,e&255)};
    }
    private static void beam(VertexConsumer out,Vector3f a,Vector3f b,float width,int rgb,float alpha){
        Vector3f side=new Vector3f(b).sub(a).cross(new Vector3f(a).negate());
        if(side.lengthSquared()<0.0000001f)return;side.normalize().mul(width);
        colored(out,new Vector3f(a).sub(side),rgb,alpha);colored(out,new Vector3f(b).sub(side),rgb,alpha);
        colored(out,new Vector3f(b).add(side),rgb,alpha);colored(out,new Vector3f(a).add(side),rgb,alpha);
    }
    private static void diamond(VertexConsumer out,Vector3f p,float size,int rgb,float alpha){
        colored(out,new Vector3f(p).add(-size,0,0),rgb,alpha);colored(out,new Vector3f(p).add(0,size*1.6f,0),rgb,alpha);
        colored(out,new Vector3f(p).add(size,0,0),rgb,alpha);colored(out,new Vector3f(p).add(0,-size*1.6f,0),rgb,alpha);
    }
    private static void colored(VertexConsumer out,Vector3f p,int rgb,float alpha){
        out.vertex(IDENTITY,p.x,p.y,p.z).color((rgb>>16)&255,(rgb>>8)&255,rgb&255,(int)(255*MathHelper.clamp(alpha,0,1)));
    }
    private static float smooth(float t){t=MathHelper.clamp(t,0,1);return t*t*(3-2*t);}
    private static Vector3f point(ComboAnimation.Pose pose,float radius,float sign,boolean camera){
        Vector3f grip=camera?AnimationMatrices.cameraGrip(pose):pose.grip(),dir=pose.blade();
        if(camera){
            Vector3f p=new Vector3f(-grip.x*sign/22,-0.3f-grip.y/32,-0.75f)
                .fma(radius*0.78f,new Vector3f(-dir.x*sign,-dir.y,dir.z));
            return AnimationMatrices.cameraMotion(pose,sign).transformPosition(p);
        }
        return new Vector3f(grip.x*sign/16,grip.y/16,grip.z/16).fma(radius,new Vector3f(dir.x*sign,dir.y,dir.z));
    }
    private static void vertex(VertexConsumer out,Vector3f inside,Vector3f outside,float row,int[] rgb,float alpha){
        Vector3f p=new Vector3f(inside).lerp(outside,row);
        out.vertex(IDENTITY,p.x,p.y,p.z).color(rgb[0],rgb[1],rgb[2],(int)(255*MathHelper.clamp(alpha,0,1)));
    }
    private SlashTrail(){}
}
