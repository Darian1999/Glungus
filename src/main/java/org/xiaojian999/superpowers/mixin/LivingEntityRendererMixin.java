package org.xiaojian999.superpowers.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xiaojian999.superpowers.PowerManager;
import org.xiaojian999.superpowers.client.GhostHud;
import org.xiaojian999.superpowers.client.GodHud;

/**
 * Rendering tweaks for transformed players.
 *
 * <p>While Ghost Form is active the local player's model is made translucent
 * (in 1.21.11 entity models are queued through {@code OrderedRenderCommandQueue},
 * so transparency is achieved the same way vanilla does it for invisible
 * entities: translucent render layer + reduced alpha in the model color).</p>
 *
 * <p>While Lightning (Storm) Form is active the transformed player is fully
 * invisible: the entire model — body, cape, held items, and name tag — is
 * skipped on every client.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<
        T extends LivingEntity,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>
        > {

    private static final int GHOST_ALPHA = 102; // ~40% opacity
    private static final int GOD_NOCLIP_ALPHA = 90; // ~35% opacity - more ghostly when phasing

    @Invoker("getRenderLayer")
    protected abstract RenderLayer superpowers$invokeGetRenderLayer(
            S state,
            boolean visible,
            boolean translucent,
            boolean outline
    );

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;")
    )
    private RenderLayer superpowers$ghostRenderLayer(
            LivingEntityRenderer<T, S, M> renderer,
            S state,
            boolean visible,
            boolean translucent,
            boolean outline,
            S renderState
    ) {
        if (isGhostSelf(renderState) || isGodNoClipSelf(renderState)) {
            return superpowers$invokeGetRenderLayer(state, visible, true, outline);
        }
        return superpowers$invokeGetRenderLayer(state, visible, translucent, outline);
    }

    @Inject(
            method = "getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private void superpowers$ghostMixColor(S state, CallbackInfoReturnable<Integer> callbackInfo) {
        if (isGhostSelf(state)) {
            callbackInfo.setReturnValue((callbackInfo.getReturnValueI() & 0x00FFFFFF) | (GHOST_ALPHA << 24));
        } else if (isGodNoClipSelf(state)) {
            callbackInfo.setReturnValue((callbackInfo.getReturnValueI() & 0x00FFFFFF) | (GOD_NOCLIP_ALPHA << 24));
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void superpowers$hideStormForm(
            S state,
            MatrixStack matrices,
            OrderedRenderCommandQueue renderCommandQueue,
            CameraRenderState cameraRenderState,
            CallbackInfo callbackInfo
    ) {
        if (isLightningForm(state)) {
            callbackInfo.cancel();
        }
    }

    private boolean isGhostSelf(S state) {
        if (!GhostHud.isGhostFormActive()) {
            return false;
        }
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && playerState.id == client.player.getId();
    }

    private boolean isGodNoClipSelf(S state) {
        if (!GodHud.isGodNoClipActive()) {
            return false;
        }
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && playerState.id == client.player.getId();
    }

    private boolean isLightningForm(S state) {
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return false;
        }
        Entity entity = client.world.getEntityById(playerState.id);
        return entity != null && PowerManager.isClientLightningFormActive(entity.getUuid());
    }
}
