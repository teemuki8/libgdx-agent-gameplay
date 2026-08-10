package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Intended velocity and its non-negative speed limit. */
public record Movement(Vec2 velocity, double maxSpeed) implements Component {
    public static final ComponentType<Movement> TYPE =
            new ComponentType<>("movement", Movement.class);

    /** Validates the finite velocity and speed bound. */
    public Movement {
        Objects.requireNonNull(velocity, "velocity");
        if (!Double.isFinite(maxSpeed) || maxSpeed < 0.0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-movement",
                    "finite maxSpeed greater than or equal to zero",
                    Double.toString(maxSpeed),
                    "Use a finite non-negative movement speed.");
        }
    }
}
