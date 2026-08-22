#include "RaycastUtil.h"
#include <limits>

namespace glungus {
namespace raycast {

Vec3 normalize(const Vec3& v) noexcept {
    const double lenSq = v.lengthSquared();
    // Mirrors Vec3d.normalize() — if zero vector, return zero (avoid NaN)
    if (lenSq < 1.0e-12) {
        return {0.0, 0.0, 0.0};
    }
    const double invLen = 1.0 / std::sqrt(lenSq);
    return {v.x * invLen, v.y * invLen, v.z * invLen};
}

Vec3 computeMaxEnd(const Vec3& start, const Vec3& dirNormalized, double range) noexcept {
    // Equivalent to start.add(dir.normalize().multiply(range))
    // Caller should already normalize dir; we normalize again defensively if needed
    // to match Java's `player.getRotationVec(1.0F).normalize()`.
    Vec3 n = dirNormalized;
    // Quick check: if not unit length, normalize (avoid extra sqrt if already ~1)
    const double lenSq = n.lengthSquared();
    if (std::abs(lenSq - 1.0) > 1e-6) {
        n = normalize(n);
    }
    return add(start, multiply(n, range));
}

bool hasEntityCloser(const Vec3& entityPos,
                     const Vec3& blockPos,
                     const Vec3& start,
                     bool blockIsMiss,
                     bool hasEntityHit) noexcept {
    if (!hasEntityHit) return false;
    if (blockIsMiss) return true;
    // Mirrors: entityHit.getPos().squaredDistanceTo(start) <= blockHit.getPos().squaredDistanceTo(start)
    return squaredDistance(entityPos, start) <= squaredDistance(blockPos, start);
}

Vec3 blockOrMax(const Vec3& blockPos,
                const Vec3& maxEnd,
                bool blockIsMiss) noexcept {
    return blockIsMiss ? maxEnd : blockPos;
}

} // namespace raycast
} // namespace glungus

// ---------------------------------------------------------------------------
// JNI helpers
// ---------------------------------------------------------------------------

namespace {

inline jdoubleArray vec3ToJDoubleArray(JNIEnv* env, const glungus::raycast::Vec3& v) {
    jdoubleArray arr = env->NewDoubleArray(3);
    if (arr == nullptr) return nullptr; // OOM
    jdouble buf[3] = {v.x, v.y, v.z};
    env->SetDoubleArrayRegion(arr, 0, 3, buf);
    return arr;
}

} // anonymous namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeHasEntityCloser(
    JNIEnv* /*env*/, jclass /*clazz*/,
    jdouble ex, jdouble ey, jdouble ez,
    jdouble bx, jdouble by, jdouble bz,
    jdouble sx, jdouble sy, jdouble sz,
    jboolean blockIsMiss) {
    using namespace glungus::raycast;
    Vec3 e{ex, ey, ez};
    Vec3 b{bx, by, bz};
    Vec3 s{sx, sy, sz};
    // hasEntityHit = true by contract (Java checks null before calling native).
    // For the raw JNI probe we assume true; Java wrapper handles null case.
    bool result = hasEntityCloser(e, b, s, blockIsMiss != JNI_FALSE, true);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jdouble JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeSquaredDistanceTo(
    JNIEnv* /*env*/, jclass /*clazz*/,
    jdouble ax, jdouble ay, jdouble az,
    jdouble bx, jdouble by, jdouble bz) {
    using namespace glungus::raycast;
    Vec3 a{ax, ay, az};
    Vec3 b{bx, by, bz};
    return squaredDistance(a, b);
}

JNIEXPORT jdoubleArray JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeComputeMaxEnd(
    JNIEnv* env, jclass /*clazz*/,
    jdouble sx, jdouble sy, jdouble sz,
    jdouble dx, jdouble dy, jdouble dz,
    jdouble range) {
    using namespace glungus::raycast;
    Vec3 start{sx, sy, sz};
    Vec3 dir{dx, dy, dz};
    Vec3 maxEnd = computeMaxEnd(start, dir, range);
    return vec3ToJDoubleArray(env, maxEnd);
}

JNIEXPORT jdoubleArray JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeNormalize(
    JNIEnv* env, jclass /*clazz*/,
    jdouble x, jdouble y, jdouble z) {
    using namespace glungus::raycast;
    Vec3 v{x, y, z};
    Vec3 n = normalize(v);
    return vec3ToJDoubleArray(env, n);
}

JNIEXPORT jdoubleArray JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeBlockOrMax(
    JNIEnv* env, jclass /*clazz*/,
    jdouble bx, jdouble by, jdouble bz,
    jdouble mx, jdouble my, jdouble mz,
    jboolean blockIsMiss) {
    using namespace glungus::raycast;
    Vec3 blockPos{bx, by, bz};
    Vec3 maxEnd{mx, my, mz};
    Vec3 res = blockOrMax(blockPos, maxEnd, blockIsMiss != JNI_FALSE);
    return vec3ToJDoubleArray(env, res);
}

JNIEXPORT jboolean JNICALL
Java_org_xiaojian999_superpowers_util_RaycastUtil_nativeIsAvailable(
    JNIEnv* /*env*/, jclass /*clazz*/) {
    return JNI_TRUE;
}

} // extern "C"
