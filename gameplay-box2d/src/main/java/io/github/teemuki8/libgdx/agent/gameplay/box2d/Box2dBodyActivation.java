package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable copied pose and velocity used to activate one disabled dynamic body. */
public record Box2dBodyActivation(
        EntityId entityId,
        Vec2 positionRenderUnits,
        double angleRadians,
        Vec2 velocityRenderUnitsPerSecond,
        double angularVelocityRadiansPerSecond) {
    /** Validates all copied activation values before native narrowing. */
    public Box2dBodyActivation {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(positionRenderUnits, "positionRenderUnits");
        Objects.requireNonNull(velocityRenderUnitsPerSecond, "velocityRenderUnitsPerSecond");
        if (!Double.isFinite(angleRadians)
                || !Double.isFinite(angularVelocityRadiansPerSecond)) {
            throw new IllegalArgumentException("activation angles and velocities must be finite");
        }
    }
}
