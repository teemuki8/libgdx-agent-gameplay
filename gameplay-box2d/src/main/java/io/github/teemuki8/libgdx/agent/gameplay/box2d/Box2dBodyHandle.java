package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dRegistration;
import java.util.Objects;

/** Package-private bridge-owned mapping to native Box2D identity. */
final class Box2dBodyHandle {
    private final EntityId entityId;
    private final String fixtureId;
    private final BodyDef.BodyType bodyType;
    private final Body body;
    private final Fixture fixture;
    private final Collider collider;
    private Box2dRegistration<Body> bodyRegistration;
    private Box2dRegistration<Fixture> fixtureRegistration;
    private boolean disposed;

    Box2dBodyHandle(
            EntityId entityId,
            String fixtureId,
            BodyDef.BodyType bodyType,
            Body body,
            Fixture fixture,
            Collider collider) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.fixtureId = Objects.requireNonNull(fixtureId, "fixtureId");
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.body = Objects.requireNonNull(body, "body");
        this.fixture = Objects.requireNonNull(fixture, "fixture");
        this.collider = Objects.requireNonNull(collider, "collider");
    }

    /** Returns the stable gameplay entity identity. */
    EntityId entityId() {
        return entityId;
    }

    /** Returns the stable derived fixture identity. */
    String fixtureId() {
        return fixtureId;
    }

    /** Returns the immutable authority policy selected at body creation. */
    BodyDef.BodyType bodyType() {
        return bodyType;
    }

    /** Returns the live bridge-created body while this handle remains registered. */
    Body body() {
        return body;
    }

    /** Returns the live bridge-created fixture while this handle remains registered. */
    Fixture fixture() {
        return fixture;
    }

    /** Reports whether native destruction has completed. */
    boolean disposed() {
        return disposed;
    }

    Box2dBodyState state(Box2dUnitConversion units) {
        Vec2 position = units.toRenderUnits(body.getPosition().x, body.getPosition().y);
        Vec2 velocity = units.toRenderUnits(
                body.getLinearVelocity().x, body.getLinearVelocity().y);
        return new Box2dBodyState(entityId, fixtureId, bodyType, position, velocity,
                collider.shape(), collider.size(), collider.offset(), collider.sensor(),
                body.isActive());
    }

    void attachInspection(
            Box2dRegistration<Body> bodyValue,
            Box2dRegistration<Fixture> fixtureValue) {
        bodyRegistration = Objects.requireNonNull(bodyValue, "bodyRegistration");
        fixtureRegistration = Objects.requireNonNull(fixtureValue, "fixtureRegistration");
    }

    void unregisterInspection() {
        if (fixtureRegistration != null) {
            fixtureRegistration.close();
            fixtureRegistration = null;
        }
        if (bodyRegistration != null) {
            bodyRegistration.close();
            bodyRegistration = null;
        }
    }

    void markDisposed() {
        disposed = true;
    }
}
