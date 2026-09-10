package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import org.junit.jupiter.api.Test;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayException;
import io.github.teemuki8.libgdx.agent.gameplay.core.diagnostic.GameplayDiagnosticCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayBulletWorldTest {
    @Test void copiedHitRejectsInvalidFractionAndNullIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new BulletHit(EntityId.of("wall"),
                Double.NaN, Vec3.ZERO, new Vec3(0, 1, 0)));
        assertThrows(NullPointerException.class, () -> new BulletHit(null, 0.5, Vec3.ZERO, new Vec3(0, 1, 0)));
    }
    @Test void boundedStableOrderingAndOwnerThreadAreEnforced() throws InterruptedException {
        try (var world = new GameplayBulletWorld(2)) {
            var pose = new Transform3D(new Vec3(0, 0, 5), new QuaternionValue(0, 0, 0, 1));
            world.addBox(EntityId.of("z"), pose, new Vec3(1, 1, 1));
            world.addBox(EntityId.of("a"), pose, new Vec3(1, 1, 1));
            assertEquals(EntityId.of("a"), world.raycast(Vec3.ZERO, new Vec3(0, 0, 10), 1).getFirst().entity());
            assertThrows(IllegalArgumentException.class, () -> world.addBox(EntityId.of("overflow"), pose, new Vec3(1, 1, 1)));
            var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread thread = new Thread(() -> {
                try { world.raycast(Vec3.ZERO, new Vec3(0, 0, 1), 1); }
                catch (Throwable failure) { error.set(failure); }
            });
            thread.start();
            thread.join();
            assertEquals(GameplayDiagnosticCode.OWNER_THREAD_VIOLATION, ((GameplayException) error.get()).code());
        }
    }

    @Test void nativeQueriesReturnCopiedStableHitsAndCapsuleStopsBeforeWall() {
        try (GameplayBulletWorld world = new GameplayBulletWorld(8)) {
            world.addBox(EntityId.of("wall"), new Transform3D(new Vec3(0, 1, 5),
                    new QuaternionValue(0, 0, 0, 1)), new Vec3(3, 2, 0.5));
            var hits = world.raycast(new Vec3(0, 1, 0), new Vec3(0, 1, 10), 8);
            assertEquals(EntityId.of("wall"), hits.getFirst().entity());
            assertEquals(0.45, hits.getFirst().fraction(), 0.001);
            var hit = world.sweepCapsule(new Vec3(0, 1, 0), new Vec3(0, 1, 10), 0.3, 1.8);
            assertTrue(hit.orElseThrow().fraction() < 0.45);
            world.updateBoxTransform(EntityId.of("wall"), new Transform3D(new Vec3(8, 1, 5),
                    new QuaternionValue(0, 0, 0, 1)));
            assertTrue(world.raycast(new Vec3(0, 1, 0), new Vec3(0, 1, 10), 8).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> world.updateBoxTransform(EntityId.of("wall"),
                    new Transform3D(new Vec3(0, 1, 5), new QuaternionValue(0, 0, 0, 1), new Vec3(2, 2, 2))));
            world.remove(EntityId.of("wall"));
            assertTrue(world.raycast(new Vec3(0, 1, 0), new Vec3(0, 1, 10), 8).isEmpty());
            assertEquals(EntityId.of("wall"), hits.getFirst().entity());
        }
    }
    @Test void rejectsScaledBoundsOutsideNativeWorldBeforeAllocatingBody() {
        try (var world = new GameplayBulletWorld(1)) {
            var pose = new Transform3D(Vec3.ZERO, new QuaternionValue(0, 0, 0, 1), new Vec3(1000, 1000, 1000));
            assertThrows(IllegalArgumentException.class, () -> world.addBox(EntityId.of("oversize"), pose,
                    new Vec3(10000, 10000, 10000)));
        }
    }
    @Test void limitsAndClosedWorldFailBeforeNativeAccess() {
        var world = new GameplayBulletWorld(1);
        world.close();
        world.close();
        assertThrows(GameplayException.class,
                () -> world.raycast(new Vec3(0, 0, 0), new Vec3(0, 0, 1), 1));
    }
}
