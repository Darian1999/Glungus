package org.xiaojian999.superpowers.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xiaojian999.superpowers.client.GhostHud;

/**
 * While the local player possesses a body, the rendered camera is shifted to
 * the body's eye height (see {@link CameraMixin}), but vanilla computes the
 * crosshair raycast from {@link Entity#getCameraPosVec(float)}, which stays at
 * the player's own eye height. Attack and mining therefore aim from a different
 * height than the one the player is looking through, so hitting and breaking
 * blocks from inside a short (or tall) possessed body is unreliable.
 *
 * <p>This offsets the raycast origin by the same synced amount the camera uses,
 * aligning the crosshair with the possessed body's eye. The camera itself is
 * unaffected — it is positioned from the focused entity's lerped position plus
 * eye height, not from {@code getCameraPosVec}.
 */
@Mixin(Entity.class)
public abstract class EntityCameraPosMixin {
    @Inject(method = "getCameraPosVec", at = @At("HEAD"), cancellable = true)
    private void superpowers$possessedAimOrigin(float tickDelta, CallbackInfoReturnable<Vec3d> callbackInfo) {
        Entity entity = (Entity) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != entity || !GhostHud.isPossessing()) {
            return;
        }
        float offset = GhostHud.getCameraOffsetY();
        if (offset == 0.0F) {
            return;
        }
        // Mirror the vanilla computation so the base is identical, then apply
        // the same eye-height shift the camera gets in CameraMixin.
        Vec3d base = entity.getLerpedPos(tickDelta).add(0.0D, entity.getStandingEyeHeight(), 0.0D);
        callbackInfo.setReturnValue(base.add(0.0D, offset, 0.0D));
    }
}
