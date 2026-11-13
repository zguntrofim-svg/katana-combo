package dev.animerpg.combo.mixin;

import dev.animerpg.combo.ComboAnimation;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void katanaCombo$pose(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
                                  float headYaw, float headPitch, CallbackInfo ci) {
        var pose = ComboAnimation.sample(entity, animationProgress - entity.age);
        if (pose == null) return;
        var model = (PlayerEntityModel<?>)(Object)this;
        float[] a = pose.angles(); float w = pose.weight();
        boolean left = entity.getMainArm() == Arm.LEFT;
        float sign = left ? -1 : 1;
        rotate(model.body, a[0], a[1]*sign, a[2]*sign, w);
        ModelPart main = left ? model.leftArm : model.rightArm;
        ModelPart support = left ? model.rightArm : model.leftArm;
        rotate(main, a[3], a[4]*sign, a[5]*sign, w);
        rotate(support, a[6], a[7]*sign, a[8]*sign, w);
        // Rotate shoulder pivots with the torso; vanilla Biped parts have no shared torso parent.
        float yaw = model.body.yaw;
        model.rightArm.pivotX = -5 * MathHelper.cos(yaw);
        model.rightArm.pivotZ = 5 * MathHelper.sin(yaw);
        model.leftArm.pivotX = 5 * MathHelper.cos(yaw);
        model.leftArm.pivotZ = -5 * MathHelper.sin(yaw);
        rotate(left ? model.leftLeg : model.rightLeg, a[9], 0, 0, w);
        rotate(left ? model.rightLeg : model.leftLeg, a[10], 0, 0, w);
        model.jacket.copyTransform(model.body);
        model.rightSleeve.copyTransform(model.rightArm); model.leftSleeve.copyTransform(model.leftArm);
        model.rightPants.copyTransform(model.rightLeg); model.leftPants.copyTransform(model.leftLeg);
    }
    private static void rotate(ModelPart part, float x, float y, float z, float weight) {
        float rad = (float)Math.PI / 180;
        part.pitch = MathHelper.lerp(weight, part.pitch, x*rad);
        part.yaw = MathHelper.lerp(weight, part.yaw, y*rad);
        part.roll = MathHelper.lerp(weight, part.roll, z*rad);
    }
}
