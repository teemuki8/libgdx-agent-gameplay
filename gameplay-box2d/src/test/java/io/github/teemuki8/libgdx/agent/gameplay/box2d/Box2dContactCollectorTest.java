package io.github.teemuki8.libgdx.agent.gameplay.box2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Manifold;
import io.github.teemuki8.libgdx.agent.gameplay.core.GameplayLimits;
import io.github.teemuki8.libgdx.agent.gameplay.core.component.StandardComponents;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.event.CollisionStarted;
import io.github.teemuki8.libgdx.agent.gameplay.core.world.GameWorld;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

final class Box2dContactCollectorTest {
    @BeforeAll
    static void initializeNatives() {
        Box2dTestSupport.initializeNatives();
    }

    @Test
    void realNativeCallbacksBecomeSortedGameplayEventsOnlyAfterWorldStep() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        AtomicInteger applicationBegins = new AtomicInteger();
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("zeta", 32, 32));
                    sink.spawn(Box2dTestSupport.body("alpha", 32, 32));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        nativeWorld.setContactListener(bridge.composeContactListener(
                listener(applicationBegins)));
        runtime.start();

        try (GameWorld world = builder.build()) {
            var collisions = world.step().events().stream()
                    .map(envelope -> envelope.event())
                    .filter(CollisionStarted.class::isInstance)
                    .map(CollisionStarted.class::cast)
                    .toList();
            assertEquals(1, collisions.size());
            assertEquals("alpha", collisions.getFirst().first().value());
            assertEquals("zeta", collisions.getFirst().second().value());
            assertEquals("alpha.collider", collisions.getFirst().firstFixtureId());
            assertEquals("zeta.collider", collisions.getFirst().secondFixtureId());
            assertEquals(1, applicationBegins.get());
        }
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void overflowFromRealCallbacksIsTypedInsteadOfTruncated() {
        World nativeWorld = new World(new Vector2(), true);
        Box2dContactCollector collector = new Box2dContactCollector(1);
        Box2dBodyFactory bodies = Box2dTestSupport.dynamicBodies();
        bodies.create(nativeWorld, view(Box2dTestSupport.body("alpha", 0, 0)));
        bodies.create(nativeWorld, view(Box2dTestSupport.body("beta", 0, 0)));
        bodies.create(nativeWorld, view(Box2dTestSupport.body("gamma", 0, 0)));
        nativeWorld.setContactListener(collector.listener());

        GameplayException failure = assertThrows(GameplayException.class,
                () -> collector.captureStep(() -> nativeWorld.step(1f / 60f, 6, 2)));
        assertEquals(GameplayDiagnosticCode.BOX2D_CONTACT_LIMIT_EXCEEDED, failure.code());
        nativeWorld.dispose();
    }

    @Test
    void bridgeDoesNotReplaceAnApplicationListenerWithoutExplicitComposition() {
        World nativeWorld = new World(new Vector2(), true);
        AtomicInteger applicationBegins = new AtomicInteger();
        nativeWorld.setContactListener(listener(applicationBegins));
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("alpha", 32, 32));
                    sink.spawn(Box2dTestSupport.body("beta", 32, 32));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        runtime.start();

        try (GameWorld world = builder.build()) {
            assertEquals(0, world.step().events().stream()
                    .filter(event -> event.event() instanceof CollisionStarted).count());
            assertEquals(1, applicationBegins.get());
        }
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }

    @Test
    void activeRuntimeSimulationTickCapturesTheSameRegisteredNativeContact() {
        World nativeWorld = new World(new Vector2(), true);
        AgentRuntime runtime = AgentRuntime.builder().build();
        GameplayBox2dBridge bridge = Box2dTestSupport.bridge(nativeWorld, runtime);
        AtomicInteger nativeBegins = new AtomicInteger();
        GameWorld.Builder builder = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .fixedStepNanos(Box2dTestSupport.STEP_NANOS)
                .initializer(sink -> {
                    sink.spawn(Box2dTestSupport.body("alpha", 32, 32));
                    sink.spawn(Box2dTestSupport.body("beta", 96, 32));
                })
                .lifecycleParticipant(bridge);
        bridge.systems().forEach(builder::system);
        nativeWorld.setContactListener(bridge.composeContactListener(listener(nativeBegins)));
        runtime.start();

        try (GameWorld world = builder.build()) {
            world.step();
            var alpha = bridge.handle(
                    io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId.of("alpha"))
                    .orElseThrow().body();
            var beta = bridge.handle(
                    io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId.of("beta"))
                    .orElseThrow().body();
            alpha.setLinearVelocity(2, 0);
            beta.setLinearVelocity(-2, 0);
            java.util.concurrent.atomic.AtomicReference<
                    io.github.teemuki8.libgdx.agent.gameplay.core.world.CompletedTick> completed =
                    new java.util.concurrent.atomic.AtomicReference<>();
            for (int index = 0; index < 60 && completed.get() == null; index++) {
                runtime.simulation().tick(Box2dTestSupport.STEP_NANOS, supplied -> {
                    var tick = world.step();
                    if (tick.events().stream()
                            .anyMatch(event -> event.event() instanceof CollisionStarted)) {
                        completed.set(tick);
                    }
                    return supplied;
                });
            }
            assertNotNull(completed.get(), "positions=" + alpha.getPosition()
                    + ":" + beta.getPosition() + ",begins=" + nativeBegins.get());
            assertEquals(1, completed.get().events().stream()
                    .filter(event -> event.event() instanceof CollisionStarted).count());
            runtime.frame(1, () -> { });
            RuntimeValue complete = runtime.entity(runtimeId("box2d.contacts.gameplay"))
                    .orElseThrow().property("complete").orElseThrow();
            assertEquals(true, ((RuntimeValue.BooleanValue) complete).value());
        }
        bridge.close();
        runtime.close();
        nativeWorld.dispose();
    }

    private static ContactListener listener(AtomicInteger begins) {
        return new ContactListener() {
            @Override public void beginContact(Contact contact) {
                begins.incrementAndGet();
            }

            @Override public void endContact(Contact contact) {
            }

            @Override public void preSolve(Contact contact, Manifold oldManifold) {
            }

            @Override public void postSolve(Contact contact, ContactImpulse impulse) {
            }
        };
    }

    private static io.github.teemuki8.libgdx.agent.runtime.core.EntityId runtimeId(String value) {
        return io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(value);
    }

    private static io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityView view(
            io.github.teemuki8.libgdx.agent.gameplay.core.world.EntityDraft draft) {
        GameWorld world = GameWorld.builder(
                        GameplayLimits.defaults(), StandardComponents.registry())
                .initializer(sink -> sink.spawn(draft))
                .build();
        try (world) {
            world.step();
            return world.entity(draft.id()).orElseThrow();
        }
    }
}
