package org.xiaojian999.superpowers.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xiaojian999.superpowers.PowerManager;
import org.xiaojian999.superpowers.client.GodHud;

/**
 * Hides the first-person hands while the local player is in Lightning (Storm)
 * Form, so the player sees only their particle cloud. Also makes the
 * first-person hands translucent when God noclip is enabled.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    private static final int GOD_HAND_ALPHA = 90; // same as third-person ~35% opacity
    private static final int GOD_HAND_COLOR = (GOD_HAND_ALPHA << 24) | 0x00FFFFFF;

    @Redirect(
            method = "renderArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;)V"
            )
    )
    private void superpowers$godNoClipHandTransparent(
            OrderedRenderCommandQueue queue,
            ModelPart part,
            MatrixStack matrices,
            RenderLayer layer,
            int light,
            int overlay,
            Sprite sprite
    ) {
        if (GodHud.isGodNoClipActive()) {
            // Use the 11-arg overload with tintedColor = translucent white
            queue.submitModelPart(part, matrices, layer, light, overlay, sprite, false, false, GOD_HAND_COLOR, null, 0);
        } else {
            queue.submitModelPart(part, matrices, layer, light, overlay, sprite);
        }
    }

    @Inject(method = "renderRightArm", at = @At("HEAD"), cancellable = true)
    private void superpowers$hideStormFormRightArm(
            MatrixStack matrices,
            OrderedRenderCommandQueue renderCommandQueue,
            int light,
            Identifier texture,
            boolean thinArm,
            CallbackInfo callbackInfo
    ) {
        if (isLocalPlayerInStormForm()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "renderLeftArm", at = @At("HEAD"), cancellable = true)
    private void superpowers$hideStormFormLeftArm(
            MatrixStack matrices,
            OrderedRenderCommandQueue renderCommandQueue,
            int light,
            Identifier texture,
            boolean thinArm,
            CallbackInfo callbackInfo
    ) {
        if (isLocalPlayerInStormForm()) {
            callbackInfo.cancel();
        }
    }

    private static boolean isLocalPlayerInStormForm() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && PowerManager.isClientLightningFormActive(client.player.getUuid());
    }
}
