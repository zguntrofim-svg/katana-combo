package dev.animerpg.combo.mixin;
import dev.animerpg.combo.ComboAnimation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
    private void katanaCombo$blade(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,float swing,
                                   ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci) {
        if(hand!=Hand.MAIN_HAND)return;
        var p=ComboAnimation.sample(player,delta);if(p==null)return;
        float sign=player.getMainArm()==Arm.LEFT?-1:1;
        Vector3f grip=p.grip(),dir=p.blade();
        // Camera Y points up; model Y points down. Keep the blade ahead of the near plane.
        Vector3f cameraDir=new Vector3f(dir.x*sign,-dir.y,dir.z);
        matrices.push();
        matrices.translate(grip.x*sign/22+cameraDir.x*0.23f,-0.3f-grip.y/32+cameraDir.y*0.23f,-0.75f+cameraDir.z*0.23f);
        matrices.multiply(p.orientation(sign,true));
        matrices.scale(0.95f,0.95f,0.95f);
        ((HeldItemRenderer)(Object)this).renderItem(player,stack,ModelTransformationMode.NONE,false,matrices,vertices,light);
        matrices.pop();ci.cancel();
    }
}
