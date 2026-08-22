package org.xiaojian999.superpowers.math;

/**
 * Compact deterministic noise for Glungus effects. No external deps.
 * - Perlin 2D/3D (classic improved)
 * - Simplex 2D fallback via Perlin skew
 * - Worley (cellular) cheap
 * Seeded via perm table; deterministic per world seed if fed through hash.
 */
public final class FastNoise {
    private FastNoise() {}

    private static final int[] PERM = new int[512];
    private static final double[] GRAD_2D_X = new double[256];
    private static final double[] GRAD_2D_Y = new double[256];
    private static final double[] GRAD_3D_X = new double[256];
    private static final double[] GRAD_3D_Y = new double[256];
    private static final double[] GRAD_3D_Z = new double[256];

    static {
        // Build perm table using SplitMix64 seeded deterministically (so all clients agree)
        long seed = 0x9E3779B97F4A7C15L ^ 0x6A09E667F3BCC908L;
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        // shuffle with deterministic RNG
        long state = seed;
        for (int i = 255; i > 0; i--) {
            state = FastRandom.splitMix64(state + 0x9E3779B97F4A7C15L);
            int j = (int) ((state >>> 33) % (i + 1));
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) PERM[i] = p[i & 255];
        // gradient directions
        for (int i = 0; i < 256; i++) {
            double angle = FastRandom.randomDouble(FastRandom.splitMix64(i * 0x9E3779B9L)) * GlungFastMath.TAU;
            GRAD_2D_X[i] = Math.cos(angle);
            GRAD_2D_Y[i] = Math.sin(angle);
            // 3D random unit
            double z = FastRandom.randomDouble(FastRandom.splitMix64(i * 0x6A09E667L)) * 2 - 1;
            double r = Math.sqrt(Math.max(0, 1 - z * z));
            double a = FastRandom.randomDouble(FastRandom.splitMix64(i * 0xBF58476DL)) * GlungFastMath.TAU;
            GRAD_3D_X[i] = r * Math.cos(a);
            GRAD_3D_Y[i] = r * Math.sin(a);
            GRAD_3D_Z[i] = z;
        }
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad2(int hash, double x, double y) {
        return GRAD_2D_X[hash & 255] * x + GRAD_2D_Y[hash & 255] * y;
    }

    private static double grad3(int hash, double x, double y, double z) {
        return GRAD_3D_X[hash & 255] * x + GRAD_3D_Y[hash & 255] * y + GRAD_3D_Z[hash & 255] * z;
    }

    public static double perlin2D(double x, double y) {
        int xi = GlungFastMath.fastFloor(x) & 255;
        int yi = GlungFastMath.fastFloor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf);
        double v = fade(yf);
        int aa = PERM[PERM[xi] + yi];
        int ab = PERM[PERM[xi] + yi + 1];
        int ba = PERM[PERM[xi + 1] + yi];
        int bb = PERM[PERM[xi + 1] + yi + 1];
        double x1 = lerp(grad2(aa, xf, yf), grad2(ba, xf - 1, yf), u);
        double x2 = lerp(grad2(ab, xf, yf - 1), grad2(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v); // in ~[-1,1]
    }

    public static double perlin3D(double x, double y, double z) {
        int xi = GlungFastMath.fastFloor(x) & 255;
        int yi = GlungFastMath.fastFloor(y) & 255;
        int zi = GlungFastMath.fastFloor(z) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);
        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);
        int aaa = PERM[PERM[PERM[xi] + yi] + zi];
        int aba = PERM[PERM[PERM[xi] + yi + 1] + zi];
        int aab = PERM[PERM[PERM[xi] + yi] + zi + 1];
        int abb = PERM[PERM[PERM[xi] + yi + 1] + zi + 1];
        int baa = PERM[PERM[PERM[xi + 1] + yi] + zi];
        int bba = PERM[PERM[PERM[xi + 1] + yi + 1] + zi];
        int bab = PERM[PERM[PERM[xi + 1] + yi] + zi + 1];
        int bbb = PERM[PERM[PERM[xi + 1] + yi + 1] + zi + 1];
        double x1 = lerp(grad3(aaa, xf, yf, zf), grad3(baa, xf - 1, yf, zf), u);
        double x2 = lerp(grad3(aba, xf, yf - 1, zf), grad3(bba, xf - 1, yf - 1, zf), u);
        double y1 = lerp(x1, x2, v);
        double x3 = lerp(grad3(aab, xf, yf, zf - 1), grad3(bab, xf - 1, yf, zf - 1), u);
        double x4 = lerp(grad3(abb, xf, yf - 1, zf - 1), grad3(bbb, xf - 1, yf - 1, zf - 1), u);
        double y2 = lerp(x3, x4, v);
        return lerp(y1, y2, w);
    }

    /** Simplex 2D approximated via Perlin skew – cheap and good enough for particles. */
    public static double simplex2D(double x, double y) {
        // Skew factor for simplex
        double s = (x + y) * 0.366025403; // F2
        int i = GlungFastMath.fastFloor(x + s);
        int j = GlungFastMath.fastFloor(y + s);
        double t = (i + j) * 0.211324865; // G2
        double xo = i - t, yo = j - t;
        double x0 = x - xo, y0 = y - yo;
        // Determine simplex triangle
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        double x1 = x0 - i1 + 0.211324865;
        double y1 = y0 - j1 + 0.211324865;
        double x2 = x0 - 1 + 2 * 0.211324865;
        double y2 = y0 - 1 + 2 * 0.211324865;
        int ii = i & 255, jj = j & 255;
        double n0 = 0, n1 = 0, n2 = 0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 >= 0) {
            t0 *= t0;
            n0 = t0 * t0 * grad2(PERM[ii + PERM[jj]], x0, y0);
        }
        double t1v = 0.5 - x1 * x1 - y1 * y1;
        if (t1v >= 0) {
            t1v *= t1v;
            n1 = t1v * t1v * grad2(PERM[ii + i1 + PERM[jj + j1]], x1, y1);
        }
        double t2v = 0.5 - x2 * x2 - y2 * y2;
        if (t2v >= 0) {
            t2v *= t2v;
            n2 = t2v * t2v * grad2(PERM[ii + 1 + PERM[jj + 1]], x2, y2);
        }
        return 70 * (n0 + n1 + n2); // scale to [-1,1]
    }

    /** Cheap Worley (cellular) distance to nearest feature point in 2D. Returns [0,1]. */
    public static double worley2D(double x, double y) {
        int xi = GlungFastMath.fastFloor(x);
        int yi = GlungFastMath.fastFloor(y);
        double best = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = xi + dx, cy = yi + dy;
                long h = FastRandom.splitMix64(((long) cx * 374761393L) ^ ((long) cy * 668265263L));
                double px = cx + ((h >>> 32) & 0xFFFFFF) / (double) 0x1000000;
                double py = cy + ((h >>> 8) & 0xFFFFFF) / (double) 0x1000000;
                double ddx = x - px, ddy = y - py;
                double d = ddx * ddx + ddy * ddy;
                if (d < best) best = d;
            }
        }
        return Math.sqrt(best); // 0..~1.4 -> clamp
    }

    /** Turbulence (abs perlin fbm). */
    public static double turbulence2D(double x, double y, int octaves, double persistence, double lacunarity) {
        double total = 0, amp = 1, freq = 1, max = 0;
        for (int i = 0; i < octaves; i++) {
            total += Math.abs(perlin2D(x * freq, y * freq)) * amp;
            max += amp;
            amp *= persistence;
            freq *= lacunarity;
        }
        return total / max;
    }
}
