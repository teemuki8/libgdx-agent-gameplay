package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Explicit fixed Box2D solver and world-behavior testimony. */
public record Box2dSolverSettings(
        int velocityIterations,
        int positionIterations,
        boolean sleepingAllowed,
        boolean warmStarting,
        boolean continuousPhysics) {
    /** Creates settings with the ordinary enabled Box2D world behaviors. */
    public Box2dSolverSettings(int velocityIterations, int positionIterations) {
        this(velocityIterations, positionIterations, true, true, true);
    }

    /** Validates bounded positive solver iteration counts. */
    public Box2dSolverSettings {
        if (velocityIterations < 1 || velocityIterations > 1_000
                || positionIterations < 1 || positionIterations > 1_000) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "configure-box2d-solver",
                    "velocity and position iterations in [1,1000]",
                    velocityIterations + ":" + positionIterations,
                    "Use explicit positive solver iteration counts.");
        }
    }
}
