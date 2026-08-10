package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.Shape;
import com.badlogic.gdx.physics.box2d.World;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Movement;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform2D;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityView;
import java.util.Objects;

/** Creates native bodies from explicit gameplay components and an application body-type policy. */
public final class Box2dBodyFactory {
    /** Selects immutable authority policy for one newly activated physics entity. */
    @FunctionalInterface
    public interface BodyTypeResolver {
        /** Returns the Box2D body type that will remain authoritative for the entity lifetime. */
        BodyDef.BodyType resolve(EntityView entity);
    }

    private final Box2dUnitConversion units;
    private final BodyTypeResolver bodyTypes;

    /** Creates a factory with one explicit conversion and body-type policy. */
    public Box2dBodyFactory(Box2dUnitConversion units, BodyTypeResolver bodyTypes) {
        this.units = Objects.requireNonNull(units, "units");
        this.bodyTypes = Objects.requireNonNull(bodyTypes, "bodyTypes");
    }

    /** Returns the immutable unit conversion used for every body and fixture. */
    public Box2dUnitConversion units() {
        return units;
    }

    /** Creates one body/fixture pair in the caller-owned world. */
    public Box2dBodyHandle create(World world, EntityView entity) {
        Objects.requireNonNull(world, "world");
        EntityView checked = Objects.requireNonNull(entity, "entity");
        Transform2D transform = checked.component(Transform2D.TYPE)
                .orElseThrow(() -> missing(checked, "transform"));
        Collider collider = checked.component(Collider.TYPE)
                .orElseThrow(() -> missing(checked, "collider"));
        BodyDef.BodyType bodyType = bodyTypes.resolve(checked);
        if (bodyType == null) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "select-box2d-body-type",
                    "non-null body type policy result",
                    "null for " + checked.id(),
                    "Return one explicit static, kinematic, or dynamic body policy.");
        }
        String fixtureId = fixtureId(checked);

        BodyDef bodyDef = new BodyDef();
        bodyDef.type = bodyType;
        bodyDef.position.set(
                units.toPhysicsFloat(transform.position().x(), "position.x"),
                units.toPhysicsFloat(transform.position().y(), "position.y"));
        bodyDef.angle = finiteFloat(transform.rotationRadians(), "rotation");
        checked.component(Movement.TYPE).ifPresent(movement -> bodyDef.linearVelocity.set(
                units.toPhysicsFloat(movement.velocity().x(), "velocity.x"),
                units.toPhysicsFloat(movement.velocity().y(), "velocity.y")));

        Shape shape = shape(collider);
        Body body = null;
        try {
            body = world.createBody(bodyDef);
            body.setUserData(checked.id());
            FixtureDef fixtureDef = new FixtureDef();
            fixtureDef.shape = shape;
            fixtureDef.isSensor = collider.sensor();
            fixtureDef.density = bodyType == BodyDef.BodyType.DynamicBody ? 1.0f : 0.0f;
            fixtureDef.filter.categoryBits = (short) collider.categoryBits();
            fixtureDef.filter.maskBits = (short) collider.maskBits();
            Fixture fixture = body.createFixture(fixtureDef);
            fixture.setUserData(new FixtureIdentity(checked.id(), fixtureId));
            return new Box2dBodyHandle(checked.id(), fixtureId, bodyType, body, fixture);
        } catch (RuntimeException | Error failure) {
            if (body != null) {
                world.destroyBody(body);
            }
            throw failure;
        } finally {
            shape.dispose();
        }
    }

    private Shape shape(Collider collider) {
        float offsetX = units.toPhysicsFloat(collider.offset().x(), "collider.offset.x");
        float offsetY = units.toPhysicsFloat(collider.offset().y(), "collider.offset.y");
        if (collider.shape() == Collider.Shape.BOX) {
            PolygonShape box = new PolygonShape();
            box.setAsBox(
                    units.toPhysicsFloat(collider.size().x() * 0.5, "collider.halfWidth"),
                    units.toPhysicsFloat(collider.size().y() * 0.5, "collider.halfHeight"),
                    new Vector2(offsetX, offsetY), 0.0f);
            return box;
        }
        if (Double.compare(collider.size().x(), collider.size().y()) != 0) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_UNSUPPORTED_COLLIDER,
                    "create-box2d-fixture",
                    "equal circle width and height",
                    collider.size().toString(),
                    "Use equal dimensions for a Box2D circle or select BOX geometry.");
        }
        CircleShape circle = new CircleShape();
        circle.setRadius(units.toPhysicsFloat(
                collider.size().x() * 0.5, "collider.radius"));
        circle.setPosition(new Vector2(offsetX, offsetY));
        return circle;
    }

    private static String fixtureId(EntityView entity) {
        String value = entity.id().value() + ".collider";
        if (value.length() > 220) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "derive-box2d-fixture-id",
                    "entity ID short enough for the .collider suffix",
                    Integer.toString(entity.id().value().length()),
                    "Use an entity ID of at most 211 characters for physics entities.");
        }
        return value;
    }

    private static float finiteFloat(double value, String field) {
        float narrowed = (float) value;
        if (!Float.isFinite(narrowed)) {
            throw GameplayException.validation(
                    GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                    "create-box2d-body",
                    "float-representable " + field,
                    Double.toString(value),
                    "Use a bounded transform representable by Box2D.");
        }
        return narrowed;
    }

    private static GameplayException missing(EntityView entity, String component) {
        return GameplayException.validation(
                GameplayDiagnosticCode.BOX2D_INVALID_CONFIGURATION,
                "create-box2d-body",
                "entity with transform and collider",
                entity.id() + " missing " + component,
                "Attach both standard components before activating physics.");
    }

    record FixtureIdentity(
            io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId entityId,
            String fixtureId) {
    }
}
