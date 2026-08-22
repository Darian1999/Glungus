package org.xiaojian999.superpowers.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.xiaojian999.superpowers.PowerManager;

/**
 * While phasing (Ghost Form / Storm Form) the camera can end up inside a solid
 * block. Vanilla only disables chunk culling for that case when the player is a
 * spectator, so a non-spectator no-clipping player would be unable to see the
 * world around the block their head is inside of. This mirrors Carpet's
 * {@code creativeNoClip} camera fix: treat currently-phasing players like
 * spectators for this one camera check so they can see through the world.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Redirect(
            method = "render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSpectator()Z")
    )
    private boolean superpowers$canSeeWorldWhilePhasing(ClientPlayerEntity player) {
        return player.isSpectator() || PowerManager.isNoClipActive(player);
    }
}
