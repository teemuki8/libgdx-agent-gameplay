package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;

/** Immutable copied motor configuration for a bridge-owned revolute joint. */
public record Box2dRevoluteMotor(
        boolean enabled,
        double speedRadiansPerSecond,
        double maximumTorqueNewtonMetres) {
    /** Validates finite speed and non-negative torque, positive while enabled. */
    public Box2dRevoluteMotor {
        if (!Double.isFinite(speedRadiansPerSecond)
                || !Double.isFinite(maximumTorqueNewtonMetres)
                || maximumTorqueNewtonMetres < 0.0
                || (enabled && maximumTorqueNewtonMetres <= 0.0)) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "configure-revolute-motor",
                    "finite speed and finite non-negative torque, positive when enabled",
                    speedRadiansPerSecond + ":" + maximumTorqueNewtonMetres,
                    "Disable a zero-torque motor or supply positive finite torque.");
        }
    }
}
