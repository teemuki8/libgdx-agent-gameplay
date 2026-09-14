package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntime;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValue;
import io.github.teemuki8.libgdx.agent.runtime.core.RuntimeValues;
import io.github.teemuki8.libgdx.agent.runtime.core.SimulationTimelineSpec;
import io.github.teemuki8.libgdx.agent.runtime.bullet.BulletContactLimits;
import org.junit.jupiter.api.Test;

final class GameplayBulletInspectionTest {
    @Test void runtimeSeesBodiesContactsLimitsAndFeedbackAcrossDynamicRagdollLifetimes() {
        try (var runtime = AgentRuntime.builder().build();
                var world = new GameplayBulletDynamicsWorld(new BulletDynamicsLimits(4, 2), new Vec3(0, -9.81, 0))) {
            runtime.simulation().register(SimulationTimelineSpec.fixedStep(16_666_667));
            var floor = EntityId.of("floor");
            var torso = EntityId.of("torso");
            var head = EntityId.of("head");
            world.add(floor, box(new Vec3(0, -.5, 0), new Vec3(10, 1, 10), 0));
            world.observe(runtime, "ragdoll", BulletContactLimits.developmentDefaults());
            runtime.start();
            world.add(torso, box(new Vec3(0, 2, 0), Vec3.ONE, 2));
            world.add(head, box(new Vec3(0, 3, 0), new Vec3(.5, .5, .5), 1));
            var neck = new BulletConstraintId("neck");
            world.constrain(neck, new BulletSixDofConstraintSpec(torso, head, new Vec3(0, 2.6, 0),
                    new Vec3(-.3, -.2, -.1), new Vec3(.4, .2, .1), true));
            assertThrows(IllegalStateException.class, () -> world.step(1d / 60));
            assertEquals(0, world.statistics().steps());
            for (int tick = 0; tick < 180; tick++) {
                runtime.simulation().tick(16_666_667, supplied -> { world.step(supplied / 1e9);
                    return supplied; });
            }
            assertEquals(RuntimeValues.integer(3), property(runtime, "bullet.world.ragdoll", "registeredBodyCount"));
            assertEquals(RuntimeValues.bool(true), property(runtime, "bullet.joint.ragdoll.neck", "feedbackEnabled"));
            assertTrue(property(runtime, "bullet.joint.ragdoll.neck", "reaction") instanceof RuntimeValue.ObjectValue);
            assertEquals(RuntimeValues.bool(false), field(property(runtime, "bullet.joint.ragdoll.neck", "linearMotor"), "enabledAvailable"));
            assertTrue(world.contactTicks(180, 180, 1).ticks().getFirst().complete());
            assertFalse(world.contactTicks(180, 180, 1).ticks().getFirst().activeContacts().isEmpty());
            assertTrue(runtime.latestFrame().orElseThrow().stats().diagnostics().isEmpty());
            runtime.beginFrame(0);
            assertThrows(io.github.teemuki8.libgdx.agent.runtime.core.AgentRuntimeException.class, () -> world.removeConstraint(neck));
            assertEquals(1, world.statistics().constraints());
            runtime.endFrame();
            world.removeConstraint(neck);
            world.remove(head);
            world.remove(torso);
            world.add(torso, box(new Vec3(2, 2, 0), Vec3.ONE, 2));
            runtime.simulation().tick(16_666_667, supplied -> { world.step(supplied / 1e9);
                    return supplied; });
            assertEquals(RuntimeValues.integer(2), property(runtime, "bullet.world.ragdoll", "registeredBodyCount"));
            assertTrue(runtime.entity(io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of("bullet.joint.ragdoll.neck")).isEmpty());
        }
    }
    @Test void loadedConstraintReportsNonzeroFiniteEqualAndOppositeReactionForces() {
        try (var runtime = AgentRuntime.builder().build();
                var world = new GameplayBulletDynamicsWorld(new BulletDynamicsLimits(2, 1), new Vec3(0, -9.81, 0))) {
            runtime.simulation().register(SimulationTimelineSpec.fixedStep(16_666_667));
            world.observe(runtime, "load", BulletContactLimits.developmentDefaults());
            var anchor = EntityId.of("anchor");
            var weight = EntityId.of("weight");
            world.add(anchor, box(Vec3.ZERO, Vec3.ONE, 0));
            world.add(weight, box(new Vec3(0, 2, 0), Vec3.ONE, 2));
            world.constrain(new BulletConstraintId("support"), new BulletSixDofConstraintSpec(anchor, weight,
                    new Vec3(0, 2, 0), Vec3.ZERO, Vec3.ZERO, true));
            runtime.start();
            for (int tick = 0; tick < 30; tick++) {
                runtime.simulation().tick(16_666_667, supplied -> {
                    world.step(supplied / 1e9);
                    return supplied;
                });
            }
            double first = ((RuntimeValue.DecimalValue) field(property(runtime,
                    "bullet.joint.load.support", "reactionForceBodyA"), "y")).value().doubleValue();
            double second = ((RuntimeValue.DecimalValue) field(property(runtime,
                    "bullet.joint.load.support", "reactionForceBodyB"), "y")).value().doubleValue();
            assertTrue(Double.isFinite(first) && Math.abs(first) > 5, "A suspended weight must load its constraint");
            assertEquals(-first, second, .0001);
        }
    }
    private static RuntimeValue property(AgentRuntime runtime, String id, String name) {
        return runtime.entity(io.github.teemuki8.libgdx.agent.runtime.core.EntityId.of(id)).orElseThrow()
                .properties().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow().value();
    }
    private static RuntimeValue field(RuntimeValue object, String name) {
        return ((RuntimeValue.ObjectValue) object).fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow().value();
    }
    private static BulletRigidBodySpec box(Vec3 position, Vec3 size, double mass) {
        return new BulletRigidBodySpec(new Transform3D(position, QuaternionValue.IDENTITY),
                new BulletBodyShape(BulletBodyShape.Kind.BOX, size), mass, .7, 0, .08, .15, 1, 65535);
    }
}
