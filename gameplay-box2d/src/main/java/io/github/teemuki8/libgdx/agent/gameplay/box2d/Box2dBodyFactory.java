package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.box2d.Box2d;
import com.badlogic.gdx.box2d.enums.b2BodyType;
import com.badlogic.gdx.box2d.structs.b2BodyDef;
import com.badlogic.gdx.box2d.structs.b2BodyId;
import com.badlogic.gdx.box2d.structs.b2Capsule;
import com.badlogic.gdx.box2d.structs.b2Circle;
import com.badlogic.gdx.box2d.structs.b2Polygon;
import com.badlogic.gdx.box2d.structs.b2Rot;
import com.badlogic.gdx.box2d.structs.b2ShapeDef;
import com.badlogic.gdx.box2d.structs.b2ShapeId;
import com.badlogic.gdx.box2d.structs.b2Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityView;
import java.util.Objects;

/** Creates Box2D 3 bodies and genuine shapes from copied gameplay values. */
public final class Box2dBodyFactory {
    private final Box2dUnitConversion units;
    private final Box2dBodySpecResolver resolver;
    private final b2BodyDef bodyDef = new b2BodyDef();
    private final b2ShapeDef shapeDef = new b2ShapeDef();
    private final Thread ownerThread;
    private boolean claimed;
    private final b2Vec2 offset = new b2Vec2();
    private final b2Rot identity = new b2Rot();
    private final b2Polygon polygon = new b2Polygon();
    private final b2Circle circle = new b2Circle();
    private final b2Capsule capsule = new b2Capsule();

    /**
     * Creates a single-bridge factory whose reusable native scratch belongs to this thread.
     *
     * @param units immutable render/SI conversion
     * @param resolver complete body policy
     */
    public Box2dBodyFactory(Box2dUnitConversion units, Box2dBodySpecResolver resolver) {
        this.units = Objects.requireNonNull(units, "units");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        ownerThread = Thread.currentThread();
    }

    /** Returns the immutable unit conversion used for every body and shape. */
    public Box2dUnitConversion units() {
        return units;
    }

