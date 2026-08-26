package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.physics.box2d.Manifold;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJoint;
import com.badlogic.gdx.utils.Array;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionImpact;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.GameSystem;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemContext;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemDescriptor;
import io.github.teemuki8.libgdx.agent.gameplay.core.system.SystemPhase;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.SystemId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec2;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GameplayBox2dBridgeTest {
    @BeforeAll
    static void initializeNatives() {
        Box2dTestSupport.initializeNatives();
    }

    @Test
    void activationCreatesStableInspectedBodyWithoutTakingWorldOwnership() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> sink.spawn(Box2dTestSupport.body("player", 32, 64)))
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            Box2dBodyHandle handle = bridge.handle(EntityId.of("player")).orElseThrow();
            assertEquals(EntityId.of("player"), handle.body().getUserData());
            assertEquals(1, nativeWorld.getBodyCount());
            runtime.frame(1, () -> { });
            assertEquals("player", runtime.entity(runtimeId("box2d.body.player"))
                    .orElseThrow().displayName().orElseThrow());
            assertEquals("player.collider", runtime.entity(
                    runtimeId("box2d.fixture.player.collider"))
                    .orElseThrow().displayName().orElseThrow());
            assertEquals("gameplay", runtime.entity(runtimeId("box2d.contacts.gameplay"))
                    .orElseThrow().displayName().orElseThrow());
        }

        assertEquals(0, nativeWorld.getBodyCount());
        nativeWorld.createBody(new com.badlogic.gdx.physics.box2d.BodyDef());
        assertEquals(1, nativeWorld.getBodyCount());
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void realNativeWorldEmitsCopiedCollisionImpactThroughTheBridge() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("alpha", 0, 32));
                    sink.spawn(Box2dTestSupport.body("beta", 96, 32));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        nativeWorld.setContactListener(bridge.contactListener());
        runtime.start();

        CollisionImpact copied = null;
        try (GameWorld world = builder.build()) {
            world.step();
            bridge.handle(EntityId.of("alpha")).orElseThrow()
                    .body().setLinearVelocity(2, 0);
            bridge.handle(EntityId.of("beta")).orElseThrow()
                    .body().setLinearVelocity(-2, 0);
            for (int step = 0; step < 90 && copied == null; step++) {
                copied = world.step().events().stream()
                        .map(envelope -> envelope.event())
                        .filter(CollisionImpact.class::isInstance)
                        .map(CollisionImpact.class::cast)
                        .findFirst()
                        .orElse(null);
            }
        }

        assertNotNull(copied);
        assertEquals(EntityId.of("alpha"), copied.first());
        assertEquals(EntityId.of("beta"), copied.second());
        assertEquals("alpha.collider", copied.firstFixtureId());
        assertEquals("beta.collider", copied.secondFixtureId());
        assertEquals(true, copied.normalImpulse() > 0.0);
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void copiedJointForceMotorLimitsAndInspectionUseRealNativeWorld() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = twoBodyWorld(bridge);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            Box2dJointId id = Box2dJointId.of("alpha-shoulder");
            bridge.createRevoluteJoint(new Box2dRevoluteJointSpec(
                    id, EntityId.of("alpha"), EntityId.of("beta"),
                    new Vec2(48, 32), -0.1, 0.1, true));

            assertEquals(1, nativeWorld.getJointCount());
            Array<Joint> nativeJoints = new Array<>();
            nativeWorld.getJoints(nativeJoints);
            RevoluteJoint nativeJoint = (RevoluteJoint) nativeJoints.first();
            assertEquals(1.5, nativeJoint.getAnchorA().x, 0.000_001);
            assertEquals(1.0, nativeJoint.getAnchorA().y, 0.000_001);
            assertEquals(-0.1, nativeJoint.getLowerLimit(), 0.000_001);
            assertEquals(0.1, nativeJoint.getUpperLimit(), 0.000_001);
            assertTrue(nativeJoint.getCollideConnected());
            Box2dRevoluteJointState initial = bridge.revoluteJointState(id).orElseThrow();
            assertEquals(EntityId.of("alpha"), initial.first());
            assertEquals(EntityId.of("beta"), initial.second());
            assertFalse(initial.motorEnabled());
            runtime.frame(1, () -> { });
            assertEquals("alpha-shoulder", runtime.entity(
                    runtimeId("box2d.joint.alpha-shoulder"))
                    .orElseThrow().displayName().orElseThrow());

            bridge.configureRevoluteMotor(id, new Box2dRevoluteMotor(true, 4.0, 20.0));
            Box2dRevoluteJointState motor = bridge.revoluteJointState(id).orElseThrow();
            assertTrue(motor.motorEnabled());
            assertEquals(4.0, motor.motorSpeedRadiansPerSecond(), 0.000_001);
            assertEquals(20.0, motor.maximumMotorTorqueNewtonMetres(), 0.000_001);

            bridge.handle(EntityId.of("alpha")).orElseThrow().body().setAwake(false);
            bridge.applyForceToCenter(EntityId.of("alpha"), new Vec2(100, 0));
            assertTrue(bridge.handle(EntityId.of("alpha")).orElseThrow().body().isAwake());
            world.step();
            assertTrue(bridge.bodyState(EntityId.of("alpha")).orElseThrow().velocity().x() > 0);

            bridge.configureRevoluteMotor(id, new Box2dRevoluteMotor(false, 0, 0));
            var beta = bridge.handle(EntityId.of("beta")).orElseThrow().body();
            beta.setTransform(beta.getPosition(), 1.0f);
            for (int step = 0; step < 30; step++) {
                world.step();
            }
            assertTrue(Math.abs(bridge.revoluteJointState(id).orElseThrow().angleRadians()) < 0.15);

            bridge.removeJoint(id);
            bridge.removeJoint(id);
            assertEquals(0, nativeWorld.getJointCount());
            assertTrue(bridge.revoluteJointState(id).isEmpty());
        }

        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void jointCreationRejectsMissingStaticDuplicateAndExhaustedInspectionCapacityAtomically() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        Box2dBodyFactory bodies = new Box2dBodyFactory(
                Box2dTestSupport.UNITS,
                entity -> entity.id().value().equals("static")
                        ? BodyDef.BodyType.StaticBody : BodyDef.BodyType.DynamicBody);
        GameplayBox2dBridge bridge = new GameplayBox2dBridge(
                nativeWorld, bodies, Box2dTestSupport.UNITS, Box2dTestSupport.SOLVER,
                runtime, GameplayLimits.defaults());
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("alpha", 32, 32));
                    sink.spawn(Box2dTestSupport.body("beta", 64, 32));
                    sink.spawn(Box2dTestSupport.body("static", 96, 32));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            assertThrows(RuntimeException.class, () -> bridge.createRevoluteJoint(joint(
                    "missing", "alpha", "absent")));
            assertThrows(RuntimeException.class, () -> bridge.createRevoluteJoint(joint(
                    "static-joint", "alpha", "static")));
            assertEquals(0, nativeWorld.getJointCount());

            assertThrows(RuntimeException.class, () -> bridge.configureRevoluteMotor(
                    Box2dJointId.of("missing-motor"),
                    new Box2dRevoluteMotor(false, 0, 0)));
            bridge.createRevoluteJoint(joint("duplicate", "alpha", "beta"));
            assertThrows(RuntimeException.class, () -> bridge.createRevoluteJoint(joint(
                    "duplicate", "alpha", "beta")));
            assertEquals(1, nativeWorld.getJointCount());

            bridge.removeJoint(Box2dJointId.of("duplicate"));
            for (int index = 0; index < 2_048; index++) {
                bridge.createRevoluteJoint(joint(
                        "bounded-" + index, "alpha", "beta"));
            }
            assertThrows(RuntimeException.class, () -> bridge.createRevoluteJoint(joint(
                    "beyond-bound", "alpha", "beta")));
            assertEquals(2_048, nativeWorld.getJointCount());
        }

        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void forceRejectsMissingInactiveAndStaticBodies() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        Box2dBodyFactory bodies = new Box2dBodyFactory(
                Box2dTestSupport.UNITS,
                entity -> entity.id().value().equals("static")
                        ? BodyDef.BodyType.StaticBody : BodyDef.BodyType.DynamicBody);
        GameplayBox2dBridge bridge = new GameplayBox2dBridge(
                nativeWorld, bodies, Box2dTestSupport.UNITS, Box2dTestSupport.SOLVER,
                runtime, GameplayLimits.defaults());
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("dynamic", 32, 32));
                    sink.spawn(Box2dTestSupport.body("static", 64, 32));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            assertThrows(RuntimeException.class,
                    () -> bridge.applyForceToCenter(EntityId.of("missing"), Vec2.ZERO));
            assertThrows(RuntimeException.class,
                    () -> bridge.applyForceToCenter(EntityId.of("static"), Vec2.ZERO));
            bridge.deactivate(EntityId.of("dynamic"));
            assertThrows(RuntimeException.class,
                    () -> bridge.applyForceToCenter(EntityId.of("dynamic"), Vec2.ZERO));
        }

        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void forceUsesBox2dSiNewtonsWithoutRenderUnitConversion() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> sink.spawn(Box2dTestSupport.body("body", 32, 32)))
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            float mass = bridge.handle(EntityId.of("body")).orElseThrow().body().getMass();
            bridge.applyForceToCenter(EntityId.of("body"), new Vec2(32, 0));
            world.step();
            double expectedVelocity = 32.0 / mass
                    * (Box2dTestSupport.STEP_NANOS / 1_000_000_000.0);
            assertEquals(expectedVelocity,
                    bridge.bodyState(EntityId.of("body")).orElseThrow().velocity().x()
                            / Box2dTestSupport.UNITS.renderUnitsPerMeter(),
                    0.000_01);
        }

        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void disposingAnEndpointAndResetDestroyJointsBeforeBodiesAndAllowStableIdReuse() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = twoBodyWorld(bridge);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            Box2dJointId id = Box2dJointId.of("lifecycle-joint");
            bridge.createRevoluteJoint(joint(id.value(), "alpha", "beta"));
            world.requestReset();
            world.step();
            world.step();
            assertEquals(0, nativeWorld.getJointCount());
            assertEquals(2, nativeWorld.getBodyCount());
            assertTrue(bridge.revoluteJointState(id).isEmpty());

            bridge.createRevoluteJoint(joint(id.value(), "alpha", "beta"));
            assertEquals(1, nativeWorld.getJointCount());
        }

        assertEquals(0, nativeWorld.getJointCount());
        assertEquals(0, nativeWorld.getBodyCount());
        nativeWorld.createBody(new BodyDef());
        assertEquals(1, nativeWorld.getBodyCount());
        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void endpointDisposalRemovesEveryConnectedJointBeforeItsBody() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = twoBodyWorld(bridge)
                .system(new GameSystem() {
                    @Override public SystemDescriptor descriptor() {
                        return new SystemDescriptor(
                                SystemId.of("dispose-alpha"), SystemPhase.GAMEPLAY, 10);
                    }

                    @Override public void update(SystemContext context) {
                        if (context.tick() == 1) {
                            context.despawn(EntityId.of("alpha"));
                        }
                    }
                });
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            bridge.createRevoluteJoint(joint("connected-one", "alpha", "beta"));
            bridge.createRevoluteJoint(joint("connected-two", "alpha", "beta"));
            world.step();
            assertEquals(0, nativeWorld.getJointCount());
            assertEquals(1, nativeWorld.getBodyCount());
        }

        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void mutationWhileNativeWorldIsLockedFailsWithoutPartialJointState() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        AtomicReference<Throwable> observed = new AtomicReference<>();
        nativeWorld.setContactListener(bridge.composeContactListener(new ContactListener() {
            @Override public void beginContact(Contact contact) {
                try {
                    bridge.createRevoluteJoint(joint("locked", "alpha", "beta"));
                } catch (Throwable failure) {
                    observed.set(failure);
                }
            }

            @Override public void endContact(Contact contact) {
            }

            @Override public void preSolve(Contact contact, Manifold oldManifold) {
            }

            @Override public void postSolve(Contact contact, ContactImpulse impulse) {
            }
        }));
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("alpha", 32, 32));
                    sink.spawn(Box2dTestSupport.body("beta", 32, 32));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            assertNotNull(observed.get());
            assertEquals(0, nativeWorld.getJointCount());
            assertTrue(bridge.revoluteJointState(Box2dJointId.of("locked")).isEmpty());
        }

        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void copiedOperationsRemainOwnerThreadConfined() throws InterruptedException {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        AtomicReference<Throwable> observed = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                bridge.revoluteJointState(Box2dJointId.of("joint"));
            } catch (Throwable failure) {
                observed.set(failure);
            }
        });

        other.start();
        other.join();

        assertNotNull(observed.get());
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }

    private static GameWorld.Builder twoBodyWorld(GameplayBox2dBridge bridge) {
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("alpha", 32, 32));
                    sink.spawn(Box2dTestSupport.body("beta", 64, 32));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        return builder;
    }

    private static Box2dRevoluteJointSpec joint(
            String id, String first, String second) {
        return new Box2dRevoluteJointSpec(
                Box2dJointId.of(id), EntityId.of(first), EntityId.of(second),
                new Vec2(48, 32), -0.5, 0.5, false);
    }

    private static io.github.teemuki8.libgdx.agent.runtime.core.EntityId runtimeId(String value) {
        return io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(value);
    }
}
