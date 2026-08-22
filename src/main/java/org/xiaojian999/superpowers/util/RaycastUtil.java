package org.xiaojian999.superpowers.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.function.Predicate;

public final class RaycastUtil {
    private RaycastUtil() {}

    public record RaycastResult(BlockHitResult blockHit, EntityHitResult entityHit) {
        public boolean hasEntityCloser(Vec3d start) {
            if (entityHit == null) return false;
            if (blockHit.getType() == HitResult.Type.MISS) return true;
            return entityHit.getPos().squaredDistanceTo(start) <= blockHit.getPos().squaredDistanceTo(start);
        }
        public Vec3d blockOrMax(Vec3d maxEnd, Vec3d start) {
            return blockHit.getType() == HitResult.Type.MISS ? maxEnd : blockHit.getPos();
        }
    }

    public static RaycastResult raycast(ServerPlayerEntity player, ServerWorld world, double range, Predicate<Entity> filter) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d dir = player.getRotationVec(1.0F).normalize();
        Vec3d maxEnd = start.add(dir.multiply(range));
        BlockHitResult blockHit = world.raycast(new RaycastContext(start, maxEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        EntityHitResult entityHit = ProjectileUtil.raycast(player, start, maxEnd, player.getBoundingBox().stretch(dir.multiply(range)).expand(1.0D), filter, range * range);
        return new RaycastResult(blockHit, entityHit);
    }

    public static BlockHitResult blockRaycast(ServerPlayerEntity player, ServerWorld world, double range) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d dir = player.getRotationVec(1.0F).normalize();
        Vec3d maxEnd = start.add(dir.multiply(range));
        return world.raycast(new RaycastContext(start, maxEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
    }
}
