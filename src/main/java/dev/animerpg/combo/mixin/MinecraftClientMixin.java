package dev.animerpg.combo.mixin;

import dev.animerpg.combo.ComboClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void katanaCombo$attack(CallbackInfoReturnable<Boolean> cir) {
        if (ComboClient.interceptAttack((MinecraftClient)(Object)this)) cir.setReturnValue(false);
    }
}
