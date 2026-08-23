package org.xiaojian999.superpowers.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xiaojian999.superpowers.god.GodWorldState;

/**
 * World-wide gravity: scales the final gravity for every entity.
 * Injected into the final getter so it stacks with LivingEntity#getGravity
 * (including the WaterWalk/Air reductions) and with subclass overrides
 * like FallingBlockEntity#getGravity.
 */
@Mixin(Entity.class)
public abstract class EntityGravityMixin {
    @Inject(method = "getFinalGravity", at = @At("RETURN"), cancellable = true)
    private void superpowers$worldGravity(CallbackInfoReturnable<Double> cir) {
        double mul = GodWorldState.getGravityMultiplier();
        if (mul != 1.0D) {
            double base = cir.getReturnValueD();
            // base may be 0 (e.g. Entity default). Multiplying 0 stays 0, which is correct.
            // For entities with no gravity (hasNoGravity) getFinalGravity already returned 0 before this injection,
            // so scaling 0 is a no-op — they remain weightless.
            cir.setReturnValue(base * mul);
        }
    }
}
