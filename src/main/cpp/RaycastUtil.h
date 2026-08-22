#pragma once
/**
 * Experimental C++ port of RaycastUtil.java via JNI
 * Package: org.xiaojian999.superpowers.util.RaycastUtil
 * Library: glungus_raycast (libglungus_raycast.so / glungus_raycast.dll / libglungus_raycast.dylib)
 *
 * This header declares pure C++ math helpers (no JNI dependency) plus JNI bridge.
 * The pure helpers mirror the Java logic so they can be unit-tested / benchmarked
 * without a JVM. JNI exports are thin wrappers around them.
 *
 * Build: see CMakeLists.txt
 *   cmake -S src/main/cpp -B build/native -DCMAKE_BUILD_TYPE=Release
 *   cmake --build build/native
 *
 * Java side: RaycastUtil.java loads System.loadLibrary("glungus_raycast") optionally.
 * If the library is absent, Java falls back to pure-Java impl (no crash).
 */

#include <jni.h>
#include <cmath>
#include <array>

namespace glungus {
namespace raycast {

// ---------------------------------------------------------------------------
// Pure C++ math — no JVM, no Minecraft deps. Mirrors Vec3d + RaycastResult logic.
// ---------------------------------------------------------------------------

struct Vec3 {
    double x = 0.0;
    double y = 0.0;
    double z = 0.0;

    constexpr Vec3() = default;
    constexpr Vec3(double x_, double y_, double z_) : x(x_), y(y_), z(z_) {}

    [[nodiscard]] double lengthSquared() const noexcept {
        return x * x + y * y + z * z;
    }
    [[nodiscard]] double length() const noexcept {
        return std::sqrt(lengthSquared());
    }
};

// Basic ops — intentionally inlined for speed (mirrors GlungFastMath style)
inline Vec3 add(const Vec3& a, const Vec3& b) noexcept {
    return {a.x + b.x, a.y + b.y, a.z + b.z};
}
inline Vec3 subtract(const Vec3& a, const Vec3& b) noexcept {
    return {a.x - b.x, a.y - b.y, a.z - b.z};
}
inline Vec3 multiply(const Vec3& v, double s) noexcept {
    return {v.x * s, v.y * s, v.z * s};
}
inline double dot(const Vec3& a, const Vec3& b) noexcept {
    return a.x * b.x + a.y * b.y + a.z * b.z;
}
inline double squaredDistance(const Vec3& a, const Vec3& b) noexcept {
    const double dx = a.x - b.x;
    const double dy = a.y - b.y;
    const double dz = a.z - b.z;
    return dx * dx + dy * dy + dz * dz;
}
inline double distance(const Vec3& a, const Vec3& b) noexcept {
    return std::sqrt(squaredDistance(a, b));
}

[[nodiscard]] Vec3 normalize(const Vec3& v) noexcept;
[[nodiscard]] Vec3 computeMaxEnd(const Vec3& start, const Vec3& dirNormalized, double range) noexcept;

/**
 * Mirrors RaycastResult.hasEntityCloser(Vec3d start)
 * @param entityPos        entityHit.getPos()
 * @param blockPos         blockHit.getPos()
 * @param start            ray start (player camera pos)
 * @param blockIsMiss      blockHit.getType() == MISS
 * @param hasEntityHit     entityHit != null (if false, always returns false)
 */
[[nodiscard]] bool hasEntityCloser(const Vec3& entityPos,
                                   const Vec3& blockPos,
                                   const Vec3& start,
                                   bool blockIsMiss,
                                   bool hasEntityHit) noexcept;

/**
 * Mirrors RaycastResult.blockOrMax(Vec3d maxEnd, Vec3d start)
 * Returns blockPos if not MISS, else maxEnd.
 */
[[nodiscard]] Vec3 blockOrMax(const Vec3& blockPos,
                              const Vec3& maxEnd,
                              bool blockIsMiss) noexcept;

} // namespace raycast
} // namespace glungus

// ---------------------------------------------------------------------------
// JNI exports — must be `extern "C"` to avoid name mangling
// Signatures must match RaycastUtil.java native declarations:
//   Java_org_xiaojian999_superpowers_util_RaycastUtil_*
// ---------------------------------------------------------------------------

extern "C" {

// boolean nativeHasEntityCloser(double ex, double ey, double ez,
//                               double bx, double by, double bz,
//                               double sx, double sy, double sz,
//                               boolean blockIsMiss)
JNIEXPORT jboolean JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeHasEntityCloser(
    JNIEnv* env, jclass clazz,
    jdouble ex, jdouble ey, jdouble ez,
    jdouble bx, jdouble by, jdouble bz,
    jdouble sx, jdouble sy, jdouble sz,
    jboolean blockIsMiss);

// double nativeSquaredDistanceTo(double ax, double ay, double az,
//                                double bx, double by, double bz)
JNIEXPORT jdouble JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeSquaredDistanceTo(
    JNIEnv* env, jclass clazz,
    jdouble ax, jdouble ay, jdouble az,
    jdouble bx, jdouble by, jdouble bz);

// double[] nativeComputeMaxEnd(double sx, double sy, double sz,
//                              double dx, double dy, double dz,
//                              double range)
JNIEXPORT jdoubleArray JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeComputeMaxEnd(
    JNIEnv* env, jclass clazz,
    jdouble sx, jdouble sy, jdouble sz,
    jdouble dx, jdouble dy, jdouble dz,
    jdouble range);

// double[] nativeNormalize(double x, double y, double z)
JNIEXPORT jdoubleArray JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeNormalize(
    JNIEnv* env, jclass clazz,
    jdouble x, jdouble y, jdouble z);

// double[] nativeBlockOrMax(double bx, double by, double bz,
//                           double mx, double my, double mz,
//                           boolean blockIsMiss)
JNIEXPORT jdoubleArray JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeBlockOrMax(
    JNIEnv* env, jclass clazz,
    jdouble bx, jdouble by, jdouble bz,
    jdouble mx, jdouble my, jdouble mz,
    jboolean blockIsMiss);

// boolean nativeIsAvailable() — simple probe to test JNI linkage
JNIEXPORT jboolean JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeIsAvailable(
    JNIEnv* env, jclass clazz);

} // extern "C"
