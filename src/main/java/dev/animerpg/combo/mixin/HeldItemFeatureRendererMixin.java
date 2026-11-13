package dev.animerpg.combo.mixin;
import dev.animerpg.combo.AnimationMatrices;
import dev.animerpg.combo.SlashTrail;
import dev.animerpg.combo.ComboAnimation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.entity.LivingEntity;
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Unique private Matrix4f katanaCombo$root;
    @Unique private ComboAnimation.Pose katanaCombo$pose;
    @Inject(method="renderItem",at=@At("HEAD"))
    private void katanaCombo$capture(LivingEntity player,ItemStack stack,ModelTransformationMode mode,Arm arm,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        katanaCombo$pose=(arm==player.getMainArm() && stack==player.getMainHandStack())?ComboAnimation.sample(player,MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false)):null;
        katanaCombo$root=katanaCombo$pose==null?null:new Matrix4f(matrices.peek().getPositionMatrix());
    }
    @Inject(method="renderItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void katanaCombo$blend(LivingEntity player,ItemStack stack,ModelTransformationMode mode,Arm arm,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        if(katanaCombo$pose==null)return;
        boolean left=player.getMainArm()==Arm.LEFT;
        AnimationMatrices.blend(matrices,katanaCombo$root,katanaCombo$pose,player,stack,mode,left,false,left?-1:1);
        SlashTrail.render(player,katanaCombo$pose,katanaCombo$root,vertices,false);
    }
    @ModifyArg(method="renderItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),index=2)
    private ModelTransformationMode katanaCombo$mode(ModelTransformationMode mode){
        return katanaCombo$pose==null?mode:ModelTransformationMode.NONE;
    }
    @ModifyArg(method="renderItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),index=3)
    private boolean katanaCombo$hand(boolean left){return katanaCombo$pose==null?left:false;}
}
