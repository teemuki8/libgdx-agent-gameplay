package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.box2d.Box2d;
import com.badlogic.gdx.box2d.enums.b2ShapeType;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.Collider;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionImpact;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GameplayBox2dBridgeTest {
    @BeforeAll
    static void initializeNatives() { Box2dTestSupport.initializeNatives(); }

    @Test
    void realBackendCopiesBodiesCapsulesJointsForcesRaycastsContactsAndLifecycle() {
        GameplayBox2dWorld nativeWorld = Box2dTestSupport.world(Vec2.ZERO);
        AgentRuntime runtime = Box2dTestSupport.runtime();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("alpha", 0, 32,
                            Collider.Shape.CAPSULE, new Vec2(24, 48)));
                    sink.spawn(Box2dTestSupport.body("beta", 96, 32,
                            Collider.Shape.BOX, new Vec2(28, 28)));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        CollisionImpact impact = null;
        try (GameWorld world = builder.build()) {
            world.step();
            EntityId alpha = EntityId.of("alpha");
            EntityId beta = EntityId.of("beta");
            Box2dBodyState alphaState = bridge.bodyState(alpha).orElseThrow();
            assertEquals(Box2dBodyType.DYNAMIC, alphaState.bodyType());
            assertEquals(Collider.Shape.CAPSULE, alphaState.colliderShape());
            assertTrue(alphaState.massKilograms() > 0.0);
            assertTrue(alphaState.rotationalInertiaKilogramMetresSquared() > 0.0);
            assertEquals(b2ShapeType.b2_capsuleShape,
                    Box2d.b2Shape_GetType(bridge.handle(alpha).orElseThrow().shape()));

            Box2dJointId jointId = Box2dJointId.of("alpha-beta");
            bridge.createRevoluteJoint(new Box2dRevoluteJointSpec(
                    jointId, alpha, beta, new Vec2(48, 32), -0.5, 0.5, false));
            bridge.configureRevoluteMotor(jointId, new Box2dRevoluteMotor(true, 3.0, 20.0));
            Box2dRevoluteJointState joint = bridge.revoluteJointState(jointId).orElseThrow();
            assertTrue(joint.motorEnabled());
            assertEquals(3.0, joint.motorSpeedRadiansPerSecond(), 1.0e-6);
            runtime.frame(1, () -> { });
            assertTrue(runtime.entity(io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(
                    "box2d.joint.alpha-beta")).isPresent());
            bridge.removeJoint(jointId);
            assertTrue(bridge.revoluteJointState(jointId).isEmpty());

            double angularBefore = alphaState.angularVelocityRadiansPerSecond();
            bridge.applyTorque(alpha, 8.0);
            bridge.applyForce(alpha, new Vec2(0, 20), new Vec2(16, 32));
            bridge.applyForceToCenter(alpha, new Vec2(50, 0));
            bridge.applyForceToCenter(beta, new Vec2(-50, 0));
            world.step();
            assertTrue(Math.abs(bridge.bodyState(alpha).orElseThrow()
                    .angularVelocityRadiansPerSecond()) > Math.abs(angularBefore));

            var rayHits = bridge.raycast(new Box2dRaycastSpec(
                    new Vec2(-64, 32), new Vec2(256, 0), 1, 0xffff, 2));
            assertEquals(2, rayHits.size());
            assertTrue(rayHits.get(0).fraction() <= rayHits.get(1).fraction());
            assertTrue(rayHits.stream().allMatch(hit -> hit.fixtureId().endsWith(".collider")));
            var nearest = bridge.raycast(new Box2dRaycastSpec(
                    new Vec2(-64, 32), new Vec2(256, 0), 1, 0xffff, 1));
            assertEquals(List.of(rayHits.get(0)), nearest);

            for (int step = 0; step < 180 && impact == null; step++) {
                impact = world.step().events().stream()
                        .map(envelope -> envelope.event())
                        .filter(CollisionImpact.class::isInstance)
                        .map(CollisionImpact.class::cast)
                        .findFirst().orElse(null);
            }
            runtime.frame(2, () -> { });
            assertTrue(runtime.entity(io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(
                    "box2d.body.alpha")).isPresent());
            assertTrue(runtime.entity(io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(
                    "box2d.fixture.alpha.collider")).isPresent());
        }

        assertNotNull(impact);
        assertEquals(EntityId.of("alpha"), impact.first());
        assertEquals(EntityId.of("beta"), impact.second());
        assertTrue(impact.normalImpulse() > 0.0);
        assertTrue(Box2d.b2World_IsValid(nativeWorld.id()));
        bridge.close();
        runtime.close();
        nativeWorld.close();
        assertTrue(nativeWorld.isClosed());
    }

    @Test
    void horizontalAndVerticalCapsulesUseCompleteBounds() {
        assertCapsuleGeometry(new Vec2(64, 16), -0.75f, 0.75f, true);
        assertCapsuleGeometry(new Vec2(16, 64), -0.75f, 0.75f, false);
    }

    @Test
    void rejectsUndersizedAndFloatRoundedShapeDimensionsBeforeNativeCreation() {
        assertRejectedGeometry(Collider.Shape.BOX, new Vec2(Double.MIN_VALUE, 32));
        assertRejectedGeometry(Collider.Shape.CIRCLE,
                new Vec2(Double.MIN_VALUE, Double.MIN_VALUE));
        assertRejectedGeometry(Collider.Shape.CIRCLE,
                new Vec2(1.0, Math.nextUp(1.0)));
        assertRejectedGeometry(Collider.Shape.CAPSULE,
                new Vec2(1.0, Math.nextUp(1.0)));

        GameplayException equalCapsule = assertThrows(GameplayException.class,
                () -> new Collider(Collider.Shape.CAPSULE, new Vec2(1, 1), Vec2.ZERO,
                        false, 1, 0xffff));
        assertEquals(GameplayDiagnosticCode.INVALID_COMPONENT_VALUE, equalCapsule.code());
    }

    private static void assertRejectedGeometry(Collider.Shape shape, Vec2 size) {
        GameplayBox2dWorld nativeWorld = Box2dTestSupport.world(Vec2.ZERO);
        AgentRuntime runtime = Box2dTestSupport.runtime();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> sink.spawn(
                        Box2dTestSupport.body("invalid", 0, 0, shape, size)))
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();
        GameplayException failure;
        try (GameWorld game = builder.build()) {
            failure = assertThrows(GameplayException.class, game::step);
        }
        assertEquals(GameplayDiagnosticCode.BOX2D_UNSUPPORTED_COLLIDER, failure.code());
        var counters = new com.badlogic.gdx.box2d.structs.b2Counters();
        Box2d.b2World_GetCounters(nativeWorld.id(), counters);
        assertEquals(0, counters.bodyCount());
        bridge.close();
        runtime.close();
        nativeWorld.close();
    }

    private static void assertCapsuleGeometry(Vec2 size, float first, float second,
            boolean horizontal) {
        GameplayBox2dWorld nativeWorld = Box2dTestSupport.world(Vec2.ZERO);
        AgentRuntime runtime = Box2dTestSupport.runtime();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> sink.spawn(Box2dTestSupport.body(
                        "capsule", 0, 0, Collider.Shape.CAPSULE, size)))
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();
        try (GameWorld world = builder.build()) {
            world.step();
            var capsule = Box2d.b2Shape_GetCapsule(
                    bridge.handle(EntityId.of("capsule")).orElseThrow().shape());
            assertEquals(0.25f, capsule.radius(), 1.0e-6);
            if (horizontal) {
                assertEquals(first, capsule.center1().x(), 1.0e-6);
                assertEquals(second, capsule.center2().x(), 1.0e-6);
            } else {
                assertEquals(first, capsule.center1().y(), 1.0e-6);
                assertEquals(second, capsule.center2().y(), 1.0e-6);
            }
        }
        assertFalse(nativeWorld.isClosed());
        runtime.close();
        nativeWorld.close();
    }
}
