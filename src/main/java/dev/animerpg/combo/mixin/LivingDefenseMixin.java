package dev.animerpg.combo.mixin;
import dev.animerpg.combo.KatanaDefense;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(LivingEntity.class)
public abstract class LivingDefenseMixin {
    @ModifyVariable(method="damage",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float katanaCombo$reduce(float amount,DamageSource source,float incoming){
        return KatanaDefense.reduce((LivingEntity)(Object)this,source,amount);
    }
}
