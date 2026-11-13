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
    private static final int[][] COLORS={{55,130,235},{65,155,245},{100,200,255},{175,235,255},{240,252,255},{175,230,255}};
    private static final Matrix4f IDENTITY=new Matrix4f();
    public static void render(LivingEntity player,ComboAnimation.Pose current,Matrix4f root,
                              VertexConsumerProvider vertices,boolean camera){
        if(player.isInvisible() || current.step()<0)return;
        var state=ComboClient.animationState(player.getUuid());if(state==null)return;
        float age=current.age(),start=ComboTiming.HIT_START[state.step()]-0.5f;
        float end=ComboTiming.HIT_END[state.step()]+0.2f,lifetime=4.8f;
        float head=Math.min(age,end),tail=Math.max(start,age-lifetime);
        if(head-tail<0.025f)return;
        float birth=smooth((age-start)/0.9f),fade=1-smooth((age-end)/lifetime);
        float alpha=birth*fade*(camera?0.78f:0.90f);
        float sign=player.getMainArm()==Arm.LEFT?-1:1;
        int count=48;
        Vector3f[] outer=new Vector3f[count+1],inside=new Vector3f[count+1];
        float[] opacity=new float[count+1],widths=new float[count+1];
        for(int i=0;i<=count;i++){
            float u=(float)i/count;
            var pose=ComboAnimation.sampleAge(state.step(),MathHelper.lerp(u,tail,head));
            if(pose==null)return;
            // A pointed leading edge, gradually widening body, and a thin fading tail.
            float envelope=(float)Math.pow(Math.max(0,Math.sin(Math.PI*u)),0.65);
            float width=(camera?0.32f:0.42f)*envelope;
            outer[i]=root.transformPosition(point(pose,1.08f,sign,camera));
            inside[i]=root.transformPosition(point(pose,1.08f-width,sign,camera));
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
        for(int i=0;i<count;i++)for(int row=0;row<ROWS.length-1;row++){
            vertex(buffer,inside[i],outer[i],ROWS[row],COLORS[row],opacity[i]*OPACITY[row]);
            vertex(buffer,inside[i+1],outer[i+1],ROWS[row],COLORS[row],opacity[i+1]*OPACITY[row]);
            vertex(buffer,inside[i+1],outer[i+1],ROWS[row+1],COLORS[row+1],opacity[i+1]*OPACITY[row+1]);
            vertex(buffer,inside[i],outer[i],ROWS[row+1],COLORS[row+1],opacity[i]*OPACITY[row+1]);
        }
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
