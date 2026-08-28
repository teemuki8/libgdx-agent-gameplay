package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/**
 * Immutable copied pose and velocity used to enable one disabled mapped dynamic body.
 *
 * <p>Positions and linear velocities use render units. Angular values use radians. This value
 * contains no native body identity and is validated before the bridge narrows it for Box2D.
 *
 * @param entityId stable gameplay entity whose mapped body will be enabled
 * @param positionRenderUnits copied world position in render units
 * @param angleRadians copied world rotation in radians
 * @param velocityRenderUnitsPerSecond copied linear velocity in render units per second
 * @param angularVelocityRadiansPerSecond copied angular velocity in radians per second
 */
public record Box2dBodyActivation(
        EntityId entityId,
        Vec2 positionRenderUnits,
        double angleRadians,
        Vec2 velocityRenderUnitsPerSecond,
        double angularVelocityRadiansPerSecond) {
    /** Validates the stable entity and all copied activation values before native narrowing. */
    public Box2dBodyActivation {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(positionRenderUnits, "positionRenderUnits");
        Objects.requireNonNull(velocityRenderUnitsPerSecond, "velocityRenderUnitsPerSecond");
        if (!Double.isFinite(angleRadians)
                || !Double.isFinite(angularVelocityRadiansPerSecond)) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "create-box2d-body-activation",
                    "finite angle and angular velocity",
                    angleRadians + ":" + angularVelocityRadiansPerSecond,
                    "Replace NaN or infinity with finite copied dynamics.");
        }
    }
}
