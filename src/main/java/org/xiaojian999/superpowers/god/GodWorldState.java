package org.xiaojian999.superpowers.god;

/**
 * Holds world-wide god state like global gravity.
 * Gravity is a multiplier applied in Entity#getFinalGravity for every entity.
 * Single-player only - static suffices (integrated server + client share JVM).
 */
public final class GodWorldState {
    private static volatile double GRAVITY_MULTIPLIER = 1.0D;

    private GodWorldState() {}

    public static double getGravityMultiplier() {
        return GRAVITY_MULTIPLIER;
    }

    public static void setGravityMultiplier(double value) {
        // Clamp 0.0 (zero-G) to 5.0 (crushing)
        double clamped = Math.max(0.0D, Math.min(5.0D, value));
        // round to 3 decimals to avoid floating noise
        clamped = Math.round(clamped * 1000.0D) / 1000.0D;
        GRAVITY_MULTIPLIER = clamped;
    }

    public static void reset() {
        GRAVITY_MULTIPLIER = 1.0D;
    }
}
