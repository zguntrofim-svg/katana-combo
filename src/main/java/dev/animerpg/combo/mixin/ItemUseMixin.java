package dev.animerpg.combo.mixin;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Item.class)
public abstract class ItemUseMixin {
    @Inject(method="getMaxUseTime",at=@At("HEAD"),cancellable=true)
    private void katanaCombo$hold(ItemStack stack,LivingEntity user,CallbackInfoReturnable<Integer> cir){
        if(stack.getItem() instanceof SwordItem && stack.getUseAction()==net.minecraft.util.UseAction.NONE)cir.setReturnValue(72000);
    }
}
