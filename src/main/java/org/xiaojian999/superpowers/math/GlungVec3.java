package org.xiaojian999.superpowers.math;

import net.minecraft.util.math.Vec3d;

/**
 * Immutable double vector with fast ops. Preferred over {@link Vec3d} in tight
 * loops where allocation pressure matters – but can convert to/from Vec3d.
 *
 * <p>All operations return new vectors; mutate-free for safety. For zero-alloc
 * paths use {@link GlungFastMath} primitive overloads.
 */
public record GlungVec3(double x, double y, double z) {
    public static final GlungVec3 ZERO = new GlungVec3(0, 0, 0);
    public static final GlungVec3 ONE = new GlungVec3(1, 1, 1);
    public static final GlungVec3 UP = new GlungVec3(0, 1, 0);
    public static final GlungVec3 DOWN = new GlungVec3(0, -1, 0);
    public static final GlungVec3 EAST = new GlungVec3(1, 0, 0);
    public static final GlungVec3 WEST = new GlungVec3(-1, 0, 0);
    public static final GlungVec3 NORTH = new GlungVec3(0, 0, -1);
    public static final GlungVec3 SOUTH = new GlungVec3(0, 0, 1);

    public GlungVec3 add(GlungVec3 o) { return new GlungVec3(x + o.x, y + o.y, z + o.z); }
    public GlungVec3 add(double ox, double oy, double oz) { return new GlungVec3(x + ox, y + oy, z + oz); }
    public GlungVec3 sub(GlungVec3 o) { return new GlungVec3(x - o.x, y - o.y, z - o.z); }
    public GlungVec3 mul(double s) { return new GlungVec3(x * s, y * s, z * s); }
    public GlungVec3 mul(GlungVec3 o) { return new GlungVec3(x * o.x, y * o.y, z * o.z); }
    public GlungVec3 neg() { return new GlungVec3(-x, -y, -z); }
    public double dot(GlungVec3 o) { return x * o.x + y * o.y + z * o.z; }
    public GlungVec3 cross(GlungVec3 o) {
        return new GlungVec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }
    public double lengthSq() { return x * x + y * y + z * z; }
    public double length() { return Math.sqrt(lengthSq()); }
    public double lengthFast() { return GlungFastMath.sqrtFast(lengthSq()); }
    public GlungVec3 normalize() {
        double len = length();
        return len < GlungFastMath.EPSILON ? ZERO : new GlungVec3(x / len, y / len, z / len);
    }
    public GlungVec3 normalizeFast() {
        double lenSq = lengthSq();
        if (lenSq < 1e-12) return ZERO;
        float inv = GlungFastMath.invSqrtFast((float) lenSq);
        return new GlungVec3(x * inv, y * inv, z * inv);
    }
    public double distanceTo(GlungVec3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    public double distanceSqTo(GlungVec3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
    public GlungVec3 lerp(GlungVec3 to, double t) {
        return new GlungVec3(GlungFastMath.lerp(x, to.x, t), GlungFastMath.lerp(y, to.y, t), GlungFastMath.lerp(z, to.z, t));
    }
    public GlungVec3 slerp(GlungVec3 to, double t) {
        return fromVec3d(GlungFastMath.slerp(toVec3d(), to.toVec3d(), t));
    }
    public Vec3d toVec3d() { return new Vec3d(x, y, z); }
    public static GlungVec3 fromVec3d(Vec3d v) { return new GlungVec3(v.x, v.y, v.z); }
    public static GlungVec3 from(double x, double y, double z) { return new GlungVec3(x, y, z); }

    // Fast rotations reusing GlungFastMath tables
    public GlungVec3 rotateY(double rad) {
        double c = GlungFastMath.fastCos(rad), s = GlungFastMath.fastSin(rad);
        return new GlungVec3(x * c - z * s, y, x * s + z * c);
    }
    public GlungVec3 rotateX(double rad) {
        double c = GlungFastMath.fastCos(rad), s = GlungFastMath.fastSin(rad);
        return new GlungVec3(x, y * c - z * s, y * s + z * c);
    }
    public GlungVec3 rotateZ(double rad) {
        double c = GlungFastMath.fastCos(rad), s = GlungFastMath.fastSin(rad);
        return new GlungVec3(x * c - y * s, x * s + y * c, z);
    }
    public GlungVec3 reflect(GlungVec3 normal) {
        double d = dot(normal) * 2;
        return sub(normal.mul(d));
    }
    public double angleTo(GlungVec3 o) {
        double d = dot(o) / Math.sqrt(lengthSq() * o.lengthSq());
        return Math.acos(GlungFastMath.clamp(d, -1, 1));
    }
    public GlungVec3 clamp(double min, double max) {
        return new GlungVec3(GlungFastMath.clamp(x, min, max), GlungFastMath.clamp(y, min, max), GlungFastMath.clamp(z, min, max));
    }

    @Override public String toString() { return String.format("GVec(%.3f, %.3f, %.3f)", x, y, z); }
}
