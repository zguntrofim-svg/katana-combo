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

/** Shared grip and blade keyframes, in player model coordinates (pixels, Y down, -Z forward). */
public final class ComboAnimation {
    private static final float[][][] FRAMES;
    static {
        try (var stream = ComboAnimation.class.getResourceAsStream("/assets/katana_combo/animation_keyframes.json")) {
            if (stream == null) throw new IllegalStateException("Missing combo keyframes");
            FRAMES = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), float[][][].class);
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public record Pose(float[] values, float weight, float age) {
        public Vector3f grip() { return new Vector3f(values[0], values[1], values[2]); }
        public Vector3f blade() {
            float yaw = values[3]*(float)Math.PI/180, elevation = values[4]*(float)Math.PI/180;
            return new Vector3f(MathHelper.sin(yaw)*MathHelper.cos(elevation), -MathHelper.sin(elevation),
                -MathHelper.cos(yaw)*MathHelper.cos(elevation));
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
        float t = MathHelper.clamp((age-times[segment])/(times[segment+1]-times[segment]),0,1);
        t = t*t*(3-2*t);
        float[] v = new float[10];
        for (int i=0;i<v.length;i++) v[i]=MathHelper.lerp(t,FRAMES[step][segment][i],FRAMES[step][segment+1][i]);
        float weight = Math.min(MathHelper.clamp(age/1.25f,0,1),MathHelper.clamp((duration-age)/2,0,1));
        return new Pose(v,weight,age);
    }
    private ComboAnimation() {}
}
