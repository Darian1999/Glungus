package org.xiaojian999.superpowers.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.xiaojian999.superpowers.client.EarthquakeClientState;
import org.xiaojian999.superpowers.client.GhostHud;

/**
 * Shifts the camera to the possessed body's eye height while the player
 * entity's feet stay locked to the body's feet. The needed Y offset is
 * synced from the server through {@code PowerStatusPayload.cameraOffsetY}.
 *
 * <p>While an Earthquake of Lucifer is shaking the local player's area, the
 * camera also jumps around: a fast random jitter layered over a slow rumble,
 * with intensity fading away from the epicenter.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private Entity focusedEntity;

    @ModifyVariable(method = "setPos(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double superpowers$cameraOffsetX(double x) {
        return x + earthquakeShakeOffset(0);
    }

    @ModifyVariable(method = "setPos(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double superpowers$possessionEyeHeightY(double y) {
        return y + possessionOffsetY() + earthquakeShakeOffset(1);
    }

    @ModifyVariable(method = "setPos(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double superpowers$cameraOffsetZ(double z) {
        return z + earthquakeShakeOffset(2);
    }

    @ModifyVariable(method = "setPos(Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), argsOnly = true)
    private Vec3d superpowers$possessionEyeHeightPos(Vec3d pos) {
        float offset = possessionOffsetY();
        double shakeX = earthquakeShakeOffset(0);
        double shakeY = earthquakeShakeOffset(1);
        double shakeZ = earthquakeShakeOffset(2);
        if (offset == 0.0F && shakeX == 0.0D && shakeY == 0.0D && shakeZ == 0.0D) {
            return pos;
        }
        return pos.add(shakeX, offset + shakeY, shakeZ);
    }

    private float possessionOffsetY() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || focusedEntity != client.player || !GhostHud.isPossessing()) {
            return 0.0F;
        }
        return GhostHud.getCameraOffsetY();
    }

    private double earthquakeShakeOffset(int axis) {
        double intensity = EarthquakeClientState.shakeIntensity();
        if (intensity <= 0.0D) {
            return 0.0D;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return 0.0D;
        }
        double jitter = (client.world.random.nextDouble() - 0.5D) * 0.7D;
        double rumble = Math.sin(client.world.getTime() * 0.6D + axis * 2.1D) * 0.18D;
        return (jitter + rumble) * intensity;
    }
}
