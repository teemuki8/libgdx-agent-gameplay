package io.github.teemuki8.libgdx.agent.gameplay.libgdx;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import java.time.Duration;
import java.util.Objects;

/** Bounded accumulator that advances simulation only in exact fixed ticks. */
public final class FixedStepLoop {
    public static final Duration MAX_RENDER_DELTA = Duration.ofMillis(250);
    public static final Duration MAX_RETAINED_TIME = Duration.ofMillis(250);
    private static final int MAX_CATCH_UP_TICKS = 60;

    private final long stepNanos;
    private final int maxCatchUpTicks;
    private long retainedNanos;
    private long lastPollNanos;
    private boolean polled;

    /** Creates a loop with a positive fixed duration and bounded catch-up count. */
    public FixedStepLoop(Duration fixedStep, int maxCatchUpTicks) {
        Objects.requireNonNull(fixedStep, "fixedStep");
        try {
            stepNanos = fixedStep.toNanos();
        } catch (ArithmeticException failure) {
            throw invalid("finite positive fixed step", fixedStep.toString());
        }
        if (stepNanos < 1 || maxCatchUpTicks < 1
                || maxCatchUpTicks > MAX_CATCH_UP_TICKS) {
            throw invalid("positive fixed step and catch-up ticks in [1,"
                    + MAX_CATCH_UP_TICKS + "]",
                    stepNanos + ":" + maxCatchUpTicks);
        }
        this.maxCatchUpTicks = maxCatchUpTicks;
    }

    /** Polls {@link System#nanoTime()} and advances due fixed ticks. */
    public int poll(Runnable fixedTick) {
        long now = System.nanoTime();
        if (!polled) {
            polled = true;
            lastPollNanos = now;
            return 0;
        }
        long elapsed = now - lastPollNanos;
        lastPollNanos = now;
        return advance(Duration.ofNanos(Math.max(0, elapsed)), fixedTick);
    }

    /** Adds one measured render duration, runs due ticks, and retains the remainder. */
    public int advance(Duration elapsed, Runnable fixedTick) {
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(fixedTick, "fixedTick");
        long measured;
        try {
            measured = elapsed.toNanos();
        } catch (ArithmeticException failure) {
            measured = MAX_RENDER_DELTA.toNanos();
        }
        if (measured < 0) {
            throw invalid("non-negative elapsed time", elapsed.toString());
        }
        try {
            retainedNanos = Math.addExact(
                    retainedNanos, Math.min(measured, MAX_RENDER_DELTA.toNanos()));
        } catch (ArithmeticException failure) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.FRAME_BACKLOG_EXCEEDED,
                    "advance-fixed-step-loop",
                    "bounded retained time",
                    "duration overflow",
                    "Restart the loop after diagnosing sustained frame starvation.");
        }
        int advanced = 0;
        while (retainedNanos >= stepNanos && advanced < maxCatchUpTicks) {
            fixedTick.run();
            retainedNanos -= stepNanos;
            advanced++;
        }
        if (retainedNanos > MAX_RETAINED_TIME.toNanos()) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.FRAME_BACKLOG_EXCEEDED,
                    "advance-fixed-step-loop",
                    "retained time <= " + MAX_RETAINED_TIME.toNanos() + "ns",
                    Long.toString(retainedNanos),
                    "Reduce frame work or increase catch-up ticks within the fixed bound.");
        }
        return advanced;
    }

    /** Returns whether at least one complete fixed tick remains queued. */
    public boolean hasBacklog() {
        return retainedNanos >= stepNanos;
    }

    /** Returns interpolation alpha for presentation only. */
    public double interpolationAlpha() {
        return Math.min(1.0, (double) retainedNanos / stepNanos);
    }

    private static GameplayException invalid(String expected, String observed) {
        return GameplayException.validation(
                GameplayDiagnosticCode.LIMIT_OUT_OF_RANGE,
                "configure-fixed-step-loop",
                expected,
                observed,
                "Use a bounded desktop fixed-step configuration.");
    }
}
