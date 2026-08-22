package org.xiaojian999.superpowers.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xiaojian999.superpowers.PowerManager;
import org.xiaojian999.superpowers.client.GodHud;

/**
 * HACKY CLIENT LIGHT: Makes God-mode players always render as if at max light.
 * No block is placed — we just lie about the brightness sampled at their eyes,
 * so the model/equipment is self-illuminated even deep underground. Combined
 * with the permanent Night Vision + GameRenderer fullbright, the player
 * effectively *is* a light source without ever calling world.setBlockState.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getBrightnessAtEyes", at = @At("HEAD"), cancellable = true)
    private void superpowers$godBrightnessHack(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof PlayerEntity player) {
            if (PowerManager.isGodModeActive(player)) {
                cir.setReturnValue(1.0F);
                return;
            }
            // Fallback for dedicated: client knows local God's HUD but not server set.
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == player && GodHud.isGodModeActive()) {
                cir.setReturnValue(1.0F);
            }
        }
    }
}