    void claimForBridge() {
        requireOwner("claim-box2d-body-factory");
        if (claimed) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "claim-box2d-body-factory",
                    "factory claimed by exactly one bridge",
                    "factory already claimed",
                    "Construct one factory per bridge on the bridge owner thread.");
        }
        claimed = true;
    }

    Box2dBodyHandle create(GameplayBox2dWorld world, EntityView entity) {
        requireOwner("create-box2d-body");
        Objects.requireNonNull(world, "world").requireUnlocked();
        EntityView checked = Objects.requireNonNull(entity, "entity");
        Transform2D transform = checked.component(Transform2D.TYPE)
                .orElseThrow(() -> missing(checked, "transform"));
        Collider collider = checked.component(Collider.TYPE)
                .orElseThrow(() -> missing(checked, "collider"));
        Box2dBodySpec spec = Objects.requireNonNull(resolver.resolve(checked),
                "body specification");
        String fixtureId = fixtureId(checked);
        NativeGeometry geometry = validateGeometry(collider);

        Box2d.b2DefaultBodyDef(bodyDef);
        bodyDef.type(nativeType(spec.type()));
        bodyDef.position().x(units.toPhysicsFloat(transform.position().x(), "position.x"));
        bodyDef.position().y(units.toPhysicsFloat(transform.position().y(), "position.y"));
        Box2d.b2MakeRot(finiteFloat(transform.rotationRadians(), "rotation"), bodyDef.rotation());
        bodyDef.linearDamping(finiteFloat(spec.linearDamping(), "linearDamping"));
        bodyDef.angularDamping(finiteFloat(spec.angularDamping(), "angularDamping"));
        bodyDef.gravityScale(finiteFloat(spec.gravityScale(), "gravityScale"));
        bodyDef.isBullet(spec.bullet());
        bodyDef.fixedRotation(spec.fixedRotation());
        checked.component(Movement.TYPE).ifPresent(movement -> {
            bodyDef.linearVelocity().x(units.toPhysicsFloat(movement.velocity().x(), "velocity.x"));
            bodyDef.linearVelocity().y(units.toPhysicsFloat(movement.velocity().y(), "velocity.y"));
        });

        b2BodyId body = Box2d.b2CreateBody(world.id(), bodyDef.asPointer());
        b2ShapeId shape = null;
        try {
            Box2d.b2DefaultShapeDef(shapeDef);
            shapeDef.density(finiteFloat(spec.densityKilogramsPerSquareMetre(), "density"));
            shapeDef.material().friction(finiteFloat(spec.friction(), "friction"));
            shapeDef.material().restitution(finiteFloat(spec.restitution(), "restitution"));
            shapeDef.isSensor(collider.sensor());
            shapeDef.filter().categoryBits(collider.categoryBits());
            shapeDef.filter().maskBits(collider.maskBits());
            shapeDef.enableContactEvents(true);
            shapeDef.enableHitEvents(true);
            shape = createShape(body, collider.shape(), geometry);
            return new Box2dBodyHandle(checked.id(), fixtureId, spec.type(), body, shape);
        } catch (RuntimeException | Error failure) {
            if (shape != null && Box2d.b2Shape_IsValid(shape)) {
                Box2d.b2DestroyShape(shape, true);
            }
            if (Box2d.b2Body_IsValid(body)) {
                Box2d.b2DestroyBody(body);
            }
            throw failure;
        }
    }

    private NativeGeometry validateGeometry(Collider collider) {
        if (collider.shape() == Collider.Shape.CIRCLE
                && Double.compare(collider.size().x(), collider.size().y()) != 0) {
            throw unsupported("exactly equal circle dimensions", collider);
        }
        if (collider.shape() == Collider.Shape.CAPSULE
                && Double.compare(collider.size().x(), collider.size().y()) == 0) {
            throw unsupported("capsule with unequal dimensions", collider);
        }
        float width = units.toPhysicsFloat(collider.size().x(), "collider.width");
        float height = units.toPhysicsFloat(collider.size().y(), "collider.height");
        requirePositive(width, "positive converted collider width", collider);
        requirePositive(height, "positive converted collider height", collider);
        float radius = Math.min(width, height) * 0.5f;
        float halfSegment = Math.max(width, height) * 0.5f - radius;
        switch (collider.shape()) {
            case BOX -> {
                requirePositive(width * 0.5f, "positive converted box half-width", collider);
                requirePositive(height * 0.5f, "positive converted box half-height", collider);
            }
            case CIRCLE ->
                    requirePositive(radius, "positive converted circle radius", collider);
            case CAPSULE -> {
                requirePositive(radius, "positive converted capsule radius", collider);
                requirePositive(halfSegment,
                        "positive converted capsule centre half-segment", collider);
            }
        }
        return new NativeGeometry(width, height,
                units.toPhysicsFloat(collider.offset().x(), "collider.offset.x"),
                units.toPhysicsFloat(collider.offset().y(), "collider.offset.y"),
                radius, halfSegment);
    }

    private b2ShapeId createShape(
            b2BodyId body, Collider.Shape shape, NativeGeometry geometry) {
        offset.x(geometry.offsetX());
        offset.y(geometry.offsetY());
        return switch (shape) {
            case BOX -> {
                Box2d.b2MakeRot(0.0f, identity);
                Box2d.b2MakeOffsetBox(
                        geometry.width() * 0.5f, geometry.height() * 0.5f,
                        offset, identity, polygon);
                yield Box2d.b2CreatePolygonShape(
                        body, shapeDef.asPointer(), polygon.asPointer());
            }
            case CIRCLE -> {
                circle.center().x(offset.x());
                circle.center().y(offset.y());
                circle.radius(geometry.radius());
                yield Box2d.b2CreateCircleShape(body, shapeDef.asPointer(), circle.asPointer());
            }
            case CAPSULE -> {
                capsule.radius(geometry.radius());
                if (geometry.width() > geometry.height()) {
                    capsule.center1().x(offset.x() - geometry.halfSegment());
                    capsule.center1().y(offset.y());
                    capsule.center2().x(offset.x() + geometry.halfSegment());
                    capsule.center2().y(offset.y());
                } else {
                    capsule.center1().x(offset.x());
                    capsule.center1().y(offset.y() - geometry.halfSegment());
                    capsule.center2().x(offset.x());
                    capsule.center2().y(offset.y() + geometry.halfSegment());
                }
                yield Box2d.b2CreateCapsuleShape(
                        body, shapeDef.asPointer(), capsule.asPointer());
            }
        };
    }

    private static void requirePositive(
            float value, String expected, Collider collider) {
        if (!Float.isFinite(value) || value <= 0.0f) {
            throw unsupported(expected, collider);
        }
    }

    private record NativeGeometry(
            float width,
            float height,
            float offsetX,
            float offsetY,
            float radius,
            float halfSegment) {
    }

    private static b2BodyType nativeType(Box2dBodyType type) {
        return switch (type) {
            case STATIC -> b2BodyType.b2_staticBody;
            case KINEMATIC -> b2BodyType.b2_kinematicBody;
            case DYNAMIC -> b2BodyType.b2_dynamicBody;
        };
    }

    private static GameplayException unsupported(String expected, Collider collider) {
        return GameplayException.validation(GameplayDiagnosticCode.BOX2D_UNSUPPORTED_COLLIDER,
                "create-box2d-shape", expected, collider.size().toString(),
                "Select geometry with valid complete bounds.");
    }

    private void requireOwner(String operation) {
        if (Thread.currentThread() != ownerThread) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.OWNER_THREAD_VIOLATION,
                    operation,
                    "factory owner thread " + ownerThread.getName(),
                    Thread.currentThread().getName(),
                    "Construct and use one factory on one bridge owner thread.");
        }
    }

    private static String fixtureId(EntityView entity) {
        String value = entity.id().value() + ".collider";
        if (value.length() > 220) {
            throw GameplayException.validation(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "derive-box2d-fixture-id", "entity ID short enough for .collider", value,
                    "Use an entity ID of at most 211 characters.");
        }
        return value;
    }

    static float finiteFloat(double value, String field) {
        float narrowed = (float) value;
        if (!Double.isFinite(value) || !Float.isFinite(narrowed)) {
            throw GameplayException.validation(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "create-box2d-body", "finite float-representable " + field,
                    Double.toString(value), "Use a bounded native scalar.");
        }
        return narrowed;
    }

    private static GameplayException missing(EntityView entity, String component) {
        return GameplayException.validation(GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                "create-box2d-body", "entity with transform and collider",
                entity.id() + " missing " + component,
                "Attach both standard components before activation.");
    }
}
