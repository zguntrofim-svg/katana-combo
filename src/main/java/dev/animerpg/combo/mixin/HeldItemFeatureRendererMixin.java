package dev.animerpg.combo.mixin;
import dev.animerpg.combo.ComboAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Inject(method="renderItem",at=@At("HEAD"),cancellable=true)
    private void katanaCombo$blade(LivingEntity entity,ItemStack stack,ModelTransformationMode mode,Arm arm,
                                   MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci) {
        if(arm!=entity.getMainArm() || stack!=entity.getMainHandStack())return;
        var p=ComboAnimation.sample(entity,MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false));
        if(p==null)return;
        float sign=arm==Arm.LEFT?-1:1;
        Vector3f grip=p.grip(),dir=p.blade();grip.x*=sign;dir.x*=sign;
        matrices.push();
        // Player models use positive-down coordinates; held-item feature already inherits that space.
        matrices.translate(grip.x/16+dir.x*0.30f,grip.y/16+dir.y*0.30f,grip.z/16+dir.z*0.30f);
        matrices.multiply(p.orientation(sign,false));
        matrices.scale(1.2f,1.2f,1.2f);
        MinecraftClient.getInstance().getEntityRenderDispatcher().getHeldItemRenderer().renderItem(entity,stack,
            ModelTransformationMode.NONE,false,matrices,vertices,light);
        matrices.pop();ci.cancel();
    }
}
