package org.xiaojian999.superpowers.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xiaojian999.superpowers.PowerManager;

/**
 * Lets a player in Ghost Form or Lightning (Storm) Form phase through walls
 * and other entities while flying. The redirects make the affected code paths
 * treat the player exactly like a spectator (no wall/entity collision, no pose
 * changes), mirroring the classic creative-noclip trick.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z"))
    private boolean superpowers$formNoClipTick(PlayerEntity player) {
        return player.isSpectator() || PowerManager.isNoClipActive(player);
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z"))
    private boolean superpowers$formNoClipMovement(PlayerEntity player) {
        return player.isSpectator() || PowerManager.isNoClipActive(player);
    }

    @Redirect(method = "updatePose", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isSpectator()Z"))
    private boolean superpowers$formNoClipPose(PlayerEntity player) {
        return player.isSpectator() || PowerManager.isNoClipActive(player);
    }

    /**
     * God Mode players can perform sweep attacks without needing a sword.
     * Vanilla requires the main-hand stack to be in ItemTags.SWORDS; we bypass that
     * check while keeping the other sweep conditions (cooldown, not crit/knockback,
     * on ground, low horizontal speed).
     */
    @Inject(method = "canUseSweepAttack", at = @At("HEAD"), cancellable = true)
    private void superpowers$godModeSweepWithoutSword(boolean cooldownPassed, boolean criticalHit, boolean knockback, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!PowerManager.isGodModeActive(self)) {
            return;
        }
        // Replicate vanilla logic without the sword requirement
        if (!cooldownPassed || criticalHit || knockback) {
            cir.setReturnValue(false);
            return;
        }
        if (!self.isOnGround()) {
            cir.setReturnValue(false);
            return;
        }
        double horiz = self.getVelocity().horizontalLengthSquared();
        double threshold = self.getMovementSpeed() * 2.5D;
        if (horiz >= threshold * threshold) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(true);
    }
}
