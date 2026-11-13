package dev.animerpg.combo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Blend complete item transforms into the real vanilla pose, not an approximation of its idle grip. */
public final class AnimationMatrices {
    public static void blend(MatrixStack matrices,Matrix4f root,ComboAnimation.Pose pose,LivingEntity entity,
                             ItemStack stack,ModelTransformationMode mode,boolean left,boolean camera,float sign){
        var client=MinecraftClient.getInstance();
        var model=client.getItemRenderer().getModel(stack,entity.getWorld(),entity,entity.getId());
        // NONE will be passed to the renderer, so apply the original model transform exactly once here.
        model.getTransformation().getTransformation(mode).apply(left,matrices);
        Matrix4f vanilla=new Matrix4f(matrices.peek().getPositionMatrix());
        Vector3f grip=pose.grip(),dir=pose.blade();dir.x*=sign;
        Matrix4f animated=new Matrix4f(root);
        if(camera){
            dir.y=-dir.y;
            animated.translate(grip.x*sign/22+dir.x*0.23f,-0.3f-grip.y/32+dir.y*0.23f,-0.75f+dir.z*0.23f);
        }else animated.translate(grip.x*sign/16+dir.x*0.30f,grip.y/16+dir.y*0.30f,grip.z/16+dir.z*0.30f);
        animated.rotate(pose.orientation(sign,camera)).scale(camera?0.95f:1.2f);
        float w=pose.weight();
        Vector3f translation=vanilla.getTranslation(new Vector3f()).lerp(animated.getTranslation(new Vector3f()),w);
        Vector3f scale=vanilla.getScale(new Vector3f()).lerp(animated.getScale(new Vector3f()),w);
        Quaternionf rotation=vanilla.getUnnormalizedRotation(new Quaternionf()).normalize()
            .slerp(animated.getUnnormalizedRotation(new Quaternionf()).normalize(),w);
        matrices.peek().getPositionMatrix().translationRotateScale(translation,rotation,scale);
        matrices.peek().getNormalMatrix().set(matrices.peek().getPositionMatrix()).invert().transpose();
    }
    private AnimationMatrices(){}
}
