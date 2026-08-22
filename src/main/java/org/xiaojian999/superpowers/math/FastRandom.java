package org.xiaojian999.superpowers.math;

/**
 * Fast deterministic RNGs. All stateless per-seed – perfect for particle
 * jitter where you need stable randomness from (x,y,z,t) without storing a
 * {@link java.util.Random}.
 */
public final class FastRandom {
    private FastRandom() {}

    // SplitMix64 – excellent for hashing seeds/positions
    public static long splitMix64(long x) {
        x += 0x9E3779B97F4A7C15L;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        return x ^ (x >>> 31);
    }

    public static long xorshift64(long x) {
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        return x;
    }

    // XorShift* with output mixer
    public static long xorShiftStar64(long[] state) {
        long x = state[0];
        x ^= x >> 12;
        x ^= x << 25;
        x ^= x >> 27;
        state[0] = x;
        return x * 0x2545F4914F6CDD1DL;
    }

    public static double randomDouble(long seed) {
        long h = splitMix64(seed);
        // top 53 bits -> [0,1)
        return ((h >>> 11) & ((1L << 53) - 1)) * 0x1.0p-53;
    }

    public static float randomFloat(long seed) {
        long h = splitMix64(seed);
        return ((h >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }

    public static int randomInt(long seed, int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        long h = splitMix64(seed) >>> 1; // positive
        return (int) (h % bound);
    }

    public static boolean randomBool(long seed) {
        return (splitMix64(seed) & 1) != 0;
    }

    /** Hash two ints to [0,1). */
    public static double hash2D(int x, int y) {
        long h = ((long) x * 374761393L) ^ ((long) y * 668265263L) ^ 0x9E3779B9L;
        return randomDouble(h);
    }

    public static double hash3D(int x, int y, int z) {
        long h = ((long) x * 374761393L) ^ ((long) y * 668265263L) ^ ((long) z * 15485863L) ^ 0xA09E667FL;
        return randomDouble(h);
    }

    /** Jitter a value by ±amount deterministically. */
    public static double jitter(long seed, double value, double amount) {
        return value + (randomDouble(seed) * 2 - 1) * amount;
    }

    /** Box-Muller gaussian from two uniform hashes. */
    public static double gaussian(long seed) {
        double u1 = randomDouble(seed);
        double u2 = randomDouble(splitMix64(seed));
        if (u1 < 1e-9) u1 = 1e-9;
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(GlungFastMath.TAU * u2);
    }

    // Mutable fast generator for per-tick loops where you want sequence
    public static final class XorShift {
        private long state;
        public XorShift(long seed) { this.state = seed != 0 ? seed : 0x9E3779B97F4A7C15L; }
        public long nextLong() { return state = xorshift64(state); }
        public int nextInt() { return (int) (nextLong() & 0x7FFFFFFF); }
        public int nextInt(int bound) { return (int) ((nextLong() >>> 1) % bound); }
        public float nextFloat() { return (nextLong() >>> 40 & 0xFFFFFF) / (float) 0x1000000; }
        public double nextDouble() { return ((nextLong() >>> 11) & ((1L << 53)-1)) * 0x1.0p-53; }
        public double nextGaussian() {
            double u1 = nextDouble(); double u2 = nextDouble();
            if (u1 < 1e-9) u1 = 1e-9;
            return Math.sqrt(-2 * Math.log(u1)) * Math.cos(GlungFastMath.TAU * u2);
        }
    }
}
