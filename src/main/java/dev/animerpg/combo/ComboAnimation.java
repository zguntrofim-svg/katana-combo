package dev.animerpg.combo;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Shared grip and blade keyframes, in player model coordinates (pixels, Y down, -Z forward). */
public final class ComboAnimation {
    private static final float[][][] FRAMES;
    static {
        try (var stream = ComboAnimation.class.getResourceAsStream("/assets/katana_combo/animation_keyframes.json")) {
            if (stream == null) throw new IllegalStateException("Missing combo keyframes");
            FRAMES = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), float[][][].class);
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public record Pose(float[] values, float weight, float age, Vector3f cuttingDirection) {
        public Vector3f grip() { return new Vector3f(values[0], values[1], values[2]); }
        public Vector3f blade() {
            float yaw = values[3]*(float)Math.PI/180, elevation = values[4]*(float)Math.PI/180;
            return new Vector3f(MathHelper.sin(yaw)*MathHelper.cos(elevation), -MathHelper.sin(elevation),
                -MathHelper.cos(yaw)*MathHelper.cos(elevation));
        }
        /** The blade axis alone leaves roll undefined. Map its width onto the actual cutting velocity. */
        public Quaternionf orientation(float handedness, boolean camera) {
            Vector3f axis=blade(), edge=new Vector3f(cuttingDirection);
            axis.x*=handedness; edge.x*=handedness;
            if(camera){axis.y=-axis.y;edge.y=-edge.y;}
            edge.fma(-edge.dot(axis),axis).normalize();
            Vector3f normal=new Vector3f(axis).cross(edge).normalize();
            // Generated vanilla sword textures run bottom-left to top-right in their local XY plane.
            float k=0.7071068f;
            Vector3f x=new Vector3f(axis).sub(edge).mul(k);
            Vector3f y=new Vector3f(axis).add(edge).mul(k);
            return new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,normal));
        }
    }
    public static Pose sample(LivingEntity entity, float delta) {
        var client = MinecraftClient.getInstance();
        var state = ComboClient.animationState(entity.getUuid());
        if (!(entity instanceof PlayerEntity) || state == null || client.world == null || !entity.isAlive()
                || !(entity.getMainHandStack().getItem() instanceof SwordItem) || entity.isUsingItem()
                || entity.isFallFlying() || entity.hasVehicle() || entity.isSwimming()) return null;
        int step = state.step(), duration = ComboTiming.DURATION[step];
        float age = Math.max(0, client.world.getTime() - state.started() + delta);
        if (age >= duration) return null;
        float[] times = {0, ComboTiming.HIT_START[step]-1, ComboTiming.HIT_START[step]+0.75f,
            ComboTiming.HIT_END[step]+2, duration};
        int segment = 0;
        while (segment < 3 && age > times[segment+1]) segment++;
        float span=times[segment+1]-times[segment];
        float t=MathHelper.clamp((age-times[segment])/span,0,1);
        float[] v=new float[13], velocity=new float[13];
        // Time-aware cubic Hermite keeps the cut flowing through contact instead of stopping at each keyframe.
        for(int i=0;i<v.length;i++){
            float a=FRAMES[step][segment][i],b=FRAMES[step][segment+1][i];
            float m0=segment==0?0:(b-FRAMES[step][segment-1][i])/(times[segment+1]-times[segment-1]);
            float m1=segment==3?0:(FRAMES[step][segment+2][i]-a)/(times[segment+2]-times[segment]);
            v[i]=(2*t*t*t-3*t*t+1)*a+(t*t*t-2*t*t+t)*span*m0+(-2*t*t*t+3*t*t)*b+(t*t*t-t*t)*span*m1;
            velocity[i]=((6*t*t-6*t)*a+(3*t*t-4*t+1)*span*m0+(-6*t*t+6*t)*b+(3*t*t-2*t)*span*m1)/span;
        }
        float rad=(float)Math.PI/180,yaw=v[3]*rad,elevation=v[4]*rad;
        Vector3f axis=new Vector3f(MathHelper.sin(yaw)*MathHelper.cos(elevation),-MathHelper.sin(elevation),-MathHelper.cos(yaw)*MathHelper.cos(elevation));
        Vector3f edge=new Vector3f(MathHelper.cos(yaw)*MathHelper.cos(elevation),0,MathHelper.sin(yaw)*MathHelper.cos(elevation)).mul(velocity[3]*rad);
        edge.fma(velocity[4]*rad,new Vector3f(-MathHelper.sin(yaw)*MathHelper.sin(elevation),-MathHelper.cos(elevation),MathHelper.cos(yaw)*MathHelper.sin(elevation)));
        edge.add(velocity[0]/19,velocity[1]/19,velocity[2]/19).fma(-edge.dot(axis),axis);
        if(edge.lengthSquared()<0.000001f){
            Vector3f normal=step==0?new Vector3f(0,-1,0):step==2?new Vector3f(1,0,0):new Vector3f(-0.65f,-0.75f,0);
            edge=normal.cross(axis);
            if(edge.lengthSquared()<0.000001f)edge=new Vector3f(0,0,1).cross(axis);
        }
        float weight=Math.min(MathHelper.clamp(age/1.25f,0,1),MathHelper.clamp((duration-age)/2,0,1));
        weight=weight*weight*(3-2*weight);
        return new Pose(v,weight,age,edge.normalize());
    }
    private ComboAnimation() {}
}
