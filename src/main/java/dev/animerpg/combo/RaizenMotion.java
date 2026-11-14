package dev.animerpg.combo;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.joml.Matrix3f;
/** Shared animation timing; changing the weapon pose drives both grip sockets automatically. */
public final class RaizenMotion {
    public static final int IDLE=0,HORIZONTAL=1,SLAM=2,DASH=3,TRANSITION=4;
    public static final float REAR_GRIP=3,FRONT_GRIP=13,GRIP_CENTER=8,TIP_DISTANCE=55.05f;
    private record Clip(String name,int duration,int hit,float[][] frames){}
    private record File(int idlePeriod,Clip[] clips){}
    private static final File DATA;
    static{
        try(var stream=RaizenMotion.class.getResourceAsStream("/assets/katana_combo/animations/raizen.json")){
            if(stream==null)throw new IllegalStateException("Missing Raizen animations");
            DATA=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),File.class);
        }catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}
    }
    public record Pose(float[] v){
        public Vector3f center(){return new Vector3f(v[0],v[1],v[2]);}
        public Vector3f axis(){float y=rad(v[3]),e=rad(v[4]);return new Vector3f((float)(Math.sin(y)*Math.cos(e)),-(float)Math.sin(e),-(float)(Math.cos(y)*Math.cos(e)));}
        public Quaternionf body(){return new Quaternionf().rotationZYX(rad(v[7]),rad(v[5]),rad(v[6]));}
    }
    public static int duration(int type){return type>0&&type<=4?DATA.clips[type-1].duration:0;}
    public static int hit(int type){return type>0&&type<=4?DATA.clips[type-1].hit:-1;}
    public static Pose sample(int type,float age){
        if(type<1 || type>4 || age<0)return constrain(new float[]{-1,-6,-14,65,33,9,2,0,0,.21f,0},IDLE);
        Clip clip=DATA.clips[type-1];float[][] frames=clip.frames;
        int segment=0;while(segment<frames.length-2 && age>frames[segment+1][0])segment++;
        float[] a=frames[segment],b=frames[segment+1];float span=b[0]-a[0];
        float t=Math.max(0,Math.min(1,(age-a[0])/span));float[] v=new float[11];
        // Monotone Hermite: continuous velocity through a cut, zero tangent only at an intentional reversal.
        for(int channel=0;channel<11;channel++){
            int j=channel+1;
            float m0=tangent(frames,segment,j),m1=tangent(frames,segment+1,j);
            float value=(2*t*t*t-3*t*t+1)*a[j]+(t*t*t-2*t*t+t)*span*m0
                +(-2*t*t*t+3*t*t)*b[j]+(t*t*t-t*t)*span*m1;
            v[channel]=Math.max(Math.min(a[j],b[j]),Math.min(Math.max(a[j],b[j]),value));
        }
        return constrain(v,type);
    }
    private static float tangent(float[][] frames,int index,int channel){
        if(index==0 || index==frames.length-1)return 0;
        float[] previous=frames[index-1],current=frames[index],next=frames[index+1];
        float left=current[0]-previous[0],right=next[0]-current[0];
        float a=(current[channel]-previous[channel])/left,b=(next[channel]-current[channel])/right;
        if(a*b<=0)return 0;
        float wa=2*right+left,wb=right+2*left;
        return (wa+wb)/(wa/a+wb/b);
    }
    private static float positive(float value,float softness){return .5f*(value+(float)Math.sqrt(value*value+softness*softness));}
    private static Pose constrain(float[] v,int type){
        Pose pose=new Pose(v);Vector3f axis=pose.axis();
        float frontal=smooth((Math.abs(axis.z)-.70f)/.28f)*(1-smooth((Math.abs(axis.y)-.25f)/.30f));
        v[1]+=(Math.min(v[1],-12)-v[1])*frontal;
        for(int i=0;i<4;i++){
            clearBody(v,axis);
            if(type==SLAM){
                Pose p=new Pose(v);Quaternionf body=p.body();
                Vector3f tip=p.center().fma(TIP_DISTANCE,axis).rotate(body).add(0,v[8],0);
                Vector3f correction=new Vector3f(0,-positive(tip.y-23.7f,.12f),0).rotate(body.conjugate());
                v[0]+=correction.x;v[1]+=correction.y;v[2]+=correction.z;
            }
        }
        clearBody(v,axis);
        return new Pose(v);
    }
    private static void clearBody(float[] center,Vector3f axis){
        // Finite pole, including butt cap and blade; model-space proxies cover cuirass, waist and mask.
        float[][] boxes={{-10,10,-19,9},{-7,7,-33,-14}};
        for(float[] box:boxes){
            float near=-GRIP_CENTER-2.3f,far=TIP_DISTANCE;
            for(int component=0;component<2;component++){
                float direction=component==0?axis.x:axis.y,origin=center[component];
                float min=box[component*2],max=box[component*2+1];
                if(Math.abs(direction)<.00001f){if(origin<min || origin>max){far=near-1;break;}}
                else{
                    float a=(min-origin)/direction,b=(max-origin)/direction;
                    near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));
                }
            }
            if(near<=far){
                float nearestBack=Math.max(center[2]+near*axis.z,center[2]+far*axis.z);
                center[2]-=positive(nearestBack+9.8f,.10f);
            }
        }
    }
    public static Quaternionf weaponRotation(int type,float age,Pose pose,Quaternionf body){
        // Idle and slam share one axial orientation; there is no quarter-turn during the vertical cut.
        float roll=90;
        if(type==HORIZONTAL || type==DASH)
            roll+=90*smooth(age/6)*(1-smooth((age-duration(type)+6)/6));
        // Explicit yaw, elevation and shaft roll remain continuous through the overhead Y-axis singularity.
        return new Quaternionf(body).rotateY(-rad(pose.v[3]))
            .rotateX(-(float)Math.PI/2-rad(pose.v[4])).rotateY(rad(roll));
    }
    public static Vector3f weaponPoint(int type,float age,float distance){
        Pose p=sample(type,age);Quaternionf body=p.body();
        return p.center().rotate(body).fma(distance,p.axis().rotate(body)).add(0,p.v[8],0).mul(1/16f);
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float rad(float degrees){return degrees*(float)Math.PI/180;}
    private RaizenMotion(){}
}
