package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.physics.box2d.BodyDef;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import java.util.Objects;

/** Immutable copied Box2D state that never exposes native object identity. */
public record Box2dBodyState(
        EntityId entityId,
        String fixtureId,
        BodyDef.BodyType bodyType,
        Vec2 position,
        Vec2 velocity,
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
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
        Objects.requireNonNull(colliderShape, "colliderShape");
        Objects.requireNonNull(colliderSize, "colliderSize");
        Objects.requireNonNull(colliderOffset, "colliderOffset");
    }
}
