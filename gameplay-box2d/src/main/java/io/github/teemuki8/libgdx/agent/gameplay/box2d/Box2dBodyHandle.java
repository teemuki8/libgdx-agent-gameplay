package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.box2d.Box2d;
import com.badlogic.gdx.box2d.structs.b2BodyId;
import com.badlogic.gdx.box2d.structs.b2Capsule;
import com.badlogic.gdx.box2d.structs.b2Circle;
import com.badlogic.gdx.box2d.structs.b2Polygon;
import com.badlogic.gdx.box2d.structs.b2Rot;
import com.badlogic.gdx.box2d.structs.b2ShapeId;
import com.badlogic.gdx.box2d.structs.b2Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.runtime.box2d.Box2dRegistration;
import java.util.Objects;

/** Package-private bridge-owned mapping to native Box2D 3 identity. */
final class Box2dBodyHandle {
    private final EntityId entityId;
    private final String fixtureId;
    private final Box2dBodyType bodyType;
    private final b2BodyId body;
    private final b2ShapeId shape;
    private final b2Vec2 positionScratch = new b2Vec2();
    private final b2Vec2 velocityScratch = new b2Vec2();
    private final b2Rot rotationScratch = new b2Rot();
    private final b2Polygon polygonScratch = new b2Polygon();
    private final b2Circle circleScratch = new b2Circle();
    private final b2Capsule capsuleScratch = new b2Capsule();
    private Box2dRegistration<b2BodyId> bodyRegistration;
    private Box2dRegistration<b2ShapeId> shapeRegistration;
    private boolean disposed;

    Box2dBodyHandle(EntityId entityId, String fixtureId, Box2dBodyType bodyType,
            b2BodyId body, b2ShapeId shape) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.fixtureId = Objects.requireNonNull(fixtureId, "fixtureId");
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.body = Objects.requireNonNull(body, "body");
        this.shape = Objects.requireNonNull(shape, "shape");
    }

    EntityId entityId() { return entityId; }
    String fixtureId() { return fixtureId; }
    Box2dBodyType bodyType() { return bodyType; }
    b2BodyId body() { return body; }
    b2ShapeId shape() { return shape; }
    boolean disposed() { return disposed; }

    Box2dBodyState state(Box2dUnitConversion units) {
        requireLive();
        Box2d.b2Body_GetPosition(body, positionScratch);
        Box2d.b2Body_GetLinearVelocity(body, velocityScratch);
        Box2d.b2Body_GetRotation(body, rotationScratch);
        ShapeState geometry = shapeState(units);
        return new Box2dBodyState(entityId, fixtureId, bodyType,
                units.toRenderUnits(positionScratch.x(), positionScratch.y()),
                units.toRenderUnits(velocityScratch.x(), velocityScratch.y()),
                Box2d.b2Rot_GetAngle(rotationScratch), Box2d.b2Body_GetAngularVelocity(body),
                Box2d.b2Body_GetMass(body), Box2d.b2Body_GetRotationalInertia(body),
                geometry.shape(), geometry.size(), geometry.offset(),
                Box2d.b2Shape_IsSensor(shape), Box2d.b2Body_IsEnabled(body));
    }

    private ShapeState shapeState(Box2dUnitConversion units) {
        return switch (Box2d.b2Shape_GetType(shape)) {
            case b2_polygonShape -> polygonState(units);
            case b2_circleShape -> circleState(units);
            case b2_capsuleShape -> capsuleState(units);
            default -> throw new IllegalStateException("unsupported bridge-owned Box2D shape type");
        };
    }

    private ShapeState polygonState(Box2dUnitConversion units) {
        Box2d.b2Shape_GetPolygon(shape, polygonScratch);
        float minimumX = Float.POSITIVE_INFINITY;
        float minimumY = Float.POSITIVE_INFINITY;
        float maximumX = Float.NEGATIVE_INFINITY;
        float maximumY = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < polygonScratch.count(); index++) {
            b2Vec2 vertex = polygonScratch.vertices().asStackElement(index);
            minimumX = Math.min(minimumX, vertex.x());
            minimumY = Math.min(minimumY, vertex.y());
            maximumX = Math.max(maximumX, vertex.x());
            maximumY = Math.max(maximumY, vertex.y());
        }
        return geometry(Collider.Shape.BOX, minimumX, minimumY, maximumX, maximumY, units);
    }

    private ShapeState circleState(Box2dUnitConversion units) {
        Box2d.b2Shape_GetCircle(shape, circleScratch);
        float radius = circleScratch.radius();
        return geometry(Collider.Shape.CIRCLE,
                circleScratch.center().x() - radius, circleScratch.center().y() - radius,
                circleScratch.center().x() + radius, circleScratch.center().y() + radius, units);
    }

    private ShapeState capsuleState(Box2dUnitConversion units) {
        Box2d.b2Shape_GetCapsule(shape, capsuleScratch);
        float radius = capsuleScratch.radius();
        float minimumX = Math.min(capsuleScratch.center1().x(), capsuleScratch.center2().x())
                - radius;
        float minimumY = Math.min(capsuleScratch.center1().y(), capsuleScratch.center2().y())
                - radius;
        float maximumX = Math.max(capsuleScratch.center1().x(), capsuleScratch.center2().x())
                + radius;
        float maximumY = Math.max(capsuleScratch.center1().y(), capsuleScratch.center2().y())
                + radius;
        return geometry(Collider.Shape.CAPSULE,
                minimumX, minimumY, maximumX, maximumY, units);
    }

    private static ShapeState geometry(Collider.Shape shape, float minimumX, float minimumY,
            float maximumX, float maximumY, Box2dUnitConversion units) {
        return new ShapeState(shape,
                units.toRenderUnits(maximumX - minimumX, maximumY - minimumY),
                units.toRenderUnits((minimumX + maximumX) * 0.5,
                        (minimumY + maximumY) * 0.5));
    }

    void attachInspection(Box2dRegistration<b2BodyId> bodyValue,
            Box2dRegistration<b2ShapeId> shapeValue) {
        bodyRegistration = Objects.requireNonNull(bodyValue, "bodyRegistration");
        shapeRegistration = Objects.requireNonNull(shapeValue, "shapeRegistration");
    }

    void unregisterInspection() {
        if (shapeRegistration != null) {
            shapeRegistration.close();
            shapeRegistration = null;
        }
        if (bodyRegistration != null) {
            bodyRegistration.close();
            bodyRegistration = null;
        }
    }

    void markDisposed() { disposed = true; }

    private void requireLive() {
        if (disposed || !Box2d.b2Body_IsValid(body) || !Box2d.b2Shape_IsValid(shape)) {
            throw new IllegalStateException("bridge-owned Box2D body identity is stale");
        }
    }

    private record ShapeState(Collider.Shape shape, Vec2 size, Vec2 offset) {
    }

    record ShapeKey(int index1, char world0, char generation) implements Comparable<ShapeKey> {
        static ShapeKey copyOf(b2ShapeId id) {
            return new ShapeKey(id.index1(), id.world0(), id.generation());
        }
        @Override public int compareTo(ShapeKey other) {
            int byIndex = Integer.compare(index1, other.index1);
            if (byIndex != 0) return byIndex;
            int byWorld = Character.compare(world0, other.world0);
            return byWorld != 0 ? byWorld : Character.compare(generation, other.generation);
        }
    }
}
