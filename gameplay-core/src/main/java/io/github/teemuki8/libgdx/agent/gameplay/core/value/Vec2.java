package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Immutable finite two-dimensional vector. */
public record Vec2(double x, double y) {
    public static final Vec2 ZERO = new Vec2(0.0, 0.0);

    /** Rejects non-finite coordinates. */
    public Vec2 {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-vector",
                    "finite x and y",
                    x + "," + y,
                    "Replace NaN or infinity with finite world coordinates.");
        }
    }
}
