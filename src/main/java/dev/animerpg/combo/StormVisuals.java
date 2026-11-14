package dev.animerpg.combo;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Depth-tested animated energy meshes: feathered crescents, runic shockwaves and branched sky bolts. */
public final class StormVisuals {
    private static final int BLUE=0x198fff,CYAN=0x49dfff,WHITE=0xe9fdff;
    private static final class Glow extends RenderLayer {
        static final List<RenderPhase> PHASES=List.of(LIGHTNING_PROGRAM,LIGHTNING_TRANSPARENCY,LEQUAL_DEPTH_TEST,DISABLE_CULLING,COLOR_MASK);
        static final RenderLayer INSTANCE=new Glow();
        Glow(){super("storm_shogun_techniques",VertexFormats.POSITION_COLOR,VertexFormat.DrawMode.QUADS,32768,false,true,
            ()->PHASES.forEach(RenderPhase::startDrawing),()->PHASES.forEach(RenderPhase::endDrawing));}
    }
    public static void init(){
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            if(StormClient.ACTIVE.isEmpty() || context.matrixStack()==null || context.consumers()==null)return;
            var eye=context.camera().getPos();var out=context.consumers().getBuffer(Glow.INSTANCE);var matrix=context.matrixStack().peek().getPositionMatrix();
            float delta=context.tickCounter().getTickDelta(false);
            Vector3f cameraRight=new Vector3f(1,0,0).rotate(context.camera().getRotation());
            for(var state:StormClient.ACTIVE.values()){
                if(!state.dimension().equals(context.world().getRegistryKey().getValue()))continue;
                double dx=state.x()-eye.x,dy=state.y()-eye.y,dz=state.z()-eye.z;
                if(dx*dx+dy*dy+dz*dz>160*160)continue;
                float age=context.world().getTime()-state.started()+delta;
                if(age<0 || age>StormCombat.DURATION[state.ability()]+16)continue;
                Painter p=new Painter(out,matrix,new Vector3f((float)dx,(float)dy,(float)dz));
                switch(state.ability()){
                    case 0->arc(p,state,age);
                    case 1->slam(p,age);
                    case 2->judgment(p,age,cameraRight);
                    default->{}
                }
            }
        });
    }
    private static void arc(Painter p,StormPackets.State s,float age){
        float cut=age-12;if(cut<0 || cut>19)return;
        float yaw=(float)Math.toRadians(s.yaw());Vector3f forward=new Vector3f(-(float)Math.sin(yaw),0,(float)Math.cos(yaw));
        Vector3f right=new Vector3f(forward.z,0,-forward.x);
        float fade=1-smooth((cut-13)/6);
        for(int echo=3;echo>=0;echo--){
            float travel=Math.min(14,Math.max(0,cut-echo*.65f));
            Vector3f center=new Vector3f(0,1.1f,0).fma(travel,forward);
            float alpha=fade*(echo==0?1:.12f/(echo*.6f));
            for(int layer=6;layer>=0;layer--){
                float width=.025f+layer*.075f;int color=layer==0?WHITE:layer<3?CYAN:BLUE;
                float opacity=alpha*(layer==0?.95f:layer<3?.2f:.055f);
                for(int i=0;i<72;i++){
                    float a=-2.05f+i*4.1f/72,b=-2.05f+(i+1)*4.1f/72;
                    float taper=(float)Math.pow(Math.sin(Math.PI*(i+.5)/72),.6);
                    Vector3f aa=crescent(center,right,forward,a,1-width*taper),ab=crescent(center,right,forward,b,1-width*taper);
                    Vector3f ba=crescent(center,right,forward,a,1+width*taper),bb=crescent(center,right,forward,b,1+width*taper);
                    p.quad(aa,ab,bb,ba,color,opacity*taper);
                }
            }
            if(echo==0)for(int i=0;i<9;i++){
                float a=-1.8f+i*.45f;Vector3f tip=crescent(center,right,forward,a,1);
                Vector3f end=new Vector3f(tip).fma(.55f+(i%3)*.2f,right).add(0,(i%2==0?1:-1)*.5f,0);
                p.bolt(tip,end,5,.17f,i+17,alpha*.85f);
            }
        }
    }
    private static Vector3f crescent(Vector3f center,Vector3f right,Vector3f forward,float angle,float radius){
        return new Vector3f(center).fma((float)Math.sin(angle)*3.5f*radius,right)
            .add(0,(float)Math.cos(angle)*1.65f*radius,0).fma((float)Math.cos(angle)*.65f,forward);
    }
    private static void slam(Painter p,float age){
        if(age<18){
            float charge=smooth(age/18);p.ring(new Vector3f(0,.035f,0),1.1f+charge*.35f,.025f,CYAN,charge*.65f,0,6.283185f);
            p.star(new Vector3f(0,.04f,0),1.0f,charge*.55f);return;
        }
        float t=age-18;if(t>25)return;float radius=Math.min(8,.7f+t*.62f),fade=1-smooth(t/25);
        p.ring(new Vector3f(0,.045f,0),radius,.045f,WHITE,fade,0,6.283185f);
        p.ring(new Vector3f(0,.04f,0),radius,.18f,CYAN,fade*.45f,0,6.283185f);
        p.ring(new Vector3f(0,.03f,0),radius,.48f,BLUE,fade*.12f,0,6.283185f);
        p.ring(new Vector3f(0,.055f,0),radius*.72f,.025f,CYAN,fade*.7f,0,6.283185f);
        p.star(new Vector3f(0,.055f,0),Math.min(3.2f,radius*.7f),fade*.9f);
        for(int i=0;i<14;i++){
            float a=i*6.283185f/14;
            Vector3f edge=new Vector3f((float)Math.cos(a)*radius,.08f,(float)Math.sin(a)*radius);
            p.bolt(new Vector3f(0,.1f,0),edge,7,.17f,i+5,fade*.65f);
            if(t<10){
                float height=(1-t/10)*(1.8f+i%4*.7f);
                p.ray(edge,new Vector3f(edge).add((float)Math.cos(a)*.25f,height,(float)Math.sin(a)*.25f),.10f,fade*.8f);
            }
        }
        if(t<9){p.bolt(new Vector3f(0,.1f,0),new Vector3f(0,6*(1-t/12),0),9,.3f,71,fade);}
        p.sparks(t,24,5,fade);
    }
    private static void judgment(Painter p,float age,Vector3f right){
        float charge=smooth(age/10),fade=age<50?1:1-smooth((age-50)/24);
        float ring=1.05f+.05f*(float)Math.sin(age*.22f);
        p.ring(new Vector3f(0,.04f,0),ring,.025f,WHITE,charge*fade,0,6.283185f);
        p.ring(new Vector3f(0,.035f,0),ring,.12f,CYAN,charge*fade*.3f,0,6.283185f);
        p.star(new Vector3f(0,.055f,0),1.3f,charge*fade*.7f);
        if(age<50){
            Vector3f mark=new Vector3f(0,1.5f,0);
            p.rune(mark,right,charge*(.8f+.2f*(float)Math.sin(age*.15f)));
            if(age>12){
                float energy=smooth((age-12)/30);
                for(int i=0;i<6;i++){
                    float a=i*1.0471975f+age*.02f;
                    Vector3f from=new Vector3f((float)Math.cos(a)*2,8+energy*5,(float)Math.sin(a)*2);
                    Vector3f to=new Vector3f((float)Math.cos(a)*.7f,3.5f,(float)Math.sin(a)*.7f);
                    p.bolt(from,to,7,.18f,i+20,energy*.25f);
                }
                p.ring(new Vector3f(0,11.5f,0),1.5f+energy,.035f,CYAN,energy*.8f,0,6.283185f);
            }
            return;
        }
        float t=age-50,burst=1-smooth(t/18);
        if(t<13){
            p.bolt(new Vector3f(.3f,18,0),new Vector3f(0,.05f,0),18,.55f,33,burst);
            for(int i=0;i<5;i++){
                float a=i*1.256637f;
                p.bolt(new Vector3f(.2f,9+i,0),new Vector3f((float)Math.cos(a)*3,2.5f+i%3,(float)Math.sin(a)*3),9,.3f,40+i,burst*.85f);
            }
            p.ray(new Vector3f(0,.1f,0),new Vector3f(0,11,0),.22f,burst*.7f);
        }
        float radius=Math.min(5,.5f+t*.45f);
        p.ring(new Vector3f(0,.07f,0),radius,.06f,WHITE,fade,0,6.283185f);
        p.ring(new Vector3f(0,.065f,0),radius,.35f,CYAN,fade*.3f,0,6.283185f);
        for(int i=0;i<10;i++){
            float a=i*.6283185f;
            p.bolt(new Vector3f(0,.1f,0),new Vector3f((float)Math.cos(a)*radius,.1f,(float)Math.sin(a)*radius),6,.2f,91+i,fade*.8f);
        }
        p.sparks(t,32,5,fade);
    }
    private static float smooth(float f){f=Math.max(0,Math.min(1,f));return f*f*(3-2*f);}
    private static final class Painter {
        final VertexConsumer out;final Matrix4f matrix;final Vector3f origin;
        Painter(VertexConsumer out,Matrix4f matrix,Vector3f origin){this.out=out;this.matrix=matrix;this.origin=origin;}
        void vertex(Vector3f p,int color,float alpha){out.vertex(matrix,p.x+origin.x,p.y+origin.y,p.z+origin.z).color((color>>16)&255,(color>>8)&255,color&255,(int)(255*Math.max(0,Math.min(1,alpha))));}
        void quad(Vector3f a,Vector3f b,Vector3f c,Vector3f d,int color,float alpha){vertex(a,color,alpha);vertex(b,color,alpha);vertex(c,color,alpha);vertex(d,color,alpha);}
        void line(Vector3f a,Vector3f b,float width,int color,float alpha){
            Vector3f dir=new Vector3f(b).sub(a);if(dir.lengthSquared()<.000001f)return;
            Vector3f side=new Vector3f(dir).cross(new Vector3f(0,1,0));
            if(side.lengthSquared()<.00001f)side.set(1,0,0);side.normalize().mul(width);
            Vector3f other=new Vector3f(dir).cross(side).normalize().mul(width);
            quad(new Vector3f(a).sub(side),new Vector3f(b).sub(side),new Vector3f(b).add(side),new Vector3f(a).add(side),color,alpha);
            quad(new Vector3f(a).sub(other),new Vector3f(b).sub(other),new Vector3f(b).add(other),new Vector3f(a).add(other),color,alpha*.65f);
        }
        void ray(Vector3f a,Vector3f b,float width,float alpha){
            line(a,b,width*4,BLUE,alpha*.08f);line(a,b,width*2,CYAN,alpha*.22f);line(a,b,width,WHITE,alpha);
        }
        void bolt(Vector3f from,Vector3f to,int steps,float jitter,int seed,float alpha){
            Vector3f previous=new Vector3f(from);
            for(int j=1;j<=steps;j++){
                float f=(float)j/steps,taper=(float)Math.sin(Math.PI*f);
                Vector3f next=new Vector3f(from).lerp(to,f).add((float)Math.sin(seed*9+j*7)*jitter*taper,
                    (float)Math.sin(seed*4+j*13)*jitter*taper,(float)Math.cos(seed*6+j*11)*jitter*taper);
                ray(previous,next,.018f,alpha);previous=next;
            }
        }
        void ring(Vector3f center,float radius,float width,int color,float alpha,float start,float end){
            for(int i=0;i<96;i++){
                float a=start+(end-start)*i/96,b=start+(end-start)*(i+1)/96;
                quad(new Vector3f(center).add((float)Math.cos(a)*(radius-width),0,(float)Math.sin(a)*(radius-width)),
                    new Vector3f(center).add((float)Math.cos(b)*(radius-width),0,(float)Math.sin(b)*(radius-width)),
                    new Vector3f(center).add((float)Math.cos(b)*(radius+width),0,(float)Math.sin(b)*(radius+width)),
                    new Vector3f(center).add((float)Math.cos(a)*(radius+width),0,(float)Math.sin(a)*(radius+width)),color,alpha);
            }
        }
        void star(Vector3f center,float radius,float alpha){
            for(int i=0;i<8;i++){
                float a=i*.785398f;
                ray(new Vector3f(center).add((float)Math.cos(a)*radius*.35f,0,(float)Math.sin(a)*radius*.35f),
                    new Vector3f(center).add((float)Math.cos(a)*radius,0,(float)Math.sin(a)*radius),.025f,alpha);
            }
        }
        void rune(Vector3f center,Vector3f right,float alpha){
            Vector3f up=new Vector3f(0,1,0);Vector3f[] diamond=new Vector3f[5];
            for(int i=0;i<5;i++){float a=i*1.570796f;diamond[i]=new Vector3f(center).fma((float)Math.cos(a)*.72f,right).fma((float)Math.sin(a)*.72f,up);}
            for(int i=0;i<4;i++)ray(diamond[i],diamond[i+1],.018f,alpha);
            float[][] strokes={{-.33f,.3f,.33f,.3f},{0,.45f,0,.1f},{-.30f,.05f,.30f,.05f},{-.3f,.05f,-.3f,-.33f},{.3f,.05f,.3f,-.33f},{-.3f,-.15f,.3f,-.15f},{-.3f,-.33f,.3f,-.33f},{0,.05f,0,-.33f}};
            for(float[] l:strokes)ray(new Vector3f(center).fma(l[0],right).fma(l[1],up),new Vector3f(center).fma(l[2],right).fma(l[3],up),.017f,alpha);
        }
        void sparks(float age,int count,float radius,float alpha){
            for(int i=0;i<count;i++){
                float a=i*2.399963f,speed=.05f+(i%6)*.025f,dist=Math.min(radius,age*speed);
                float h=.1f+age*(.06f+i%4*.025f)-age*age*.0035f;if(h<0)continue;
                Vector3f p=new Vector3f((float)Math.cos(a)*dist,h,(float)Math.sin(a)*dist);
                ray(p,new Vector3f(p).add(0,.09f,0),.025f,alpha);
            }
        }
    }
    private StormVisuals(){}
}
