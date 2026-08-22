package org.xiaojian999.superpowers.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xiaojian999.superpowers.PowerManager;
import org.xiaojian999.superpowers.client.GodHud;

/**
 * HACKY LIGHT: God Mode should make the player *look* like a light source
 * without ever placing a Blocks.LIGHT in the world (which would overwrite the
 * block at the player's feet and leave holes). Instead we hijack night-vision:
 * vanilla computes a lerped {@code getNightVisionStrength} from the Night Vision
 * effect. While the server gives God players permanent Night Vision, this mixin
 * forces the strength to 1.0 instantly so caves are fullbright without pulsing
 * and without any world mutation. The halo particles in GodPowerHandler sell
 * the visual, the lightmap hack sells the brightness.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void superpowers$godFullbright(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (entity != null && PowerManager.isGodModeActive(entity)) {
            cir.setReturnValue(1.0F);
            return;
        }
        // Dedicated servers: PowerManager.isGodModeActive lives on the server JVM and is
        // invisible to the client. Fall back to the client HUD state for the local player.
        if (entity != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == entity && GodHud.isGodModeActive()) {
                cir.setReturnValue(1.0F);
            }
        }
    }
}
