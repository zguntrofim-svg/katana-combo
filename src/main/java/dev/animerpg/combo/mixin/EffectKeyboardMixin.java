package dev.animerpg.combo.mixin;
import dev.animerpg.combo.EffectClient;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Keyboard.class)
public abstract class EffectKeyboardMixin {
    @Inject(method="onKey",at=@At("HEAD"),cancellable=true)
    private void katanaCombo$effects(long window,int key,int scan,int action,int modifiers,CallbackInfo ci){
        if(EffectClient.key(window,key,scan,action))ci.cancel();
    }
}
