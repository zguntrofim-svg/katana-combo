package dev.animerpg.combo.mixin;
import dev.animerpg.combo.AnimationMatrices;
import dev.animerpg.combo.SlashTrail;
import dev.animerpg.combo.FirstPersonArms;
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
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Hand;
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique private Matrix4f katanaCombo$root;
    @Unique private ComboAnimation.Pose katanaCombo$pose;
    @Inject(method="renderFirstPersonItem",at=@At("HEAD"))
    private void katanaCombo$capture(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,float swing,ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        katanaCombo$pose=(hand==Hand.MAIN_HAND)?ComboAnimation.sample(player,delta):null;
        katanaCombo$root=katanaCombo$pose==null?null:new Matrix4f(matrices.peek().getPositionMatrix());
    }
    @Inject(method="renderFirstPersonItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void katanaCombo$blend(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,float swing,ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider vertices,int light,CallbackInfo ci){
        if(katanaCombo$pose==null)return;
        boolean left=player.getMainArm()==Arm.LEFT;
        AnimationMatrices.blend(matrices,katanaCombo$root,katanaCombo$pose,player,stack,(player.getMainArm()==Arm.LEFT?ModelTransformationMode.FIRST_PERSON_LEFT_HAND:ModelTransformationMode.FIRST_PERSON_RIGHT_HAND),left,true,left?-1:1);
        FirstPersonArms.render(player,katanaCombo$root,new Matrix4f(matrices.peek().getPositionMatrix()),vertices,light,katanaCombo$pose);
        SlashTrail.render(player,katanaCombo$pose,katanaCombo$root,vertices,true);
    }
    @ModifyArg(method="renderFirstPersonItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),index=2)
    private ModelTransformationMode katanaCombo$mode(ModelTransformationMode mode){
        return katanaCombo$pose==null?mode:ModelTransformationMode.NONE;
    }
    @ModifyArg(method="renderFirstPersonItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),index=3)
    private boolean katanaCombo$hand(boolean left){return katanaCombo$pose==null?left:false;}
}
