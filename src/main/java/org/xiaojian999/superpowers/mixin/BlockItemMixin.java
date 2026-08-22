package org.xiaojian999.superpowers.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.xiaojian999.superpowers.PowerManager;

/**
 * A phasing player (Ghost Form / Storm Form) can be standing inside the space
 * a block is being placed into, and vanilla {@code canPlace} treats the
 * player's own bounding box as an obstruction — so block placement fails while
 * no-clipping. This mirrors Carpet's {@code creativeNoClip} fix: when the
 * placer is currently phasing, check collisions against everything except the
 * placer themselves.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Redirect(
            method = "canPlace(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;canPlace(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Z")
    )
    private boolean superpowers$canPlaceWhilePhasing(
            World world,
            BlockState state,
            BlockPos pos,
            ShapeContext context,
            ItemPlacementContext placementContext,
            BlockState stateOuter
    ) {
        PlayerEntity player = placementContext.getPlayer();
        if (player != null && PowerManager.isNoClipActive(player)) {
            VoxelShape shape = state.getCollisionShape(world, pos, context);
            return shape.isEmpty() || world.doesNotIntersectEntities(player, shape.offset(pos));
        }
        return world.canPlace(state, pos, context);
    }
}
