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

    // -------------------------------------------------------------------------
    // Experimental JNI — C++ port in src/main/cpp/RaycastUtil.{h,cpp}
    // Library: glungus_raycast (libglungus_raycast.so / .dylib / .dll)
    // Build: cmake -S src/main/cpp -B build/native -DCMAKE_BUILD_TYPE=Release
    //        cmake --build build/native -j
    // If the native lib is absent or fails to load, all calls fall back to
    // pure-Java impl. This keeps single-player worlds working without requiring
    // the C++ toolchain.
    // -------------------------------------------------------------------------
    private static final boolean NATIVE_LOADED;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("glungus_raycast");
            // Probe that JNI symbols actually link
            loaded = nativeIsAvailable();
        } catch (UnsatisfiedLinkError | SecurityException e) {
            // Expected on dev machines without native build — silently fall back
            loaded = false;
        }
        NATIVE_LOADED = loaded;
    }

    /** @return true if the experimental C++ native library is loaded and usable */
    public static boolean isNativeAvailable() {
        return NATIVE_LOADED;
    }

    // Mirror of C++ Vec3 helpers — exposed as native for perf experiments
    private static native boolean nativeHasEntityCloser(
            double ex, double ey, double ez,
            double bx, double by, double bz,
            double sx, double sy, double sz,
            boolean blockIsMiss);

    private static native double nativeSquaredDistanceTo(
            double ax, double ay, double az,
            double bx, double by, double bz);

    private static native double[] nativeComputeMaxEnd(
            double sx, double sy, double sz,
            double dx, double dy, double dz,
            double range);

    private static native double[] nativeNormalize(double x, double y, double z);

    private static native double[] nativeBlockOrMax(
            double bx, double by, double bz,
            double mx, double my, double mz,
            boolean blockIsMiss);

    private static native boolean nativeIsAvailable();

    public record RaycastResult(BlockHitResult blockHit, EntityHitResult entityHit) {
        public boolean hasEntityCloser(Vec3d start) {
            if (entityHit == null) return false;
            boolean blockIsMiss = blockHit.getType() == HitResult.Type.MISS;
            if (NATIVE_LOADED) {
                try {
                    Vec3d ePos = entityHit.getPos();
                    Vec3d bPos = blockHit.getPos();
                    return nativeHasEntityCloser(
                            ePos.x, ePos.y, ePos.z,
                            bPos.x, bPos.y, bPos.z,
                            start.x, start.y, start.z,
                            blockIsMiss);
                } catch (UnsatisfiedLinkError ignored) {
                    // fall through to Java
                }
            }
            if (blockIsMiss) return true;
            return entityHit.getPos().squaredDistanceTo(start) <= blockHit.getPos().squaredDistanceTo(start);
        }

        public Vec3d blockOrMax(Vec3d maxEnd, Vec3d start) {
            boolean blockIsMiss = blockHit.getType() == HitResult.Type.MISS;
            if (NATIVE_LOADED) {
                try {
                    Vec3d bPos = blockHit.getPos();
                    double[] res = nativeBlockOrMax(
                            bPos.x, bPos.y, bPos.z,
                            maxEnd.x, maxEnd.y, maxEnd.z,
                            blockIsMiss);
                    if (res != null && res.length == 3) {
                        return new Vec3d(res[0], res[1], res[2]);
                    }
                } catch (UnsatisfiedLinkError ignored) {
                    // fall through
                }
            }
            return blockIsMiss ? maxEnd : blockHit.getPos();
        }
    }

    // -------------------------------------------------------------------------
    // Public helpers that exercise the C++ Vec3 path (experimental)
    // -------------------------------------------------------------------------

    /** C++-accelerated maxEnd = start + normalize(dir) * range, falls back to Java */
    public static Vec3d computeMaxEnd(Vec3d start, Vec3d dir, double range) {
        if (NATIVE_LOADED) {
            try {
                double[] res = nativeComputeMaxEnd(start.x, start.y, start.z, dir.x, dir.y, dir.z, range);
                if (res != null && res.length == 3) return new Vec3d(res[0], res[1], res[2]);
            } catch (UnsatisfiedLinkError ignored) {}
        }
        return start.add(dir.normalize().multiply(range));
    }

    /** C++-accelerated normalize, falls back to Java */
    public static Vec3d normalize(Vec3d v) {
        if (NATIVE_LOADED) {
            try {
                double[] res = nativeNormalize(v.x, v.y, v.z);
                if (res != null && res.length == 3) return new Vec3d(res[0], res[1], res[2]);
            } catch (UnsatisfiedLinkError ignored) {}
        }
        return v.normalize();
    }

    public static double squaredDistance(Vec3d a, Vec3d b) {
        if (NATIVE_LOADED) {
            try {
                return nativeSquaredDistanceTo(a.x, a.y, a.z, b.x, b.y, b.z);
            } catch (UnsatisfiedLinkError ignored) {}
        }
        return a.squaredDistanceTo(b);
    }

    public static RaycastResult raycast(ServerPlayerEntity player, ServerWorld world, double range, Predicate<Entity> filter) {
        Vec3d start = player.getCameraPosVec(1.0F);
        // Use native normalize + computeMaxEnd if available (experimental fast path)
        Vec3d dir = normalize(player.getRotationVec(1.0F));
        Vec3d maxEnd = computeMaxEnd(start, dir, range);
        BlockHitResult blockHit = world.raycast(new RaycastContext(start, maxEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        EntityHitResult entityHit = ProjectileUtil.raycast(player, start, maxEnd, player.getBoundingBox().stretch(dir.multiply(range)).expand(1.0D), filter, range * range);
        return new RaycastResult(blockHit, entityHit);
    }

    public static BlockHitResult blockRaycast(ServerPlayerEntity player, ServerWorld world, double range) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d dir = normalize(player.getRotationVec(1.0F));
        Vec3d maxEnd = computeMaxEnd(start, dir, range);
        return world.raycast(new RaycastContext(start, maxEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
    }

    /**
     * Pure-Java reference impl for benchmarking against native.
     * Not used in production — call this to compare correctness/perf.
     */
    public static RaycastResult raycastJavaOnly(ServerPlayerEntity player, ServerWorld world, double range, Predicate<Entity> filter) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d dir = player.getRotationVec(1.0F).normalize();
        Vec3d maxEnd = start.add(dir.multiply(range));
        BlockHitResult blockHit = world.raycast(new RaycastContext(start, maxEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        EntityHitResult entityHit = ProjectileUtil.raycast(player, start, maxEnd, player.getBoundingBox().stretch(dir.multiply(range)).expand(1.0D), filter, range * range);
        return new RaycastResult(blockHit, entityHit);
    }
}
