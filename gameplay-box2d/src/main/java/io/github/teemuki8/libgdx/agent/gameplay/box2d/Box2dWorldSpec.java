package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable application-owned Box2D world configuration. */
public record Box2dWorldSpec(
        Vec2 gravityMetresPerSecondSquared,
        int subStepCount,
        double hitEventThresholdMetresPerSecond) {
    /** Validates deterministic zero-worker world settings. */
    public Box2dWorldSpec {
        Objects.requireNonNull(gravityMetresPerSecondSquared,
                "gravityMetresPerSecondSquared");
        if (subStepCount < 1 || subStepCount > 16) {
            throw new IllegalArgumentException("subStepCount must be in [1,16]");
        }
        if (!Double.isFinite(hitEventThresholdMetresPerSecond)
                || hitEventThresholdMetresPerSecond < 0.0) {
            throw new IllegalArgumentException(
                    "hitEventThresholdMetresPerSecond must be finite and non-negative");
        }
    }
}
