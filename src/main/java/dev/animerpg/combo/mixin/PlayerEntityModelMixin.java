package dev.animerpg.combo.mixin;

import dev.animerpg.combo.ComboAnimation;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Inject(method="setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void katanaCombo$pose(LivingEntity player,float limb,float distance,float progress,float yaw,float pitch,CallbackInfo ci) {
        var m=(PlayerEntityModel<?>)(Object)this;
        // Vanilla resets rotation each frame but not arm scale.
        m.rightArm.yScale=1; m.leftArm.yScale=1;
        var p=ComboAnimation.sample(player,progress-player.age);
        if(p==null)return;
        float[] v=p.values(); float w=p.weight(), sign=player.getMainArm()==Arm.LEFT?-1:1;
        float rad=(float)Math.PI/180;
        m.body.pitch=MathHelper.lerp(w,m.body.pitch,v[5]*rad);
        m.body.yaw=MathHelper.lerp(w,m.body.yaw,v[6]*rad*sign);
        m.body.roll=MathHelper.lerp(w,m.body.roll,0);
        float torsoYaw=m.body.yaw;
        m.rightArm.pivotX=-5*MathHelper.cos(torsoYaw); m.rightArm.pivotZ=5*MathHelper.sin(torsoYaw);
        m.leftArm.pivotX=5*MathHelper.cos(torsoYaw); m.leftArm.pivotZ=-5*MathHelper.sin(torsoYaw);
        // Main hand near guard; support hand 1.5 pixels down the same handle.
        Vector3f grip=p.grip(), direction=p.blade(); grip.x*=sign; direction.x*=sign;
        ModelPart main=sign>0?m.rightArm:m.leftArm, support=sign>0?m.leftArm:m.rightArm;
        aim(main,grip,w); aim(support,new Vector3f(grip).fma(-1.5f,direction),w);
        m.rightLeg.pitch=MathHelper.lerp(w,m.rightLeg.pitch,v[7]*rad);
        m.leftLeg.pitch=MathHelper.lerp(w,m.leftLeg.pitch,v[8]*rad);
        m.rightLeg.yaw=MathHelper.lerp(w,m.rightLeg.yaw,-0.12f);
        m.leftLeg.yaw=MathHelper.lerp(w,m.leftLeg.yaw,0.12f);
        m.rightLeg.roll=MathHelper.lerp(w,m.rightLeg.roll,0.20f);
        m.leftLeg.roll=MathHelper.lerp(w,m.leftLeg.roll,-0.20f);
        m.jacket.copyTransform(m.body);m.rightSleeve.copyTransform(m.rightArm);m.leftSleeve.copyTransform(m.leftArm);
        m.rightPants.copyTransform(m.rightLeg);m.leftPants.copyTransform(m.leftLeg);
    }
    private static void aim(ModelPart arm,Vector3f grip,float w) {
        float dx=grip.x-arm.pivotX,dy=grip.y-arm.pivotY,dz=grip.z-arm.pivotZ;
        float xy=(float)Math.sqrt(dx*dx+dy*dy);
        // Rz * Rx maps the downwards arm onto the shoulder-to-grip vector.
        float x=(float)Math.atan2(dz,xy),z=(float)Math.atan2(-dx,dy);
        arm.pitch=MathHelper.lerp(w,arm.pitch,x);arm.yaw=MathHelper.lerp(w,arm.yaw,0);
        arm.roll=MathHelper.lerp(w,arm.roll,z);
        arm.yScale=MathHelper.lerp(w,1,(float)Math.sqrt(dx*dx+dy*dy+dz*dz)/10);
    }
}
