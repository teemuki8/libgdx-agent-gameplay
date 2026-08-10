package io.github.teemuki8.libgdx.agent.gameplay.core.random;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/**
 * Project-owned SplitMix64 stream with stable cross-platform output.
 *
 * <p>This class deliberately does not delegate reproducibility to a platform RNG.</p>
 */
public final class DeterministicRandom {
    private static final long GAMMA = 0x9e3779b97f4a7c15L;
    private static final long MIX_ONE = 0xbf58476d1ce4e5b9L;
    private static final long MIX_TWO = 0x94d049bb133111ebL;
    private long state;

    /** Starts the stream from the exact 64-bit seed. */
    public DeterministicRandom(long seed) {
        state = seed;
    }

    /** Returns the next SplitMix64 value. */
    public long nextLong() {
        state += GAMMA;
        long mixed = state;
        mixed = (mixed ^ mixed >>> 30) * MIX_ONE;
        mixed = (mixed ^ mixed >>> 27) * MIX_TWO;
        return mixed ^ mixed >>> 31;
    }

    /** Returns an unbiased value in {@code [0, bound)}. */
    public int nextInt(int bound) {
        if (bound < 1) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                    "draw-deterministic-random",
                    "bound >= 1",
                    Integer.toString(bound),
                    "Use a positive exclusive bound.");
        }
        long candidate = nextLong() >>> 1;
        long result = candidate % bound;
        while (candidate - result + (bound - 1L) < 0L) {
            candidate = nextLong() >>> 1;
            result = candidate % bound;
        }
        return (int) result;
    }

    /** Returns a value in {@code [0.0, 1.0)} from the next 53 random bits. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }
}
