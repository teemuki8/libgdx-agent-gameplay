package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dRegistration;
import java.util.Objects;

/** Stable bridge-owned mapping to one native body and its canonical collider fixture. */
public final class Box2dBodyHandle {
    private final EntityId entityId;
    private final String fixtureId;
    private final BodyDef.BodyType bodyType;
    private final Body body;
    private final Fixture fixture;
    private Box2dRegistration<Body> bodyRegistration;
    private Box2dRegistration<Fixture> fixtureRegistration;
    private boolean disposed;

    Box2dBodyHandle(
            EntityId entityId,
            String fixtureId,
            BodyDef.BodyType bodyType,
            Body body,
            Fixture fixture) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.fixtureId = Objects.requireNonNull(fixtureId, "fixtureId");
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.body = Objects.requireNonNull(body, "body");
        this.fixture = Objects.requireNonNull(fixture, "fixture");
    }

    /** Returns the stable gameplay entity identity. */
    public EntityId entityId() {
        return entityId;
    }

    /** Returns the stable derived fixture identity. */
    public String fixtureId() {
        return fixtureId;
    }

    /** Returns the immutable authority policy selected at body creation. */
    public BodyDef.BodyType bodyType() {
        return bodyType;
    }

    /** Returns the live bridge-created body while this handle remains registered. */
    public Body body() {
        return body;
    }

    /** Returns the live bridge-created fixture while this handle remains registered. */
    public Fixture fixture() {
        return fixture;
    }

    /** Reports whether native destruction has completed. */
    public boolean disposed() {
        return disposed;
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
