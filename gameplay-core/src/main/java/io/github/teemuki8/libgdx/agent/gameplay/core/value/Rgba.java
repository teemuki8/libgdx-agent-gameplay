package io.github.teemuki8.libgdx.agent.gameplay.core.value;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Immutable normalized red, green, blue, and alpha values. */
public record Rgba(double red, double green, double blue, double alpha) {
    public static final Rgba WHITE = new Rgba(1.0, 1.0, 1.0, 1.0);

    /** Rejects channels outside the normalized range. */
    public Rgba {
        channel("red", red);
        channel("green", green);
        channel("blue", blue);
        channel("alpha", alpha);
    }

    private static void channel(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-color",
                    name + " in [0,1]",
                    Double.toString(value),
                    "Use a finite normalized color channel.");
        }
    }
}
