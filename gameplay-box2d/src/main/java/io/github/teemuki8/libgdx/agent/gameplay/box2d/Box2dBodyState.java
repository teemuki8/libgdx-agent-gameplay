package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable copied Box2D state that never exposes native identity. */
public record Box2dBodyState(
        EntityId entityId,
        String fixtureId,
        Box2dBodyType bodyType,
        Vec2 positionRenderUnits,
        Vec2 velocityRenderUnitsPerSecond,
        double angleRadians,
        double angularVelocityRadiansPerSecond,
        double massKilograms,
        double rotationalInertiaKilogramMetresSquared,
        Collider.Shape colliderShape,
        Vec2 colliderSize,
        Vec2 colliderOffset,
        boolean sensor,
        boolean active) {
    /** Validates copied stable state. */
    public Box2dBodyState {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(fixtureId, "fixtureId");
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(positionRenderUnits, "positionRenderUnits");
        Objects.requireNonNull(velocityRenderUnitsPerSecond, "velocityRenderUnitsPerSecond");
        Objects.requireNonNull(colliderShape, "colliderShape");
        Objects.requireNonNull(colliderSize, "colliderSize");
        Objects.requireNonNull(colliderOffset, "colliderOffset");
        if (!Double.isFinite(angleRadians) || !Double.isFinite(angularVelocityRadiansPerSecond)
                || !Double.isFinite(massKilograms) || massKilograms < 0.0
                || !Double.isFinite(rotationalInertiaKilogramMetresSquared)
                || rotationalInertiaKilogramMetresSquared < 0.0) {
            throw new IllegalArgumentException("copied dynamics must be finite and non-negative where applicable");
        }
    }
}
