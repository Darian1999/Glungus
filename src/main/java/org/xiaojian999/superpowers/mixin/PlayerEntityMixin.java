package org.xiaojian999.superpowers.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
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
}
