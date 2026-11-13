package dev.animerpg.combo.mixin;

import dev.animerpg.combo.ComboAnimation;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {
    @Unique private float[] katanaCombo$pivots;
    @Unique private ModelPart[] katanaCombo$parts(){
        var m=(PlayerEntityModel<?>)(Object)this;
        return new ModelPart[]{m.body,m.head,m.hat,m.rightArm,m.leftArm,m.rightLeg,m.leftLeg};
    }
    @Inject(method="setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",at=@At("HEAD"))
    private void katanaCombo$restore(LivingEntity p,float a,float b,float c,float d,float e,CallbackInfo ci){
        ModelPart[] parts=katanaCombo$parts();
        if(katanaCombo$pivots!=null){
            for(int i=0;i<parts.length;i++){
                parts[i].pivotX=katanaCombo$pivots[i*3];parts[i].pivotY=katanaCombo$pivots[i*3+1];parts[i].pivotZ=katanaCombo$pivots[i*3+2];
            }
            katanaCombo$pivots=null;
        }
        var m=(PlayerEntityModel<?>)(Object)this;m.rightArm.yScale=1;m.leftArm.yScale=1;
    }
    @Inject(method="setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void katanaCombo$pose(LivingEntity player,float limb,float distance,float progress,float yaw,float pitch,CallbackInfo ci) {
        var m=(PlayerEntityModel<?>)(Object)this;
        // Vanilla resets rotation each frame but not arm scale.
        m.rightArm.yScale=1; m.leftArm.yScale=1;
        var p=ComboAnimation.sample(player,progress-player.age);
        if(p==null)return;
        ModelPart[] parts=katanaCombo$parts();katanaCombo$pivots=new float[parts.length*3];
        for(int i=0;i<parts.length;i++){
            katanaCombo$pivots[i*3]=parts[i].pivotX;katanaCombo$pivots[i*3+1]=parts[i].pivotY;katanaCombo$pivots[i*3+2]=parts[i].pivotZ;
        }
        float[] v=p.values(); float w=p.weight(), sign=player.getMainArm()==Arm.LEFT?-1:1;
        float rad=(float)Math.PI/180;
        m.body.pitch=MathHelper.lerp(w,m.body.pitch,v[5]*rad);
        m.body.yaw=MathHelper.lerp(w,m.body.yaw,v[6]*rad*sign);
        m.body.roll=MathHelper.lerp(w,m.body.roll,v[11]*rad*sign);
        Quaternionf torso=new Quaternionf().rotationZYX(m.body.roll,m.body.yaw,m.body.pitch);
        // Rotate around the hips, not the neck. Carry shoulders and head with the torso.
        Vector3f root=new Vector3f(0,12,0).rotate(torso).negate().add(v[10]*w*sign,12+v[9]*w,0);
        Vector3f base=new Vector3f(m.body.pivotX,m.body.pivotY,m.body.pivotZ).add(root);
        m.body.pivotX=base.x;m.body.pivotY=base.y;m.body.pivotZ=base.z;
        Vector3f right=new Vector3f(-5,2,0).rotate(torso).add(base);
        Vector3f left=new Vector3f(5,2,0).rotate(torso).add(base);
        m.rightArm.pivotX=right.x;m.rightArm.pivotY=right.y;m.rightArm.pivotZ=right.z;
        m.leftArm.pivotX=left.x;m.leftArm.pivotY=left.y;m.leftArm.pivotZ=left.z;
        m.head.pivotX+=root.x;m.head.pivotY+=root.y;m.head.pivotZ+=root.z;
        m.head.pitch+=m.body.pitch*0.3f;m.head.yaw+=m.body.yaw*0.2f;
        m.hat.copyTransform(m.head);
        // Main hand near guard; support hand 1.5 pixels down the same handle.
        Vector3f grip=p.grip(), direction=p.blade(); grip.x*=sign; direction.x*=sign;
        ModelPart main=sign>0?m.rightArm:m.leftArm, support=sign>0?m.leftArm:m.rightArm;
        aim(main,grip,w); aim(support,new Vector3f(grip).fma(-1.5f,direction),w);
        m.rightLeg.pitch=MathHelper.lerp(w,m.rightLeg.pitch,v[7]*rad);
        m.leftLeg.pitch=MathHelper.lerp(w,m.leftLeg.pitch,v[8]*rad);
        m.rightLeg.yaw=MathHelper.lerp(w,m.rightLeg.yaw,-0.12f);
        m.leftLeg.yaw=MathHelper.lerp(w,m.leftLeg.yaw,0.12f);
        float spread=v[12]*rad;
        m.rightLeg.roll=MathHelper.lerp(w,m.rightLeg.roll,spread);
        m.leftLeg.roll=MathHelper.lerp(w,m.leftLeg.roll,-spread);
        m.rightLeg.pivotX+=v[10]*w*sign*0.35f;m.leftLeg.pivotX+=v[10]*w*sign*0.35f;
        m.rightLeg.pivotZ+=v[7]*rad*w*2;m.leftLeg.pivotZ+=v[8]*rad*w*2;
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
