package dev.animerpg.combo;

import java.util.List;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Boss-specific telegraphs: driven by tracked action/start time, purely visual. */
public final class RaizenEffects {
    private static final class Glow extends RenderLayer {
        static final List<RenderPhase> PHASES=List.of(LIGHTNING_PROGRAM,LIGHTNING_TRANSPARENCY,LEQUAL_DEPTH_TEST,DISABLE_CULLING,COLOR_MASK);
        static final RenderLayer INSTANCE=new Glow();
        Glow(){super("raizen_storm",VertexFormats.POSITION_COLOR,VertexFormat.DrawMode.QUADS,8192,false,true,
            ()->PHASES.forEach(RenderPhase::startDrawing),()->PHASES.forEach(RenderPhase::endDrawing));}
    }
    public static void render(MatrixStack matrices,VertexConsumerProvider vertices,RaizenBoss boss,float delta){
        if(boss.isInvisible() || !boss.isAlive())return;
        int type=boss.action();float age=boss.attackAge(delta);float clock=boss.age+delta;
        if(type==0 && !boss.phaseTwo())return;
        VertexConsumer out=vertices.getBuffer(Glow.INSTANCE);Matrix4f m=matrices.peek().getPositionMatrix();
        int cyan=0x47cfff,white=0xe4fbff;
        if(type==RaizenMotion.HORIZONTAL || type==RaizenMotion.DASH || type==RaizenMotion.SLAM){
            float start=type==RaizenMotion.HORIZONTAL?12:type==RaizenMotion.DASH?10:20;
            float end=RaizenMotion.hit(type)+3,head=Math.min(end,age),tail=Math.max(start,age-5);
            if(head>tail){
                float fade=1-smooth((age-end)/5);
                for(int i=0;i<36;i++){
                    float a=tail+(head-tail)*i/36,b=tail+(head-tail)*(i+1)/36;
                    Vector3f ai=RaizenMotion.weaponPoint(type,a,RaizenMotion.TIP_DISTANCE-11),ao=RaizenMotion.weaponPoint(type,a,RaizenMotion.TIP_DISTANCE);
                    Vector3f bi=RaizenMotion.weaponPoint(type,b,RaizenMotion.TIP_DISTANCE-11),bo=RaizenMotion.weaponPoint(type,b,RaizenMotion.TIP_DISTANCE);
                    float alpha=fade*(i+1)/36f;
                    quad(out,m,ai,bi,bo,ao,cyan,alpha*.38f);
                    line(out,m,ao,bo,.027f,white,alpha*.9f);
                    line(out,m,ao,bo,.075f,cyan,alpha*.18f);
                }
            }
        }
        if(type==RaizenMotion.SLAM && age>=24 && age<37){
            float t=(age-24)/13;
            Vector3f p=RaizenMotion.weaponPoint(RaizenMotion.SLAM,24,RaizenMotion.TIP_DISTANCE);p.y=1.485f;
            ring(out,m,p,.2f+t*2.8f,.035f,cyan,(1-t)*.85f);
            ring(out,m,p,.2f+t*2.8f,.10f,cyan,(1-t)*.15f);
            for(int i=0;i<12;i++){
                float a=i*6.283185f/12;
                Vector3f q=new Vector3f(p).add((float)Math.sin(a)*t*2.0f,-.15f*(1-t),(float)Math.cos(a)*t*2.0f);
                line(out,m,q,new Vector3f(q).add(0,-.20f*(1-t),0),.014f,white,(1-t)*.8f);
            }
        }
        if(type==RaizenMotion.DASH && age>6 && age<18){
            float fade=1-smooth((age-14)/4);
            for(int i=0;i<4;i++)line(out,m,new Vector3f((i-1.5f)*.28f,-.6f+i*.15f,.2f),
                new Vector3f((i-1.5f)*.28f,-.6f+i*.15f,1.8f),.014f,cyan,fade*.45f);
        }
        boolean transition=type==RaizenMotion.TRANSITION;
        if(transition || boss.phaseTwo()){
            float energy=transition?smooth(age/28):.38f;
            if(transition && age>26)ring(out,m,new Vector3f(0,1.48f,0),.5f+(age-26)*.11f,.045f,cyan,(1-smooth((age-40)/10))*.8f);
            for(int i=0;i<(transition?9:4);i++){
                float angle=i*2.39996f+clock*.014f;
                float radius=transition?1.0f:.9f;
                Vector3f previous=null;
                for(int j=0;j<6;j++){
                    float wobble=(float)Math.sin(i*9.1+j*7.3+clock*.24f)*.13f;
                    Vector3f p=new Vector3f((float)Math.cos(angle)*(radius+wobble),1.1f-j*.53f,(float)Math.sin(angle)*(radius+wobble));
                    if(previous!=null){line(out,m,previous,p,.015f,white,energy*.85f);line(out,m,previous,p,.055f,cyan,energy*.18f);}
                    previous=p;
                }
            }
        }
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static void ring(VertexConsumer out,Matrix4f m,Vector3f center,float radius,float width,int color,float alpha){
        for(int i=0;i<64;i++){
            float a=i*6.283185f/64,b=(i+1)*6.283185f/64;
            quad(out,m,new Vector3f(center).add((float)Math.cos(a)*(radius-width),0,(float)Math.sin(a)*(radius-width)),
                new Vector3f(center).add((float)Math.cos(b)*(radius-width),0,(float)Math.sin(b)*(radius-width)),
                new Vector3f(center).add((float)Math.cos(b)*(radius+width),0,(float)Math.sin(b)*(radius+width)),
                new Vector3f(center).add((float)Math.cos(a)*(radius+width),0,(float)Math.sin(a)*(radius+width)),color,alpha);
        }
    }
    private static void line(VertexConsumer out,Matrix4f m,Vector3f a,Vector3f b,float width,int color,float alpha){
        Vector3f direction=new Vector3f(b).sub(a),side=new Vector3f(direction).cross(new Vector3f(0,0,1));
        if(side.lengthSquared()<.000001f)side.set(width,0,0);else side.normalize().mul(width);
        quad(out,m,new Vector3f(a).sub(side),new Vector3f(b).sub(side),new Vector3f(b).add(side),new Vector3f(a).add(side),color,alpha);
    }
    private static void quad(VertexConsumer out,Matrix4f m,Vector3f a,Vector3f b,Vector3f c,Vector3f d,int color,float alpha){
        vertex(out,m,a,color,alpha);vertex(out,m,b,color,alpha);vertex(out,m,c,color,alpha);vertex(out,m,d,color,alpha);
    }
    private static void vertex(VertexConsumer out,Matrix4f m,Vector3f p,int color,float alpha){
        out.vertex(m,p.x,p.y,p.z).color((color>>16)&255,(color>>8)&255,color&255,(int)(255*Math.max(0,Math.min(1,alpha))));
    }
    private RaizenEffects(){}
}
