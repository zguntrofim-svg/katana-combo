package dev.animerpg.combo;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Animated world-space burst meshes. Rings, crescents, shards and forks are not vanilla particles. */
public final class ImpactRenderer {
    private record Burst(HitImpact event,long born){}
    private static final Deque<Burst> ACTIVE=new ArrayDeque<>();
    private static final class Layer extends RenderLayer {
        static final List<RenderPhase> BODY_PHASES=List.of(LIGHTNING_PROGRAM,TRANSLUCENT_TRANSPARENCY,LEQUAL_DEPTH_TEST,DISABLE_CULLING,COLOR_MASK);
        static final List<RenderPhase> GLOW_PHASES=List.of(LIGHTNING_PROGRAM,LIGHTNING_TRANSPARENCY,LEQUAL_DEPTH_TEST,DISABLE_CULLING,COLOR_MASK);
        static final RenderLayer BODY=new Layer(false),GLOW=new Layer(true);
        private Layer(boolean glow){super(glow?"katana_impact_glow":"katana_impact_body",VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,16384,false,true,()->(glow?GLOW_PHASES:BODY_PHASES).forEach(RenderPhase::startDrawing),
            ()->(glow?GLOW_PHASES:BODY_PHASES).forEach(RenderPhase::endDrawing));}
    }
    public static void init(){
        ClientPlayNetworking.registerGlobalReceiver(HitImpact.ID,(packet,context)->{
            var world=context.client().world;
            if(world==null || !world.getRegistryKey().getValue().equals(packet.dimension())
                    || packet.style()<0 || packet.style()>=EffectStyle.values().length
                    || !Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z()))return;
            if(ACTIVE.size()>=256)ACTIVE.removeFirst();
            ACTIVE.addLast(new Burst(packet,world.getTime()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->ACTIVE.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.world==null){ACTIVE.clear();return;}
            long now=client.world.getTime();
            ACTIVE.removeIf(b->!b.event.dimension().equals(client.world.getRegistryKey().getValue()) || now-b.born>lifetime(b.event));
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            if(ACTIVE.isEmpty() || context.matrixStack()==null || context.consumers()==null)return;
            var camera=context.camera();var eye=camera.getPos();
            Vector3f right=new Vector3f(1,0,0).rotate(camera.getRotation());
            Vector3f up=new Vector3f(0,1,0).rotate(camera.getRotation());
            Matrix4f matrix=context.matrixStack().peek().getPositionMatrix();
            double now=context.world().getTime()+context.tickCounter().getTickDelta(false);
            for(boolean glow:new boolean[]{false,true}){
                VertexConsumer out=context.consumers().getBuffer(glow?Layer.GLOW:Layer.BODY);
                for(Burst burst:ACTIVE){
                    HitImpact p=burst.event;float age=(float)(now-burst.born);
                    if(age<0 || age>=lifetime(p) || !context.world().getRegistryKey().getValue().equals(p.dimension()))continue;
                    double dx=p.x()-eye.x,dy=p.y()-eye.y,dz=p.z()-eye.z;
                    if(dx*dx+dy*dy+dz*dz>96*96)continue;
                    Vector3f center=new Vector3f((float)dx+p.nx()*0.012f,(float)dy+p.ny()*0.012f,(float)dz+p.nz()*0.012f);
                    Painter painter=new Painter(out,matrix,center,right,up,p.finisher()?1.55f:1f);
                    render(painter,p,age,glow);
                }
            }
        });
    }
    private static float lifetime(HitImpact p){return (p.style()==4?20:14)+(p.finisher()?4:0);}
    private static void render(Painter v,HitImpact p,float age,boolean glow){
        EffectStyle style=EffectStyle.get(p.style());float life=lifetime(p),t=age/life;
        float envelope=(1-smooth(t))*MathHelper.clamp(age/0.6f,0,1);
        float growth=1-(float)Math.pow(1-Math.min(1,t*1.8f),3);
        float radius=0.10f+growth*0.62f;
        int color=style.color,edge=style.edge;
        float phase=(p.seed()&1023)/1023f*6.283185f;
        if(glow){
            v.star(0,0,0.18f*(1-t),4,phase,edge,envelope*0.90f);
            v.star(0,0,0.33f*(1-t),8,phase,color,envelope*0.18f);
        }
        switch(style){
            case MOON->{
                if(glow){
                    v.arc(radius,0.055f,0,6.283185f,0,color,envelope*0.4f);
                    v.arc(radius*0.82f,0.045f,0.65f,5.2f,-0.12f,edge,envelope*0.85f);
                    v.star(0,0,radius*0.70f,8,phase,edge,envelope*0.7f);
                    if(p.finisher())v.arc(radius*1.18f,0.025f,0,6.283185f,0,color,envelope*0.55f);
                }else v.disk(radius,48,0,color,envelope*0.12f);
            }
            case CRIMSON->{
                float reach=radius*1.15f;
                for(int i=0;i<4;i++){
                    float angle=(float)Math.PI/4+i*(float)Math.PI/2;
                    v.ray(angle,reach,glow?0.024f:0.12f,glow?edge:color,envelope*(glow?0.95f:0.6f));
                    if(glow)v.ray(angle,reach*1.15f,0.07f,color,envelope*0.18f);
                }
                if(glow)v.star(0,0,radius*.6f,12,phase,color,envelope*.55f);
            }
            case VOID->{
                if(!glow)v.disk(radius*0.72f,36,phase,0x160a26,envelope*0.70f);
                else {
                    for(int i=0;i<7;i++)v.arc(radius*(0.78f+0.10f*(float)Math.sin(i*3.2+phase)),0.026f,
                        phase+i*0.897f,phase+i*0.897f+0.65f,0,color,envelope*0.9f);
                    v.arc(radius*1.09f,0.075f,phase,phase+4.6f,0,color,envelope*.23f);
                    for(int i=0;i<8;i++){
                        float a=phase+i*0.785398f;
                        v.line((float)Math.cos(a)*radius*.4f,(float)Math.sin(a)*radius*.4f,
                            (float)Math.cos(a+.18)*radius,(float)Math.sin(a+.18)*radius,0.014f,edge,envelope*.65f);
                    }
                }
            }
            case AZURE->{
                if(glow){v.star(0,0,radius,8,phase*.15f,edge,envelope*.95f);v.arc(radius*.75f,0.02f,0,6.283185f,0,color,envelope*.65f);}
                else v.star(0,0,radius*1.08f,8,phase*.15f,color,envelope*.35f);
            }
            case PHANTOM->{
                int echoes=p.finisher()?4:3;
                for(int i=0;i<echoes;i++){
                    float local=age-i*3;if(local<0 || local>11)continue;
                    float f=(1-local/11)*(1-local/11),r=0.13f+local*.048f;
                    v.star((i-1)*.045f,0,r,8,phase+i*.16f,glow?edge:color,f*(glow?.76f:.22f));
                    if(glow)v.arc(r*1.2f,0.02f,phase,phase+4.8f,0,color,f*.45f);
                }
            }
            case STORM->{
                if(glow){
                    for(int i=0;i<8;i++){
                        float a=phase+i*0.785398f;
                        float x=0,y=0;
                        for(int j=1;j<=4;j++){
                            float r=radius*j/4,side=(float)Math.sin(i*7+j*9+phase)*radius*.17f;
                            float xx=(float)Math.cos(a)*r-(float)Math.sin(a)*side,yy=(float)Math.sin(a)*r+(float)Math.cos(a)*side;
                            v.line(x,y,xx,yy,.055f,color,envelope*.2f);v.line(x,y,xx,yy,.012f,edge,envelope*.95f);
                            if(j==3)v.line(xx,yy,xx+(float)Math.cos(a+.8f)*radius*.35f,yy+(float)Math.sin(a+.8f)*radius*.35f,.009f,edge,envelope*.6f);
                            x=xx;y=yy;
                        }
                    }
                    v.arc(radius*.55f,0.02f,0,6.283185f,0,color,envelope*.75f);
                }else v.star(0,0,radius*.65f,10,phase,color,envelope*.25f);
            }
        }
        int count=p.finisher()?16:9;
        for(int i=0;i<count;i++){
            float angle=phase+i*2.399963f;
            float speed=0.025f+(i%5)*.009f,travel=age*speed;
            float x=(float)Math.cos(angle)*travel,y=(float)Math.sin(angle)*travel-age*age*.0006f;
            float size=(0.018f+(i%3)*.007f)*(1-t);
            if(glow){v.star(x,y,size,4,angle,edge,envelope*.8f);v.star(x,y,size*2.4f,4,angle,color,envelope*.13f);}
            else if(style==EffectStyle.VOID)v.star(x,y,size*2,3,angle,0x261037,envelope*.8f);
        }
    }
    private static float smooth(float t){t=MathHelper.clamp(t,0,1);return t*t*(3-2*t);}
    private static final class Painter {
        final VertexConsumer out;final Matrix4f matrix;final Vector3f center,right,up;final float scale;
        Painter(VertexConsumer out,Matrix4f matrix,Vector3f center,Vector3f right,Vector3f up,float scale){this.out=out;this.matrix=matrix;this.center=center;this.right=right;this.up=up;this.scale=scale;}
        void vertex(float x,float y,int color,float alpha){
            Vector3f point=new Vector3f(center).fma(x*scale,right).fma(y*scale,up);
            out.vertex(matrix,point.x,point.y,point.z).color((color>>16)&255,(color>>8)&255,color&255,(int)(255*MathHelper.clamp(alpha,0,1)));
        }
        void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy,int color,float alpha){vertex(ax,ay,color,alpha);vertex(bx,by,color,alpha);vertex(cx,cy,color,alpha);vertex(dx,dy,color,alpha);}
        void line(float x,float y,float xx,float yy,float width,int color,float alpha){
            float dx=xx-x,dy=yy-y,len=(float)Math.sqrt(dx*dx+dy*dy);if(len<.00001f)return;
            float sx=-dy/len*width,sy=dx/len*width;
            quad(x-sx,y-sy,xx-sx,yy-sy,xx+sx,yy+sy,x+sx,y+sy,color,alpha);
        }
        void ray(float angle,float length,float width,int color,float alpha){
            float x=(float)Math.cos(angle)*length,y=(float)Math.sin(angle)*length;
            float sx=-(float)Math.sin(angle)*width,sy=(float)Math.cos(angle)*width;
            quad(sx,sy,x,y,-sx,-sy,0,0,color,alpha);
        }
        void star(float x,float y,float radius,int points,float angle,int color,float alpha){
            for(int i=0;i<points;i++){
                float a=angle+i*6.283185f/points,b=a+3.141592f/points,c=a+6.283185f/points;
                float inner=radius*.20f;
                quad(x,y,x+(float)Math.cos(a)*inner,y+(float)Math.sin(a)*inner,
                    x+(float)Math.cos(b)*radius,y+(float)Math.sin(b)*radius,
                    x+(float)Math.cos(c)*inner,y+(float)Math.sin(c)*inner,color,alpha);
            }
        }
        void disk(float radius,int segments,float phase,int color,float alpha){
            for(int i=0;i<segments;i++){
                float a=phase+i*6.283185f/segments,b=phase+(i+1)*6.283185f/segments;
                quad(0,0,(float)Math.cos(a)*radius,(float)Math.sin(a)*radius,(float)Math.cos(b)*radius,(float)Math.sin(b)*radius,0,0,color,alpha);
            }
        }
        void arc(float radius,float width,float from,float to,float offsetX,int color,float alpha){
            int n=Math.max(8,(int)((to-from)*10));
            for(int i=0;i<n;i++){
                float a=from+(to-from)*i/n,b=from+(to-from)*(i+1)/n;
                float feather=(float)Math.sin(Math.PI*(i+.5)/n);
                quad(offsetX+(float)Math.cos(a)*(radius-width),(float)Math.sin(a)*(radius-width),
                    offsetX+(float)Math.cos(b)*(radius-width),(float)Math.sin(b)*(radius-width),
                    offsetX+(float)Math.cos(b)*(radius+width),(float)Math.sin(b)*(radius+width),
                    offsetX+(float)Math.cos(a)*(radius+width),(float)Math.sin(a)*(radius+width),color,alpha*(.4f+.6f*feather));
            }
        }
    }
    private ImpactRenderer(){}
}
