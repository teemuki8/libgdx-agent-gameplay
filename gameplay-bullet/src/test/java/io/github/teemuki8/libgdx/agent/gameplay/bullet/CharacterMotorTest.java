package io.github.teemuki8.libgdx.agent.gameplay.bullet;

import io.github.teemuki8.libgdx.agent.gameplay.core.component.Transform3D;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.EntityId;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.QuaternionValue;
import io.github.teemuki8.libgdx.agent.gameplay.core.value.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CharacterMotorTest {
    @Test void defaultCrouchBeginsWithAnIntermediateSpeedInsteadOfAnInstantTargetChange() {
        try (var world = room()) {
            var motor = new BulletCharacterMotor(world, CharacterConfig.defaults());
            var state = new CharacterState(new Vec3(0, 0.01, 0), new Vec3(5, 0, 0), true, false);
            state = motor.step(state, new CharacterIntent(new Vec3(1, 0, 0), false, true), 1.0 / 60);
            assertTrue(state.velocity().x() > 4.7 && state.velocity().x() < 5,
                    "first fixed tick should retain the interpolated standing-to-crouch speed: " + state);
        }
    }

    @Test void configurableCrouchRatioControlsSpeedWithoutHiddenMultiplier() {
        double[] travelled = new double[2];
        double[] ratios = {1, 0.25};
        for (int index = 0; index < ratios.length; index++) {
            try (var world = room()) {
                var config = new CharacterConfig(0.3, 1.8, 1.0, 5, 25, 30, 20, 7, 0.3,
                        Math.toRadians(45), ratios[index]);
                var motor = new BulletCharacterMotor(world, config);
                var state = new CharacterState(new Vec3(0, 0.01, 0), Vec3.ZERO, true, false);
                for (int tick = 0; tick < 60; tick++) state = motor.step(state,
                        new CharacterIntent(new Vec3(1, 0, 0), false, true), 1.0 / 60);
                travelled[index] = state.feet().x();
            }
        }
        assertTrue(travelled[0] > 4.4, "ratio one preserves standing top speed");
        assertTrue(travelled[1] > 1.1 && travelled[1] < 1.3, "quarter speed is applied once");
    }
    @Test void landsJumpsAndCannotWalkThroughWall() {
        try (var world = room()) {
            var motor = new BulletCharacterMotor(world, CharacterConfig.defaults());
            var state = new CharacterState(new Vec3(0, 0.01, 0), new Vec3(0, 0, 0), false, false);
            for (int i = 0; i < 60; i++) state = motor.step(state, CharacterIntent.idle(), 1.0 / 60);
            assertTrue(state.grounded());
            state = motor.step(state, new CharacterIntent(new Vec3(0, 0, 0), true, false), 1.0 / 60);
            assertFalse(state.grounded());
            assertTrue(state.velocity().y() > 0);
            for (int i = 0; i < 240; i++) {
                state = motor.step(state, new CharacterIntent(new Vec3(0, 0, 1), false, false), 1.0 / 60);
            }
            assertTrue(state.feet().z() < 4.25);
            assertTrue(state.grounded());
        }
    }
    @Test void cannotStandInsideLowCeilingAndResolvesSmallStartingPenetration() {
        try (var world = room()) {
            world.addBox(EntityId.of("ceiling"), pose(0, 1.5, 0), new Vec3(2, 0.2, 2));
            var motor = new BulletCharacterMotor(world, CharacterConfig.defaults());
            var state = new CharacterState(new Vec3(0, -0.02, 0), new Vec3(0, 0, 0), true, true);
            state = motor.step(state, CharacterIntent.idle(), 1.0 / 60);
            assertTrue(state.crouched());
            assertTrue(state.feet().y() >= -0.001);
        }
    }
    @Test void walksUpLowStepAndWalkableSlopeButRejectsSteepSlope() {
        try (var world = room()) {
            world.addBox(EntityId.of("step"), pose(0, 0.10, 1.5), new Vec3(2, 0.10, 0.5));
            var motor = new BulletCharacterMotor(world, CharacterConfig.defaults());
            var state = new CharacterState(new Vec3(0, 0.01, 0), Vec3.ZERO, true, false);
            double highest = 0;
            for (int i = 0; i < 35; i++) {
                state = motor.step(state, new CharacterIntent(new Vec3(0, 0, 1), false, false), 1.0 / 60);
                highest = Math.max(highest, state.feet().y());
            }
            assertTrue(state.feet().z() > 2.0, "must pass low step: " + state);
            assertTrue(highest > 0.18, "must rise onto step");
        }
        for (double degrees : new double[]{25, 65}) {
            try (var world = new GameplayBulletWorld(8)) {
                double angle = Math.toRadians(degrees);
                world.addBox(EntityId.of("floor"), pose(0, -0.5, 0), new Vec3(20, 0.5, 20));
                world.addBox(EntityId.of("slope"), new Transform3D(new Vec3(0, 0.9, 3),
                        new QuaternionValue(-Math.sin(angle / 2), 0, 0, Math.cos(angle / 2))), new Vec3(2, 0.1, 2));
                var motor = new BulletCharacterMotor(world, CharacterConfig.defaults());
                var state = new CharacterState(new Vec3(0, 0.01, 0), Vec3.ZERO, true, false);
                for (int i = 0; i < 65; i++) state = motor.step(state,
                        new CharacterIntent(new Vec3(0, 0, 1), false, false), 1.0 / 60);
                if (degrees == 25) assertTrue(state.feet().y() > 0.3, "must climb slope: " + state);
                else assertTrue(state.feet().y() < 0.2, "must reject steep slope: " + state);
            }
        }
    }
    private static GameplayBulletWorld room() {
        var world = new GameplayBulletWorld(8);
        world.addBox(EntityId.of("floor"), pose(0, -0.5, 0), new Vec3(20, 0.5, 20));
        world.addBox(EntityId.of("wall"), pose(0, 2, 5), new Vec3(20, 2, 0.5));
        return world;
    }
    private static Transform3D pose(double x, double y, double z) {
        return new Transform3D(new Vec3(x, y, z), new QuaternionValue(0, 0, 0, 1));
    }
}
