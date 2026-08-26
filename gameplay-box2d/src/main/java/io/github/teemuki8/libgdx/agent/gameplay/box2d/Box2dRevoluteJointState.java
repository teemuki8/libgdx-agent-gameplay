package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import java.util.Objects;

/** Immutable copied revolute-joint state with no native Box2D identity. */
public record Box2dRevoluteJointState(
        Box2dJointId id,
        EntityId first,
        EntityId second,
        double angleRadians,
        double speedRadiansPerSecond,
        boolean motorEnabled,
        double motorSpeedRadiansPerSecond,
        double maximumMotorTorqueNewtonMetres) {
    /** Validates stable endpoints and finite copied native values. */
    public Box2dRevoluteJointState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)
                || !Double.isFinite(angleRadians)
                || !Double.isFinite(speedRadiansPerSecond)
                || !Double.isFinite(motorSpeedRadiansPerSecond)
                || !Double.isFinite(maximumMotorTorqueNewtonMetres)
                || maximumMotorTorqueNewtonMetres < 0.0
                || (motorEnabled && maximumMotorTorqueNewtonMetres <= 0.0)) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "copy-revolute-joint-state",
                    "distinct endpoints and finite state with valid motor torque",
                    first + ":" + second + ":" + angleRadians + ":"
                            + speedRadiansPerSecond + ":" + motorSpeedRadiansPerSecond + ":"
                            + maximumMotorTorqueNewtonMetres,
                    "Copy a live bridge-owned revolute joint with valid finite state.");
        }
    }
}
