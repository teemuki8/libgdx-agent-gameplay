package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable copied raycast result. */
public record Box2dRaycastHit(
        EntityId entityId,
        String fixtureId,
        Vec2 pointRenderUnits,
        Vec2 normal,
        double fraction) {
    /** Validates one copied hit. */
    public Box2dRaycastHit {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(fixtureId, "fixtureId");
        Objects.requireNonNull(pointRenderUnits, "pointRenderUnits");
        Objects.requireNonNull(normal, "normal");
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("raycast fraction must be finite and in [0,1]");
        }
    }
}
