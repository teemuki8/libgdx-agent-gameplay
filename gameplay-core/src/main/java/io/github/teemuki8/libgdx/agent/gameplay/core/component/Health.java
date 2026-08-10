package io.github.teemuki8.libgdx.agent.gameplay.core.component;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Current and maximum integral health. */
public record Health(long current, long max) implements Component {
    public static final ComponentType<Health> TYPE = new ComponentType<>("health", Health.class);

    /** Validates a positive maximum and current health within range. */
    public Health {
        if (max < 1 || current < 0 || current > max) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.INVALID_COMPONENT_VALUE,
                    "create-health",
                    "max >= 1 and current in [0,max]",
                    "current=" + current + ",max=" + max,
                    "Clamp current health and use a positive maximum.");
        }
    }
}
