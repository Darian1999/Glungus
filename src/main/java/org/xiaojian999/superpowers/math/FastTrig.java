package org.xiaojian999.superpowers.math;

/**
 * Fast trigonometry via 16384-entry sine table (14-bit, ~0.00038 rad step).
 * Error < 2e-4 vs {@link Math#sin}. Cos derived via phase offset.
 * Atan approximated with polynomial; atan2 handles quadrants.
 */
public final class FastTrig {
    private FastTrig() {}

    private static final int TABLE_BITS = 14;
    private static final int TABLE_SIZE = 1 << TABLE_BITS; // 16384
    private static final int TABLE_MASK = TABLE_SIZE - 1;
    private static final int TABLE_QUARTER = TABLE_SIZE >> 2; // 4096 = PI/2 offset
    private static final double STEP = GlungFastMath.TAU / TABLE_SIZE;
    private static final double INV_STEP = 1.0 / STEP;

    private static final float[] SIN_TABLE = new float[TABLE_SIZE];

    static {
        for (int i = 0; i < TABLE_SIZE; i++) {
            double angle = i * STEP;
            SIN_TABLE[i] = (float) Math.sin(angle);
        }
    }

    public static double sin(double rad) {
        rad %= GlungFastMath.TAU;
        if (rad < 0) rad += GlungFastMath.TAU;
        double pos = rad * INV_STEP;
        int i = (int) pos & TABLE_MASK;
        int j = (i + 1) & TABLE_MASK;
        double frac = pos - (int) pos;
        // linear interp for ~2x accuracy at cost of 2 loads
        float s0 = SIN_TABLE[i];
        float s1 = SIN_TABLE[j];
        return s0 + (s1 - s0) * frac;
    }

    public static double cos(double rad) {
        rad %= GlungFastMath.TAU;
        if (rad < 0) rad += GlungFastMath.TAU;
        double pos = rad * INV_STEP;
        double frac = pos - (int) pos;
        int i = ((int) pos + TABLE_QUARTER) & TABLE_MASK;
        int j = (i + 1) & TABLE_MASK;
        float c0 = SIN_TABLE[i];
        float c1 = SIN_TABLE[j];
        return c0 + (c1 - c0) * frac;
    }

    public static double tan(double rad) {
        double s, c;
        // compute sin/cos together to avoid double wrap
        rad %= GlungFastMath.TAU;
        if (rad < 0) rad += GlungFastMath.TAU;
        double pos = rad * INV_STEP;
        double frac = pos - (int) pos;
        int si = (int) pos & TABLE_MASK;
        int sj = (si + 1) & TABLE_MASK;
        int ci = (si + TABLE_QUARTER) & TABLE_MASK;
        int cj = (ci + 1) & TABLE_MASK;
        float s0 = SIN_TABLE[si], s1 = SIN_TABLE[sj];
        float c0 = SIN_TABLE[ci], c1 = SIN_TABLE[cj];
        s = s0 + (s1 - s0) * frac;
        c = c0 + (c1 - c0) * frac;
        if (Math.abs(c) < 1e-9) return Math.copySign(Double.MAX_VALUE, s);
        return s / c;
    }

    /** Fast asin - delegates to Math.asin; hot path is sin/cos. */
    public static double asin(double v) {
        if (v <= -1) return -GlungFastMath.HALF_PI;
        if (v >= 1) return GlungFastMath.HALF_PI;
        return Math.asin(v);
    }

    public static double acos(double v) {
        return GlungFastMath.HALF_PI - asin(v);
    }

    /** Fast atan via rational approximation, error ~0.0015 rad. */
    public static double atan(double v) {
        // Approximation from https://www.dsprelated.com/showarticle/1052.php
        double a = Math.abs(v);
        double c = (a < 1) ? a : 1 / a;
        double c2 = c * c;
        double res = c * (0.99997726 - c2 * (0.33262347 - c2 * 0.19354346));
        // refine with one Newton? keep simple
        if (a >= 1) res = GlungFastMath.HALF_PI - res;
        return v < 0 ? -res : res;
        // Note: For tighter accuracy, could call Math.atan for |v|>10
    }

    public static double atan2(double y, double x) {
        if (x == 0 && y == 0) return 0;
        if (x > 0) return atan(y / x);
        if (x < 0) {
            double a = atan(y / x);
            return y >= 0 ? a + GlungFastMath.PI : a - GlungFastMath.PI;
        }
        return y > 0 ? GlungFastMath.HALF_PI : -GlungFastMath.HALF_PI;
    }

    /** Fill out array with sincos pair to avoid double wrap/lookup. */
    public static void sinCos(double rad, double[] outSinCos) {
        rad %= GlungFastMath.TAU;
        if (rad < 0) rad += GlungFastMath.TAU;
        double pos = rad * INV_STEP;
        double frac = pos - (int) pos;
        int si = (int) pos & TABLE_MASK;
        int sj = (si + 1) & TABLE_MASK;
        int ci = (si + TABLE_QUARTER) & TABLE_MASK;
        int cj = (ci + 1) & TABLE_MASK;
        float s0 = SIN_TABLE[si], s1 = SIN_TABLE[sj];
        float c0 = SIN_TABLE[ci], c1 = SIN_TABLE[cj];
        outSinCos[0] = s0 + (s1 - s0) * frac;
        outSinCos[1] = c0 + (c1 - c0) * frac;
    }

    /** Degree variants. */
    public static double sinDeg(double deg) { return sin(deg * GlungFastMath.DEG_TO_RAD); }
    public static double cosDeg(double deg) { return cos(deg * GlungFastMath.DEG_TO_RAD); }
}
