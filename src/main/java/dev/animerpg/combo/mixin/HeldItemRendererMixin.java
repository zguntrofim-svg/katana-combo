package dev.animerpg.combo.mixin;

import dev.animerpg.combo.ComboAnimation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void katanaCombo$blade(AbstractClientPlayerEntity player, float delta, float pitch, Hand hand,
                                   float swing, ItemStack stack, float equip, MatrixStack matrices,
                                   VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (hand != Hand.MAIN_HAND) return;
        var pose = ComboAnimation.sample(player, delta);
        if (pose == null) return;
        float[] a = pose.angles(); float w = pose.weight();
        float sign = player.getMainArm() == Arm.LEFT ? -1 : 1;
        matrices.translate(sign * -a[4]/160 * w, -(a[3]+85)/160 * w, -0.18*w);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign*a[4]*w));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((a[3]+85)*w));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign*(a[5]-15)*w));
    }
}
