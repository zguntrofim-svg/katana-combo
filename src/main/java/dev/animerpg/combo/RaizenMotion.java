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
        if(type<1 || type>4 || age<0)return new Pose(new float[]{-1,-6,-11,65,33,9,2,0,0,.21f,0});
        Clip clip=DATA.clips[type-1];float[][] frames=clip.frames;
        int segment=0;while(segment<frames.length-2 && age>frames[segment+1][0])segment++;
        float[] a=frames[segment],b=frames[segment+1];float span=b[0]-a[0];
        float t=Math.max(0,Math.min(1,(age-a[0])/span));float[] v=new float[11];
        for(int channel=0;channel<11;channel++){
            int j=channel+1;
            float m0=segment==0?0:(b[j]-frames[segment-1][j])/(b[0]-frames[segment-1][0]);
            float m1=segment==frames.length-2?0:(frames[segment+2][j]-a[j])/(frames[segment+2][0]-a[0]);
            v[channel]=(2*t*t*t-3*t*t+1)*a[j]+(t*t*t-2*t*t+t)*span*m0+(-2*t*t*t+3*t*t)*b[j]+(t*t*t-t*t)*span*m1;
        }
        if(type==SLAM){
            Pose raw=new Pose(v);Quaternionf body=raw.body();
            Vector3f tip=raw.center().fma(46.5f,raw.axis()).rotate(body).add(0,v[8],0);
            float pulse=smooth((age-19)/4)*(1-smooth((age-29)/8));
            Vector3f correction=new Vector3f(0,-Math.max(0,tip.y-23.7f),-8*pulse).rotate(body.conjugate());
            v[0]+=correction.x;v[1]+=correction.y;v[2]+=correction.z;
        }
        return new Pose(v);
    }
    public static Quaternionf weaponRotation(int type,float age,Vector3f axis,Quaternionf body){
        Vector3f guard=new Vector3f(.76f,-.54f,-.36f).normalize();
        Vector3f normal=new Vector3f(0,0,1).rotate(new Quaternionf().rotationTo(new Vector3f(0,1,0),guard));
        float blend=type==0?0:smooth(age/6)*(1-smooth((age-duration(type)+6)/6));
        Vector3f cutPlane=type==HORIZONTAL?new Vector3f(0,-1,0):new Vector3f(1,0,0);
        if(type==TRANSITION)blend=0;
        normal.lerp(cutPlane,blend).rotate(body);
        normal.fma(-normal.dot(axis),axis);
        if(normal.lengthSquared()<.00001f)normal=new Vector3f(0,0,1).rotate(body).fma(-new Vector3f(0,0,1).rotate(body).dot(axis),axis);
        normal.normalize();
        Vector3f x=new Vector3f(axis).cross(normal).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x).setColumn(1,axis).setColumn(2,normal));
    }
    public static Vector3f weaponPoint(int type,float age,float distance){
        Pose p=sample(type,age);Quaternionf body=p.body();
        return p.center().rotate(body).fma(distance,p.axis().rotate(body)).add(0,p.v[8],0).mul(1/16f);
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float rad(float degrees){return degrees*(float)Math.PI/180;}
    private RaizenMotion(){}
}
