package com.edreams.mock.inventory;

/**
 * Deterministic pseudo-random decisions. Every choice in the generator is a function of the seed,
 * an index and a salt, so the same configuration always yields the same inventory.
 */
public final class Hashing {

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private Hashing() {
    }

    /** Mixes all parts into one 64-bit hash (splitmix64 finaliser per part). */
    public static long mix(long... parts) {
        long h = GOLDEN;
        for (long part : parts) {
            h = splitmix64(h ^ part);
        }
        return h;
    }

    /** Uniform double in [0, 1). */
    public static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    /** Uniform int in [0, bound). */
    public static int bounded(long hash, int bound) {
        return (int) Math.floorMod(hash, (long) bound);
    }

    private static long splitmix64(long z) {
        z += GOLDEN;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
