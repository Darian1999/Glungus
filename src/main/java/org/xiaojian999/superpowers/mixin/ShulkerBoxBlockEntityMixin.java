package org.xiaojian999.superpowers.mixin;

import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.xiaojian999.superpowers.PowerManager;

/**
 * When a piston pushes a shulker box, {@code pushEntities} shoves every entity
 * in the box's path. A phasing player (Ghost Form / Storm Form) standing inside
 * the box would get teleported/pushed around by it; mirroring Carpet's
 * {@code creativeNoClip} fix, report {@link PistonBehavior#IGNORE} for players
 * currently no-clipping so the shulker passes straight through them.
 */
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityMixin {
    @Redirect(
            method = "pushEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPistonBehavior()Lnet/minecraft/block/piston/PistonBehavior;")
    )
    private PistonBehavior superpowers$noClipPistonBehavior(Entity entity) {
        if (PowerManager.isNoClipActive(entity)) {
            return PistonBehavior.IGNORE;
        }
        return entity.getPistonBehavior();
    }
}
