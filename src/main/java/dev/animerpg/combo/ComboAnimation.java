package dev.animerpg.combo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.SwordItem;
import net.minecraft.util.math.MathHelper;

/** Keyframes in degrees: torso pitch/yaw/roll, right arm XYZ, left arm XYZ, legs pitch. */
public final class ComboAnimation {
    private static final float[][][] POSES = {
        {{-8,-35,0, -90,-55,30, -100,-20,-35, -18,22},
         {8,25,-6, -88,45,-18, -92,70,-35, 24,-25},
         {10,65,-5, -80,80,-12, -85,95,-35, 30,-30}},
        {{10,55,-6, -45,55,-30, -60,80,-40, 20,-25},
         {-8,-25,6, -140,-25,20, -145,-5,-25, -25,25},
         {-10,-60,4, -155,-55,22, -150,-30,-20, -28,32}},
        {{-12,0,0, -165,-12,15, -165,12,-15, -20,20},
         {22,0,0, -55,0,12, -65,28,-25, 32,-32},
         {28,0,0, -35,5,12, -45,30,-25, 35,-35}}
    };
    public record Pose(float[] angles, float weight, int step, float age) {}
    public static Pose sample(LivingEntity entity, float tickDelta) {
        var client = MinecraftClient.getInstance();
        var state = ComboClient.animationState(entity.getUuid());
        if (state == null || client.world == null || !entity.isAlive()
                || !(entity.getMainHandStack().getItem() instanceof SwordItem)
                || entity.isUsingItem() || entity.isFallFlying() || entity.hasVehicle() || entity.isSwimming()) return null;
        int step = state.step(), duration = ComboTiming.DURATION[step];
        float age = Math.max(0, client.world.getTime() - state.started() + tickDelta);
        if (age >= duration) return null;
        float start = ComboTiming.HIT_START[step], end = ComboTiming.HIT_END[step];
        int segment = age < start ? 0 : 1;
        float t = MathHelper.clamp(segment == 0 ? age / start : (age - start) / (end - start + 2), 0, 1);
        t = t * t * (3 - 2 * t);
        float[] result = new float[11];
        for (int i = 0; i < result.length; i++) result[i] = MathHelper.lerp(t, POSES[step][segment][i], POSES[step][segment+1][i]);
        float weight = Math.min(MathHelper.clamp(age / 1.5f, 0, 1), MathHelper.clamp((duration - age) / 3, 0, 1));
        return new Pose(result, weight, step, age);
    }
    private ComboAnimation() {}
}
