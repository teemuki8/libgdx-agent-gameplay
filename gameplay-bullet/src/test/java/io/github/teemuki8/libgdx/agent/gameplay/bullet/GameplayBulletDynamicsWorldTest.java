package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import org.junit.jupiter.api.Test;

final class GameplayBulletDynamicsWorldTest {
    private static final EntityId FLOOR = EntityId.of("floor");
    private static final EntityId UPPER = EntityId.of("upper");
    private static final EntityId LOWER = EntityId.of("lower");

    @Test void dynamicBodiesStepAndConstraintKeepsEndpointsTogether() {
        try (var world = new GameplayBulletDynamicsWorld(new BulletDynamicsLimits(3, 1), new Vec3(0, -9.81, 0))) {
            world.add(FLOOR, body(new Vec3(0, -.5, 0), new Vec3(8, 1, 8), 0));
            world.add(UPPER, body(new Vec3(0, 3, 0), new Vec3(.5, 1, .5), 2), new Vec3(1, 0, 0), Vec3.ZERO);
            assertEquals(1, world.state(UPPER).orElseThrow().linearVelocity().x(), 1e-6);
            world.add(LOWER, body(new Vec3(0, 2, 0), new Vec3(.4, 1, .4), 1));
            world.constrain(new BulletConstraintId("knee"), new BulletSixDofConstraintSpec(UPPER, LOWER,
                    new Vec3(0, 2.5, 0), new Vec3(-.1, 0, 0), new Vec3(1.5, 0, 0), true));
            world.applyCentralImpulse(UPPER, new Vec3(2, 0, 0));
            world.applyImpulse(LOWER, new Vec3(0, 0, 1), new Vec3(0, 1.7, 0));
            assertTrue(Math.abs(world.state(LOWER).orElseThrow().angularVelocity().x()) > .1);
            assertTrue(world.state(LOWER).orElseThrow().linearVelocity().z() > .5);
            for (int i = 0; i < 120; i++) world.step(1d / 60);
            var upper = world.state(UPPER).orElseThrow();
            var lower = world.state(LOWER).orElseThrow();
            assertTrue(upper.position().y() > -.01);
            assertTrue(upper.position().y() < 2.9, "Gravity must advance the native bodies");
            assertTrue(upper.position().x() > .5, "Initial velocity and impulse must translate the body");
            assertTrue(lower.position().y() > -.01);
            assertTrue(distance(upper.position(), lower.position()) < 1.5);
            assertEquals(new BulletDynamicsStatistics(3, 1, 120), world.statistics());
            assertThrows(IllegalStateException.class, () -> world.remove(UPPER));
        }
    }

    @Test void boundsGeometryAndOwnerThreadFailBeforeMutation() throws InterruptedException {
        try (var world = new GameplayBulletDynamicsWorld(new BulletDynamicsLimits(1, 0), new Vec3(0, -9.81, 0))) {
            world.add(FLOOR, body(new Vec3(0, 0, 0), new Vec3(1, 1, 1), 0));
            assertThrows(IllegalArgumentException.class, () -> world.add(UPPER,
                    body(new Vec3(0, 2, 0), new Vec3(1, 1, 1), 1)));
            assertEquals(new BulletDynamicsStatistics(1, 0, 0), world.statistics());
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var thread = new Thread(() -> {
                try {
                    world.statistics();
                } catch (Throwable value) {
                    failure.set(value);
                }
            });
            thread.start();
            thread.join();
            assertNotNull(failure.get());
        }
        try (var world = new GameplayBulletDynamicsWorld(new BulletDynamicsLimits(2, 0), Vec3.ZERO)) {
            assertThrows(IllegalArgumentException.class, () -> world.add(UPPER,
                    body(new Vec3(1_000_001, 2, 0), new Vec3(1, 1, 1), 1)));
            assertEquals(new BulletDynamicsStatistics(0, 0, 0), world.statistics());
        }
        assertThrows(IllegalArgumentException.class, () -> new BulletBodyShape(BulletBodyShape.Kind.CAPSULE_Y,
                new Vec3(1, .5, 1)));
        assertThrows(IllegalArgumentException.class, () -> new BulletDynamicsLimits(4097, 24));
        assertThrows(IllegalArgumentException.class, () ->
                body(new Vec3(0, 1, 0), new Vec3(1, 1, 1), Double.MIN_VALUE));
        assertDoesNotThrow(() -> body(new Vec3(0, 1, 0), new Vec3(1, 1, 1), .001));
        assertThrows(IllegalArgumentException.class, () -> new BulletSixDofConstraintSpec(UPPER, LOWER,
                Vec3.ZERO, new Vec3(0, 2, 0), new Vec3(0, 2.5, 0), true));
        assertThrows(IllegalArgumentException.class, () -> new BulletConstraintId("a-".repeat(5_000) + "a"));
        assertThrows(IllegalArgumentException.class, () ->
                new GameplayBulletDynamicsWorld(new BulletDynamicsLimits(1, 0), new Vec3(0, -1_000_001, 0)));
    }

    @Test void orientedConstraintRotatesItsAsymmetricAngularAxes() {
        var yaw = new QuaternionValue(0, Math.sin(Math.PI / 4), 0, Math.cos(Math.PI / 4));
        QuaternionValue oriented = hingeResponse(yaw);
        QuaternionValue worldAligned = hingeResponse(QuaternionValue.IDENTITY);
        assertTrue(Math.abs(oriented.z()) > .1,
                "Yawed local X hinge must permit rotation around its corresponding world Z axis");
        assertTrue(Math.abs(oriented.z()) > Math.abs(worldAligned.z()) * 3,
                "World-aligned and yawed asymmetric hinges must not behave as the same frame");
    }

    private static QuaternionValue hingeResponse(QuaternionValue anchorRotation) {
        try (var world = new GameplayBulletDynamicsWorld(new BulletDynamicsLimits(2, 1), Vec3.ZERO)) {
            world.add(FLOOR, body(Vec3.ZERO, new Vec3(1, 1, 1), 0));
            world.add(UPPER, body(Vec3.ZERO, new Vec3(.4, 2, .4), 1), Vec3.ZERO, new Vec3(0, 0, 3));
            world.constrain(new BulletConstraintId("hinge"), new BulletSixDofConstraintSpec(FLOOR, UPPER,
                    Vec3.ZERO, anchorRotation, new Vec3(-.8, 0, 0), new Vec3(.8, 0, 0), true));
            for (int tick = 0; tick < 30; tick++) world.step(1d / 60);
            return world.state(UPPER).orElseThrow().rotation();
        }
    }

    private static BulletRigidBodySpec body(Vec3 position, Vec3 size, double mass) {
        return new BulletRigidBodySpec(new Transform3D(position, QuaternionValue.IDENTITY, new Vec3(1, 1, 1)),
                new BulletBodyShape(BulletBodyShape.Kind.BOX, size), mass, .7, .05, .08, .15, 1, 65535);
    }
    private static double distance(Vec3 a, Vec3 b) {
        return Math.hypot(Math.hypot(a.x() - b.x(), a.y() - b.y()), a.z() - b.z());
    }
}
