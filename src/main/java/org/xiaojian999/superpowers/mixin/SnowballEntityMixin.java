package org.xiaojian999.superpowers.mixin;

import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xiaojian999.superpowers.PowerManager;

@Mixin(SnowballEntity.class)
public abstract class SnowballEntityMixin {
    @Inject(method = "onCollision", at = @At("HEAD"))
    private void superpowers$onCollision(HitResult hitResult, CallbackInfo callbackInfo) {
        PowerManager.handleSnowballCollision((SnowballEntity) (Object) this, hitResult);
    }
}
